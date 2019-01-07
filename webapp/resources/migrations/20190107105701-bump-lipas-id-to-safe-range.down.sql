DO $func$
  BEGIN
  PERFORM setval('lipas_id_seq', (SELECT MAX(lipas_id) FROM sports_site));
END;$func$;
