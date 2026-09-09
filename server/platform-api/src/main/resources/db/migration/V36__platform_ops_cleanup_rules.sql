ALTER TABLE platform_ops_settings
    ADD COLUMN cleanup_rules_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE platform_ops_settings
    ALTER COLUMN auto_cleanup_days SET DEFAULT 1;

-- auto_cleanup_days now means "run every N days", not a global retention window.
UPDATE platform_ops_settings
SET auto_cleanup_days = 1
WHERE auto_cleanup_enabled = FALSE
  AND auto_cleanup_days = 30;
