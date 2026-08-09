create table voice_startup_measurements (
    id uuid primary key,
    source_key varchar(180) not null unique,
    channel varchar(40) not null,
    latency_ms integer not null,
    measured_at timestamp with time zone not null
);

create index idx_voice_startup_measurements_channel_time
    on voice_startup_measurements (channel, measured_at);
