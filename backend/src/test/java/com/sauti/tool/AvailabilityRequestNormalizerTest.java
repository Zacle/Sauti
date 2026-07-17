package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AvailabilityRequestNormalizerTest {
    @Test
    void resolvesFrenchTomorrowAndNoonFromTheCallerTranscript() {
        var call = callInUtc();
        var normalized = AvailabilityRequestNormalizer.normalize(call, new LlmToolCall(
                "tool-1", "check_availability", Map.of()
        ), "Je voudrais une consultation demain à midi.");

        assertThat(normalized.arguments())
                .containsEntry("date", LocalDate.now(ZoneId.of("UTC")).plusDays(1).toString())
                .containsEntry("time_preference", "12:00")
                .containsEntry("duration_minutes", 60)
                .containsEntry("timezone", "UTC");
    }

    @Test
    void resolvesEnglishWeekdayAndDottedPmWithoutChangingTheHour() {
        var call = callInUtc();
        var normalized = AvailabilityRequestNormalizer.normalize(call, new LlmToolCall(
                "tool-2", "check_availability", Map.of("date", "Monday", "time_preference", "5 p.m.")
        ), "I would like Monday at 5 p.m.");

        var expectedMonday = LocalDate.now(ZoneId.of("UTC"))
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        assertThat(normalized.arguments())
                .containsEntry("date", expectedMonday.toString())
                .containsEntry("time_preference", "17:00");
    }

    @Test
    void preservesAnExactIsoDateAndTwentyFourHourTime() {
        var normalized = AvailabilityRequestNormalizer.normalize(callInUtc(), new LlmToolCall(
                "tool-3", "check_availability", Map.of("date", "2026-07-20", "time_preference", "20:00")
        ), "tomorrow at eight");

        assertThat(normalized.arguments())
                .containsEntry("date", "2026-07-20")
                .containsEntry("time_preference", "20:00");
    }

    private Call callInUtc() {
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getAgent()).thenReturn(agent);
        when(agent.getTimezone()).thenReturn("UTC");
        return call;
    }
}
