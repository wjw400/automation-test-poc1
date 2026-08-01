create table if not exists platform_agent_audit_event (
    id varchar(64) primary key,
    task_id varchar(64) not null,
    project_id varchar(64) not null,
    actor_id varchar(100) not null,
    event_type varchar(80) not null,
    payload_json text not null,
    created_at timestamp with time zone not null
);

create index if not exists idx_platform_agent_audit_task
    on platform_agent_audit_event(task_id, created_at);

create index if not exists idx_platform_agent_audit_project_actor
    on platform_agent_audit_event(project_id, actor_id, created_at);

alter table platform_scenario_draft
    add column if not exists created_by varchar(100);

alter table platform_scenario_draft
    add column if not exists agent_task_id varchar(64);

alter table platform_scenario_draft
    add column if not exists last_command_fingerprint varchar(64);

create index if not exists idx_platform_scenario_draft_agent_task
    on platform_scenario_draft(agent_task_id, status);
