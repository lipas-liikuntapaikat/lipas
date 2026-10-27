ALTER TABLE public.site_note_status
  DROP CONSTRAINT IF EXISTS site_note_status_status_check;

--;;

-- NOT VALID: status rows are append-only, so 'deleted' rows written meanwhile
-- can't be removed. The old code hides them (it lists active and archived only).
ALTER TABLE public.site_note_status
  ADD CONSTRAINT site_note_status_status_check
    CHECK (status IN ('active', 'archived')) NOT VALID;
