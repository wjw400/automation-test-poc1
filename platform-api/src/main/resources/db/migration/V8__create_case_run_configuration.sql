create table if not exists platform_case_run_configuration (
    id varchar(64) primary key,
    case_id varchar(64) not null references platform_case(id) on delete cascade,
    name varchar(200) not null,
    environment_id varchar(64) references platform_environment(id) on delete set null,
    profile_id varchar(64) references platform_profile(id) on delete set null,
    is_default boolean not null default false,
    enabled boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

insert into platform_case_run_configuration (
    id,
    case_id,
    name,
    environment_id,
    profile_id,
    is_default,
    enabled,
    sort_order,
    created_at,
    updated_at
)
select
    md5(test_case.id || ':default-run-configuration'),
    test_case.id,
    concat(
        coalesce(environment.name, 'No environment'),
        ' / ',
        coalesce(profile.name, 'No profile')
    ),
    test_case.default_environment_id,
    test_case.default_profile_id,
    true,
    true,
    1,
    current_timestamp,
    current_timestamp
from platform_case test_case
left join platform_environment environment on environment.id = test_case.default_environment_id
left join platform_profile profile on profile.id = test_case.default_profile_id
where test_case.default_environment_id is not null
   or test_case.default_profile_id is not null
on conflict (id) do nothing;

create unique index if not exists uq_platform_case_run_configuration_name
    on platform_case_run_configuration(case_id, lower(name));

create unique index if not exists uq_platform_case_run_configuration_default
    on platform_case_run_configuration(case_id)
    where is_default;

create index if not exists idx_platform_case_run_configuration_case
    on platform_case_run_configuration(case_id, sort_order);

alter table platform_run
    add column if not exists run_configuration_id varchar(64),
    add column if not exists run_configuration_name varchar(200);

alter table platform_case
    drop constraint if exists fk_platform_case_default_environment,
    drop constraint if exists fk_platform_case_default_profile;

drop index if exists idx_platform_case_default_environment;
drop index if exists idx_platform_case_default_profile;

alter table platform_case
    drop column if exists default_environment_id,
    drop column if exists default_profile_id;
