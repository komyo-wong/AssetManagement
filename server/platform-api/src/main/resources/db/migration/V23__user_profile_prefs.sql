-- Self-service profile: avatar + in-app alert UX preferences
ALTER TABLE iam_users
    ADD COLUMN IF NOT EXISTS avatar_url TEXT;

ALTER TABLE iam_users
    ADD COLUMN IF NOT EXISTS ui_preferences_json TEXT;

COMMENT ON COLUMN iam_users.avatar_url IS
    'Optional avatar data URL or https URL for the account.';
COMMENT ON COLUMN iam_users.ui_preferences_json IS
    'JSON preferences, e.g. {"alertPopupEnabled":true,"alertSoundEnabled":true}.';
