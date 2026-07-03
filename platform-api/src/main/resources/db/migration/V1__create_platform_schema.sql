create table if not exists platform_project (
    id varchar(64) primary key,
    code varchar(100) not null unique,
    name varchar(200) not null,
    description text,
    status varchar(50) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists platform_environment (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    name varchar(200) not null,
    base_url varchar(500) not null,
    variables_json text not null,
    status varchar(50) not null
);

create table if not exists platform_profile (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    name varchar(200) not null,
    type varchar(50) not null,
    sender_public_key text,
    sender_private_key text,
    sender_private_key_passphrase varchar(500),
    recipient_public_key text,
    recipient_private_key text,
    recipient_private_key_passphrase varchar(500),
    enabled boolean not null
);

create table if not exists platform_case (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    name varchar(200) not null,
    code varchar(100) not null,
    description text,
    tags_json text not null,
    dsl_version varchar(50) not null,
    content text not null,
    status varchar(50) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists platform_run (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    case_id varchar(64) not null references platform_case(id),
    environment_id varchar(64),
    profile_id varchar(64),
    triggered_by varchar(100) not null,
    status varchar(50) not null,
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone not null,
    duration_ms bigint not null,
    error_message text
);

create table if not exists platform_step_run (
    id varchar(64) primary key,
    run_id varchar(64) not null references platform_run(id) on delete cascade,
    step_index integer not null,
    step_name varchar(200) not null,
    profile_name varchar(200),
    status varchar(50) not null,
    error_message text,
    request_json text,
    response_json text,
    decrypted_payloads_json text,
    validations_json text,
    extracted_values_json text
);

create index if not exists idx_platform_environment_project on platform_environment(project_id);
create index if not exists idx_platform_profile_project on platform_profile(project_id);
create index if not exists idx_platform_case_project on platform_case(project_id);
create index if not exists idx_platform_run_project on platform_run(project_id);
create index if not exists idx_platform_run_case on platform_run(case_id);
create index if not exists idx_platform_step_run_run on platform_step_run(run_id);
