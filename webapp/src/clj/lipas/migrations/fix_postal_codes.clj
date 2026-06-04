(ns lipas.migrations.fix-postal-codes
  "Trim sports-site postal-code values to the leading 5-digit code so they
  pass the anchored schema `#\"^[0-9]{5}$\"`. Examples: \"82300 Rääkkylä\"
  -> \"82300\", \"Oulu 90670\" -> \"90670\"."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(defn- extract-5-digits [s]
  (when s (re-find #"\d{5}" s)))

(defn migrate-up
  [{:keys [db] :as _config}]
  (log/info "Starting migration: fix-postal-codes")
  (let [rows (jdbc/execute!
              db
              ["SELECT lipas_id, document->'location'->>'postal-code' AS postal_code
                FROM sports_site_current
                WHERE document->'location'->>'postal-code' IS NOT NULL
                  AND document->'location'->>'postal-code' !~ '^[0-9]{5}$'"]
              {:builder-fn rs/as-unqualified-maps})]
    (log/info "Found" (count rows) "sports sites with malformed postal-code")
    (doseq [{:keys [lipas_id postal_code]} rows]
      (if-let [fixed (extract-5-digits postal_code)]
        (do
          (log/info "Fixing lipas-id" lipas_id ":" (pr-str postal_code) "->" (pr-str fixed))
          (jdbc/execute-one!
           db
           ["UPDATE sports_site
             SET document = jsonb_set(document, '{location,postal-code}', to_jsonb(?::text))
             WHERE lipas_id = ?
               AND (lipas_id, event_date) IN (
                 SELECT lipas_id, MAX(event_date)
                 FROM sports_site
                 WHERE status = 'published'
                 GROUP BY lipas_id
               )"
            fixed
            lipas_id]))
        (log/warn "No 5-digit code in lipas-id" lipas_id "postal-code:" (pr-str postal_code))))
    (log/info "Migration complete: fix-postal-codes. Fixed" (count rows) "sites")))

(defn migrate-down [_config]
  (log/warn "Rollback not supported for fix-postal-codes migration"))
