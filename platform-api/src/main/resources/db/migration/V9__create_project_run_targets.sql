create table if not exists platform_run_target (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id) on delete cascade,
    name varchar(200) not null,
    environment_id varchar(64) references platform_environment(id) on delete set null,
    profile_id varchar(64) references platform_profile(id) on delete set null,
    enabled boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

alter table platform_case_run_configuration
    add column if not exists run_target_id varchar(64);

with ranked_configurations as (
    select
        configuration.*,
        test_case.project_id,
        test_case.code as case_code,
        count(*) over (
            partition by test_case.project_id, lower(configuration.name)
        ) as duplicate_name_count
    from platform_case_run_configuration configuration
    join platform_case test_case on test_case.id = configuration.case_id
    where test_case.status <> 'DELETED'
)
insert into platform_run_target (
    id,
    project_id,
    name,
    environment_id,
    profile_id,
    enabled,
    created_at,
    updated_at
)
select
    md5('run-target:' || configuration.id),
    configuration.project_id,
    case
        when configuration.duplicate_name_count = 1 then configuration.name
        else left(configuration.name, 150)
            || ' (' || left(configuration.case_code, 24)
            || '-' || left(configuration.id, 8) || ')'
    end,
    configuration.environment_id,
    configuration.profile_id,
    configuration.enabled,
    configuration.created_at,
    configuration.updated_at
from ranked_configurations configuration
on conflict (id) do nothing;

update platform_case_run_configuration configuration
set run_target_id = md5('run-target:' || configuration.id)
from platform_case test_case
where test_case.id = configuration.case_id
  and test_case.status <> 'DELETED';

alter table platform_run
    add column if not exists run_target_id varchar(64);

update platform_run run
set run_target_id = md5('run-target:' || run.run_configuration_id)
where run.run_configuration_id is not null
  and exists (
      select 1
      from platform_run_target target
      where target.id = md5('run-target:' || run.run_configuration_id)
  );

delete from platform_case_run_configuration configuration
using platform_case test_case
where test_case.id = configuration.case_id
  and test_case.status = 'DELETED';

alter table platform_case_run_configuration
    alter column run_target_id set not null;

alter table platform_case_run_configuration
    add constraint fk_platform_case_run_configuration_target
    foreign key (run_target_id) references platform_run_target(id) on delete restrict;

drop index if exists uq_platform_case_run_configuration_name;

alter table platform_case_run_configuration
    drop column if exists name,
    drop column if exists environment_id,
    drop column if exists profile_id;

create unique index if not exists uq_platform_run_target_name
    on platform_run_target(project_id, lower(name));

create index if not exists idx_platform_run_target_project
    on platform_run_target(project_id, name);

create unique index if not exists uq_platform_case_run_configuration_target
    on platform_case_run_configuration(case_id, run_target_id);
