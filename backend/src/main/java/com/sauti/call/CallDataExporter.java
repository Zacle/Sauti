package com.sauti.call;

import com.sauti.tenant.application.port.WorkspaceDataExporter;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Calls, with summaries and transcripts, in a workspace data export. Recordings are linked, not embedded. */
@Component
class CallDataExporter implements WorkspaceDataExporter {
    private final CallRepository calls;

    CallDataExporter(CallRepository calls) {
        this.calls = calls;
    }

    @Override
    public String section() {
        return "calls";
    }

    @Override
    @Transactional(readOnly = true)
    public Object export(UUID workspaceId) {
        return calls.findAllByTenantIdOrderByStartedAtDesc(workspaceId).stream().map(call -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", call.getId());
            row.put("agentId", call.getAgent() == null ? null : call.getAgent().getId());
            row.put("direction", call.getDirection());
            row.put("callerNumber", call.getCallerNumber());
            row.put("startedAt", call.getStartedAt());
            row.put("endedAt", call.getEndedAt());
            row.put("durationSeconds", call.getDurationSeconds());
            row.put("language", call.getLanguageDetected());
            row.put("outcome", call.getOutcome());
            row.put("intent", call.getIntent());
            row.put("sentiment", call.getSentiment());
            row.put("summary", call.getCallSummary());
            row.put("transcript", call.getTranscript());
            row.put("hasRecording", call.getRecordingUrl() != null && call.getRecordingPurgedAt() == null);
            return row;
        }).toList();
    }
}
