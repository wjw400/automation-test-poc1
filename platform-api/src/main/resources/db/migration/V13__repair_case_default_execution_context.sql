update platform_case test_case
set default_environment_id = null,
    default_profile_id = null
where test_case.default_environment_id is not null
  and test_case.default_profile_id is not null
  and not exists (
      select 1
      from platform_environment environment
      join platform_profile_environment compatibility
        on compatibility.environment_id = environment.id
      join platform_profile profile
        on profile.id = compatibility.profile_id
      where environment.id = test_case.default_environment_id
        and profile.id = test_case.default_profile_id
        and environment.project_id = test_case.project_id
        and profile.project_id = test_case.project_id
        and upper(environment.status) = 'ACTIVE'
        and profile.enabled = true
  );

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
