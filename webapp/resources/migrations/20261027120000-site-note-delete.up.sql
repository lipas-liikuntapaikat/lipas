-- Site notes can be deleted, but only once archived: archiving stays the
-- reversible default, deleting is the final step for notes nobody needs (e.g.
-- test notes). Still append-only: a delete is one more status row, and the
-- note leaves every list but stays in the database.
ALTER TABLE public.site_note_status
  DROP CONSTRAINT IF EXISTS site_note_status_status_check;

--;;

ALTER TABLE public.site_note_status
  ADD CONSTRAINT site_note_status_status_check
    CHECK (status IN ('active', 'archived', 'deleted'));
