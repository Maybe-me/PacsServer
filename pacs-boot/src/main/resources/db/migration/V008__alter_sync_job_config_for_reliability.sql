ALTER TABLE sync_job_config ADD COLUMN max_retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE sync_job_config ADD COLUMN failure_threshold INT NOT NULL DEFAULT 3;
ALTER TABLE sync_job_config ADD COLUMN paused BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sync_job_config ADD COLUMN consecutive_failure_count INT NOT NULL DEFAULT 0;
ALTER TABLE sync_job_config ADD COLUMN last_error_category VARCHAR(32);
ALTER TABLE sync_job_config ADD COLUMN last_error_message VARCHAR(1024);
ALTER TABLE sync_job_config ADD COLUMN last_success_at TIMESTAMP;
ALTER TABLE sync_job_config ADD COLUMN last_failure_at TIMESTAMP;
