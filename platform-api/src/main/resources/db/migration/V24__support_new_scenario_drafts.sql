alter table platform_scenario_draft
    alter column scenario_id drop not null;

alter table platform_scenario_draft
    add column if not exists source_suite_id varchar(64) references platform_suite(id);

create index if not exists idx_platform_scenario_draft_source_suite
    on platform_scenario_draft(source_suite_id, updated_at desc);
