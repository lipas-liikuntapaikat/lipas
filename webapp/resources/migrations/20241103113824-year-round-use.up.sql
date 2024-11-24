UPDATE sports_site
SET document = jsonb_set(document, '{properties, year-round-use?}', 'true')
WHERE id IN (
  SELECT id
  FROM sports_site_current
  WHERE document::jsonb->'properties'->>'winter-usage?' = 'true' OR
        document::jsonb->'properties'->>'summer-usage?' = 'true')
