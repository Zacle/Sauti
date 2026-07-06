package com.sauti.session;

import com.sauti.call.Call;
import com.sauti.llm.ConversationMessage;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CallSession {
    private UUID callId;
    private String callSid;
    private String streamSid;
    private UUID agentId;
    private UUID tenantId;
    private String callerPhone;
    private List<ConversationMessage> conversationHistory = new ArrayList<>();
    private BookingDraft pendingBookingDraft;
    private String agentSpeakingMarkName;
    private boolean speaking;
    private boolean currentTurnInterrupted;
    private int turnCount;
    private OffsetDateTime startedAt;
    private OffsetDateTime lastActivityAt;

    public CallSession() {
    }

    public static CallSession fromCall(Call call, String systemPrompt) {
        var session = new CallSession();
        session.callId = call.getId();
        session.callSid = call.getTwilioCallSid();
        session.agentId = call.getAgent().getId();
        session.tenantId = call.getTenant().getId();
        session.callerPhone = call.getCallerNumber();
        session.startedAt = call.getStartedAt();
        session.lastActivityAt = OffsetDateTime.now();
        session.upsertSystemMessage(systemPrompt);
        return session;
    }

    public void touch() {
        this.lastActivityAt = OffsetDateTime.now();
    }

    public void upsertSystemMessage(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return;
        }
        if (!conversationHistory.isEmpty() && "system".equals(conversationHistory.get(0).role())) {
            conversationHistory.set(0, new ConversationMessage("system", systemPrompt));
        } else {
            conversationHistory.add(0, new ConversationMessage("system", systemPrompt));
        }
        touch();
    }

    public UUID getCallId() {
        return callId;
    }

    public void setCallId(UUID callId) {
        this.callId = callId;
    }

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
    }

    public String getStreamSid() {
        return streamSid;
    }

    public void setStreamSid(String streamSid) {
        this.streamSid = streamSid;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public void setAgentId(UUID agentId) {
        this.agentId = agentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getCallerPhone() {
        return callerPhone;
    }

    public void setCallerPhone(String callerPhone) {
        this.callerPhone = callerPhone;
    }

    public List<ConversationMessage> getConversationHistory() {
        return conversationHistory;
    }

    public void setConversationHistory(List<ConversationMessage> conversationHistory) {
        this.conversationHistory = conversationHistory == null ? new ArrayList<>() : new ArrayList<>(conversationHistory);
    }

    public BookingDraft getPendingBookingDraft() {
        return pendingBookingDraft;
    }

    public void setPendingBookingDraft(BookingDraft pendingBookingDraft) {
        this.pendingBookingDraft = pendingBookingDraft;
    }

    public String getAgentSpeakingMarkName() {
        return agentSpeakingMarkName;
    }

    public void setAgentSpeakingMarkName(String agentSpeakingMarkName) {
        this.agentSpeakingMarkName = agentSpeakingMarkName;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public void setSpeaking(boolean speaking) {
        this.speaking = speaking;
    }

    public boolean isCurrentTurnInterrupted() {
        return currentTurnInterrupted;
    }

    public void setCurrentTurnInterrupted(boolean currentTurnInterrupted) {
        this.currentTurnInterrupted = currentTurnInterrupted;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(OffsetDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
