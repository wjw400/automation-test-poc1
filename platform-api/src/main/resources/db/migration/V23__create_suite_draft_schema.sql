alter table platform_suite
    add column if not exists revision bigint not null default 0;

create table if not exists platform_suite_draft (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    suite_id varchar(64) not null references platform_suite(id) on delete cascade,
    source_revision bigint not null,
    base_content text not null,
    content text not null,
    status varchar(32) not null,
    last_command_fingerprint varchar(64),
    revision bigint not null,
    created_by varchar(100),
    agent_task_id varchar(64),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_platform_suite_draft_project
    on platform_suite_draft(project_id, updated_at desc);

create index if not exists idx_platform_suite_draft_suite
    on platform_suite_draft(suite_id, updated_at desc);

create index if not exists idx_platform_suite_draft_agent_task
    on platform_suite_draft(agent_task_id, status);
