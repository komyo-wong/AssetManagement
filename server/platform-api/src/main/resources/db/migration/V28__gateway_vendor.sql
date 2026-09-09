-- Distinguishes first-party gateways from third-party HCBG01 (Hivecom) JSON gateways.
ALTER TABLE gateways
    ADD COLUMN IF NOT EXISTS vendor VARCHAR(24) NOT NULL DEFAULT 'NATIVE';

ALTER TABLE gateways
    DROP CONSTRAINT IF EXISTS gateways_vendor_ck;

ALTER TABLE gateways
    ADD CONSTRAINT gateways_vendor_ck CHECK (vendor IN ('NATIVE', 'HCBG'));
