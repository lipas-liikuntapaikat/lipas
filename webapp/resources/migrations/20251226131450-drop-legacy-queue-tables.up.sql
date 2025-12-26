-- Drop legacy queue tables that predate the current job system.
-- These have been replaced by the jobs table and are no longer used.

DROP TABLE IF EXISTS analysis_queue;
DROP TABLE IF EXISTS analysis_queue_backup;
DROP TABLE IF EXISTS elevation_queue;
DROP TABLE IF EXISTS elevation_queue_backup;
DROP TABLE IF EXISTS email_out_queue;
DROP TABLE IF EXISTS email_out_queue_backup;
