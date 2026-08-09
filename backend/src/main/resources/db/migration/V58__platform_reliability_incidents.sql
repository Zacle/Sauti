CREATE TABLE platform_reliability_incidents (
    id UUID PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    first_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notified_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notified_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_reliability_incidents_status_detected
    ON platform_reliability_incidents(status, first_detected_at DESC);
CREATE INDEX idx_reliability_incidents_provider_status
    ON platform_reliability_incidents(provider, status);
