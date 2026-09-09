\set ON_ERROR_STOP on

-- Application tables are owned by Flyway migrations in the server project.
-- This bootstrap file only enables capabilities required before migrations run.
CREATE EXTENSION IF NOT EXISTS postgis;
