-- The durable end-of-call record work for each call: fetch a phone call's transcript from the voice
-- provider, then summarise it. One row per call, so a repeated "call ended" event never runs the work
-- twice, and a restart picks up where it stopped. The row stays as "done" to record what happened.
CREATE TABLE call_record_jobs (
    call_id UUID PRIMARY KEY REFERENCES calls(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    stage VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    transcript_status VARCHAR(20),
    analysis_status VARCHAR(20),
    last_error VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_call_record_jobs_due ON call_record_jobs(stage, next_attempt_at);
CREATE INDEX idx_call_record_jobs_tenant ON call_record_jobs(tenant_id, stage);
