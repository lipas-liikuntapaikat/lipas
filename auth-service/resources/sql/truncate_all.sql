-- :name truncate-all-tables-in-database!
-- :command :execute
-- :result :affected
-- :doc Truncate all tables in the database. Obviously very destructive only use in tests
DO
$body$
BEGIN
  EXECUTE (
    SELECT 'TRUNCATE TABLE '
        || string_agg(quote_ident(schemaname) || '.' || quote_ident(tablename), ', ')
        || ' CASCADE'
    FROM   pg_tables
    WHERE  schemaname = 'public');
END
$body$;
