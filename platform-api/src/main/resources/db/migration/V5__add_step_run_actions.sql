alter table platform_step_run
    add column if not exists actions_json text;
