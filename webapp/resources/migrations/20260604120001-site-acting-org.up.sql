-- Records "on whose behalf" (which org the editing user acted for) a site
-- revision was made. Parallels author_id; nullable for non-org edits. Written
-- now so revision history captures it going forward; the sports_site_current
-- view is intentionally left unchanged (it has dependent views, and current
-- reads don't need this per-revision audit field — history queries hit the
-- sports_site table directly).
ALTER TABLE public.sports_site ADD COLUMN IF NOT EXISTS acting_org_id uuid;
