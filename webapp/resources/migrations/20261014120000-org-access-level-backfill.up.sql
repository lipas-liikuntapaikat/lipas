-- Store every org's org-management access level explicitly.
--
-- EXCEPTION TO THE NORMAL PATTERN — DO NOT COPY. The `org` table is an
-- append-only revision log: new or changed data goes in as a NEW REVISION
-- (see lipas.backend.org/update-document!), never by rewriting stored rows.
--
-- This migration rewrites rows in place only because it records information
-- that was never collected before: `access-level` did not exist, and every
-- org was "ptv-only" by definition until now. Filling the empty slot in each
-- existing revision (current and historical) states that fact explicitly and
-- destroys nothing. Appending a revision per org instead would put a
-- meaningless, authorless "change" into every org's history.
--
-- Only revisions WITHOUT the key are touched, so a level an admin has already
-- set is never overwritten.
UPDATE public.org
SET document = jsonb_set(document, '{access-level}', '"ptv-only"')
WHERE document IS NOT NULL
  -- (not `document ? 'access-level'`: JDBC would read the `?` as a parameter)
  AND document->'access-level' IS NULL;
