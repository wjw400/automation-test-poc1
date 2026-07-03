create table if not exists platform_api_spec (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    name varchar(255) not null,
    title varchar(255),
    spec_version varchar(50),
    source_format varchar(100),
    content text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists platform_api_operation (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    api_spec_id varchar(64) not null references platform_api_spec(id) on delete cascade,
    tag_name varchar(100),
    method varchar(20) not null,
    path varchar(500) not null,
    operation_id varchar(255),
    summary varchar(255),
    description text,
    request_body_json text,
    responses_json text,
    raw_operation_json text,
    created_at timestamp with time zone not null
);

create unique index if not exists uk_platform_api_spec_project_name
    on platform_api_spec(project_id, name);

create index if not exists idx_platform_api_operation_project
    on platform_api_operation(project_id);

create index if not exists idx_platform_api_operation_spec
    on platform_api_operation(api_spec_id);
