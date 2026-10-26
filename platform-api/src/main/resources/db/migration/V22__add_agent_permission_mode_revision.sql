alter table platform_agent_task
    add column if not exists permission_mode_revision integer not null default 1;
