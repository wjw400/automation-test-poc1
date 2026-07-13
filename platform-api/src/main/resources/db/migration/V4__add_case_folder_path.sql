alter table platform_case
    add column if not exists folder_path varchar(500);

create index if not exists idx_platform_case_folder
    on platform_case(project_id, folder_path);
