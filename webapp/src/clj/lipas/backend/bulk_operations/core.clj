(ns lipas.backend.bulk-operations.core
  "CQRS-style actions for backend operations"
  (:require [clojure.java.jdbc :as jdbc]
            [lipas.data.bulk-operations :as bulk-fields]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.search :as search]
            [lipas.schema.common :as common-schema]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.utils :as utils]
            [malli.core :as m]
            [malli.error :as me]
            [taoensso.timbre :as log]))

;; The `:updates` patch a bulk edit may submit. The static fields are
;; enumerated so route-level coercion validates them; `:properties` is a loose
;; map here and validated per-key against the SELECTED sites' types in
;; `mass-update-org-sites!` (which property keys are valid depends on the
;; selection, so it can't be a static schema). Fields whose site value is
;; REQUIRED (status, admin) are NOT `:maybe` — they can be re-set but not
;; cleared; optional fields are `:maybe` so an explicit nil clears them.
(def updates-schema
  [:map
   [:email {:optional true} [:maybe #'sites-schema/email]]
   [:phone-number {:optional true} [:maybe #'sites-schema/phone-number]]
   [:www {:optional true} [:maybe #'sites-schema/www]]
   [:reservations-link {:optional true} [:maybe #'sites-schema/reservations-link]]
   [:status {:optional true} #'common-schema/status]
   [:admin {:optional true} #'sites-schema/admin]
   [:construction-year {:optional true} [:maybe #'sites-schema/construction-year]]
   [:location {:optional true}
    [:map
     [:postal-code {:optional true} [:maybe #'location-schema/postal-code]]
     [:postal-office {:optional true} [:maybe #'location-schema/postal-office]]
     [:city {:optional true}
      [:map
       [:neighborhood {:optional true} [:maybe #'location-schema/neighborhood]]]]]]
   [:properties {:optional true} [:map-of :keyword :any]]])

(def mass-update-payload
  [:map
   [:lipas-ids [:vector #'sites-schema/lipas-id]]
   [:updates updates-schema]])

(defn get-org-editable-sites
  "Bulk-update candidates for an org: the sites it may edit (owned ∪ granted),
  i.e. `search-meta.editor-org-ids` contains the org-id. Each result carries an
  `:owned?` flag (owner-org-id = org) so the UI can filter owned vs granted.

  Replaces the old per-user, hand-rolled role query: bulk update is now an org
  operation, so the candidate set is simply the org's editable sites and the
  caller is already gated by `:site/create-edit` for the org (org-editor)."
  [search org-id]
  (let [org-id (str org-id)
        query {:query {:bool {:filter [{:term {:search-meta.editor-org-ids org-id}}]}}
               :size 10000
               :_source {:includes ["lipas-id"
                                    "event-date"
                                    "status"
                                    "location.city.city-code"
                                    "location.city.city-name"
                                    "name"
                                    "type.type-code"
                                    "type.name"
                                    "admin"
                                    "owner"
                                    "email"
                                    "phone-number"
                                    "www"
                                    "reservations-link"
                                    "search-meta.owner-org-id"]}}
        search-index (get-in search [:indices :sports-site :search])
        response (search/search (:client search) search-index query)]
    (->> response
         :body
         :hits
         :hits
         (mapv (fn [hit]
                 (let [src (:_source hit)]
                   (-> src
                       (assoc :owned? (= org-id (get-in src [:search-meta :owner-org-id])))
                       (dissoc :search-meta))))))))

(def ^:private absent
  "Sentinel for get-in: distinguishes a key that is present-with-nil (a CLEAR)
  from a key that is simply absent (leave the field untouched)."
  ::absent)

(defn- dissoc-in
  "Remove the leaf at `path`. A no-op if an ancestor map is missing, so it never
  fabricates nil ancestor maps (e.g. clearing a property on a site that has no
  :properties leaves the doc untouched)."
  [m path]
  (let [parent (butlast path)
        k (last path)]
    (cond
      (empty? parent)           (dissoc m k)
      (some? (get-in m parent)) (update-in m (vec parent) dissoc k)
      :else                     m)))

(defn- static-update-pairs
  "[[doc-path value] ...] for the static (type-independent) fields PRESENT in
  `updates`. A key present with nil is kept — it is a clear, applied as a
  dissoc-in. Only registry paths are read, so nothing outside the whitelist can
  ride into the document."
  [updates]
  (for [path (vals bulk-fields/static-field-paths)
        :let [v (get-in updates path absent)]
        :when (not= v absent)]
    [path v]))

(defn- property-update-pairs
  "[[doc-path value] ...] for the submitted `:properties`, restricted to
  `allowed-prop-keys` (the properties common to every selected site's type).
  Each non-nil value is validated against its prop-type schema; a key off the
  whitelist or a value that fails its schema throws :invalid-payload (→ 400)."
  [updates allowed-prop-keys]
  (let [props    (:properties updates)
        bad-keys (remove allowed-prop-keys (keys props))]
    (when (seq bad-keys)
      (throw (ex-info "Property not shared by all selected sites' types"
                      {:type :invalid-payload
                       :invalid-property-keys (vec bad-keys)})))
    (doseq [[k v] props
            :when (some? v)]
      (let [schema (get prop-types/schemas k)]
        (when-not (and schema (m/validate schema v))
          (throw (ex-info "Invalid property value"
                          {:type :invalid-payload :property k :value v})))))
    (for [[k v] props]
      [[:properties k] v])))

(defn- apply-update-pairs
  "Apply [[doc-path value] ...] onto a stored site: a nil value clears (removes)
  the leaf, any other value REPLACES it. Nested paths (:location.*, :properties.*)
  merge key-wise — sibling keys are preserved — because each pair targets a
  single leaf via assoc-in / dissoc-in. Vectors (enum-coll properties) replace
  wholesale, matching the tool's replace semantics."
  [site pairs]
  (reduce (fn [s [path v]]
            (if (nil? v)
              (dissoc-in s path)
              (assoc-in s path v)))
          site
          pairs))

(defn mass-update-org-sites!
  "Mass-update a chosen set of fields on an org's sites. The authorized set is
  the org's editable sites (owned ∪ granted); any requested lipas-id outside it
  is rejected. The caller must already hold `:site/create-edit` for the org.

  `updates` is a partial site patch (see `updates-schema`): the type-independent
  fields the FE checked, plus a `:properties` map. Only whitelisted paths are
  applied — the static field paths from `lipas.data.bulk-operations`, and the
  property keys COMMON to every selected site's type (their intersection); any
  other property key, or a property value that fails its prop-type schema, is
  rejected (:invalid-payload → 400). A field present with nil clears it; any
  other value replaces it (vectors/enum-colls replace wholesale).

  ES (`get-org-editable-sites`) is used for CANDIDATE LISTING only. The write
  path is sourced and authorized from the DB:

  - the stored current revisions are batch-loaded from the DB and the updates
    are applied onto THEM (never onto the stale ES `_source`, which can lag
    behind the DB — e.g. a just-approved takeover not yet reindexed),
  - the org's editability (owner ∪ edit-grants) is checked against those DB
    revisions, so a grant revoked in the DB blocks the update even when ES
    still lists the site as editable,
  - the property whitelist (intersection) is computed from those DB revisions'
    type-codes, not from anything the client sends,
  - each save goes through `core/upsert-sports-site!` — the same enforcement
    point as the regular save endpoint (per-user privilege over stored AND
    submitted docs, ownership/edit-grant invariants, owner lock). The patched
    doc is submitted WITHOUT :owner-org-id/:edit-grants so the server-side
    carry-forward keeps the freshest stored values authoritative.

  All-or-nothing: any unauthorized lipas-id rejects the whole request (the
  existing handler/FE contract), and the DB writes share one transaction. ES
  is bulk-indexed AFTER the transaction commits, from the documents that were
  actually written to the DB."
  [db search _ptv user org-id lipas-ids updates]
  (log/info "Starting org mass update of sports sites"
            {:user-id (:id user) :org-id org-id :lipas-ids lipas-ids
             :fields (keys updates)})

  ;; Validate the patch against schema (belt-and-suspenders: the route coerces
  ;; too, but this guards direct callers and the property map is loose there).
  (when-not (m/validate mass-update-payload {:lipas-ids lipas-ids :updates updates})
    (throw (ex-info "Invalid payload"
                    {:type :invalid-payload
                     :error (me/humanize (m/explain mass-update-payload {:lipas-ids lipas-ids :updates updates}))})))

  (let [org-id (str org-id)
        lipas-ids (vec (distinct lipas-ids))
        ;; Authoritative current state from the DB — NOT the ES cache.
        stored-by-id (->> (db/get-sports-sites-by-lipas-ids db lipas-ids)
                          (utils/index-by :lipas-id))
        org-may-edit? (fn [site]
                        (and site
                             (or (= org-id (some-> site :owner-org-id str))
                                 (contains? (set (map str (:edit-grants site))) org-id))))
        unauthorized-ids (vec (remove (comp org-may-edit? stored-by-id) lipas-ids))]

    (log/info "Permission check results (against DB state)"
              {:requested-count (count lipas-ids)
               :unauthorized-ids unauthorized-ids})

    ;; Validate all requested sites are authorized (within the org's editable
    ;; set per the DB's current revisions)
    (when (seq unauthorized-ids)
      (throw (ex-info "Permission denied for sites"
                      {:unauthorized-lipas-ids unauthorized-ids})))

    ;; Resolve the concrete [path value] edits ONCE, from DB state:
    ;; static fields by path + properties whitelisted to the selection's common
    ;; props (and value-validated). Same edits applied to every site.
    (let [allowed-prop-keys (types/common-prop-keys
                              (map #(get-in % [:type :type-code]) (vals stored-by-id)))
          update-pairs (concat (static-update-pairs updates)
                               (property-update-pairs updates allowed-prop-keys))]

      (when (empty? update-pairs)
        (throw (ex-info "No updatable fields in payload"
                        {:type :invalid-payload})))

      ;; Save through core/upsert-sports-site! (single enforcement point) inside
      ;; one transaction; collect what was actually written for ES indexing.
      (let [saved-sites
            (jdbc/with-db-transaction [tx db]
              (mapv (fn [lipas-id]
                      (let [updated-site
                            (-> (stored-by-id lipas-id)
                                (apply-update-pairs update-pairs)
                                (assoc
                                 ;; fresh per-revision timestamp — reusing the
                                 ;; stored :event-date would collide with the
                                 ;; previous revision (FE keys history by it)
                                  :event-date (utils/timestamp)
                                 ;; audit: on whose behalf the bulk op runs.
                                 ;; Stamped by this trusted path AFTER the
                                 ;; whitelist above.
                                  :acting-org-id org-id)
                                ;; let core's carry-forward keep the stored
                                ;; (freshest) ownership fields authoritative
                                (dissoc :owner-org-id :edit-grants))]
                        (log/debug "Updating site"
                                   {:lipas-id lipas-id :paths (map first update-pairs)})
                        (core/upsert-sports-site! tx user updated-site)))
                    lipas-ids))]

      ;; Bulk index to ES outside the transaction (an ES failure must not roll
      ;; back committed DB data; worst case ES lags and is fixed by reindexing)
      ;; using the documents as they were written to the DB.
        (let [search-index (get-in search [:indices :sports-site :search])
            ;; resolved once per batch; keeps :search-meta :owner-org-name
            ;; (F15) present on re-indexed org-owned docs
              org-names (core/org-names db)
              enriched-sites (map #(core/enrich % org-names) saved-sites)
              bulk-data (search/->bulk search-index :lipas-id enriched-sites)]
          (log/debug "Bulk indexing" (count enriched-sites) "sports sites")
          (search/bulk-index-sync! (:client search) bulk-data))

      ;; NOTE: Background jobs deliberately not enqueued: current background
      ;; processes are relevant only if geoms change and bulk-ops don't touch
      ;; geoms. TODO: If/when webhooks are enabled again, they need to be
      ;; added here!

        (log/info "Mass update completed"
                  {:updated-count (count saved-sites)
                   :total-requested (count lipas-ids)})

        {:updated-sites (mapv :lipas-id saved-sites)
         :total-updated (count saved-sites)}))))
