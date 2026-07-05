create table if not exists platform_suite (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    name varchar(200) not null,
    description text,
    status varchar(50) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists platform_suite_case (
    id varchar(64) primary key,
    suite_id varchar(64) not null references platform_suite(id) on delete cascade,
    case_id varchar(64) not null references platform_case(id),
    alias varchar(200),
    case_order integer not null,
    enabled boolean not null,
    variable_overrides_json text not null,
    environment_id varchar(64),
    profile_id varchar(64)
);

create index if not exists idx_platform_suite_project on platform_suite(project_id);
create index if not exists idx_platform_suite_case_suite on platform_suite_case(suite_id);
create index if not exists idx_platform_suite_case_case on platform_suite_case(case_id);
