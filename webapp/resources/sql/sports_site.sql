-- :name next-lipas-id
-- :command :query
-- :result :one
-- :doc Returns next value in lipas_id_seq sequence
SELECT nextval('lipas_id_seq');

-- :name insert-sports-site-rev!
-- :command :insert
-- :result :raw
-- :doc Inserts a sports-site revision into sports_site table
INSERT INTO public.sports_site (
  event_date,
  lipas_id,
  status,
  document,
  author_id,
  type_code,
  city_code,
  acting_org_id
)
VALUES (
  :event_date ::timestamptz,
  :lipas_id,
  :status,
  :document,
  :author_id ::uuid,
  :type_code,
  :city_code,
  :acting_org_id ::uuid
);

-- :name get
-- :command :query
-- :result :one
-- :doc Returns current revision of sports-site with given lipas-id
SELECT *
FROM sports_site_current
WHERE lipas_id = :lipas_id

-- :name get-by-type-code
-- :command :query
-- :result :many
-- :doc Returns all revisions of sports sites by type_code
SELECT *
FROM sports_site
WHERE type_code = :type_code

-- :name get-latest-by-type-code
-- :command :query
-- :result :many
-- :doc Returns latests revisions of all sports sites by type_code
SELECT *
FROM sports_site_current
WHERE type_code = :type_code

-- :name get-history
-- :command :query
-- :result :many
-- :doc Returns history for a single sports-site (lipas_id)
SELECT *
FROM sports_site_by_year
WHERE lipas_id = :lipas_id

-- :name get-edit-history
-- :command :query
-- :result :many
-- :doc Lightweight per-revision edit history (timestamp + author, no document) for a single sports-site
SELECT event_date, author_id, status
FROM sports_site
WHERE lipas_id = :lipas_id
ORDER BY event_date DESC

-- :name get-full-history
-- :command :query
-- :result :many
-- :doc Returns the full, raw revision history (every row, including drafts) for a single sports-site (lipas_id)
SELECT *
FROM sports_site
WHERE lipas_id = :lipas_id
ORDER BY event_date DESC, created_at DESC

-- :name get-yearly-by-type-code
-- :command :query
-- :result :many
-- :doc Lists latest revision for each sports-site for each year
SELECT *
FROM sports_site_by_year
WHERE type_code = :type_code

-- :name get-by-type-code-and-year
-- :command :query
-- :result :many
-- :doc Lists latest revision for each sports-site for given year
SELECT *
FROM sports_site_by_year
WHERE type_code = :type_code AND date_part('year', event_date) = :year

-- :name get-by-author-and-status
-- :command :query
-- :result :many
-- :doc Lists sports-sites by given author_id (user) and status
SELECT *
FROM sports_site
WHERE status = :status AND author_id = :author_id ::uuid

-- :name get-last-modified
-- :command :query
-- :result :many
-- :doc Lists latest timestamps for given lipas_ids
SELECT lipas_id, created_at, event_date
FROM sports_site_current
WHERE lipas_id IN (:v*:lipas_ids)

-- :name get-modified-since
-- :command :query
-- :result :many
-- :doc Lists latest revisions of sports-sites modified after given timestamp
SELECT *
FROM sports_site_current
WHERE created_at > :timestamp ::timestamptz

-- :name get-latest-by-ids
-- :command :query
-- :result :many
-- :doc Lists latest revisions of sports-sites by lipas-ids
SELECT *
FROM sports_site_current
WHERE lipas_id IN (:v*:lipas_ids)

-- :name get-lipas-ids-by-ptv-service-channel-id
-- :command :query
-- :result :many
-- :doc Lipas-ids (and names) of current revisions whose PTV service-channel-ids
-- :doc array contains :service_channel_id. Used to detect double-linking (more
-- :doc than one sports-site bound to the same PTV service-location). The :ptv
-- :doc subtree is not indexed in Elasticsearch ({:enabled false}), so this JSONB
-- :doc query against the source of truth is the authoritative lookup.
SELECT lipas_id, document ->> 'name' AS name
FROM sports_site_current
WHERE document #> '{ptv,service-channel-ids}' @> to_jsonb(:service_channel_id ::text)

-- :name get-ptv-sync-bases
-- :command :query
-- :result :many
-- :doc For each given site with a PTV sync, the revision that sync wrote: the
-- :doc first one stored that carries the current revision's [:ptv :last-sync].
-- :doc Every sync writes a revision with a fresh last-sync and later revisions
-- :doc copy it forward, so the earliest holding it is the sync's own. Matched
-- :doc on the stored value, not on event_date, so no timestamp format or
-- :doc precision can make it miss.
SELECT DISTINCT ON (s.lipas_id) s.lipas_id, s.document
FROM sports_site s
JOIN sports_site_current c USING (lipas_id)
WHERE s.lipas_id IN (:v*:lipas_ids)
  AND s.status = 'published'
  AND c.document #>> '{ptv,last-sync}' IS NOT NULL
  AND s.document #>> '{ptv,last-sync}' = c.document #>> '{ptv,last-sync}'
