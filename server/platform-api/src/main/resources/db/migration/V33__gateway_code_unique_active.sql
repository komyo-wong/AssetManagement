-- Archived gateways keep their row but must not block reuse of the 4-digit business code.
DROP INDEX IF EXISTS ux_gateways_project_code_ci;
CREATE UNIQUE INDEX ux_gateways_project_code_ci
    ON gateways (project_id, LOWER(code))
    WHERE status <> 'ARCHIVED';
