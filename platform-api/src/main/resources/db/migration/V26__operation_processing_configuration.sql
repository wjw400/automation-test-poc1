ALTER TABLE platform_api_operation ADD COLUMN processing_json text;
ALTER TABLE platform_api_operation ADD COLUMN imported_processing_json text;
ALTER TABLE platform_api_operation ADD COLUMN processing_source varchar(255);
ALTER TABLE platform_api_operation ADD COLUMN processing_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE platform_api_operation ADD COLUMN processing_needs_review boolean NOT NULL DEFAULT false;
ALTER TABLE platform_api_operation ADD COLUMN processing_import_error text;
ALTER TABLE platform_api_operation ADD COLUMN row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE platform_step_run ADD COLUMN processing_snapshot_json text;
ALTER TABLE platform_step_run ADD COLUMN business_request_json text;
