alter table platform_agent_task
    add column if not exists pause_reason varchar(64);

alter table platform_agent_task
    add column if not exists pause_context_json text;

alter table platform_agent_task
    add column if not exists continuation_count integer not null default 0;
