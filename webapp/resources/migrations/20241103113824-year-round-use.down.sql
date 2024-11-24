UPDATE sports_site
SET document = jsonb_set(
    document,
    '{properties}',
    (document::jsonb->'properties')::jsonb - 'year-round-use?'
)
WHERE document::jsonb->'properties' ? 'year-round-use?';
