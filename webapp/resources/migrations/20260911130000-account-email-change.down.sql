-- The case-duplicate archival of the up migration is not reversed.

ALTER TABLE account DROP CONSTRAINT account_email_verified_via_check;

--;;

-- 'change' has no pre-existing equivalent; the address was proven, so the
-- closest older value is 'legacy' (trusted).
UPDATE account SET email_verified_via = 'legacy' WHERE email_verified_via = 'change';

--;;

ALTER TABLE account
  ADD CONSTRAINT account_email_verified_via_check
  CHECK (email_verified_via IN ('legacy', 'registration', 'login'));

--;;

ALTER TABLE account ADD CONSTRAINT account_email_key UNIQUE (email);

--;;

DROP INDEX account_email_lower_key;
