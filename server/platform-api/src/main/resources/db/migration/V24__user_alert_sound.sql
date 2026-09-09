ALTER TABLE iam_users
    ADD COLUMN IF NOT EXISTS alert_sound_data TEXT;

ALTER TABLE iam_users
    ADD COLUMN IF NOT EXISTS alert_sound_name VARCHAR(160);

COMMENT ON COLUMN iam_users.alert_sound_data IS
    'Optional custom alert sound as data URL (mp3/wma), max ~300KB binary.';
COMMENT ON COLUMN iam_users.alert_sound_name IS
    'Original file name of the custom alert sound.';
