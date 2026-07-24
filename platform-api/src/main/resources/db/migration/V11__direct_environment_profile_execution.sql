alter table platform_environment
    add column if not exists endpoints_json text not null default '{}';

create table if not exists platform_profile_environment (
    profile_id varchar(64) not null references platform_profile(id) on delete cascade,
    environment_id varchar(64) not null references platform_environment(id) on delete cascade,
    primary key (profile_id, environment_id)
);

insert into platform_profile_environment (profile_id, environment_id)
select profile.id, environment.id
from platform_profile profile
join platform_environment environment on environment.project_id = profile.project_id
on conflict do nothing;

alter table platform_run
    add column if not exists environment_name varchar(200),
    add column if not exists profile_name varchar(200);

update platform_run run
set environment_name = environment.name
from platform_environment environment
where environment.id = run.environment_id
  and run.environment_name is null;

update platform_run run
set profile_name = profile.name
from platform_profile profile
where profile.id = run.profile_id
  and run.profile_name is null;

alter table platform_run
    drop column if exists run_target_id,
    drop column if exists run_target_name;

drop table if exists platform_run_target;

create index if not exists idx_platform_profile_environment_environment
    on platform_profile_environment(environment_id, profile_id);
