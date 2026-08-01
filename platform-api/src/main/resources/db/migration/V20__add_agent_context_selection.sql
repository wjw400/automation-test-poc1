alter table platform_agent_task
    add column if not exists context_json text not null default '{"included":[],"excluded":[]}';
