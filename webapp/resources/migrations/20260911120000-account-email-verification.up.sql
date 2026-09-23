-- Email verification.
--
-- Records whether — and how — the account's email address has been shown to
-- belong to the person using the account. Until now nothing checked it: a
-- self-registration picked its own password for any address it liked, and an
-- org invite for that address would then attach to the squatted account.
--
-- A property of the row, not a lifecycle state. `status` (active/archived) is
-- unchanged and orthogonal. Self-registration no longer creates a row until the
-- emailed link has been opened (lipas.backend.core/register!), so there is no
-- "pending" account to clean up.
--
-- email_verified_via:
--   'legacy'       — account predates verification; trusted implicitly.
--   'registration' — opened the emailed registration link before the account
--                    existed.
--   'login'        — account was created FOR the address (org invite, admin
--                    magic link) with a random password, so its first successful
--                    login could only have come through that inbox.
--   NULL           — not (yet) verified. Only invite/admin-created accounts that
--                    have never logged in.
--
-- email_verified_at is NULL for 'legacy': we don't know when, and inventing a
-- timestamp would read as a fact.
ALTER TABLE account
  ADD COLUMN email_verified_at timestamptz,
  ADD COLUMN email_verified_via text
    CONSTRAINT account_email_verified_via_check
    CHECK (email_verified_via IN ('legacy', 'registration', 'login'));

--;;

UPDATE account SET email_verified_via = 'legacy';

--;;

COMMENT ON COLUMN account.email_verified_via IS
  'How the email address was proven: legacy (pre-verification account, implicit), registration (emailed registration link), login (first login of an invite/admin-created account). NULL = unverified. See lipas.backend.core.';

--;;

COMMENT ON COLUMN account.email_verified_at IS
  'When the email address was proven. NULL for legacy accounts and unverified ones.';
