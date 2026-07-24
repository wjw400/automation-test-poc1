alter table platform_case
    add column if not exists default_environment_id varchar(64)
        references platform_environment(id) on delete set null,
    add column if not exists default_profile_id varchar(64)
        references platform_profile(id) on delete set null;

with latest_run as (
    select
        run.case_id,
        run.environment_id,
        run.profile_id,
        row_number() over (
            partition by run.case_id
            order by run.started_at desc, run.id desc
        ) as position
    from platform_run run
    join platform_profile_environment compatibility
      on compatibility.profile_id = run.profile_id
     and compatibility.environment_id = run.environment_id
)
update platform_case test_case
set default_environment_id = latest_run.environment_id,
    default_profile_id = latest_run.profile_id
from latest_run
where latest_run.case_id = test_case.id
  and latest_run.position = 1
  and test_case.default_environment_id is null
  and test_case.default_profile_id is null;

with project_default as (
    select project_id, environment_id, profile_id
    from (
        select
            environment.project_id,
            environment.id as environment_id,
            profile.id as profile_id,
            row_number() over (
                partition by environment.project_id
                order by environment.name, profile.name
            ) as position
        from platform_environment environment
        join platform_profile_environment compatibility
          on compatibility.environment_id = environment.id
        join platform_profile profile
          on profile.id = compatibility.profile_id
         and profile.enabled = true
        where upper(environment.status) = 'ACTIVE'
    ) ranked_default
    where position = 1
)
update platform_case test_case
set default_environment_id = project_default.environment_id,
    default_profile_id = project_default.profile_id
from project_default
where project_default.project_id = test_case.project_id
  and test_case.default_environment_id is null
  and test_case.default_profile_id is null;

create index if not exists idx_platform_case_default_environment
    on platform_case(default_environment_id);

create index if not exists idx_platform_case_default_profile
    on platform_case(default_profile_id);
