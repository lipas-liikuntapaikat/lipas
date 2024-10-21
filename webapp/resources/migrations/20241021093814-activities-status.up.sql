update sports_site set document = jsonb_set(document, '{activities, outdoor-recreation-areas, status}', '"active"') where document->'activities'->>'outdoor-recreation-areas' is not null;
update sports_site set document = jsonb_set(document, '{activities, outdoor-recreation-facilities, status}', '"active"') where document->'activities'->>'outdoor-recreation-facilities' is not null;
update sports_site set document = jsonb_set(document, '{activities, outdoor-recreation-routes, status}', '"active"') where document->'activities'->>'outdoor-recreation-routes' is not null;
update sports_site set document = jsonb_set(document, '{activities, cycling, status}', '"active"') where document->'activities'->>'cycling' is not null;
update sports_site set document = jsonb_set(document, '{activities, paddling, status}', '"active"') where document->'activities'->>'paddling' is not null;
update sports_site set document = jsonb_set(document, '{activities, birdwatching, status}', '"active"') where document->'activities'->>'birdwatching' is not null;
update sports_site set document = jsonb_set(document, '{activities, fishing, status}', '"active"') where document->'activities'->>'fishing' is not null;
