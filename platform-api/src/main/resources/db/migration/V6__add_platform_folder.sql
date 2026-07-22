create table if not exists platform_folder (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    parent_folder_id varchar(64) references platform_folder(id) on delete restrict,
    name varchar(200) not null,
    sort_order integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

alter table platform_case
    add column if not exists folder_id varchar(64);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_platform_case_folder') then
        alter table platform_case
            add constraint fk_platform_case_folder
            foreign key (folder_id) references platform_folder(id) on delete set null;
    end if;
end $$;

create unique index if not exists uq_platform_folder_sibling_name
    on platform_folder(project_id, coalesce(parent_folder_id, ''), lower(name));

create index if not exists idx_platform_folder_project_parent
    on platform_folder(project_id, parent_folder_id, sort_order);

create index if not exists idx_platform_case_folder
    on platform_case(project_id, folder_id);
