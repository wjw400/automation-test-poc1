alter table platform_run
    add column if not exists run_target_name varchar(200);

update platform_run
set run_target_name = run_configuration_name
where run_target_name is null
  and run_configuration_name is not null;

drop table if exists platform_case_run_configuration;

alter table platform_run
    drop column if exists run_configuration_id,
    drop column if exists run_configuration_name;
