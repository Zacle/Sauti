CREATE TABLE agent_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    category VARCHAR(80) NOT NULL,
    greeting_message TEXT NOT NULL,
    system_prompt TEXT NOT NULL,
    default_language VARCHAR(10) NOT NULL,
    supported_languages VARCHAR(255) NOT NULL,
    configuration_json TEXT NOT NULL DEFAULT '{}',
    version INT NOT NULL DEFAULT 1,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agent_templates_tenant ON agent_templates(tenant_id, updated_at DESC);
CREATE INDEX idx_agent_templates_system ON agent_templates(published, category);

INSERT INTO agent_templates (
    id, tenant_id, name, description, category, greeting_message, system_prompt,
    default_language, supported_languages, configuration_json, version, published,
    created_at, updated_at
) VALUES
(
    '10000000-0000-0000-0000-000000000001', NULL,
    'Appointment Booker',
    'Checks availability, collects caller details, and confirms bookings.',
    'Appointments',
    'Hello, thank you for calling. I can help you schedule or manage an appointment. How may I assist you?',
    'You are a professional appointment booking assistant. Understand what the caller wants to book, collect their name, preferred date and time, contact details, and relevant notes. Check availability before offering or confirming a time. Read the final details back and ask for confirmation. Be warm, concise, and professional. Ask one question at a time. Never invent availability. Offer a human transfer when the request is outside your scope.',
    'sw', 'sw,en',
    '{"bookingEnabled":true,"tools":["calendar_availability","calendar_booking","human_transfer"]}',
    1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000002', NULL,
    'Customer Support',
    'Answers common questions, troubleshoots issues, and escalates safely.',
    'Support',
    'Hello, thank you for calling support. Tell me what you need help with and I will guide you.',
    'You are a calm customer support voice agent. Identify the caller issue and confirm your understanding. Use only approved knowledge to provide accurate, concise guidance. Summarize completed steps before ending the call. Ask one question at a time. Never guess when information is missing. Escalate urgent, sensitive, or unresolved requests to a human.',
    'sw', 'sw,en',
    '{"bookingEnabled":false,"tools":["knowledge_search","human_transfer"]}',
    1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000003', NULL,
    'Lead Qualifier',
    'Qualifies enquiries and routes opportunities to the right team.',
    'Sales',
    'Hello, thanks for your interest. I have a few quick questions so I can connect you with the right person.',
    'You are a helpful lead qualification agent. Learn the caller need, location, timeframe, and budget range. Ask only questions relevant to the request. Summarize the opportunity and route it to the appropriate team. Be conversational rather than interrogative. Do not make pricing or delivery promises. Ask permission before collecting contact details.',
    'en', 'en,sw',
    '{"bookingEnabled":true,"tools":["calendar_booking","human_transfer","webhook"]}',
    1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000004', NULL,
    'Support Callback',
    'Captures an issue and schedules a callback with the correct specialist.',
    'Support',
    'Hello, I can arrange a callback with the right member of our team. What can we help you with?',
    'You arrange customer support callbacks. Collect the caller name, phone number, issue summary, urgency, and preferred callback time. Confirm every detail before creating the callback. For emergencies or high-risk requests, transfer to a human immediately.',
    'en', 'en,sw',
    '{"bookingEnabled":true,"tools":["calendar_booking","human_transfer"]}',
    1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
