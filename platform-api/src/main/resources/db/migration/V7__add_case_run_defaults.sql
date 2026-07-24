alter table platform_case
    add column if not exists default_environment_id varchar(64),
    add column if not exists default_profile_id varchar(64);

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_platform_case_default_environment'
    ) then
        alter table platform_case
            add constraint fk_platform_case_default_environment
            foreign key (default_environment_id) references platform_environment(id) on delete set null;
    end if;

    if not exists (
        select 1 from pg_constraint where conname = 'fk_platform_case_default_profile'
    ) then
        alter table platform_case
            add constraint fk_platform_case_default_profile
            foreign key (default_profile_id) references platform_profile(id) on delete set null;
    end if;
end $$;

create index if not exists idx_platform_case_default_environment
    on platform_case(default_environment_id);

create index if not exists idx_platform_case_default_profile
    on platform_case(default_profile_id);
