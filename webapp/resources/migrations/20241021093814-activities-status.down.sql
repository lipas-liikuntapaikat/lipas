update sports_site set document = document #- '{activities, outdoor-recreation-areas, status}' where document->'activities'->>'outdoor-recreation-areas' is not null;
update sports_site set document = document #- '{activities, outdoor-recreation-facilities, status}' where document->'activities'->>'outdoor-recreation-facilities' is not null;
update sports_site set document = document #- '{activities, outdoor-recreation-routes, status}' where document->'activities'->>'outdoor-recreation-routes' is not null;
update sports_site set document = document #- '{activities, cycling, status}' where document->'activities'->>'cycling' is not null;
update sports_site set document = document #- '{activities, paddling, status}' where document->'activities'->>'paddling' is not null;
update sports_site set document = document #- '{activities, birdwatching, status}' where document->'activities'->>'birdwatching' is not null;
update sports_site set document = document #- '{activities, fishing, status}' where document->'activities'->>'fishing' is not null;