ORDER BY s.lipas_id, s.created_at, s.event_date

-- :name get-ptv-adoption
-- :command :query
-- :result :many
-- :doc One row per site that has ever had a :ptv subtree: the Helsinki-local
-- :doc month of its first :ptv revision and of its first revision published in
-- :doc PTV (NULL if never) and of leaving PTV after its last publication (NULL
-- :doc if still in PTV), plus the current revision's fields the PTV state
-- :doc rules need (lipas.data.ptv/attention). Feeds the admin PTV adoption view.
WITH first_ptv AS (
  SELECT lipas_id,
         to_char(min(event_date) AT TIME ZONE 'Europe/Helsinki', 'YYYY-MM') AS first_month
  FROM sports_site
  WHERE jsonb_typeof(document -> 'ptv') = 'object'
  GROUP BY lipas_id
),
first_published AS (
  SELECT lipas_id,
         to_char(min(event_date) AT TIME ZONE 'Europe/Helsinki', 'YYYY-MM') AS first_published_month
  FROM sports_site
  WHERE document #>> '{ptv,publishing-status}' = 'Published'
  GROUP BY lipas_id
),
last_published AS (
  SELECT lipas_id, max(event_date) AS last_published_at
  FROM sports_site
  WHERE status = 'published'
    AND document #>> '{ptv,publishing-status}' = 'Published'
  GROUP BY lipas_id
),
left_ptv AS (
  -- The first revision after the last Published one that isn't Published
  -- (archived, or the PTV data gone): when the site left PTV.
  SELECT s.lipas_id,
         to_char(min(s.event_date) AT TIME ZONE 'Europe/Helsinki', 'YYYY-MM') AS left_month
  FROM sports_site s
  JOIN last_published lp USING (lipas_id)
  WHERE s.status = 'published'
    AND s.event_date > lp.last_published_at
    AND coalesce(s.document #>> '{ptv,publishing-status}', '') <> 'Published'
  GROUP BY s.lipas_id
)
SELECT f.lipas_id,
       f.first_month,
       p.first_published_month,
       l.left_month,
       c.city_code,
       jsonb_build_object('lipas-id', c.lipas_id,
                          'name', c.document -> 'name',
                          'status', c.document -> 'status',
                          'owner', c.document -> 'owner',
                          'type', c.document -> 'type',
                          'ptv', c.document -> 'ptv') AS site
FROM first_ptv f
JOIN sports_site_current c USING (lipas_id)
LEFT JOIN first_published p USING (lipas_id)
LEFT JOIN left_ptv l USING (lipas_id)

-- :name invalidate-since!
-- :command :execute
-- :result :affected
-- :doc Changes status to 'incorrect-data' for all revs of :lipas-id after :event-date
UPDATE public.sports_site
SET document = jsonb_set(document, '{status}', '"incorrect-data"')
WHERE lipas_id = :lipas_id AND event_date > :event_date ::timestamptz

-- :name get-latest-by-city-code
-- :command :query
-- :result :many
-- :doc Returns latests revisions of all sports sites by city_code
SELECT *
FROM sports_site_current
WHERE city_code = :city_code

-- :name get-ptv-language-usage
-- :command :query
-- :result :many
-- :doc For a PTV organisation, how many of its service locations published
-- :doc in PTV each language version is in (LIPAS codes), as their last sync
-- :doc stored them: what a sync removes when the org stops publishing it.
SELECT lang, count(*) AS n
FROM sports_site_current s,
     jsonb_array_elements_text(
       CASE WHEN jsonb_typeof(s.document #> '{ptv,languages}') = 'array'
            THEN s.document #> '{ptv,languages}'
            ELSE '[]'::jsonb
       END) AS lang
WHERE s.document #>> '{ptv,org-id}' = :ptv_org_id
  AND s.document #>> '{ptv,publishing-status}' = 'Published'
GROUP BY lang

-- :name get-ptv-published-sites
-- :command :query
-- :result :many
-- :doc The current revisions of a PTV organisation's sites whose service
-- :doc location is published in PTV, as their last sync stored it.
SELECT *
FROM sports_site_current
WHERE document #>> '{ptv,org-id}' = :ptv_org_id
  AND document #>> '{ptv,publishing-status}' = 'Published'
