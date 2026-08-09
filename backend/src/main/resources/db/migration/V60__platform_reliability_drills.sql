create table platform_reliability_drills (
    id uuid primary key,
    incident_id uuid not null unique references platform_reliability_incidents(id),
    status varchar(20) not null,
    initiated_by varchar(254) not null,
    initiated_at timestamp with time zone not null,
    acknowledged_by varchar(254),
    acknowledged_at timestamp with time zone,
    resolved_by varchar(254),
    resolved_at timestamp with time zone
);

create index idx_platform_reliability_drills_started
    on platform_reliability_drills (initiated_at desc);
