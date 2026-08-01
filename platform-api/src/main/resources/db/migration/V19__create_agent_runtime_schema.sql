create table if not exists platform_agent_task (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    title varchar(200) not null,
    status varchar(32) not null,
    target_type varchar(32),
    target_id varchar(64),
    permission_mode varchar(32) not null,
    runtime_type varchar(32) not null,
    model_id varchar(200),
    created_by varchar(100) not null,
    iteration_count integer not null,
    token_budget bigint not null,
    revision bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    completed_at timestamp with time zone
);

create table if not exists platform_agent_message (
    id varchar(64) primary key,
    task_id varchar(64) not null references platform_agent_task(id) on delete cascade,
    sequence bigint not null,
    role varchar(32) not null,
    content_json text not null,
    created_at timestamp with time zone not null,
    constraint uq_platform_agent_message_sequence unique (task_id, sequence)
);

create table if not exists platform_agent_event (
    id varchar(64) primary key,
    task_id varchar(64) not null references platform_agent_task(id) on delete cascade,
    sequence bigint not null,
    event_type varchar(64) not null,
    payload_json text not null,
    created_at timestamp with time zone not null,
    constraint uq_platform_agent_event_sequence unique (task_id, sequence)
);

create table if not exists platform_agent_artifact (
    id varchar(64) primary key,
    task_id varchar(64) not null references platform_agent_task(id) on delete cascade,
    artifact_type varchar(64) not null,
    target_type varchar(32),
    target_id varchar(64),
    base_revision bigint,
    status varchar(32) not null,
    content_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_platform_agent_task_project
    on platform_agent_task(project_id, updated_at desc);

create index if not exists idx_platform_agent_message_task
    on platform_agent_message(task_id, sequence);

create index if not exists idx_platform_agent_event_task
    on platform_agent_event(task_id, sequence);

create index if not exists idx_platform_agent_artifact_task
    on platform_agent_artifact(task_id, created_at);
