-- Regional State Administrative Agencies (AVI) were abolished at the end of
-- 2025 and their sports-facility subsidy duties moved to the new national
-- Lupa- ja valvontavirasto (LVV) from 2026 on.
--
-- Subsidy issuer names are normalized to the *current* agency (the same was
-- done when ELY centres became AVIs: all ELY rows are stored as "AVI"), so
-- the stats UI shows one continuous issuer series. Fold AVI into LVV.
-- The grant year still tells which agency actually issued each subsidy.
--
-- See lipas.maintenance/subsidy-issuer-normalization. The subsidies ES
-- index is rebuilt from this table by lipas.maintenance/index-subsidies!,
-- which must be run after this migration (the yearly import does it).
UPDATE subsidy
   SET data = jsonb_set(data, '{issuer}', '"LVV"')
 WHERE data->>'issuer' = 'AVI';
