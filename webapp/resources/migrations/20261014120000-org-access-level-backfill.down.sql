-- Irreversible by design: after the up migration, "ptv-only" written by the
-- backfill is indistinguishable from "ptv-only" set by an admin, and both mean
-- the same thing. Leaving the explicit values in place is harmless.
SELECT 1;
