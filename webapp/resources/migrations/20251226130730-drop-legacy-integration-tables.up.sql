-- Drop legacy integration tables that were used for syncing between new LIPAS and old LIPAS.
-- The old LIPAS system has been decommissioned and migrated into this codebase,
-- so these tables are no longer needed.

DROP TABLE IF EXISTS integration_out_queue;
DROP TABLE IF EXISTS integration_log;
