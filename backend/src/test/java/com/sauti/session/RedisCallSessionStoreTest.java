package com.sauti.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisCallSessionStoreTest {
    @Test
    void recordsAManagedConfirmationOnlyForTheExactRetainedAction() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        var store = new RedisCallSessionStore(redis, new ObjectMapper().findAndRegisterModules(), 7200);
        var session = new CallSession();
        session.setConversationState(new ConversationState(
                Map.of("booking_number", "SAT-AB12CD34"),
                ConversationState.SUBJECT_UNKNOWN,
                ConversationState.INTENT_ACTIVE,
                3
        ));
        session.setPendingAction(new PendingAction(
                "cancel_booking",
                Map.of("booking_number", "SAT-AB12CD34", "caller_phone", "0115752441"),
                3
        ));
        store.create("managed-confirmation", session);

        assertThat(store.recordManagedConfirmation(
                "managed-confirmation",
                "cancel_booking",
                Map.of("booking_number", "SAT-DIFFERENT", "caller_phone", "0115752441")
        )).isFalse();
        assertThat(store.conversationState("managed-confirmation").orElseThrow().revision()).isEqualTo(3);

        assertThat(store.recordManagedConfirmation(
                "managed-confirmation",
                "cancel_booking",
                Map.of("booking_number", "SAT-AB12CD34", "caller_phone", "0115752441")
        )).isTrue();
        var confirmed = store.conversationState("managed-confirmation").orElseThrow();
        assertThat(confirmed.revision()).isEqualTo(4);
        assertThat(confirmed.values()).containsEntry("review_decision", "approved");
    }

    @Test
    void refusesAManagedConfirmationAfterTheRetainedStateHasChanged() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        var store = new RedisCallSessionStore(redis, new ObjectMapper().findAndRegisterModules(), 7200);
        var session = new CallSession();
        session.setConversationState(new ConversationState(
                Map.of("booking_number", "SAT-CHANGED"),
                ConversationState.SUBJECT_UNKNOWN,
                ConversationState.INTENT_ACTIVE,
                5
        ));
        session.setPendingAction(new PendingAction(
                "cancel_booking",
                Map.of("booking_number", "SAT-AB12CD34", "caller_phone", "0115752441"),
                4
        ));
        store.create("stale-managed-confirmation", session);

        assertThat(store.recordManagedConfirmation(
                "stale-managed-confirmation",
                "cancel_booking",
                Map.of("booking_number", "SAT-AB12CD34", "caller_phone", "0115752441")
        )).isFalse();
        var state = store.conversationState("stale-managed-confirmation").orElseThrow();
        assertThat(state.revision()).isEqualTo(5);
        assertThat(state.values()).doesNotContainKey("review_decision");
    }

    @Test
    void consumesOnlyAnExactlyMatchingProposalApprovedOnALaterRevision() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        var store = new RedisCallSessionStore(redis, new ObjectMapper().findAndRegisterModules(), 7200);
        var session = new CallSession();
        session.setConversationState(new ConversationState(
                Map.of("review_decision", "approved"),
                ConversationState.SUBJECT_UNKNOWN,
                ConversationState.INTENT_ACTIVE,
                4
        ));
        session.setPendingAction(new PendingAction(
                "cancel_booking", Map.of("booking_number", "SAT-AB12CD34"), 3
        ));
        store.create("atomic-action", session);

        assertThat(store.consumeConfirmedAction(
                "atomic-action", "cancel_booking", Map.of("booking_number", "SAT-DIFFERENT")
        )).isFalse();
        assertThat(store.pendingAction("atomic-action")).isPresent();

        assertThat(store.consumeConfirmedAction(
                "atomic-action", "cancel_booking", Map.of("booking_number", "SAT-AB12CD34")
        )).isTrue();
        assertThat(store.pendingAction("atomic-action")).isEmpty();
        assertThat(store.consumeConfirmedAction(
                "atomic-action", "cancel_booking", Map.of("booking_number", "SAT-AB12CD34")
        )).isFalse();
    }

    @Test
    void refusesApprovalFromTheSameSemanticRevisionAsTheProposal() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        var store = new RedisCallSessionStore(redis, new ObjectMapper().findAndRegisterModules(), 7200);
        var session = new CallSession();
        session.setConversationState(new ConversationState(
                Map.of("review_decision", "approved"),
                ConversationState.SUBJECT_UNKNOWN,
                ConversationState.INTENT_ACTIVE,
                5
        ));
        session.setPendingAction(new PendingAction("collect_deposit", Map.of("amount", 20), 5));
        store.create("stale-approval", session);

        assertThat(store.consumeConfirmedAction(
                "stale-approval", "collect_deposit", Map.of("amount", 20)
        )).isFalse();
        assertThat(store.pendingAction("stale-approval")).isPresent();
    }
}
