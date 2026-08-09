package com.sauti.call;

import com.sauti.reliability.QueueHealthContributor;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecordingQueueHealthContributor implements QueueHealthContributor {
    private static final String CONTROL = "TELNYX-CALL-CONTROL:";
    private static final String LEG = "TELNYX-CALL-LEG:";
    private static final String UNAVAILABLE = "TELNYX-RECORDING-UNAVAILABLE:";
    private final CallRepository calls;

    public RecordingQueueHealthContributor(CallRepository calls) {
        this.calls = calls;
    }

    @Override
    public List<QueueState> snapshot() {
        OffsetDateTime oldest = java.util.stream.Stream.concat(
                        calls.findTop25ByRecordingUrlIsNullAndRecordingSidStartingWithAndEndedAtIsNotNullOrderByEndedAtAsc(CONTROL).stream(),
                        calls.findTop25ByRecordingUrlIsNullAndRecordingSidStartingWithAndEndedAtIsNotNullOrderByEndedAtAsc(LEG).stream())
                .map(Call::getEndedAt).min(OffsetDateTime::compareTo).orElse(null);
        var pending = calls.countByRecordingUrlIsNullAndRecordingSidStartingWithAndEndedAtIsNotNull(CONTROL)
                + calls.countByRecordingUrlIsNullAndRecordingSidStartingWithAndEndedAtIsNotNull(LEG);
        return List.of(new QueueState("recording_reconciliation", "Recording reconciliation",
                pending, 0, calls.countByRecordingSidStartingWith(UNAVAILABLE), oldest));
    }
}
