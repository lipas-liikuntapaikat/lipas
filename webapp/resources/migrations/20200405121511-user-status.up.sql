-- Mark all users as 'active'. The field status has existed for a long
-- time but it hasn't been used until now. Even now it's just to
-- filter user list in admin view.
UPDATE account SET status = 'active';
