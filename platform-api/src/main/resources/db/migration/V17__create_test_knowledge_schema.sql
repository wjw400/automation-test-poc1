create table if not exists platform_test_knowledge (
    id varchar(64) primary key,
    project_id varchar(64) not null references platform_project(id),
    name varchar(255) not null,
    description text,
    scope varchar(32) not null,
    target_id varchar(64),
    tags_json text not null,
    content text not null,
    status varchar(32) not null,
    version integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_platform_test_knowledge_project
    on platform_test_knowledge(project_id, updated_at desc);

create index if not exists idx_platform_test_knowledge_scope
    on platform_test_knowledge(project_id, scope, target_id);
