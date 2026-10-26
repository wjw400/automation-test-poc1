-- Source Suite is provenance, like suite_id on historical Suite runs.
-- Keep the source id and Draft content even when the Suite is deleted.
alter table platform_scenario_draft
    drop constraint if exists platform_scenario_draft_source_suite_id_fkey;
