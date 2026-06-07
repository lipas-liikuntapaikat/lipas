-- GIN index on account.permissions to support the "who can edit site Z" legacy
-- user lookup (design-spec §6 step 4). That query pre-filters candidate accounts
-- with jsonb containment (`permissions @> '{"roles":[{"city-code":[..]}]}'`, etc.)
-- before the exact in-memory check-privilege pass. jsonb_path_ops is the small,
-- fast operator class that supports @>. At today's account count a seq scan is
-- already ms-scale; this keeps the selective (city/type/lipas-id) path fast as
-- the account table grows.
CREATE INDEX IF NOT EXISTS account_permissions_gin
  ON public.account USING gin (permissions jsonb_path_ops);
