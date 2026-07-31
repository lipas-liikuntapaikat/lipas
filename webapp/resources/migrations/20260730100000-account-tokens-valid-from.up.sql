-- Per-user token revocation.
--
-- JWTs are stateless and carry the user's roles in the payload, so until now
-- nothing could invalidate one before it expired: archiving an account or
-- stripping its roles took up to 6h to take effect (7 days for a magic-link
-- token). This column is the revocation point.
--
-- Semantics: a token is rejected when its :iat (issued-at) predates
-- tokens_valid_from. Bumping the column to now() therefore kills every token
-- that user currently holds, without storing a single token anywhere.
--
-- NULL means "never revoked" and is the default, so applying this migration
-- does not log anybody out.
ALTER TABLE account
  ADD COLUMN tokens_valid_from timestamptz;

--;;

COMMENT ON COLUMN account.tokens_valid_from IS
  'Tokens issued before this instant are rejected. NULL = never revoked. Bumped on archive, role change, password reset and GDPR removal. See lipas.backend.auth.';
