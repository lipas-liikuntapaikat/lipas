-- Restore the pre-2026 issuer name for rows that were folded into LVV.
-- Rows from 2026 on were genuinely issued by LVV and are left as-is.
UPDATE subsidy
   SET data = jsonb_set(data, '{issuer}', '"AVI"')
 WHERE data->>'issuer' = 'LVV'
   AND year < 2026;
