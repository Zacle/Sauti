package com.sauti.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.ConversationMessage;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisCallSessionStore implements CallSessionStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCallSessionStore.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Map<String, CallSession> fallback = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CallSession> eldest) {
            return size() > 1000;
        }
    });
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public RedisCallSessionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${sauti.call-session.ttl-seconds:7200}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void create(String callSid, CallSession session) {
        mutate(callSid, ignored -> session);
    }

    @Override
    public void createIfAbsent(String callSid, CallSession session) {
        mutate(callSid, existing -> existing == null ? session : existing);
    }

    @Override
    public Optional<CallSession> get(String callSid) {
        return Optional.ofNullable(read(callSid));
    }

    @Override
    public void upsertSystemMessage(String callSid, String systemPrompt) {
        mutate(callSid, session -> {
            if (session != null) {
                session.upsertSystemMessage(systemPrompt);
            }
            return session;
        });
    }

    @Override
    public void updateStreamSid(String callSid, String streamSid) {
        mutate(callSid, session -> {
            if (session != null) {
                session.setStreamSid(streamSid);
                session.touch();
            }
            return session;
        });
    }

    @Override
    public void appendUserMessage(String callSid, String transcript) {
        mutate(callSid, session -> {
            if (session != null && transcript != null && !transcript.isBlank()) {
                session.getConversationHistory().add(new ConversationMessage("user", transcript));
                session.setTurnCount(session.getTurnCount() + 1);
                session.touch();
            }
            return session;
        });
    }

    @Override
    public void appendAssistantMessage(String callSid, String text, List<LlmToolCall> toolCalls) {
        mutate(callSid, session -> {
            if (session != null) {
                session.getConversationHistory().add(ConversationMessage.assistantToolCalls(text == null ? "" : text, toolCalls));
                session.touch();
            }
            return session;
        });
    }

    @Override
    public void appendToolResult(String callSid, LlmToolResult result) {
        mutate(callSid, session -> {
            if (session != null && result != null) {
                session.getConversationHistory().add(ConversationMessage.toolResult(result));
                session.touch();
            }
            return session;
        });
    }

    @Override
    public List<ConversationMessage> conversationHistory(String callSid) {
        return get(callSid)
                .map(CallSession::getConversationHistory)
                .map(List::copyOf)
                .orElse(List.of());
    }

    @Override
    public Optional<BookingDraft> pendingBooking(String callSid) {
        return get(callSid).map(CallSession::getPendingBookingDraft);
    }

    @Override
    public void updatePendingBooking(String callSid, BookingDraft draft) {
        mutate(callSid, session -> {
            if (session != null) {
                session.setPendingBookingDraft(draft);
                session.touch();
            }
            return session;
        });
    }

    @Override
    public void setSpeaking(String callSid, boolean speaking, String markName) {
        mutate(callSid, session -> {
            if (session != null) {
                session.setSpeaking(speaking);
                session.setAgentSpeakingMarkName(markName);
                session.touch();
            }
            return session;
        });
    }

    @Override
    public void markInterrupted(String callSid) {
        mutate(callSid, session -> {
            if (session != null) {
                session.setCurrentTurnInterrupted(true);
                session.setSpeaking(false);
                session.setAgentSpeakingMarkName("");
                session.touch();
            }
            return session;
        });
    }

    @Override
    public boolean consumeInterrupted(String callSid) {
        if (callSid == null || callSid.isBlank()) {
            return false;
        }
        var interrupted = new boolean[1];
        mutate(callSid, session -> {
            if (session != null) {
                interrupted[0] = session.isCurrentTurnInterrupted();
                session.setCurrentTurnInterrupted(false);
                session.touch();
            }
            return session;
        });
        return interrupted[0];
    }

    @Override
    public Optional<String> snapshotForArchive(String callSid) {
        var lock = lock(callSid);
        lock.lock();
        try {
            var session = read(callSid);
            if (session == null) {
                return Optional.empty();
            }
            return Optional.of(serialize(session));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String callSid) {
        if (callSid == null || callSid.isBlank()) {
            return;
        }
        var lock = lock(callSid);
        lock.lock();
        try {
            deleteInternal(callSid);
        } finally {
            lock.unlock();
            locks.remove(callSid, lock);
        }
    }

    private void mutate(String callSid, SessionMutation mutation) {
        if (callSid == null || callSid.isBlank()) {
            return;
        }
        var lock = lock(callSid);
        lock.lock();
        try {
            var existing = read(callSid);
            var updated = mutation.apply(existing);
            if (updated == null) {
                return;
            }
            updated.setCallSid(callSid);
            write(callSid, updated);
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lock(String callSid) {
        return locks.computeIfAbsent(callSid, ignored -> new ReentrantLock());
    }

    private void evictExpiredFallbacks() {
        var cutoff = OffsetDateTime.now().minus(ttl);
        synchronized (fallback) {
            fallback.entrySet().removeIf(entry ->
                    entry.getValue().getLastActivityAt() != null
                            && entry.getValue().getLastActivityAt().isBefore(cutoff));
        }
    }

    private CallSession read(String callSid) {
        if (callSid == null || callSid.isBlank()) {
            return null;
        }
        try {
            var json = redisTemplate.opsForValue().get(key(callSid));
            if (json == null || json.isBlank()) {
                evictExpiredFallbacks();
                return fallback.get(callSid);
            }
            return objectMapper.readValue(json, CallSession.class);
        } catch (Exception exception) {
            LOGGER.warn("Redis call session read failed for callSid={}, using in-memory fallback", callSid, exception);
            evictExpiredFallbacks();
            return fallback.get(callSid);
        }
    }

    private void write(String callSid, CallSession session) {
        evictExpiredFallbacks();
        fallback.put(callSid, session);
        try {
            redisTemplate.opsForValue().set(key(callSid), serialize(session), ttl);
        } catch (Exception exception) {
            LOGGER.warn("Redis call session write failed for callSid={}, using in-memory fallback", callSid, exception);
        }
    }

    private void deleteInternal(String callSid) {
        fallback.remove(callSid);
        try {
            redisTemplate.delete(key(callSid));
        } catch (Exception exception) {
            LOGGER.warn("Redis call session delete failed for callSid={}", callSid, exception);
        }
    }

    private String serialize(CallSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize call session", exception);
        }
    }

    private String key(String callSid) {
        return "call:session:" + callSid;
    }

    private interface SessionMutation {
        CallSession apply(CallSession session);
    }
}
