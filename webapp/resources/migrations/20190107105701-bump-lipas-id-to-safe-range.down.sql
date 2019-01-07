SELECT setval('lipas_id_seq', (SELECT MAX(lipas_id) FROM sports_site));
