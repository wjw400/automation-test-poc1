alter table platform_case
    add column if not exists revision bigint not null default 0;

create table if not exists platform_scenario_draft (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    scenario_id varchar(64) not null references platform_case(id),
    source_revision bigint not null,
    base_content text not null,
    content text not null,
    base_step_ids_json text not null,
    step_ids_json text not null,
    status varchar(32) not null,
    revision bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_platform_scenario_draft_project
    on platform_scenario_draft(project_id, updated_at desc);

create index if not exists idx_platform_scenario_draft_scenario
    on platform_scenario_draft(scenario_id, updated_at desc);
