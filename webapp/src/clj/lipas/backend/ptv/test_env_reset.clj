(ns lipas.backend.ptv.test-env-reset
  "Restores one org's PTV integration to its pre-integration state, on both
  the PTV and the LIPAS side, so trainings and tests in the PTV test
  environment can start over. A REPL tool; refuses to run unless the PTV
  component points at the test environment.

  Integration state is whatever carries LIPAS's mark.

  PTV — only entities with a `lipas-` sourceId; everything else in the org
  (created in PTV directly) is left alone:
  - ServiceLocations are archived. Their sourceIds embed a timestamp, so a
    later create can't collide with the archived row.
  - Services, LIPAS-created and adopted alike, get their sourceId renamed to
    a tombstone and are then archived. PTV keeps an archived row's sourceId
    reserved, so without the rename the next create of the same
    sub-category service would be rejected.

  LIPAS:
  - sites carrying the org's `:ptv` get a new revision without it
  - the org's ptv_service and ptv_service_audit rows and its sites'
    ptv_site_audit rows are deleted (see `delete-lipas-rows!`)

  Not undone: texts LIPAS wrote into adopted services, or into pre-existing
  channels a site was linked to. No pre-integration copy of them exists, so
  they are archived with the rest.

  Preview with `reset-plan`, run with `reset-org!`. Both read PTV and the
  database afresh, and every step is idempotent: a rerun picks up whatever
  an earlier run skipped."
  (:require [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.integration :as ptvi]
            [lipas.utils :as utils]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- assert-test-env! [ptv]
  (when-not (some-> (:api-url ptv) ptvi/test-env?)
    (throw (ex-info "Refusing to run: PTV is not the test environment"
                    {:api-url (:api-url ptv)}))))

;;; PTV side ;;;

(defn- lipas-source-id? [entity]
  (some-> (:sourceId entity) (str/starts-with? "lipas-")))

(defn- responsible-org-id [service]
  (some #(when (= "Responsible" (:roleType %)) (-> % :organization :id))
        (:organizations service)))

(defn- fi-value [localized]
  (some #(when (= "fi" (:language %)) (:value %)) localized))

(defn- channel-summary [channel]
  {:id (:id channel)
   :source-id (:sourceId channel)
   :name (fi-value (:serviceChannelNames channel))})

(defn- service-summary [service]
  {:id (:id service)
   :source-id (:sourceId service)
   :name (fi-value (:serviceNames service))})

(defn- ptv-targets
  "The org's published PTV entities that carry LIPAS's mark. Drafts are
  invisible to the list endpoints, so a channel or service that exists only
  as a draft is neither listed nor touched."
  [ptv ptv-org-id]
  (let [channels (:itemList (ptvi/get-org-service-channels ptv ptv-org-id))
        services (:itemList (ptvi/get-org-services ptv ptv-org-id))
        locations (filterv #(and (lipas-source-id? %)
                                 (= "ServiceLocation" (:serviceChannelType %))
                                 (= ptv-org-id (:organizationId %)))
                           channels)
        ;; The list may include services the org only produces; those belong
        ;; to their responsible org.
        lipas-services (filterv #(and (lipas-source-id? %)
                                      (= ptv-org-id (responsible-org-id %)))
                                services)]
    {:service-locations locations
     :services lipas-services
     :untouched {:channels (- (count channels) (count locations))
                 :services (- (count services) (count lipas-services))}}))

(defn- modified-status-error?
  "PTV refuses writes to an entity that has an unpublished draft."
  [e]
  (and (= 400 (get-in (ex-data e) [:resp :status]))
       (some-> (ex-message e) (str/includes? "status Modified"))))

(defn- run-each!
  "Calls `f` on each entity. A failure doesn't stop the rest: entities with
  a pending draft (publish or discard it in the PTV UI first) and other
  failures are collected for the report, and a rerun retries them."
  [label summarize f entities]
  (let [n (count entities)]
    (reduce (fn [acc [i entity]]
              (log/infof "%s %d/%d %s" label (inc i) n (:id entity))
              (try
                (f entity)
                (update acc :archived inc)
                (catch clojure.lang.ExceptionInfo e
                  (if (modified-status-error? e)
                    (update acc :skipped-modified conj (summarize entity))
                    (do (log/warn e label "failed for" (:id entity))
                        (update acc :failed conj (assoc (summarize entity)
                                                        :error (ex-message e))))))))
            {:archived 0 :skipped-modified [] :failed []}
            (map-indexed vector entities))))

(defn- tombstone-source-id
  "Unique per run, so repeated resets of an org never collide."
  [source-id]
  (str source-id "-tombstone-" (System/currentTimeMillis)))

(defn- archive-service!
  "PUT-by-id rejects partial bodies: mainResponsibleOrganization,
  publishingStatus, ontologyTerms, serviceClasses and targetGroups are
  required, so they're sent back as listed. PTV silently drops a sourceId
  change made together with the archive, hence two writes. A service that
  already has its tombstone (an earlier run failed between the writes) is
  only archived.

  Calls `ptvi/http` directly: `ptvi/update-service-by-id` takes different
  arguments on master and on the PTV feature branches that lipas-dev runs."
  [ptv ptv-org-id service]
  (let [put! (fn [body]
               (log/infof "Update PTV service by ID %s" (:id service))
               (ptvi/http ptv ptv-org-id {:url (ptvi/make-url ptv "/v11/Service/" (:id service))
                                          :method :put
                                          :form-params body}))
        body {:mainResponsibleOrganization ptv-org-id
              :publishingStatus (:publishingStatus service)
              :ontologyTerms (mapv :uri (:ontologyTerms service))
              :serviceClasses (mapv :uri (:serviceClasses service))
              :targetGroups (mapv :uri (:targetGroups service))}]
    (when-not (str/includes? (:sourceId service) "-tombstone-")
      (put! (assoc body :sourceId (tombstone-source-id (:sourceId service)))))
    (put! (assoc body :publishingStatus "Deleted"))))

(defn- reset-ptv! [ptv ptv-org-id {:keys [service-locations services]}]
  ;; Locations first: archiving them drops their connections to the services.
  {:service-locations (run-each! "Archive service-location" channel-summary
                                 #(ptvi/archive-service-location ptv ptv-org-id (:id %))
                                 service-locations)
   :services (run-each! "Archive service" service-summary
                        #(archive-service! ptv ptv-org-id %)
                        services)})

;;; LIPAS side ;;;

(defn- lipas-org
  "The LIPAS org linked to the PTV org. Its ptv_service rows are keyed by the
  LIPAS org id, so an ambiguous link is refused rather than guessed."
  [db ptv-org-id]
  (let [orgs (->> (jdbc/execute! db ["SELECT org_id, document FROM org_current
                                      WHERE document->'ptv-data'->>'org-id' = ?"
                                     ptv-org-id]
                                 query-opts)
                  (map backend-org/unmarshall))]
    (case (count orgs)
      1 (first orgs)
      0 (throw (ex-info "No LIPAS org is linked to the PTV org"
                        {:ptv-org-id ptv-org-id}))
      (throw (ex-info "Several LIPAS orgs are linked to the PTV org"
                      {:ptv-org-id ptv-org-id
                       :orgs (mapv #(select-keys % [:id :name]) orgs)})))))

(def ^:private org-site-condition
  "Current sites that belong to the org's integration: their :ptv names the
  PTV org, or no PTV org is recorded and the site lies in the org's cities.
  The second case catches audits that outlived their site's :ptv (an
  earlier reset removed the :ptv but kept the audits)."
  "(s.document->'ptv'->>'org-id' = ?
    OR (s.document->'ptv'->>'org-id' IS NULL AND s.city_code = ANY(?)))")

(defn- org-site-params [org ptv-org-id]
  [ptv-org-id (into-array String (map str (get-in org [:ptv-data :city-codes])))])

(defn- lipas-targets [db ptv-org-id]
  (let [org (lipas-org db ptv-org-id)
        lipas-ids (fn [sql]
                    (mapv :lipas-id (jdbc/execute! db (into [sql] (org-site-params org ptv-org-id))
                                                   query-opts)))
        org-row-count (fn [table]
                        (:n (jdbc/execute-one! db [(str "SELECT count(*) AS n FROM " table
                                                        " WHERE org_id = ?")
                                                   (:id org)])))]
    {:org org
     :sites (lipas-ids (str "SELECT s.lipas_id FROM sports_site_current s
                             WHERE jsonb_typeof(s.document->'ptv') = 'object'
                             AND " org-site-condition " ORDER BY 1"))
     :site-audit-lipas-ids (lipas-ids (str "SELECT DISTINCT a.lipas_id FROM ptv_site_audit a
                                            JOIN sports_site_current s ON s.lipas_id = a.lipas_id
                                            WHERE " org-site-condition " ORDER BY 1"))
     :ptv-site-audits (:n (jdbc/execute-one! db (into [(str "SELECT count(*) AS n FROM ptv_site_audit a
                                                             JOIN sports_site_current s ON s.lipas_id = a.lipas_id
                                                             WHERE " org-site-condition)]
                                                      (org-site-params org ptv-org-id))))
     :ptv-service-audits (org-row-count "ptv_service_audit")
     :ptv-services (org-row-count "ptv_service")}))

(defn- delete-lipas-rows!
  "These tables are append-only, but nothing reads their `status`: the
  *_current views and accessors return the latest row whatever it says, so a
  tombstone row would still be shown. They hold the integration's own
  bookkeeping, so its rows are deleted outright; the sites themselves keep
  their history and get a new revision instead. Service audits go before the
  services they reference."
  [db {:keys [org site-audit-lipas-ids]}]
  (jdbc/with-transaction [tx db]
    (let [delete! (fn [sql & params]
                    (::jdbc/update-count (jdbc/execute-one! tx (into [sql] params))))
          site-audits (delete! "DELETE FROM ptv_site_audit WHERE lipas_id = ANY(?)"
                               (int-array site-audit-lipas-ids))
          service-audits (delete! "DELETE FROM ptv_service_audit WHERE org_id = ?" (:id org))
          services (delete! "DELETE FROM ptv_service WHERE org_id = ?" (:id org))]
      {:ptv-site-audits site-audits
       :ptv-service-audits service-audits
       :ptv-services services})))

(defn- remove-site-ptv!
  "Uses the trusted upsert: this is maintenance, and a site that fails an
  unrelated save check (e.g. the owner lock) must still be reset."
  [db search user index-ctx lipas-id]
  (let [site (core/get-sports-site db lipas-id)
        resp (core/upsert-sports-site!* db user (-> site
                                                    (dissoc :ptv)
                                                    (assoc :event-date (utils/timestamp))))]
    (core/index! search resp :sync index-ctx)))

(defn- reset-lipas! [db search user {:keys [sites] :as targets}]
  (let [deleted (delete-lipas-rows! db targets)
        index-ctx (core/index-context db)]
    (doseq [[i lipas-id] (map-indexed vector sites)]
      (log/infof "Remove :ptv from site %d/%d %s" (inc i) (count sites) lipas-id)
      (remove-site-ptv! db search user index-ctx lipas-id))
    (assoc deleted :sites (count sites))))

;;; API ;;;

(defn system->ctx
  "The components `reset-plan` and `reset-org!` take, from a running system
  map: integrant.repl.state/system locally,
  @lipas.backend.system/current-system on a server. Site revisions are
  authored by the robot user."
  [system]
  (let [db (:lipas/db system)]
    {:db db
     :search (:lipas/search system)
     :ptv (:lipas/ptv system)
     :user (core/get-user! db "robot@lipas.fi")}))

(defn reset-plan
  "What `reset-org!` would do for the PTV org, without writing anything."
  [{:keys [db ptv]} ptv-org-id]
  (assert-test-env! ptv)
  (let [{:keys [org] :as lipas} (lipas-targets db ptv-org-id)
        {:keys [service-locations services untouched]} (ptv-targets ptv ptv-org-id)]
    {:ptv-org-id ptv-org-id
     :lipas-org (select-keys org [:id :name])
     :ptv {:archive {:service-locations (mapv channel-summary service-locations)
                     :services (mapv service-summary services)}
           :untouched untouched}
     :lipas {:remove-ptv-from-sites (:sites lipas)
             :delete {:ptv-site-audits (:ptv-site-audits lipas)
                      :ptv-service-audits (:ptv-service-audits lipas)
                      :ptv-services (:ptv-services lipas)}}}))

(defn reset-org!
  "Resets the PTV org's integration (see the namespace doc). PTV first, then
  LIPAS. Returns what was done; entities listed under :skipped-modified or
  :failed are still in PTV and need a rerun."
  [{:keys [db search ptv user]} ptv-org-id]
  (assert-test-env! ptv)
  (assert (:id user) "reset-org! needs the user to author the site revisions")
  (let [lipas (lipas-targets db ptv-org-id)
        ptv-result (reset-ptv! ptv ptv-org-id (ptv-targets ptv ptv-org-id))
        lipas-result (reset-lipas! db search user lipas)]
    (log/infof "Reset of PTV org %s (%s) done" ptv-org-id (get-in lipas [:org :name]))
    {:ptv-org-id ptv-org-id
     :ptv ptv-result
     :lipas lipas-result}))

(comment
  ;; PTV test orgs
  (def utajarvi "3d1759a2-e47a-4947-9a31-cab1c1e2512b") ; org 6
  (def raahe "92374b0f-7d3c-4017-858e-666ee3ca2761")    ; org 8
  (def liminka "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5")  ; org 9

  ;; local dev
  (def ctx (system->ctx integrant.repl.state/system))
  ;; server (lipas-dev): load this file into its nREPL, then
  (def ctx (system->ctx @lipas.backend.system/current-system))

  (reset-plan ctx raahe)
  (reset-org! ctx raahe))
