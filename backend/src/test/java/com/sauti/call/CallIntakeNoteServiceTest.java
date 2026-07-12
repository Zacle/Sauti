package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallIntakeNoteServiceTest {

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

    private CallTurn turn(String caller, String agent) {
        var turn = mock(CallTurn.class);
        when(turn.getCallerTranscript()).thenReturn(caller);
        when(turn.getAgentResponse()).thenReturn(agent);
        return turn;
    }
}
