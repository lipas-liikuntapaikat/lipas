(ns lipas.backend.ptv.core
  (:require [cheshire.core :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.email :as email]
            [lipas.backend.gis :as gis]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.ai :as ai]
            [lipas.backend.ptv.audit :as audit]
            [lipas.backend.ptv.integration :as ptv]
            [lipas.backend.search :as search]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.data.types :as types]
            [lipas.roles :as roles]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

(defn parse-ptv-error
  "Extract structured error info from a PTV API ExceptionInfo. Returns nil
  if `e` doesn't carry a PTV HTTP response in its ex-data."
  [^Exception e]
  (let [data (ex-data e)
        resp-body (get-in data [:resp :body])
        status (get-in data [:resp :status])]
    (when (and status resp-body)
      {:ptv-status status
       :ptv-error (if (string? resp-body)
                    (try (json/parse-string resp-body true)
                         (catch Exception _ {:raw resp-body}))
                    resp-body)})))

(defn get-ptv-integration-candidates
  "The org's PTV-eligible sites from the search index. Their audits are a
   separate fetch (get-ptv-site-audits) the frontend merges in."
  [search criteria]
  (ptv/get-eligible-sites search criteria))

(defn get-ptv-site-audits
  "Current katselmointi of the given sites (see lipas.backend.ptv.audit/site-audits)."
  [db lipas-ids]
  (audit/site-audits db lipas-ids))

(defn get-ptv-sync-bases
  "lipas-id -> what the site's last PTV sync sent (ptv-review/->review-base),
  for the sync review. Sites never synced are left out."
  [db lipas-ids]
  (update-vals (db/get-ptv-sync-bases db lipas-ids) ptv-review/->review-base))

(defn- get-channel-or-nil
  "The service location, or nil when PTV doesn't have it (archived)."
  [ptv org-id channel-id]
  (try
    (ptv/get-org-service-channel ptv org-id channel-id)
    (catch clojure.lang.ExceptionInfo e
      (if (= 404 (-> e ex-data :resp :status))
        nil
        (throw e)))))

(defn- service-names-by-id
  "services-by-id shaped {id {:serviceNames ...}} for the services among
  `service-ids` that PTV has, read one by one: a site has a few."
  [ptv org-id service-ids]
  (into {} (keep (fn [id]
                   (try
                     [id (select-keys (ptv/get-service ptv org-id id) [:serviceNames])]
                     (catch clojure.lang.ExceptionInfo e
                       (log/warnf "PTV service %s not readable for the sync review: %s"
                                  id (ex-message e))
                       nil))))
        service-ids))

(defn get-ptv-site-review
  "What the site form needs to review a synced site against PTV before a
  save syncs it over PTV: what the last sync sent (:base), the linked
  service location as PTV has it now (:channel, nil if gone), names for the
  services only Lipas connects it to (:services; the location names its own)
  and the org's PTV languages. nil for a site not synced to a location."
  [db ptv lipas-id]
  (let [site (db/get-sports-site db lipas-id)
        org-id (-> site :ptv :org-id)
        channel-id (-> site :ptv :service-channel-ids first)]
    (when (and org-id channel-id (-> site :ptv :last-sync))
      (let [channel (get-channel-or-nil ptv org-id channel-id)
            lipas-only (remove (set (ptv-data/channel-service-ids channel))
                               (-> site :ptv :service-ids))]
        {:lipas-id lipas-id
         :base (get (get-ptv-sync-bases db [lipas-id]) lipas-id)
         :channel channel
         :services (service-names-by-id ptv org-id lipas-only)
         :org-langs (or (:supported-languages (ptv/get-org-ptv-config-with-fallback ptv org-id))
                        ptv-data/fallback-languages)}))))

(defn- enforce-summary-limit
  "Single-result variant of `ai/enforce-summary-limit`. Non-map content
   (an unparseable model response) passes through untouched."
  [result & opts]
  (if (map? result)
    (first (apply ai/enforce-summary-limit [result] opts))
    result))

(defn generate-ptv-descriptions
  [{:keys [client indices] :as _search}
   lipas-id & [{:keys [reference]}]]
  (let [idx (get-in indices [:sports-site :search])
        doc (-> (search/fetch-document client idx lipas-id)
                :body
                :_source)]
    (-> (ai/generate-ptv-descriptions doc {:reference reference})
        :message
        :content
        enforce-summary-limit)))

(defn generate-ptv-descriptions-batch
  "Generate PTV descriptions for multiple same-type sports sites in one Gemini call.

  opts keys:
    :lipas-ids  — seq of integer lipas ids (same type, ≤10 recommended)
    :reference  — optional {:summary :description} (Finnish) anchoring style across
                  partitioned batches

  Returns {:sites [{:lipas-id :summary :description :user-instruction} ...]}"
  [{:keys [client indices] :as _search}
   {:keys [lipas-ids reference]}]
  (let [idx   (get-in indices [:sports-site :search])
        docs  (mapv (fn [lipas-id]
                      (-> (search/fetch-document client idx lipas-id)
                          :body
                          :_source))
                    lipas-ids)
        content (-> (ai/generate-ptv-descriptions-batch docs {:reference reference})
                    :message
                    :content)]
    (cond-> content
      (and (map? content) (seq (:sites content)))
      (update :sites ai/enforce-summary-limit))))

(defn generate-ptv-descriptions-from-data
  [doc & [{:keys [reference]}]]
  (let [doc (core/enrich doc)]
    (-> (ai/generate-ptv-descriptions doc {:reference reference})
        :message
        :content
        enforce-summary-limit)))

(defn select-translation
  "Narrows a model translation `result` to what `doc` asked for. The response
   schema makes the model fill every language, and fill a :user-instruction
   even when none was sent, so: keep only the :to languages, drop
   :user-instruction unless the source had one, and put the :from texts back
   exactly as written."
  [{:keys [from to user-instruction] :as doc} result]
  (let [from (keyword from)
        keep-langs (conj (set (map keyword to)) from)
        fields (cond-> [:summary :description]
                 user-instruction (conj :user-instruction))]
    (into {}
          (for [field fields]
            [field (-> (select-keys (get result field) keep-langs)
                       (assoc from (get doc field)))]))))

