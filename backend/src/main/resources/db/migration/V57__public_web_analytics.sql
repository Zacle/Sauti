CREATE TABLE public_web_analytics_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    path VARCHAR(300) NOT NULL,
    visitor_hash VARCHAR(64) NOT NULL,
    referrer_host VARCHAR(160),
    utm_source VARCHAR(100),
    utm_medium VARCHAR(100),
    utm_campaign VARCHAR(160),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_public_web_analytics_occurred ON public_web_analytics_events(occurred_at);
CREATE INDEX idx_public_web_analytics_event_occurred ON public_web_analytics_events(event_type, occurred_at);
CREATE INDEX idx_public_web_analytics_visitor_occurred ON public_web_analytics_events(visitor_hash, occurred_at);

