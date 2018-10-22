-- Separate 'document status' from 'sports-site status'

-- Document status describes status of the document
-- Sports-site status describes status of the actual sports-site

-- Document status is not stored within the "document" JSON. It exists
-- only as field in 'sports_site' table.

-- Sports-site status is not *currently* included in 'sports_site' table
-- but it can be added there later if needed for indexing etc.

-- NOTE: JSON field document.status is the actual 'sports-site status'.

-- Document statuses are now 'draft' and 'published'
update sports_site set status = 'published' where status = 'active';

-- Change existing 'draft' sports-site statuses to 'active'
update sports_site set "document" = jsonb_set("document", '{status}', '"active"')
where "document"::json->>'status' = 'draft';
