-- GDPR: impersonation history events used to embed the impersonator's /
-- target's email address in the OTHER party's account.history row —
-- exactly where gdpr-remove-user! (which only anonymizes the removed
-- account's own row) can never reach them. The events now record only
-- account UUIDs, which degrade gracefully: after a removal the UUID
-- resolves to the anonymized account. This scrubs the emails that were
-- already written.
UPDATE account
SET history = jsonb_set(
  history,
  '{events}',
  (SELECT jsonb_agg(evt.value - 'impersonator-email' - 'target-email'
                    ORDER BY evt.ordinality)
   FROM jsonb_array_elements(history->'events')
     WITH ORDINALITY AS evt(value, ordinality)))
-- jsonb_exists() instead of the ? operator: migration runners and JDBC
-- treat a bare ? as a bind-parameter placeholder.
WHERE jsonb_typeof(history->'events') = 'array'
  AND EXISTS (
    SELECT 1 FROM jsonb_array_elements(history->'events') AS e
    WHERE jsonb_exists(e, 'impersonator-email')
       OR jsonb_exists(e, 'target-email'));
