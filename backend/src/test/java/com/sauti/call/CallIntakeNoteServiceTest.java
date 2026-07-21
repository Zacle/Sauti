package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallIntakeNoteServiceTest {

    @Test
    void semanticSessionStateOverridesLegacyTranscriptPatternExtraction() {
        var repository = mock(CallTurnRepository.class);
        var sessions = mock(com.sauti.session.CallSessionStore.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTwilioCallSid()).thenReturn("semantic-session");
        when(sessions.conversationState("semantic-session")).thenReturn(Optional.of(
                new com.sauti.session.ConversationState(
                        Map.of("caller_name", "Zachary", "appointment_name", "Zachary"),
                        "self", "active", 3
                )
        ));

        var service = new CallIntakeNoteService(repository, sessions);

        assertThat(service.notes(call, "可以更正我刚才说的名字吗？"))
                .containsEntry("caller_name", "Zachary")
                .containsEntry("appointment_name", "Zachary")
                .containsEntry("booking_subject", "self")
                .containsEntry("conversation_state_revision", "3");
        assertThat(service.promptBlock(call, "different wording"))
                .contains("AUTHORITATIVE SEMANTIC CALL STATE")
                .contains("latest explicit caller correction wins");
    }

    @Test
    void retainsEarlyCallerDetailsAcrossTheCompleteCall() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var history = List.of(
                turn("mon nom c'est Zachary Zuyuki", "Quelle est votre date de naissance ?"),
                turn("le trois septembre mille-neuf-cent-nonante-cinq", "Quel numéro de téléphone puis-je utiliser ?"),
                turn("zéro un un un cinq sept cinq trois quatre quatre un", "Est-ce bien cela ?"),
                turn("oui c'est bien ça", "Quel est le motif de votre visite ?"),
                turn("une consultation", "Est-ce que mercredi à 16 heures vous conviendrait ?"),
                turn("oui pas de problème", "Parfait, je note mercredi à 16 heures.")
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(history);

        var notes = new CallIntakeNoteService(repository).notes(call, "");

        assertThat(notes).containsEntry("caller_name", "Zachary Zuyuki")
                .containsEntry("date_of_birth_spoken", "le trois septembre mille-neuf-cent-nonante-cinq")
                .containsEntry("caller_phone", "01115753441")
                .containsEntry("service_or_reason", "consultation")
                .containsEntry("preferred_day", "mercredi")
                .containsEntry("preferred_time", "16:00");
    }

    @Test
    void replacesARejectedPhoneAndAppendsShortContinuationDigits() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var history = List.of(
                turn("zéro cent onze cinq cent septante-cinq trois quatre quatre un", "Est-ce bien cela ?"),
                turn("non ça commence par zéro cent onze", "Redonnez-moi le numéro complet."),
                turn("zéro un un un cinq sept cinq trois quatre", "Vous avez dit zéro un un un cinq sept cinq trois quatre ?"),
                turn("quatre un", "Merci.")
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(history);

        assertThat(new CallIntakeNoteService(repository).notes(call, ""))
                .containsEntry("caller_phone", "01115753441");
    }

    @Test
    void treatsRepeatedSttAAsOnesWithoutTreatingIlYAAsADigit() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var history = List.of(
                turn("zéro un un sept cinq trois quatre quatre un", "Est-ce correct ?"),
                turn("non il y a trois un", "Redonnez-moi le numéro complet."),
                turn("le numéro c'est zéro", "Je vous écoute."),
                turn("a a un cinq sept cinq trois quatre quatre un", "Merci.")
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(history);

        assertThat(new CallIntakeNoteService(repository).notes(call, ""))
                .containsEntry("caller_phone", "01115753441")
                .containsEntry("phone_repetition_hint", "the number contains three consecutive 1 digits");
    }

    @Test
    void replacesTheOldNumberWhenCallerRestartsThenAppendsTheRemainingDigits() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var history = List.of(
                turn("mon numero est zero un un cinq sept cinq trois quatre quatre un", "Est-ce exact ?"),
                turn("non c'est zero un un un", "Pouvez-vous me donner a nouveau tout le numero lentement ?"),
                turn("zero un un un", "Merci, je note zero, un, un, un. Pouvez-vous continuer ?")
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(history);

        assertThat(new CallIntakeNoteService(repository).notes(call, "5-6-5-3-4-4-1"))
                .containsEntry("caller_phone", "01115653441");
    }

    @Test
    void preservesAnEnglishRelativeDayAndExactPmTime() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(List.of());

        assertThat(new CallIntakeNoteService(repository).notes(call, "Tomorrow 08:00 p.m."))
                .containsEntry("preferred_day", "tomorrow")
                .containsEntry("preferred_time", "20:00");
    }

    @Test
    void keepsOnlyTheLiteralNameBeforeTheCallerContinuesTheirRequest() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(List.of());

        assertThat(new CallIntakeNoteService(repository).notes(
                call, "My name is Fatou and I want to book a consultation tomorrow."
        )).containsEntry("caller_name", "Fatou");
    }

    @Test
    void keepsTheSpeakerSeparateFromThePersonReceivingTheService() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var history = List.of(
                turn("Hello, my name is Zachary.", "How can I help?"),
                turn("I would like to book for my wife.", "What service would she like?"),
                turn("A woman hairstyle.", "What phone number can we use to reach her?"),
                turn("010-575-3441", "What date and time would she prefer?"),
                turn("Next Thursday at 1 p.m.", "Could I have your wife's name for the booking?")
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(history);

        var service = new CallIntakeNoteService(repository);
        var notes = service.notes(call, "Alexandra");

        assertThat(notes)
                .containsEntry("caller_name", "Zachary")
                .containsEntry("booking_for_relation", "wife")
                .containsEntry("appointment_name", "Alexandra")
                .containsEntry("service_type", "A woman hairstyle")
                .containsEntry("caller_phone", "0105753441")
                .containsEntry("preferred_day", "thursday")
                .containsEntry("preferred_time", "13:00");
        assertThat(service.promptBlock(call, "Alexandra"))
                .contains("caller_name (person speaking): Zachary")
                .contains("appointment_name (person receiving the booked service): Alexandra");
    }

    @Test
    void leavesTheAppointmentNameMissingAfterAThirdPartyBookingIntent() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var history = List.of(
                turn("My name is Zachary.", "How can I help?"),
                turn("I want to book for my wife.", "What service would she like?")
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(history);

        var service = new CallIntakeNoteService(repository);
        var notes = service.notes(call, "A woman hairstyle.");

        assertThat(notes)
                .containsEntry("caller_name", "Zachary")
                .containsEntry("booking_for_relation", "wife")
                .doesNotContainKey("appointment_name");
        assertThat(service.promptBlock(call, "A woman hairstyle."))
                .contains("booking subject name is still missing")
                .contains("never substitute caller_name for it");
    }

    @Test
    void retainsTheNamedRecipientAndDoesNotAcceptUnclearSpeechAsAPhoneNumber() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var history = List.of(
                turn("Hello, my name is Zachary. I'm calling to book some appointments.",
                        "What service would you like to book?"),
                turn("I would like to book for my wife, Alexandra.",
                        "Which service would she like?"),
                turn("Women hairstyle, but I would like to know how much it costs.",
                        "It costs eight dollars. Could you share Alexandra's phone number?")
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId)).thenReturn(history);

        var service = new CallIntakeNoteService(repository);
        var notes = service.notes(call, "Ba\u015fka?");

        assertThat(notes)
                .containsEntry("caller_name", "Zachary")
                .containsEntry("booking_for_relation", "wife")
                .containsEntry("appointment_name", "Alexandra")
                .containsEntry("service_type", "Women hairstyle")
                .doesNotContainKey("caller_phone");
        assertThat(service.promptBlock(call, "Ba\u015fka?"))
                .contains("CURRENT PENDING FIELD: caller_phone")
                .contains("do not advance to another booking field");
    }

    @Test
    void tracksAnActiveBookingAndThenHonorsTheCallersWithdrawal() {
        var repository = mock(CallTurnRepository.class);
        var call = mock(Call.class);
        var callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        var openingTurn = turn(
                "Hello, my name is Zachary. I'm calling to get an appointment, please.",
                "What service would you like to book?"
        );
        var serviceTurn = turn(
                "I would like to book a men's hairstyle for myself.",
                "What phone number can we use?"
        );
        when(repository.findByCall_IdOrderByTurnIndexAsc(callId))
                .thenReturn(List.of(openingTurn, serviceTurn));

        var service = new CallIntakeNoteService(repository);
        assertThat(service.notes(call, "082-011-0502"))
                .containsEntry("booking_intent", "active");

        var withdrawal = "Everything is wrong, but don't book yet. I will call you back later.";
        assertThat(service.notes(call, withdrawal))
                .containsEntry("booking_intent", "paused");
        assertThat(service.promptBlock(call, withdrawal))
                .contains("BOOKING IS PAUSED BY THE CALLER")
                .contains("Confirm briefly that no booking will be made")
                .contains("close warmly");
    }

    private CallTurn turn(String caller, String agent) {
        var turn = mock(CallTurn.class);
        when(turn.getCallerTranscript()).thenReturn(caller);
        when(turn.getAgentResponse()).thenReturn(agent);
        return turn;
    }
}
