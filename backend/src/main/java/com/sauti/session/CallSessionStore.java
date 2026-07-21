package com.sauti.session;

import com.sauti.llm.ConversationMessage;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.util.List;
import java.util.Optional;

public interface CallSessionStore {
    void create(String callSid, CallSession session);

    void createIfAbsent(String callSid, CallSession session);

    Optional<CallSession> get(String callSid);

    void upsertSystemMessage(String callSid, String systemPrompt);

    void updateStreamSid(String callSid, String streamSid);

    void appendUserMessage(String callSid, String transcript);

    void appendAssistantMessage(String callSid, String text, List<LlmToolCall> toolCalls);

    void appendToolResult(String callSid, LlmToolResult result);

    List<ConversationMessage> conversationHistory(String callSid);

    Optional<ConversationState> conversationState(String callSid);

    void updateConversationState(String callSid, ConversationState state);

    Optional<BookingDraft> pendingBooking(String callSid);

    void updatePendingBooking(String callSid, BookingDraft draft);

    void setSpeaking(String callSid, boolean speaking, String markName);

    void markInterrupted(String callSid);

    boolean consumeInterrupted(String callSid);

    Optional<String> snapshotForArchive(String callSid);

    void delete(String callSid);
}
