ALTER TABLE pacs_instance ADD COLUMN storage_type VARCHAR(32) NOT NULL DEFAULT 'local';
ALTER TABLE pacs_instance ADD COLUMN storage_bucket VARCHAR(255);
ALTER TABLE pacs_instance ADD COLUMN storage_key VARCHAR(512);

UPDATE pacs_instance
SET storage_type = COALESCE(storage_type, 'local'),
    storage_key = COALESCE(storage_key, file_path);

ALTER TABLE pacs_instance ALTER COLUMN storage_key SET NOT NULL;
