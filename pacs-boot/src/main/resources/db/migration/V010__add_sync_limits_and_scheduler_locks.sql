ALTER TABLE sync_job_config ADD COLUMN max_studies_per_run INT NOT NULL DEFAULT 0;
ALTER TABLE sync_job_config ADD COLUMN max_instances_per_run INT NOT NULL DEFAULT 0;
ALTER TABLE sync_job_config ADD COLUMN throttle_delay_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sync_job_config ADD COLUMN source_aet_allow_list TEXT;
ALTER TABLE sync_job_config ADD COLUMN source_aet_block_list TEXT;

CREATE TABLE sync_scheduler_lock (
    lock_name VARCHAR(64) PRIMARY KEY,
    locked_until TIMESTAMP,
    locked_at TIMESTAMP,
    locked_by VARCHAR(255)
);

INSERT INTO sync_scheduler_lock(lock_name, locked_until, locked_at, locked_by) VALUES
    ('sync-pull-dispatcher', TIMESTAMP '1970-01-01 00:00:00', TIMESTAMP '1970-01-01 00:00:00', 'bootstrap'),
    ('sync-push-dispatcher', TIMESTAMP '1970-01-01 00:00:00', TIMESTAMP '1970-01-01 00:00:00', 'bootstrap');
