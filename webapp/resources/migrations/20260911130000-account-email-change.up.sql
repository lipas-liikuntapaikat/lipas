-- Email change support: case-insensitive email uniqueness, and 'change' as a
-- way an address can be proven.
--
-- Every lookup already compares LOWER(email), but the uniqueness constraint
-- from 2018 (account_email_key) is case-sensitive, so "Maija@x.fi" and
-- "maija@x.fi" could both exist — and one pair did. For such a pair,
-- login-by-email picks an arbitrary account.
--
-- 1. Resolve existing case-duplicates. Per address, keep the account with the
--    latest recorded login (fallback: the most recently created) and archive
--    the others: their address becomes a unique placeholder (the GDPR removal
--    pattern), their sessions are revoked and an audit event is appended.
--    Their site revisions stay attributed to them. NOT reversed by the down
--    migration.
WITH candidates AS (
  SELECT a.id,
         a.email,
         a.created_at,
         (SELECT max(e->>'event-date')
            FROM jsonb_array_elements(coalesce(a.history->'events', '[]'::jsonb)) e
           WHERE e->>'event' = 'login') AS last_login
    FROM account a
   WHERE lower(a.email) IN (SELECT lower(email) FROM account GROUP BY 1 HAVING count(*) > 1)
),
ranked AS (
  SELECT id,
         row_number() OVER (PARTITION BY lower(email)
                            ORDER BY last_login DESC NULLS LAST, created_at DESC) AS rn
    FROM candidates
)
UPDATE account a
   SET status = 'archived',
       email = 'case_duplicate_' || a.id || '@lipas.fi',
       tokens_valid_from = now(),
       history = jsonb_set(coalesce(a.history, '{}'::jsonb),
                           '{events}',
                           coalesce(a.history->'events', '[]'::jsonb)
                           || jsonb_build_array(jsonb_build_object(
                                'event', 'archived-as-case-duplicate',
                                'event-date', to_char(now() AT TIME ZONE 'UTC',
                                                      'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'))))
  FROM ranked r
 WHERE a.id = r.id
   AND r.rn > 1;

--;;

CREATE UNIQUE INDEX account_email_lower_key ON account (lower(email));

--;;

ALTER TABLE account DROP CONSTRAINT account_email_key;

--;;

-- 2. An email change (lipas.backend.core/confirm-email-change!) proves the new
--    address by the link opened from it.
ALTER TABLE account DROP CONSTRAINT account_email_verified_via_check;

--;;

ALTER TABLE account
  ADD CONSTRAINT account_email_verified_via_check
  CHECK (email_verified_via IN ('legacy', 'registration', 'login', 'change'));

--;;

COMMENT ON COLUMN account.email_verified_via IS
  'How the email address was proven: legacy (pre-verification account, implicit), registration (emailed registration link), login (first login of an invite/admin-created account), change (confirmation link of an email change). NULL = unverified. See lipas.backend.core.';