(defn translate-to-other-langs
  [doc]
  (let [content (-> (ai/translate-to-other-langs doc) :message :content)]
    (if (map? content)
      (-> (select-translation doc content)
          (enforce-summary-limit :skip-langs #{(keyword (:from doc))}))
      content)))

(defn make-overview
  [sites]
  {:city-name (->> sites first :search-meta :location :city :name)
   :service-name (->> sites first :search-meta :type :sub-category :name)
   :sports-facilities (for [site sites]
                        {:type (-> site :search-meta :type :name :fi)})})

(defn- nil-safe-frequencies
  "Like frequencies but replaces nil keys with \"unknown\"."
  [coll]
  (into {} (map (fn [[k v]] [(if (nil? k) "unknown" k) v]))
        (frequencies coll)))

(defn make-aggregate-overview
  [sites {:keys [free-use? surface-materials? lighting?]}]
  (let [base {:city-name    (->> sites first :search-meta :location :city :name)
              :service-name (->> sites first :search-meta :type :sub-category :name)
              :total-count  (count sites)
              :by-type      (nil-safe-frequencies (map #(-> % :search-meta :type :name :fi) sites))}]
    (cond-> base
      free-use?
      (assoc :free-use (nil-safe-frequencies (map #(get-in % [:properties :free-use?]) sites)))

      surface-materials?
      (assoc :surface-materials (nil-safe-frequencies (mapcat #(get-in % [:properties :surface-material]) sites)))

      lighting?
      (assoc :lighting (nil-safe-frequencies (map #(get-in % [:properties :ligthing?]) sites))))))

(defn generate-ptv-service-descriptions
  [search
   {:keys [sub-category-id city-codes overview]}]
  (let [doc (or overview
                (let [type-codes (->> (types/by-sub-category sub-category-id)
                                      (map :type-code))
                      sites (ptv/get-eligible-sites search {:type-codes type-codes
                                                            :city-codes city-codes
                                                            :owners ["city" "city-main-owner"]})]
                  ;; Aggregate stats (counts, free-use, surfaces, lighting)
                  ;; give the model grounding data for access/fee claims —
                  ;; the same doc shape the prompt was benched with.
                  (make-aggregate-overview sites {:free-use? true
                                                  :surface-materials? true
                                                  :lighting? true})))]
    (-> (ai/generate-ptv-service-descriptions doc {:sub-category-id sub-category-id})
        :message
        :content
        enforce-summary-limit)))

(defn persist-ptv-service-revision!
  "Appends a ptv_service revision from a PTV Service API response. Never
   throws — the PTV write has already succeeded and cannot be rolled back;
   a failed shadow write is only logged and the next upsert self-heals.
   (Audits live apart in ptv_service_audit, nothing to carry forward.)"
  ([db user ptv-org-id ptv-resp]
   (persist-ptv-service-revision! db user ptv-org-id ptv-resp nil))
  ([db user ptv-org-id ptv-resp extra-doc]
   (try
     (let [source-id (:sourceId ptv-resp)]
       (if (str/blank? source-id)
         (log/warnf "PTV service %s has no sourceId; skipping ptv_service revision"
                    (:id ptv-resp))
         (if-let [org (backend-org/get-org-by-ptv-org-id db ptv-org-id)]
           (let [now (utils/timestamp)
                 doc (merge (ptv-data/->service-document ptv-org-id ptv-resp)
                            extra-doc
                            {:last-sync now})]
             (ptv-service-db/insert-service-rev!
               db
               {:org-id (:id org)
                :source-id source-id
                :service-id (:id ptv-resp)
                :status "active"
                :author-id (or (:id user) (get-in user [:login :user :id]))
                :event-date now
                :document doc}))
           (log/warnf "No LIPAS org found for PTV org %s; skipping ptv_service revision"
                      ptv-org-id))))
     (catch Exception e
       (log/error e "Failed to persist ptv_service revision (PTV write already succeeded)")))))

(defn- org-languages
  "The org's languages from its live PTV config, never from the request,
  which carries the browser's possibly stale copy of it."
  [ptv org-id]
  (or (:supported-languages (ptv/get-org-ptv-config-with-fallback ptv org-id))
      ptv-data/fallback-languages))

(defn- check-text-lengths!
  "Throws, before anything is sent, when PTV would reject a text of the
  Service PUT `body` as too short (see ptv-data/min-text-length)."
  [body]
  (when-let [problems (seq (ptv-data/short-service-texts body))]
    (throw (ex-info "PTV texts under the minimum length"
                    {:type :texts-too-short
                     :problems (vec problems)}))))

(defn- live-lipas-service
  "The live PTV version of the LIPAS-created service `service-id`, or nil when
  there is none to build on: no id was given, or PTV no longer has it. Throws
  when the id belongs to another service than `source-id`'s."
  [ptv org-id source-id service-id]
  (when service-id
    (when-let [live (try
                      (ptv/get-service ptv org-id service-id)
                      (catch clojure.lang.ExceptionInfo e
                        (when-not (= 404 (:status (:resp (ex-data e))))
                          (throw e))))]
      (when (not= source-id (:sourceId live))
        (throw (ex-info "service-id and source-id name different PTV services"
                        {:service-id service-id :source-id source-id
                         :live-source-id (:sourceId live)})))
      live)))

(defn- upsert-lipas-service!
  "Writes a service LIPAS created: by source-id, created with the full body
  built from the sub-category when PTV doesn't know it yet.

  An update leaves out the defaults LIPAS sets only on creation and resends
  PTV's own target groups and the entries LIPAS never writes (see
  ptv-data/lipas-service-update-body). `service-id`, the service's PTV id
  when it already exists, is only read from, to know what PTV has."
  [ptv {:keys [org-id source-id service-id] :as m}]
  (let [create-body (ptv-data/->ptv-service (assoc m :languages (org-languages ptv org-id)))
        update-body (ptv-data/lipas-service-update-body
                      create-body
                      (live-lipas-service ptv org-id source-id service-id))]
    (check-text-lengths! update-body)
    (try
      (ptv/update-service ptv source-id update-body)
      (catch clojure.lang.ExceptionInfo e
        (if (= 404 (:status (:resp (ex-data e))))
          (ptv/create-service ptv create-body)
          (throw e))))))

(defn- native-service-writable?
  "True when LIPAS may patch the texts of `service`, a service made in PTV:
  it has no sourceId yet, or the one LIPAS gave it on its first patch. A
  sourceId of another system means that system maintains it."
  [org-id service-id service]
  (let [source-id (:sourceId service)]
    (or (str/blank? source-id)
        (= source-id (ptv-data/->adopted-service-source-id org-id service-id)))))

(defn- native-service-put-body
  "The PUT body for patching `texts` into the live `service`
  (ptv-data/native-patch-body), or throws when the result would leave a
  language version, or a text, PTV rejects."
  [org-id service-id service texts remove-langs]
  (let [body (ptv-data/native-patch-body org-id service-id service texts remove-langs)
        problems (ptv-data/language-version-problems body)]
    (when (seq problems)
      (throw (ex-info "Incomplete PTV language versions"
                      {:type :incomplete-language-versions
                       :problems problems})))
    (check-text-lengths! body)
    body))

(defn- patch-native-service!
  "Patches LIPAS's texts into a service made in PTV (see
  ptv-data/patch-service-texts): by PTV id, on top of the live service, so
  its classification, other texts and language versions LIPAS doesn't touch
  are kept. The first patch also gives it LIPAS's sourceId, which marks it
  as one LIPAS writes texts to (and so one DVV audits) and gives it a
  ptv_service lineage.

  `expected-modified` is the PTV modified time the user's edits were made
  on; a service changed in PTV since then is refused rather than overwritten."
  [ptv {:keys [org-id service-id expected-modified remove-languages] :as m}]
  (let [service (ptv/get-service ptv org-id service-id)]
    (when-not (native-service-writable? org-id service-id service)
      (throw (ex-info "PTV service is maintained by another system"
                      {:type :source-id-in-use :service-id service-id})))
    (when (and expected-modified (not= expected-modified (:modified service)))
      (throw (ex-info "PTV service changed since it was loaded"
                      {:type :changed-in-ptv
                       :service-id service-id
                       :modified (:modified service)})))
    (ptv/update-service-by-id
      ptv org-id service-id
      (native-service-put-body org-id service-id service
                               (select-keys m [:name :summary :description :user-instruction])
                               (keep ptv-data/lipas-lang->ptv-lang remove-languages)))))

(defn- mirrored-service-id
  "The PTV id of the service `source-id` as LIPAS's ptv_service mirror
  recorded it on LIPAS's last write to it, or nil when LIPAS has none."
  [db ptv-org-id source-id]
  (when source-id
    (when-let [org (backend-org/get-org-by-ptv-org-id db ptv-org-id)]
      (some-> (ptv-service-db/get-current db (:id org) source-id) :service-id str))))

(defn upsert-ptv-service!
  "Writes a service's texts to PTV and appends the result to the ptv_service
  mirror. `mode` says which kind of service it is, and so how it's written:

  - \"lipas\" (default): created by LIPAS, which maintains its name, texts,
    service classes, ontology terms and languages. Written by `source-id`:
    created with LIPAS's full body when PTV doesn't know it yet, otherwise
    updated on top of the live service (the one `service-id` names, or the
    ptv_service mirror does when the request doesn't), leaving to PTV what
    LIPAS only defaults (see upsert-lipas-service!).
  - \"native\": made in PTV. Only its name and texts are patched in, by
    `service-id` (see patch-native-service!).

  The mode is explicit: whether a service-id happens to be present must not
  decide how a service is written."
  [db ptv user {:keys [org-id mode] :as m}]
  (let [resp (case (or mode "lipas")
               ;; Without the service's id from the client (the wizard, an
               ;; older browser), LIPAS's own record of it, so the write
               ;; still keeps what PTV has that LIPAS doesn't write
               "lipas" (upsert-lipas-service!
                         ptv (update m :service-id
                                     #(or % (mirrored-service-id db org-id (:source-id m)))))
               "native" (patch-native-service! ptv m))]
    (persist-ptv-service-revision! db user org-id resp)
    resp))

(defn fetch-ptv-org
  [ptv org-id]
  (ptv/get-org ptv org-id))

(defn fetch-ptv-service-collections
  [ptv org-id]
  (ptv/get-org-service-collections ptv org-id))

(defn fetch-ptv-services
  [ptv org-id]
  (ptv/get-org-services ptv org-id))

(defn fetch-ptv-service-channels
  "Fetch the org's service channels from PTV with full entity data.
   Uses the /list/organization endpoint which returns complete channel
   entities (descriptions, sourceId, publishingStatus etc.) needed for
   drift detection and status warnings."
  [ptv org-id]
  (ptv/get-org-service-channels ptv org-id))

(defn fetch-ptv-service-channel
  [ptv org-id service-channel-id]
  (ptv/get-org-service-channel ptv org-id service-channel-id))

;; Single source of truth lives in lipas.data.ptv so the sync path and the
;; no-sync meta path persist exactly the same editable keys.
(def persisted-ptv-keys ptv-data/persisted-ptv-keys)

(defn resolve-service-location-source-id
  "The sourceId to send when writing the service-location `channel-id`, or
  nil for `->ptv-service-location` to generate a fresh one.

  A LIPAS sourceId is bound to the PTV channel it was first sent with, and
  PTV keeps it reserved even after that channel is archived. The stored
  `:source-id` is therefore stale whenever the site now points at another
  channel (relinked to a pre-existing one, or unlinked and re-created), and
  sending it makes PTV fail with an unexplained 500. So follow PTV's own
  record of the target channel instead:

  - create (no channel-id): always a fresh sourceId
  - update: the target channel's sourceId, fresh if it has none (a channel
    created outside LIPAS)
  - update of a channel that 404s (archived, being re-published): the stored
    one, which is what the archived channel carries"
  [channel-id channel stored-ptv]
  (cond
    (nil? channel-id) nil
    (nil? channel) (:source-id stored-ptv)
    :else (:sourceId channel)))

(defn upsert-ptv-service-location!*
  [ptv-component {:keys [org-id site ptv archive?] :as _m}]
  (let [id (-> ptv :service-channel-ids first)
        ;; Languages are determined by the org's live PTV config at sync time —
        ;; we don't trust the site's persisted :languages because it may be a
        ;; snapshot from an older org config. See calc-derived-fields archaeology.
        org-config (ptv/get-org-ptv-config-with-fallback ptv-component org-id)
        org-langs (or (:supported-languages org-config)
                      ptv-data/fallback-languages)
        ;; ...narrowed to the languages the site has texts for, so the stored
        ;; :languages records what PTV actually got.
        ptv (assoc ptv :languages (ptv-data/effective-languages (update site :ptv merge ptv)
                                                                org-langs))
        ;; PTV would reject the whole body for one text under its minimum
        ;; length in a language it publishes: refuse before calling PTV at
        ;; all. A status-only archive sends no texts.
        _ (when (and (not (and archive? id))
                     (seq (ptv-data/service-location-short-texts (update site :ptv merge ptv)
                                                                 org-langs)))
            (throw (ex-info "PTV texts under the minimum length"
                            {:blockers [:ptv/texts-too-short]})))

        ;; Fetch the stored channel for service-connection diffing below.
        ;; Drift detection in `sports-site->ptv-input` uses a separately
        ;; cached channel snapshot, not this fetch.
        ;; A previously-archived channel (publishing-status "Deleted") 404s on
        ;; every PTV read endpoint but is still addressable for a PUT — so a
        ;; resurrect (re-publish) must tolerate the 404 here and proceed.
        old-service-location (when id
                               (try
                                 (ptv/get-org-service-channel ptv-component org-id id)
                                 (catch clojure.lang.ExceptionInfo e
                                   (if (= 404 (-> e ex-data :resp :status))
                                     nil
                                     (throw e)))))
        ;; The first sync of a location made in PTV keeps the services it is
        ;; already connected to (ptv-data/link-service-ids); afterwards
        ;; LIPAS's list is the master.
        ptv (cond-> ptv
              old-service-location
              (assoc :service-ids (ptv-data/link-service-ids (merge (:ptv site) ptv)
                                                             old-service-location)))

        ;; merge or just replace?
        site (-> (update site :ptv merge ptv)
                 (assoc-in [:ptv :source-id]
                           (resolve-service-location-source-id id old-service-location (:ptv site))))
        ;; Use the same TS for sourceId, ptv last-sync and site event-date
        now (utils/timestamp)
        ;; Archiving an existing channel changes only its publishingStatus. A
        ;; full body would be validated as a whole, and e.g. a name clashing
        ;; with another channel in the org would block the archive.
        status-only-archive? (and archive? id)
        data (when-not status-only-archive?
               (cond-> (ptv-data/->ptv-service-location org-id gis/wgs84->tm35fin-no-wrap now (core/enrich site))
                 archive? (assoc :publishingStatus "Deleted")))
        ;; Note: Update request doesn't update Service connections!

        ptv-resp (cond
                   status-only-archive? (ptv/archive-service-location ptv-component
                                                                      (or (-> site :ptv :org-id) org-id)
                                                                      id)
                   id (ptv/update-service-location ptv-component id data)
                   :else (ptv/create-service-location ptv-component data))
        _ (when id
            ;; Update service connection changes
            (let [old-services (->> old-service-location
                                    :services
                                    (map (comp :id :service))
                                    set)
                  new-services (set (:service-ids (:ptv site)))
                  removed-services (set/difference old-services new-services)
                  new-services (set/difference new-services old-services)
                  service-channel-id (first (:service-channel-ids (:ptv site)))]
              (log/infof "Update service-location connections: +%s -%s" new-services removed-services)
              (doseq [service-id removed-services]
                (ptv/update-service-connections ptv-component org-id service-id #(disj % service-channel-id)))
              (doseq [service-id new-services]
                (ptv/update-service-connections ptv-component org-id service-id #(conj % service-channel-id)))))
        ;; Refetch the channel via GET so the FE caches the canonical
        ;; post-sync shape (the same shape /list/organization returns and
        ;; that the sync review, `lipas.data.ptv/sync-review`, expects).
        ;; The PUT response is captured BEFORE the separate
        ;; update-service-connections calls land, so its :services list
        ;; can be stale on updates. Without refetching, drift detection
        ;; sees a stale :services list and reports :content-drift instead
        ;; of :ok — that's why the green chip didn't appear immediately
        ;; after sync (tester feedback). Tolerate refetch failures
        ;; (transient PTV errors) by falling back to the PUT response.
        ptv-resp (try
                   (ptv/get-org-service-channel ptv-component org-id (:id ptv-resp))
                   (catch Exception e
                     (log/warnf e "Failed to refetch channel %s after sync; using PUT response" (:id ptv-resp))
                     ptv-resp))
        ;; Store the new PTV info to Lipas DB
        new-ptv-data (-> ptv
                         (select-keys persisted-ptv-keys)
                         (assoc :org-id (or (:org-id ptv)
                                            org-id)
                                :last-sync now
                                ;; Store the current type-code into ptv data, so this can be
                                ;; used to comapre if the services need to recalculated on site data update.
                                :previous-type-code (:type-code (:type site))
                                :source-id (:sourceId ptv-resp)
                                ;; Store the PTV status so we can ignore Lipas archived places that we already archived in PTV.
                                :publishing-status (:publishingStatus ptv-resp)
                                ;; NOTE: The ptv map might not have this value in some cases...?
                                ;; but the value merged with data from site should have it always?
                                :service-ids (:service-ids (:ptv site))
                                ;; Take the created ID from ptv response and store to Lipas DB right away.
                                ;; TODO: Is there a case where this could be multiple ids?
                                :service-channel-ids [(:id ptv-resp)])
                         ;; Keep :source-id and :service-channel-ids on archive so
                         ;; the LIPAS↔PTV link survives. :publishing-status is
                         ;; stored as "Deleted" (from ptv-resp) above, so
                         ;; is-sent-to-ptv? stays false and nothing re-archives;
                         ;; but a later re-activation re-publishes the SAME PTV
                         ;; channel (PUT Published) instead of creating a
                         ;; duplicate that collides with PTV's unique-name rule.
                         ;; Clear only the one-shot :delete-existing flag.
                         (cond->
                           archive? (dissoc :delete-existing)))]

    (log/infof "Upserted service-location %s (status: %s, update: %s, channel: %s)"
               (:lipas-id site) (:status site) (boolean id) (:id ptv-resp))

    [ptv-resp new-ptv-data]))

(defn assert-no-double-link!
  "Guards the PTV invariant of one service-location per sports-site: throws an
  ex-info {:type :double-link ...} when `service-channel-id` is already bound to
  a sports-site other than `lipas-id`. Two LIPAS sites sharing one PTV
  service-location corrupt each other — their one-way syncs overwrite the
  channel and archiving one archives the shared channel. A blank/nil channel-id
  (a fresh create or a deliberate unlink) is always allowed. Re-syncing a site's
  own channel is allowed because the current `lipas-id` is excluded."
  [db lipas-id service-channel-id]
  (when-not (str/blank? service-channel-id)
    (let [others (->> (db/get-sports-sites-by-ptv-service-channel-id db service-channel-id)
                      (remove #(= lipas-id (:lipas-id %))))]
      (when (seq others)
        (throw (ex-info "PTV service-location already linked to another sports-site"
                        {:type :double-link
                         :service-channel-id service-channel-id
                         :lipas-id lipas-id
                         :other-sites (vec others)}))))))

(defn check-service-channel-link
  "Returns the OTHER sports-sites currently linked to `service-channel-id`,
  excluding `lipas-id`. The single-site PTV view uses this to warn about a
  pre-existing double-link, which it can't detect locally (it loads neither the
  org's channels nor its sibling sites)."
  [db {:keys [lipas-id service-channel-id]}]
  {:service-channel-id service-channel-id
   :other-sites (->> (db/get-sports-sites-by-ptv-service-channel-id db service-channel-id)
                     (remove #(= lipas-id (:lipas-id %)))
                     vec)})

(defn upsert-ptv-service-location!
  "Syncs the saved site with `ptv` merged in. `site-names` (the sync
   review's choice of PTV's names, see ptv-review/review-site-names) are
   written into the site first, which needs the right to edit it."
  [db ptv-component search user {:keys [lipas-id org-id ptv archive? site-names]}]
  ;; FIXME: This is called from inside tx in save-sports-site! is that a problem?
  ;; FIXME: Separate version from this fn for use in sync-ptv! which doesn't load the
  ;; sports site from db etc.?
  (jdbc/with-db-transaction [tx db]
    (let [site (db/get-sports-site db lipas-id)
          _ (assert (some? site) (str "Sports site " lipas-id " not found in DB"))
          _ (when (and (seq site-names)
                       (not (roles/check-privilege user (roles/site-roles-context site)
                                                   :site/create-edit)))
              (throw (ex-info "Changing the site's name needs the right to edit the site"
                              {:type :no-permission :lipas-id lipas-id})))
          site (cond-> site
                 (seq site-names) (ptv-review/apply-site-names site-names))
          ;; Reject linking to a service-location another site already owns
          ;; before we touch PTV (a fresh create / unlink has no id and passes).
          _ (assert-no-double-link! tx lipas-id (-> ptv :service-channel-ids first))

          [ptv-resp new-ptv-data] (upsert-ptv-service-location!* ptv-component
                                                                 {:org-id org-id
                                                                  :site site
                                                                  :ptv ptv
                                                                  :archive? archive?})]

      (let [resp (core/upsert-sports-site! tx
                                           user
                                           (assoc site
                                                  :event-date (:last-sync new-ptv-data)
                                                  :ptv new-ptv-data)
                                           false)]
        (core/index! search resp :sync (core/index-context db)))

      ;; No need to re-index for search after ptv change

      {;; Return the updated :ptv meta for sports-site, to for the app-db
       :ptv new-ptv-data
       ;; Return full PTV response so frontend can update service-channels cache
       ;; (needed for drift detection and PTV link)
       :ptv-resp ptv-resp})))

(comment
  (require '[integrant.repl.state :as state])

  (let [ptv-component (:lipas/ptv state/system)
        org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
        services (:itemList (ptv/get-org-services ptv-component org-id))]
    (->> services
         (utils/index-by :sourceId)
         keys)))

(def ^:private removal-statuses
  "Statuses that mean the facility is genuinely gone — the only status
  transition that auto-archives a previously-published PTV service-location."
  #{"incorrect-data" "out-of-service-permanently"})

(defn to-archive?
  "Should saving `sports-site` archive its PTV service-location
  (publishingStatus \"Deleted\")? Only for a genuine removal of a
  previously-published site: its status set to permanently-out / incorrect-data,
  or an explicit `:delete-existing` request in `ptv`. Owner/type changes and
  incomplete (not ptv-ready?) texts never archive."
  [sports-site ptv]
  (boolean
    (and (ptv-data/is-sent-to-ptv? sports-site)
         (or (contains? removal-statuses (:status sports-site))
             (:delete-existing ptv)))))

(defn- site->missing-services
  "Services the site's sub-category still needs in PTV before it can sync;
  a seq of missing services, empty when the site is satisfied.

  A site is satisfied as soon as it references a service (`:ptv
  :service-ids` non-empty). That deliberately covers all three kinds of
  service: LIPAS sub-category-managed (`lipas-{org}-{sub-category}`),
  adopted (`lipas-{org}-ptv-{id}`), and services the org created in PTV
  directly (no LIPAS sourceId, so absent from `source-id->service`). The
  presence of a service-id is the durable signal — we do NOT require a
  `lipas-{org}-{sub-category}` service to exist.

  History: the old call hard-coded `:service-ids #{}` and so only ever
  recognized sub-category-managed services. That was correct when the BE
  auto-created missing services (pre-62fb687a), but once creation moved to
  the FE and adoption was added the check was never reconciled, wrongly
  rejecting sites that reference an existing PTV service. The input shape
  here is the flat one the FE wizard feeds `resolve-missing-services`
  (sports-site->ptv-input), so FE and BE agree on what 'missing' means."
  [types source-id->service org-id sports-site]
  (ptv-data/resolve-missing-services
    org-id source-id->service
    [{:service-ids (-> sports-site :ptv :service-ids)
      :sub-category-id (-> sports-site :type :type-code types :sub-category)
      :sub-category (-> sports-site :search-meta :type :sub-category :name :fi)}]))

;; Used through resolve due to circular dep
;; TODO: Check if code can be moved around to avoid this
^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn sync-ptv! [tx search ptv-component user {:keys [sports-site ptv org-id lipas-id]}]
  (try
    (let [ready? (ptv-data/ptv-ready? sports-site)
          archive? (to-archive? sports-site ptv)]
      (cond
        ;; Safety guards. core/check-ptv-save! rejects PTV edits that would
        ;; land here, but an untouched, pre-existing unsyncable :ptv (or a
        ;; future caller) still can. Never skip silently: the throw is caught
        ;; below and stored as :ptv :error on a new revision, so the site
        ;; form and the admin views show it. Any existing PTV record stays
        ;; frozen.
        (str/blank? org-id)
        (throw (ex-info "PTV-organisaatio puuttuu – liikuntapaikkaa ei voi viedä PTV:hen"
                        {:blockers [:ptv/no-org]}))

        (and (not archive?) (not ready?))
        (throw (ex-info "PTV-kuvaukset puuttuvat – liikuntapaikkaa ei voi viedä PTV:hen"
                        {:blockers [:ptv/missing-texts]}))

        :else
        (let [type-code (-> sports-site :type :type-code)
              type-code-changed? (not= type-code (:previous-type-code ptv))
              ptv (if type-code-changed?
                    (let [types types/all
                           ;; Figure out what services are available in PTV for the site organization
                          services (:itemList (ptv/get-org-services ptv-component org-id))
                          source-id->service (->> services
                                                  (utils/index-by :sourceId))

                           ;; Block only sites whose sub-category has NO service
                           ;; at all. A site that already references a service
                           ;; (LIPAS-managed, adopted, or created directly in
                           ;; PTV by the org) is satisfied — see
                           ;; site->missing-services. The FE is expected to have
                           ;; created/linked the service first.
                          missing-services (site->missing-services types source-id->service org-id sports-site)

                          _ (when (seq missing-services)
                              (throw (ex-info "Site needs a PTV Service that doesn't exists"
                                              {:missing-services missing-services})))

                           ;; Remove old service-ids from :ptv data and add the new.
                           ;; Don't touch other service-ids in the data, those could have be added manually in UI or in PTV.
                           ;; NOTE: OK, PTV updates are likely lost, because our :ptv :service-ids is what the create/update from
                           ;; Lipas previously returned, so if PTV ServiceLocation was modified after that in PTV, we lose those changes.
                          old-sports-site (assoc-in sports-site [:type :type-code] (:previous-type-code ptv))
                          old-service-ids (ptv-data/sports-site->service-ids types source-id->service old-sports-site)
                          new-service-ids (ptv-data/sports-site->service-ids types source-id->service sports-site)]
                      (log/infof "Site %d type changed %s => %s, service-ids %s => %s"
                                 lipas-id (:previous-type-code ptv) type-code
                                 old-service-ids new-service-ids)
                      (update ptv :service-ids (fn [ids]
                                                 (let [x (set ids)
                                                       x (apply disj x old-service-ids)
                                                       x (into x new-service-ids)]
                                                   (vec x)))))
                    ptv)

              [_ptv-resp new-ptv-data] (upsert-ptv-service-location!* ptv-component
                                                                      {:org-id org-id
                                                                       :ptv ptv
                                                                       :site sports-site
                                                                       :archive? archive?})

              resp (core/upsert-sports-site! tx
                                             user
                                             (assoc sports-site
                                                    :event-date (:last-sync new-ptv-data)
                                                    :ptv new-ptv-data)
                                             false)]

          (core/index! search resp :sync (core/index-context tx))
          ;; Return both :ptv and :event-date so the caller's outer index!
          ;; reindexes the same revision we just wrote.
          {:ptv new-ptv-data :event-date (:event-date resp)})))
    (catch Exception e
      ;; Don't store (ex-data e) directly — clj-http's response includes a
      ;; live `HttpClient` Java object that Cheshire can't serialize to the
      ;; JSONB column, and `(ex-data e) :req :headers` carries the PTV
      ;; bearer token. The parsed `:ptv-status`/`:ptv-error` covers
      ;; everything the frontend and operators actually need.
      (let [new-ptv-data (assoc ptv :error (merge {:message (.getMessage e)}
                                                  ;; our own guards above
                                                  (select-keys (ex-data e) [:blockers])
                                                  (parse-ptv-error e)))]
        (log/errorf e "Sports site %d updated but PTV sync failed" lipas-id)
        (let [resp (core/upsert-sports-site! tx
                                             user
                                             (-> sports-site
                                                 (assoc :event-date (utils/timestamp))
                                                 (assoc :ptv new-ptv-data))
                                             false)]
          (core/index! search resp :sync (core/index-context tx))
          ;; Failure path also wrote a new revision (with :error captured
          ;; on :ptv). Return the same :event-date so DB and ES match.
          {:ptv (:ptv resp) :event-date (:event-date resp)})))))

(defn save-ptv-integration-definitions
  "Saves ptv definitions under key :ptv. Does not notify webhooks,
  integrations or analysis queues since they're not likely interested
  in this."
  [db search user lipas-id->ptv-meta]
  (jdbc/with-db-transaction [tx db]
    (doseq [[lipas-id ptv] lipas-id->ptv-meta]
      ;; Meta-save can persist :service-channel-ids without a sync, so guard the
      ;; one-service-location-per-site invariant here too.
      (assert-no-double-link! tx lipas-id (-> ptv :service-channel-ids first))
      (when-let [existing (core/get-sports-site tx lipas-id)]
        ;; Persist the same editable keys the sync path does (persisted-ptv-keys
        ;; from the payload), and explicitly preserve the server-owned lifecycle
        ;; keys from the existing record — there's no PTV response here to
        ;; re-derive them, and a blanket (assoc :ptv ptv) would wipe them and
        ;; break the reversible-archive bookkeeping (is-sent-to-ptv?).
        ;; :last-sync is server-owned too (when LIPAS last pushed to PTV):
        ;; never taken from the client, and dropping it made a site that IS in
        ;; PTV read as never exported (sync-status :not-synced).
        (let [old-ptv (:ptv existing)
              new-ptv (-> (select-keys ptv persisted-ptv-keys)
                          (assoc :source-id          (:source-id old-ptv)
                                 :publishing-status  (:publishing-status old-ptv)
                                 :previous-type-code (:previous-type-code old-ptv))
                          (dissoc :last-sync)
                          (cond-> (:last-sync old-ptv) (assoc :last-sync (:last-sync old-ptv))))
              site (assoc existing
                          :event-date (utils/timestamp)
                          :ptv new-ptv)]
          (core/upsert-sports-site! tx user site)
          (core/index! search site :sync (core/index-context db))))))
  {:status "OK"})

(defn save-ptv-audit
  "Saves DVV's katselmointi verdicts on a sports site (see
   lipas.backend.ptv.audit/save-site-audit!). Returns the stored audit
   map, or nil for an unknown site."
  [db user {:keys [lipas-id audit]}]
  (when-let [site (core/get-sports-site db lipas-id)]
    (audit/save-site-audit! db user site audit)))

(defn ptv-member-suggestions
  "Accounts with PTV rights for a municipality that `org-id` covers, but who
  aren't its members — the same rule as the admin PTV user list
  (`ptv-data/ptv-user-rows`), so both offer exactly the same people. For the
  org's admins: names only, no emails, so org-admins don't get a new way to
  enumerate accounts."
  [db org-id]
  (let [users (backend-org/with-org-info db (db/get-users db))]
    (->> (ptv-data/ptv-user-rows users (backend-org/all-orgs db))
         (filter #(and (= (str org-id) (get-in % [:org :id]))
                       (not (:member? %))))
         (map #(select-keys % [:user-id :username :name]))
         distinct
         vec)))

(defn add-suggested-ptv-member!
  "Adds a suggested PTV manager to the org as a plain member — which roles they
  get is the org-admin's call (Members tab). Only accounts from
  `ptv-member-suggestions` are accepted, so this can't add arbitrary accounts by
  id.

  Silent on purpose: no email. Org management isn't a public feature; for PTV
  the org is just where a municipality's PTV config lives, and these users
  never see the org views, so a \"you've been added to an organization\" mail
  would only confuse them. (An org invite does email: someone chose to
  invite.)"
  [db org-id user-id author-id]
  (when-not (some #(= (str user-id) (:user-id %)) (ptv-member-suggestions db org-id))
    (throw (ex-info "Not a suggested member of this organization"
                    {:type :invalid-selection
                     :org-id (str org-id)
                     :user-id (str user-id)})))
  (backend-org/add-member! db org-id user-id {:roles []} author-id)
  {:user-id (str user-id)})

(defn get-ptv-managers
  "Returns the org's members who hold the :ptv-manager role for any of the
  organization's PTV cities — whether the role is stored directly on the
  account OR granted via the org's role-template catalog. Catalog grants are
  projection-only (JWT, never persisted to account.permissions), so they must
  be projected here with the same fn the login path uses
  (`backend-org/derive-org-roles`); filtering stored roles alone would miss
  every catalog-granted manager (F10)."
  [db org-id]
  (let [org (backend-org/get-org db org-id)
        org-city-codes (-> org :ptv-data :city-codes set)
        org-users (backend-org/get-org-users db org-id)
        ptv-manager? (fn [role]
                       (and (= :ptv-manager (keyword (:role role)))
                            (seq (set/intersection org-city-codes (set (:city-code role))))))]
    (filter (fn [user]
              (some ptv-manager?
                    (concat (-> user :permissions :roles)
                            (backend-org/derive-org-roles (:id user) [org]))))
            org-users)))

(defn get-audit-notification-recipients
  "Recipient emails for an org's audit-complete notification. Surfaced in
   the UI so the auditor sees who receives the notification (and what it
   contains) before sending it. Returns nil for an unknown org."
  [db org-id]
  (when (backend-org/get-org db org-id)
    {:recipients (->> (get-ptv-managers db org-id)
                      (map :email)
                      (remove str/blank?)
                      distinct
                      sort
                      vec)}))

;; The katselmointi notification tells the org's PTV managers which items
;; currently await their fixes. Contents are derived here at send/preview
;; time from the same cljc whose-move fns the audit view uses — never
;; trusted from the client — so an already-fixed item can't be reported as
;; an open change request, and repeat notifications only ever name what's
;; still (or newly) actionable.

(defn site-audit-notification-data
  "Current-state contents ({:action-items [..] :approved-count n}) for the
   org's sites-section katselmointi notification. Sites come from the same
   ES query the audit view lists. Returns nil for an unknown org."
  [db search org-id]
  (when-let [org (backend-org/get-org db org-id)]
    (->> (get-ptv-integration-candidates
           search (select-keys (:ptv-data org) [:city-codes :owners]))
         (audit/with-site-audits db)
         (map (fn [site]
                {:ref {:lipas-id (:lipas-id site) :name (:name site)}
                 :audit (get-in site [:ptv :audit])
                 :fields (ptv-data/site-audit-fields site)}))
         (ptv-data/audit-notification-summary))))

(defn service-audit-notification-data
  "Services-section counterpart of site-audit-notification-data: live PTV
   services joined with their stored ptv_service audits, the same join the
   audit view does. Returns nil for an unknown org."
  [db ptv org-id]
  (when-let [org (backend-org/get-org db org-id)]
    (let [ptv-org-id (-> org :ptv-data :org-id)
          services (when ptv-org-id
                     (:itemList (fetch-ptv-services ptv ptv-org-id)))
          audits (audit/current-service-audits db (:id org))]
      (->> services
           (filter #(some-> % :sourceId (str/starts-with? "lipas-")))
           (map (fn [svc]
                  (let [texts (ptv-data/ptv-descriptions->texts
                                (:serviceDescriptions svc))]
                    {:ref {:service-id (str (:id svc))
                           :name (ptv-data/select-service-name (:serviceNames svc))}
                     :audit (get audits (str (:id svc)))
                     :fields (ptv-data/service-audit-fields texts)})))
           (ptv-data/audit-notification-summary)))))

(defn get-audit-notification-preview
  "Recipients + derived contents for the notification confirmation dialog,
   so the auditor sees exactly what the send endpoint will email (the send
   re-derives the same data). Returns nil for an unknown org."
  [db search ptv org-id section]
  (when-let [recipients (get-audit-notification-recipients db org-id)]
    (merge recipients
           (case section
             "services" (service-audit-notification-data db ptv org-id)
             (site-audit-notification-data db search org-id)))))

(defn- send-audit-notification!*
  [db emailer org-id section data]
  (when-let [org (backend-org/get-org db org-id)]
    (let [managers (get-ptv-managers db org-id)]
      (if (seq managers)
        (do
          (doseq [manager managers]
            (email/send-ptv-audit-notification-email!
              emailer
              (:email manager)
              (assoc data
                     :org-name (:name org)
                     :section section)))
          {:sent (count managers)
           :recipients (mapv :email managers)})
        (do
          (log/warnf "No PTV managers found for organization %s" org-id)
          {:sent 0
           :recipients []
           :warning "No PTV managers found"})))))

(defn send-audit-notification!
  "Send the sites-section katselmointi notification to the org's PTV
   managers. Contents are derived at send time — see the section comment."
  [db search emailer org-id]
  (send-audit-notification!* db emailer org-id :sites
                             (site-audit-notification-data db search org-id)))

(defn- resolve-service-revision!
  "The lineage's current ptv_service revision for the service, by PTV
   service id then by source-id. When the service has never been
   persisted, takes an initial content revision from live PTV so the
   lineage — and the audit's revision reference — exist. Only
   sourceId-bearing (LIPAS-managed or adopted) services form auditable
   lineages; nil otherwise."
  [db ptv user org {:keys [service-id source-id]}]
  (or (ptv-service-db/get-current-by-service-id db (:id org) service-id)
      (when source-id
        (ptv-service-db/get-current db (:id org) source-id))
      (when-let [ptv-org-id (-> org :ptv-data :org-id)]
        (persist-ptv-service-revision!
          db user ptv-org-id (ptv/get-service ptv ptv-org-id (str service-id))))))

(defn save-ptv-service-audit
  "Saves auditor feedback for a PTV Service of the LIPAS org `org-id` (see
   lipas.backend.ptv.audit/save-service-audit!). Returns the stored audit
   map, or nil when the org or the service can't be resolved."
  [db ptv user {:keys [org-id] :as m}]
  (when-let [org (backend-org/get-org db org-id)]
    (when-let [current (resolve-service-revision! db ptv user org m)]
      (audit/save-service-audit! db user org current m))))

(defn get-ptv-service-audits
  "Current katselmointi of the org's services (see
   lipas.backend.ptv.audit/service-audits)."
  [db org-id]
  (audit/service-audits db org-id))

(defn send-service-audit-notification!
  "Services-section counterpart of send-audit-notification!."
  [db ptv emailer org-id]
  (send-audit-notification!* db emailer org-id :services
                             (service-audit-notification-data db ptv org-id)))

(defn backfill-ptv-service-revisions!
  "REPL helper: seed ptv_service with current PTV state for every org
   with PTV config. Skips lineages that already exist only in the sense
   that a fresh revision is appended (harmless). Not wired to migrations
   or startup."
  [db ptv]
  (doseq [org (backend-org/all-orgs db)
          :let [ptv-org-id (-> org :ptv-data :org-id)]
          :when ptv-org-id
          svc (:itemList (ptv/get-org-services ptv ptv-org-id))
          :when (some-> (:sourceId svc) (str/starts-with? "lipas-"))]
    (persist-ptv-service-revision! db nil ptv-org-id svc)))

(comment
  (generate-ptv-service-descriptions
    (user/search)
    {:sub-category-id 2200
     :city-codes [992 #_92]})

  (def s1 (core/get-sports-site (repl/db) 612967))
  (def s2 (core/get-sports-site (repl/db) 506032))
  (core/enrich s1)
  (generate-ptv-descriptions-from-data s1)
  (generate-ptv-descriptions-from-data s2)

  (generate-ptv-descriptions-from-data (assoc s1 :comment "Luistinrata ylläpidetään lumisena aikana viikottain. Pukukopit käytössä aukioloaikoina. Kentälle on saatavissa pieniä jääkiekkomaaleja pelien järjestämistä ja lajitaitojen harjoittelua varten."))
  (generate-ptv-descriptions-from-data s2)

  *1)
