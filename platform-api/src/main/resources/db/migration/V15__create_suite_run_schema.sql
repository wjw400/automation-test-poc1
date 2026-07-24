create table if not exists platform_suite_run (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    suite_id varchar(64) not null references platform_suite(id),
    suite_name varchar(200) not null,
    triggered_by varchar(100) not null,
    status varchar(50) not null,
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone not null,
    duration_ms bigint not null,
    error_message text
);

alter table platform_run
    add column if not exists suite_run_id varchar(64) references platform_suite_run(id) on delete cascade;

alter table platform_run
    add column if not exists suite_case_id varchar(64);

alter table platform_run
    add column if not exists suite_display_name varchar(200);

alter table platform_run
    add column if not exists suite_case_order integer;

create index if not exists idx_platform_suite_run_project on platform_suite_run(project_id);
create index if not exists idx_platform_suite_run_suite on platform_suite_run(suite_id);
create index if not exists idx_platform_run_suite_run on platform_run(suite_run_id);
