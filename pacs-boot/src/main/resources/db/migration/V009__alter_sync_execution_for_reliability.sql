ALTER TABLE sync_execution ADD COLUMN attempt_count INT NOT NULL DEFAULT 1;
ALTER TABLE sync_execution ADD COLUMN error_category VARCHAR(32);
