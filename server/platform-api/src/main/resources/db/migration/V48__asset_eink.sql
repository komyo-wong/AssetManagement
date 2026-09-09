-- Manual e-ink flag on assets (ZA25 etc. often omit FF70 from advertisements).

ALTER TABLE assets
    ADD COLUMN IF NOT EXISTS eink_capable BOOLEAN,
    ADD COLUMN IF NOT EXISTS eink_profile VARCHAR(16);
