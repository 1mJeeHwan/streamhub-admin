-- DB-per-service schemas for the extracted microservices (streamhub-audit-service owns its audit
-- store separately from the monolith's `streamhub` DB). Runs once on a FRESH mysql data volume
-- (docker-entrypoint-initdb.d). For an EXISTING volume the script does not re-run — create the schema
-- manually once (see docs/msa-split.md):
--   docker exec -i streamhub-mysql mysql -uroot -proot < deploy/mysql-init/01-create-service-dbs.sql
CREATE DATABASE IF NOT EXISTS streamhub_audit
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON streamhub_audit.* TO 'streamhub'@'%';
FLUSH PRIVILEGES;
