(ns lipas.ui.ptv.events
  (:require [ajax.core :as ajax]
            [clojure.set :as set]
            [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.roles :as roles]
            [lipas.schema.sports-sites.ptv :as ptv-schema]
            [lipas.ui.ptv.review-data :as review-data]
            [lipas.ui.utils :as utils]
            [malli.core :as m]
            [malli.transform :as mt]
            [re-frame.core :as rf]))

(defn ptv-error-payload
  "Return the structured PTV error map from any of the shapes it appears in:
  the raw ajax response (wrapped under `:response`), the parsed handler
  payload (`{:ptv-error ...}`), or the per-site `:error` map stored on
  `(:ptv site)` by backend `sync-ptv!`."
  [resp]
  (or (:ptv-error (:response resp))
      (:ptv-error resp)))

(defn ptv-modified-error?
  "Check if a PTV sync failure is due to 'Modified' publishing status."
  [resp]
  (some-> (ptv-error-payload resp)
          :PublishingStatus
          first
          (str/includes? "Modified")))

(defn ptv-invalid-postal-code
  "If the PTV sync failed because the postal code isn't in Posti's registry,
  return the offending code (e.g. \"29170\"). Otherwise nil.

  PTV returns model-state errors keyed by property path, e.g.
  `:Addresses[0].StreetAddress.PostalCode` →
  [\"The code '29170' was not found!\"]."
  [resp]
  (let [body (ptv-error-payload resp)]
    (when (map? body)
      (some (fn [[k v]]
              (when (and (str/ends-with? (name k) "PostalCode")
                         (sequential? v))
                (some (fn [msg]
                        (when (string? msg)
                          (second (re-find #"'([^']+)'" msg))))
                      v)))
            body))))

(defn ptv-duplicate-name
  "If the PTV sync failed because the service-channel name already exists in
  the organization, return the offending name (e.g. \"Haapamäen
  urheilukenttä\"). Otherwise nil.

  PTV returns `:ServiceChannelNames [\"Value 'X' of language code 'fi'
  already exists within organization. The name must be unique.\"]`."
  [resp]
  (let [body (ptv-error-payload resp)]
    (when (map? body)
      (some (fn [[k v]]
              (when (and (= "ServiceChannelNames" (name k))
                         (sequential? v))
                (some (fn [msg]
                        (when (and (string? msg)
                                   (str/includes? msg "already exists within organization"))
                          (second (re-find #"'([^']+)'" msg))))
                      v)))
            body))))

(def blocker->tr-key
  "lipas.data.ptv/sync-blockers keys -> translation keys."
  {:ptv/no-org :ptv/blocker-no-org
   :ptv/missing-texts :ptv/blocker-missing-texts
   :ptv/texts-too-short :ptv/blocker-texts-too-short})

(defn blocker-messages
  "Localized messages for sync blockers. Accepts keywords or the strings they
  turn into via JSON (\"ptv/no-org\")."
  [tr blockers]
  (->> blockers
       (map keyword)
       (keep blocker->tr-key)
       (sort)
       (mapv tr)))

(def ^:private ptv-text-type->tr-key
  {"Name" :ptv/service-name
   "Summary" :ptv/summary
   "Description" :ptv/description
   "UserInstruction" :ptv/user-instruction
   "Requirement" :ptv.service/text-requirement
   "ChargeTypeAdditionalInfo" :ptv.service/text-charge-type-info
   "DeadLine" :ptv.service/text-deadline
   "ProcessingTime" :ptv.service/text-processing-time
   "ValidityTime" :ptv.service/text-validity-time
   "ServiceType" :ptv.service/text-service-type})

(defn ptv-text-type-label
  "The name of a PTV service text type (\"ChargeTypeAdditionalInfo\", or
  \"Requirement\" for a requirement), as PTV's form calls it."
  [tr ptv-type]
  (if-let [k (ptv-text-type->tr-key ptv-type)]
    (tr k)
    ptv-type))

(defn ptv-language-label
  "\"SE\" for PTV's \"sv\": a language LIPAS models by its LIPAS code, any
  other by PTV's."
  [ptv-lang]
  (str/upper-case (name (or (ptv-data/lang->locale ptv-lang) ptv-lang))))

(defn short-texts-text
  "\"Tiivistelmä (EN), Ehdot ja kriteerit (FI)\" for
  lipas.data.ptv/short-service-texts."
  [tr problems]
  (->> problems
       (map (fn [{:keys [type language]}]
              (str (ptv-text-type-label tr type) " (" (ptv-language-label language) ")")))
       (str/join ", ")))

(defn ptv-rejection-text
  "PTV's own validation messages from a sync it rejected as invalid (400),
  \"Field: message\" per message, or nil for anything else: an outage or an
  auth error is no verdict on the data, and an unparsed body (e.g. a
  gateway's HTML page) is no message. Used when LIPAS has no message of its
  own for the error, so the user sees what PTV said."
  [resp]
  (let [status (or (:ptv-status (:response resp)) (:ptv-status resp))
        payload (ptv-error-payload resp)]
    (when (and (= 400 status) (map? payload) (not (contains? payload :raw)))
      (not-empty
        (str/join "; " (for [[k v] payload
                             msg (if (sequential? v) v [v])
                             :when (and (string? msg) (not (str/blank? msg)))]
                         (str (name k) ": " msg)))))))

(defn incomplete-versions-text
  "\"SE: Tiivistelmä, Kuvaus\" for lipas.data.ptv/language-version-problems,
  `problems` keyed by PTV language code (\"sv\")."
  [tr problems]
  (->> problems
       (map (fn [[lang missing]]
              (str (ptv-language-label (name lang))
                   ": "
                   (str/join ", " (map #(tr (ptv-text-type->tr-key % :ptv/description)) missing)))))
       (str/join "; ")))

(def ^:private dialog-body-keys
  {:modified            :ptv/sync-failed-body-ptv-modified
   :invalid-postal-code :ptv/sync-failed-body-invalid-postal-code
   :duplicate-name      :ptv/sync-failed-body-name-conflict
   :changed-since-review :ptv/sync-failed-body-changed-since-review
   :not-reviewed        :ptv/sync-failed-body-not-reviewed})

(def ^:private save-body-keys
  {:modified            :ptv/saved-but-sync-failed-body-ptv-modified
   :invalid-postal-code :ptv/saved-but-sync-failed-body-invalid-postal-code
   :duplicate-name      :ptv/saved-but-sync-failed-body-name-conflict
   :changed-since-review :ptv/saved-but-sync-failed-body-changed-since-review
   :not-reviewed        :ptv/saved-but-sync-failed-body-not-reviewed})

(defn- refusal-type
  "The :type of the endpoint's refusal, or of the error sync-ptv! stored on
   the site."
  [resp]
  (some-> (or (:type (:response resp)) (:type resp)) keyword))

(defn changed-since-review?
  "Whether a sync was refused because PTV holds a change the user hasn't
   decided about: one made after their review, or, for a sync without a
   review (a quick edit, a status change), one made since the last sync."
  [resp]
  (contains? #{:ptv-changed-since-review :ptv-not-reviewed} (refusal-type resp)))

(defn- ptv-sync-error-body
  "Localized actionable body text for a PTV sync failure. `body-keys`
  selects the translation family. For a PTV rejection LIPAS has no message
  of its own for, PTV's message; nil when there's no detail at all beyond
  the title."
  [tr resp body-keys]
  (let [bad-code (ptv-invalid-postal-code resp)
        dup-name (ptv-duplicate-name resp)
        refusal (:response resp)
        ;; sync-ptv! stores them on the site's :ptv :error, the
        ;; service-location endpoint answers with them
        blockers (or (seq (:blockers resp)) (seq (:blockers refusal)))]
    (cond
      ;; nothing was written: a change made in PTV nobody has reviewed...
      (= :ptv-not-reviewed (refusal-type resp))
      (tr (:not-reviewed body-keys))

      ;; ...or one made after the user's review
      (changed-since-review? resp)
      (tr (:changed-since-review body-keys))

      (= :no-permission (some-> refusal :type keyword))
      (tr :ptv.review/needs-edit-right)

      (= :double-link (some-> refusal :type keyword))
      (tr :ptv.double-link/explanation)

      ;; LIPAS's own refusals on a service write
      (= :changed-in-ptv (some-> refusal :type keyword))
      (tr :ptv.service/changed-in-ptv)

      (= :incomplete-language-versions (some-> refusal :type keyword))
      (tr :ptv.service/incomplete-language-versions
          (incomplete-versions-text tr (:problems refusal)))

      (= :texts-too-short (some-> refusal :type keyword))
      (tr :ptv.service/texts-too-short
          ptv-data/min-text-length
          (short-texts-text tr (:problems refusal)))

      (= :source-id-in-use (some-> refusal :type keyword))
      (tr :ptv.service/other-system-read-only)

      ;; A LIPAS-side guard before PTV (no org / missing or short texts)
      blockers
      (str/join " " (blocker-messages tr blockers))

      (ptv-modified-error? resp)
      (tr (:modified body-keys))

      bad-code
      (tr (:invalid-postal-code body-keys) bad-code)

      dup-name
      (tr (:duplicate-name body-keys) dup-name)

      ;; PTV failed mid-write: it may have left a draft that blocks the
      ;; next write until it's published or discarded in PTV
      (>= (or (:ptv-status refusal) (:ptv-status resp) 0) 500)
      (tr :ptv/sync-failed-body-ptv-down)

      :else
      (some->> (ptv-rejection-text resp)
               (tr :ptv/sync-failed-body-ptv-message)))))

(defn ptv-error-body
  "Public helper for the persistent inline Alert on the sports-site PTV tab.
  Returns the actionable body text using dialog-flow phrasing (the inline
  Alert stays on the tab, not tied to the immediate save event).
  Returns nil if there's no specific detail."
  [tr resp]
  (ptv-sync-error-body tr resp dialog-body-keys))

(defn- error-notification
  "Builds a notification map for a PTV failure. `title-key` is the bold
  one-line summary; `body-keys` selects the actionable detail family.
  `severity` is `:error` (red) or `:warning` (yellow). Long actionable
  messages are sticky so the user can read them; generic failures use
  the title alone."
  [tr resp {:keys [title-key body-keys severity]}]
  (let [body (ptv-sync-error-body tr resp body-keys)]
    (if body
      {:title (tr title-key) :message body :severity severity :sticky? true}
      {:message (tr title-key) :severity severity})))

(defn ptv-sync-error-notification
  "Notification map for a PTV sync failure in a dialog-driven flow
  (Käyttöönotto wizard / Liikuntapaikat tab). Red error severity."
  [tr resp]
  (error-notification tr resp
                      {:title-key :ptv/sync-failed
                       :body-keys dialog-body-keys
                       :severity  :error}))

(defn ptv-saved-but-sync-failed-notification
  "Notification map for the sports-site save flow when the LIPAS-side save
  succeeded but the downstream PTV sync failed. Yellow warning — the user's
  data was persisted; only the downstream propagation needs follow-up."
  [tr resp]
  (error-notification tr resp
                      {:title-key :ptv/saved-but-sync-failed
                       :body-keys save-body-keys
                       :severity  :warning}))

(rf/reg-event-fx ::notify-if-sync-failed
  ;; Fires after a sports-site save whose response carries a PTV sync error.
  ;; The save endpoint always returns 200 (the DB write succeeded) even when
  ;; backend `sync-ptv!` failed — the failure is captured on `:ptv :error`.
  ;; The inline Alert on the sports-site PTV tab handles persistent state;
  ;; this event surfaces a sticky warning toast so the user gets immediate
  ;; feedback no matter which tab they end up on, framed as "saved but…"
  ;; (yellow) rather than a plain failure (red).
  (fn [{:keys [db]} [_ result]]
    (when-let [err (some-> result :ptv :error)]
      (let [tr (:translator db)]
        {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                          (ptv-saved-but-sync-failed-notification tr err)]]]}))))

;; The PTV orgs the user may act for: orgs covering a municipality they hold
;; :ptv/manage for (direct OR org-catalog role), all for admins/auditors. NOT the
;; membership-based [:user :orgs] — a city-scoped PTV manager needn't be a
;; member of the org (see lipas.backend.ptv.handler/ptv-orgs-for-user).
(rf/reg-event-fx ::fetch-ptv-orgs
  ;; Optional `{:then [event ...]}` dispatched once the orgs have landed.
  (fn [{:keys [db]} [_ {:keys [then]}]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method :post
        :uri (str (:backend-url db) "/actions/get-ptv-orgs")
        :headers {:Authorization (str "Token " token)}
        :params {}
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::fetch-ptv-orgs-success then]
        :on-failure [::fetch-ptv-orgs-failure then]}})))

(rf/reg-event-fx ::fetch-ptv-orgs-success
  (fn [{:keys [db]} [_ then resp]]
    ;; Always non-nil after a fetch, so "loaded?" checks can't refetch-loop.
    (cond-> {:db (assoc-in db [:ptv :ptv-orgs] (vec resp))}
      then (assoc :dispatch then))))

(rf/reg-event-fx ::fetch-ptv-orgs-failure
  (fn [{:keys [db]} [_ then _resp]]
    (cond-> {:db (assoc-in db [:ptv :ptv-orgs] [])}
      then (assoc :dispatch then))))

(defn -get-ptv-org-id
  [db]
  (get-in db [:ptv :selected-org :ptv-data :org-id]))

(defn- ptv-privilege-for-org?
  "Does `user` hold a PTV privilege covering `lipas-org`? Auditors hold the
  global :ptv/audit; managers hold :ptv/manage for at least one of the org's
  PTV city-codes (same check as the ::has-manage-privilege? sub)."
  [user lipas-org]
  (or (roles/check-privilege user {} :ptv/audit)
      (boolean
        (some (fn [city-code]
                (roles/check-privilege user {:city-code city-code} :ptv/manage))
              (get-in lipas-org [:ptv-data :city-codes])))))

(defn- auto-selectable-org
  "The org to pre-select when the user has no real choice to make: exactly one
  accessible org, PTV-configured, and covered by their PTV privileges. nil
  otherwise — admins and auditors are served every org, so they still pick."
  [db]
  (let [orgs (get-in db [:ptv :ptv-orgs])]
    (when (= 1 (count orgs))
      (let [org (first orgs)]
        (when (and (get-in org [:ptv-data :org-id])
                   (ptv-privilege-for-org? (get-in db [:user :login]) org))
          org)))))

;; Convenience for single-org municipalities: skip the one-item dropdown.
;; No-op once an org is selected, so it is safe to fire on every dialog open.
(rf/reg-event-fx ::maybe-auto-select-org
  (fn [{:keys [db]} _]
    (when-not (:selected-org (:ptv db))
      (when-let [org (auto-selectable-org db)]
        {:fx [[:dispatch [::select-org org]]]}))))

(rf/reg-event-fx ::open-dialog
  (fn [{:keys [db]} [_ _]]
    (let [orgs-loaded? (seq (get-in db [:ptv :ptv-orgs]))]
      {:db (assoc-in db [:ptv :dialog :open?] true)
       :fx (cond-> []
             ;; Fetch the PTV orgs the user may act for if not already loaded.
             ;; Auto-select runs as the continuation, once the orgs have landed.
             (not orgs-loaded?)
             (conj [:dispatch [::fetch-ptv-orgs
                               {:then [::maybe-auto-select-org]}]])

             ;; Select previously selected org if exists
             (:selected-org (:ptv db))
             (conj [:dispatch [::select-org (:selected-org (:ptv db))]])

             ;; ...or the only one on offer, when nothing is selected yet
             orgs-loaded?
             (conj [:dispatch [::maybe-auto-select-org]]))})))

(rf/reg-event-db ::close-dialog
  (fn [db [_ _]]
    (-> db
        (assoc-in [:ptv :dialog :open?] false)
        (assoc-in [:ptv :selected-tab] "wizard")
        (update :ptv dissoc :candidates-search :selected-step))))

;; assistant dialog-context moved to lipas.ui.ptv.context (base module)

(rf/reg-event-fx ::select-org
  (fn [{:keys [db]} [_ lipas-org]]
    {:db (-> db
             (assoc-in [:ptv :selected-org] lipas-org)
             (assoc-in [:ptv :audit :notification-sent?] false)
             (assoc-in [:ptv :audit :service-notification-sent?] false)
             ;; org data is refetched, discarding unsaved audit edits
             ;; and any selection from the previous org
             (update-in [:ptv :audit] dissoc :site-draft :service-draft)
             (update :ptv dissoc :selected-audit-site :selected-audit-service))
     :fx [[:dispatch [::fetch-ptv-org-data lipas-org]]]}))

(rf/reg-event-fx ::set-candidates-search
  (fn [{:keys [db]} [_ search]]
    {:db (assoc-in db [:ptv :candidates-search] search)}))

(rf/reg-event-fx ::set-step
  (fn [{:keys [db]} [_ v]]
    (let [prev-step (or (:selected-step (:ptv db)) 0)]
      {:db (assoc-in db [:ptv :selected-step] v)
       :fx [(when (= 0 prev-step)
              [:dispatch [::fetch-integration-candidates (:selected-org (:ptv db))]])]})))

(rf/reg-event-db ::select-tab
  (fn [db [_ v]]
    (assoc-in db [:ptv :selected-tab] v)))

(rf/reg-event-fx ::reset-wizard
  ;; Reset all ephemeral wizard state back to defaults.
  (fn [{:keys [db]} _]
    (let [org-id (-get-ptv-org-id db)]
      {:db (-> db
               (update :ptv dissoc
                       :selected-step
                       :candidates-search
                       :service-locations-creation)
               (assoc-in [:ptv :batch-descriptions-generation]
                         {:sports-sites-filter "sync-enabled"
                          :halt? false})
               ;; Clear local service candidate edits
               (update-in [:ptv :org org-id :data] dissoc
                          :service-candidates
                          :manual-services))
       :fx [;; Re-fetch fresh data from PTV
            [:dispatch [::fetch-ptv-org-data (:selected-org (:ptv db))]]]})))

(rf/reg-event-fx ::fetch-integration-candidates
  (fn [{:keys [db]} [_ lipas-org]]
    (when lipas-org
      (let [token (-> db :user :login :token)
            ;; Always fetch ALL candidates - filtering is done in subscriptions
            ptv-config (:ptv-data lipas-org)
            ptv-org-id (:org-id ptv-config)
            params (select-keys ptv-config [:city-codes :owners])]
        {:db (assoc-in db [:ptv :loading-from-lipas :candidates] true)
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/get-ptv-integration-candidates")
                :params params
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::fetch-integration-candidates-success ptv-org-id]
                :on-failure [::fetch-integration-candidates-failure]}]]}))))

(defn- persisted-sync?
  "Snapshot of whether the SAVED site has PTV sync on. Kept next to the live
  :ptv draft (whose :sync-enabled flips on every toggle) so ::persist-ptv-meta
  can tell whether turning sync off changes anything on the backend. Only the
  load and the save/sync/archive responses may set it."
  [ptv]
  (boolean (:sync-enabled ptv)))

(defn- saved-snapshot
  "Snapshot of what the SAVED site has in the :ptv keys a sync sends, kept as
  :ptv-saved next to the live :ptv draft so the site's sync status can tell
  an edit that isn't saved yet (ptv-data/unsaved-ptv-edits?). Only the load
  and the save/sync/archive responses may set it."
  [ptv]
  (select-keys ptv ptv-data/synced-content-keys))

(rf/reg-event-fx ::fetch-integration-candidates-success
  (fn [{:keys [db]} [_ ptv-org-id resp]]
    (let [new-sites (utils/index-by :lipas-id resp)
          existing-sites (get-in db [:ptv :org ptv-org-id :data :sports-sites])
                         ;; Preserve locally-modified :ptv data (e.g. AI-generated descriptions)
          merged-sites (reduce-kv
                         (fn [m lipas-id new-site]
                           (let [existing (get existing-sites lipas-id)
                                 ;; Snapshot the audited fields as they are in the
                                 ;; saved document (pre-merge), so audit whose-move
                                 ;; states can be derived from persisted content
                                 ;; instead of the live edit draft.
                                 persisted (select-keys (:ptv new-site)
                                                        ptv-data/site-audited-field-keys)]
                             (assoc m lipas-id
                                    (-> new-site
                                        (assoc :ptv-persisted persisted
                                               :ptv-persisted-sync? (persisted-sync? (:ptv new-site))
                                               ;; what the sync review compares PTV with
                                               :review-saved (ptv-review/->review-base new-site)
                                               :ptv-saved (saved-snapshot (:ptv new-site)))
                                        (cond-> (:ptv existing) (update :ptv #(merge (:ptv existing) %)))
                                        ;; a search index not yet reindexed after the
                                        ;; audit move still serves the legacy in-document
                                        ;; copy; the cache never holds one (see below)
                                        (update :ptv dissoc :audit)))))
                         {}
                         new-sites)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :candidates] false)
               (assoc-in [:ptv :org ptv-org-id :data :sports-sites] merged-sites))
       ;; audits are stored and served apart from the sites
       :fx [[:dispatch [::fetch-ptv-site-audits ptv-org-id (vec (keys merged-sites))]]
            ;; ...and so is what each synced site's last sync sent, the base
            ;; its sync review tells PTV's changes from Lipas's by
            [:dispatch [::fetch-ptv-sync-bases ptv-org-id
                        (vec (keep (fn [[lipas-id site]]
                                     (when (-> site :ptv :last-sync) lipas-id))
                                   merged-sites))]]]})))

;; What each synced site's last sync sent (lipas.data.ptv/->review-base),
;; cached at [:ptv :org <ptv-org-id> :data :sync-bases] keyed by lipas-id and
;; joined into the sites by the ::sports-sites sub for their sync review.

(rf/reg-event-fx ::fetch-ptv-sync-bases
  (fn [{:keys [db]} [_ ptv-org-id lipas-ids]]
    (when (seq lipas-ids)
      (let [token (-> db :user :login :token)]
        {:fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/fetch-ptv-sync-bases")
                :params {:lipas-ids lipas-ids}
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::fetch-ptv-sync-bases-success ptv-org-id]
                :on-failure [::fetch-ptv-sync-bases-failure]}]]}))))

(rf/reg-event-db ::fetch-ptv-sync-bases-success
  (fn [db [_ ptv-org-id resp]]
    (update-in db [:ptv :org ptv-org-id :data :sync-bases] merge resp)))

(rf/reg-event-db ::fetch-ptv-sync-bases-failure
  ;; Without bases the review can't tell who changed what and asks about
  ;; every difference, which is safe.
  (fn [db [_ resp]]
    (assoc-in db [:ptv :errors :sync-bases] resp)))

;; Site audits live in ptv_site_audit and are served apart from the sites
;; (/actions/fetch-ptv-site-audits). They are cached apart too, at
;; [:ptv :org <ptv-org-id> :data :site-audits] keyed by lipas-id, and the
;; subs join them into the sites (see ::sites-with-audit in the subs ns).
;; Service audits are kept the same way under :service-audits, keyed by
;; (str service-id) like the :services cache.

(defn- -merge-audits
  "`cached` audits with `fetched` ones merged in. Audits are append-only,
   so the newer :timestamp always wins: a response requested before a save
   and arriving after it can't overwrite what the save stored, and a fetch
   covering a subset of items can't drop the rest."
  [cached fetched]
  (merge-with (fn [old new]
                (if (pos? (compare (:timestamp new) (:timestamp old))) new old))
              cached
              fetched))

(rf/reg-event-fx ::fetch-ptv-site-audits
  (fn [{:keys [db]} [_ ptv-org-id lipas-ids]]
    (when (seq lipas-ids)
      (let [token (-> db :user :login :token)]
        {:fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/fetch-ptv-site-audits")
                :params {:lipas-ids lipas-ids}
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::fetch-ptv-site-audits-success ptv-org-id]
                :on-failure [::fetch-ptv-site-audits-failure]}]]}))))

(rf/reg-event-db ::fetch-ptv-site-audits-success
  (fn [db [_ ptv-org-id resp]]
    (update-in db [:ptv :org ptv-org-id :data :site-audits]
               -merge-audits (into {} (map (juxt :lipas-id :audit)) resp))))

(rf/reg-event-fx ::fetch-ptv-site-audits-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (assoc-in db [:ptv :errors :site-audits] resp)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-integration-candidates-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :candidates] false)
               (assoc-in [:ptv :errors :candidates] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-ptv-org-data
  (fn [{:keys [_db]} [_ lipas-org]]
    (when lipas-org
      {:fx [[:dispatch [::fetch-integration-candidates lipas-org]]
            [:dispatch [::fetch-ptv-org lipas-org]]
            [:dispatch [::fetch-ptv-services lipas-org]]
            [:dispatch [::fetch-ptv-service-channels lipas-org]]
            [:dispatch [::fetch-ptv-service-collections lipas-org]]
            [:dispatch [::fetch-ptv-service-audits lipas-org]]
            [:dispatch [::fetch-awaiting-archive (get-in lipas-org [:ptv-data :org-id])]]
            [:dispatch [::fetch-ptv-service-bases (get-in lipas-org [:ptv-data :org-id])]]]})))

(rf/reg-event-fx ::fetch-ptv-org
  (fn [{:keys [db]} [_ lipas-org]]
    (let [token (-> db :user :login :token)
          ptv-org-id (get-in lipas-org [:ptv-data :org-id])]
      (when ptv-org-id
        {:db (assoc-in db [:ptv :loading-from-ptv :org] true)
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/fetch-ptv-org")
                :params {:org-id ptv-org-id}
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::fetch-ptv-org-success ptv-org-id]
                :on-failure [::fetch-ptv-org-failure]}]]}))))

(rf/reg-event-fx ::fetch-ptv-org-success
  (fn [{:keys [db]} [_ ptv-org-id resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-ptv :org] false)
             (assoc-in [:ptv :org ptv-org-id :data :org ptv-org-id] resp))}))

(rf/reg-event-fx ::fetch-ptv-org-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :org] false)
               (assoc-in [:ptv :errors :org] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-ptv-services
  (fn [{:keys [db]} [_ lipas-org]]
    (let [token (-> db :user :login :token)
          ptv-org-id (get-in lipas-org [:ptv-data :org-id])]
      (when ptv-org-id
        {:db (assoc-in db [:ptv :loading-from-ptv :services] true)
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/fetch-ptv-services")
                :params {:org-id ptv-org-id}
                :format (ajax/transit-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success [::fetch-ptv-services-success ptv-org-id]
                :on-failure [::fetch-ptv-services-failure]}]]}))))

(rf/reg-event-fx ::fetch-ptv-services-success
  (fn [{:keys [db]} [_ ptv-org-id resp]]
    (let [services (->> resp :itemList (utils/index-by :id))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :services] false)
               (assoc-in [:ptv :org ptv-org-id :data :services] services))
       :fx [[:dispatch [::assign-services-to-sports-sites]]]})))

(rf/reg-event-fx ::fetch-ptv-services-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :services] false)
               (assoc-in [:ptv :errors :services] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-ptv-service-channels
  (fn [{:keys [db]} [_ lipas-org]]
    (let [ptv-org-id (get-in lipas-org [:ptv-data :org-id])]
      (when ptv-org-id
        (let [token (-> db :user :login :token)]
          {:db (assoc-in db [:ptv :loading-from-ptv :service-channels] true)
           :fx [[:http-xhrio
                 {:method :post
                  :headers {:Authorization (str "Token " token)}
                  :uri (str (:backend-url db) "/actions/fetch-ptv-service-channels")
                  :params {:org-id ptv-org-id}
                  :format (ajax/transit-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::fetch-ptv-service-channels-success ptv-org-id]
                  :on-failure [::fetch-ptv-service-channels-failure]}]]})))))

(rf/reg-event-fx ::fetch-ptv-service-channels-success
  (fn [{:keys [db]} [_ org-id resp]]
    (let [service-channels (->> resp :itemList (utils/index-by :id))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-channels] false)
               (assoc-in [:ptv :org org-id :data :service-channels] service-channels))})))

(rf/reg-event-fx ::fetch-ptv-service-channels-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-channels] false)
               (assoc-in [:ptv :errors :service-channels] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-ptv-service-collections
  (fn [{:keys [db]} [_ lipas-org]]
    (let [ptv-org-id (get-in lipas-org [:ptv-data :org-id])]
      (when ptv-org-id
        (let [token (-> db :user :login :token)]
          {:db (assoc-in db [:ptv :loading-from-ptv :service-collections] true)
           :fx [[:http-xhrio
                 {:method :post
                  :headers {:Authorization (str "Token " token)}
                  :uri (str (:backend-url db) "/actions/fetch-ptv-service-collections")
                  :params {:org-id ptv-org-id}
                  :format (ajax/transit-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::fetch-ptv-service-collections-success ptv-org-id]
                  :on-failure [::fetch-ptv-service-collections-failure]}]]})))))

(rf/reg-event-fx ::fetch-ptv-service-collections-success
  (fn [{:keys [db]} [_ ptv-org-id resp]]
    (let [service-collections (->> resp :itemList (utils/index-by :id))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-collections] false)
               (assoc-in [:ptv :org ptv-org-id :data :service-collections] service-collections))})))

(rf/reg-event-fx ::fetch-ptv-service-collections-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-collections] false)
               (assoc-in [:ptv :errors :service-collections] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

;;; Services views and manipulation ;;;

(rf/reg-event-db ::toggle-services-filter
  ;; "sports" (the default, see ::subs/services-filter) <-> "all"
  (fn [db _]
    (let [current-val (get-in db [:ptv :services-filter] "sports")]
      (assoc-in db [:ptv :services-filter] (if (= "sports" current-val) "all" "sports")))))

(rf/reg-event-db ::select-service-details-tab
  (fn [db [_ v]]
    (assoc-in db [:ptv :service-details-tab] v)))

;;; Service locations views and manipulation ;;;

(rf/reg-event-db ::set-sync-enabled
  (fn [db [_ org-id lipas-id v]]
    (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :sync-enabled] v)))

(rf/reg-event-fx ::persist-ptv-meta
  ;; Persist a single site's :ptv meta (sync-enabled, texts, links, ...) via
  ;; save-ptv-meta without syncing to PTV. Reads the (already-updated) app-db
  ;; site, so dispatch any flag change before this. save-ptv-meta keys off the
  ;; top-level :lipas-id and reads the remaining meta from the flattened map.
  ;;
  ;; Only called to persist sync OFF, so it's skipped when the saved site
  ;; doesn't have sync on — no :ptv saved at all, or saved with sync already
  ;; off. Off is the default; saving it would change nothing (and a site that
  ;; never had PTV data would send an entry without :org-id or texts, which
  ;; the backend rightly rejects).
  (fn [{:keys [db]} [_ lipas-id]]
    (let [org-id (-get-ptv-org-id db)
          site (get-in db [:ptv :org org-id :data :sports-sites lipas-id])]
      (when (:ptv-persisted-sync? site)
        {:fx [[:dispatch [::save-ptv-meta [(assoc (:ptv site) :lipas-id lipas-id)]]]]}))))

(rf/reg-event-fx ::toggle-sync-enabled
  ;; Turning sync OFF on a site that is currently published in PTV prompts the
  ;; user whether to also archive the PTV service-location (Kyllä = archive,
  ;; Ei = keep it frozen in PTV, Peruuta = leave sync on). Other toggles just
  ;; flip the flag.
  ;;
  ;; With :persist? true (the Liikuntapaikat tab, which has no batch "save all"
  ;; step) turning sync OFF is written to the backend via save-ptv-meta so the
  ;; switch sticks across reloads: "Ei" and a non-prompt off persist directly,
  ;; "Kyllä"/archive persists on its own, and "Peruuta" persists nothing (and
  ;; doesn't flip the flag). Turning sync ON is NOT persisted here — that's the
  ;; "Synkronoi nyt" button's job. Callers without :persist? (the wizard, which
  ;; persists later via its batch export) keep the app-db-only behaviour.
  (fn [{:keys [db]} [_ {:keys [lipas-id service-channel-publishing-status]} v & {:keys [persist?]}]]
    (let [org-id (-get-ptv-org-id db)
          tr (:translator db)
          published? (= "Published" service-channel-publishing-status)]
      (if (and (not v) published?)
        {:fx [[:dispatch [:lipas.ui.events/confirm
                          (tr :ptv.actions/archive-on-sync-off-confirm)
                          (fn []
                            (rf/dispatch [::set-sync-enabled org-id lipas-id false])
                            (rf/dispatch [::archive-ptv-service-location lipas-id]))
                          (fn []
                            (rf/dispatch [::set-sync-enabled org-id lipas-id false])
                            (when persist? (rf/dispatch [::persist-ptv-meta lipas-id])))]]]}
        {:fx (cond-> [[:dispatch [::set-sync-enabled org-id lipas-id v]]]
               ;; Persist OFF only; ON is persisted by an actual sync.
               (and persist? (not v)) (conj [:dispatch [::persist-ptv-meta lipas-id]]))}))))

(rf/reg-event-fx ::toggle-site-sync-enabled
  ;; Site-tab specific: toggle :ptv :sync-enabled on the site's edit-buffer.
  ;; Enabling writes the flag AND the resolved org-id in one edit, or nothing:
  ;; sync-enabled without an org-id is exactly the state the backend gate
  ;; (core/check-ptv-save!) rejects, and the one that left sites silently
  ;; unsynced before it existed.
  (fn [{:keys [db]} [_ lipas-id enabled?]]
    (let [edit-site (get-in db [:sports-sites lipas-id :editing])
          latest-id (get-in db [:sports-sites lipas-id :latest])
          latest-site (get-in db [:sports-sites lipas-id :history latest-id])
          tr (:translator db)
          resolved-org-id (ptv-data/resolve-org-id (or edit-site latest-site)
                                                   (get-in db [:ptv :ptv-orgs]))
          ;; Published in PTV right now? Archiving only makes sense then.
          published? (ptv-data/is-sent-to-ptv? (or edit-site latest-site))
          set-flag (fn [v] [:dispatch [:lipas.ui.sports-sites.events/edit-field
                                       lipas-id [:ptv :sync-enabled] v]])
          set-archive (fn [v] [:dispatch [:lipas.ui.sports-sites.events/edit-field
                                          lipas-id [:ptv :delete-existing] v]])]
      (cond
        (and enabled? resolved-org-id)
        {:fx [[:dispatch [:lipas.ui.sports-sites.events/edit-fields
                          lipas-id {[:ptv :sync-enabled] true
                                    [:ptv :org-id] resolved-org-id}]]]}

        ;; The switch is disabled in this case; this is the defence.
        enabled?
        {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                          {:message (tr :ptv/no-org-for-site)
                           :success? false}]]]}

        ;; Disabling a published site: ask whether to archive in PTV. The archive
        ;; itself is deferred to the next sports-site save (via :delete-existing).
        published?
        {:fx [[:dispatch [:lipas.ui.events/confirm
                          (tr :ptv.actions/archive-on-sync-off-confirm)
                          (fn []
                            (rf/dispatch (set-flag false))
                            (rf/dispatch (set-archive true)))
                          (fn []
                            (rf/dispatch (set-flag false)))]]]}

        ;; Disabling a not-yet-published site: just turn it off.
        :else
        {:fx [(set-flag false)]}))))

(rf/reg-event-db ::select-services
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-ids] v))))

(rf/reg-event-db ::select-service-channels
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (-get-ptv-org-id db)
          ;; Clearing the single-select autocomplete fires on-change with [nil];
          ;; normalize to [] so "no link" is represented consistently — clean
          ;; sync payload, and link-removed? (the sync status) actually fires.
          v (vec (remove str/blank? v))
          new-id (first v)
          sites (get-in db [:ptv :org org-id :data :sports-sites])
          ;; Hard-block double-linking: refuse to bind this site to a
          ;; service-location another loaded site already holds. Clearing the
          ;; link (empty v) is always allowed — it's the remediation path.
          conflict (when new-id
                     (some (fn [[other-lipas-id site]]
                             (when (and (not= other-lipas-id lipas-id)
                                        (contains? (set (-> site :ptv :service-channel-ids)) new-id))
                               {:lipas-id other-lipas-id
                                :name (:name site)
                                :service-channel-id new-id}))
                           sites))]
      (if conflict
        (assoc-in db [:ptv :double-link-block lipas-id] conflict)
        (let [site-path [:ptv :org org-id :data :sports-sites lipas-id :ptv]
              channel (when new-id
                        (get-in db [:ptv :org org-id :data :service-channels new-id]))]
          (-> db
              (update-in [:ptv :double-link-block] dissoc lipas-id)
              (assoc-in (conj site-path :service-channel-ids) v)
              ;; A location made in PTV keeps the services it's connected
              ;; to: shown in the site's list before the sync, which keeps
              ;; them (ptv-data/link-service-ids)
              (cond->
                channel (update-in site-path
                                   #(assoc % :service-ids (ptv-data/link-service-ids % channel))))))))))

(rf/reg-event-db ::select-service-integration
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-integration] v))))

(rf/reg-event-db ::select-service-channel-integration
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-channel-integration] v))))

(rf/reg-event-db ::select-descriptions-integration
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :descriptions-integration] v))))

(rf/reg-event-db ::select-service-integration-default
  (fn [db [_ v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :default-settings :service-integration] v))))

(rf/reg-event-db ::select-service-channel-integration-default
  (fn [db [_ v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :default-settings :service-channel-integration] v))))

(rf/reg-event-db ::select-descriptions-integration-default
  (fn [db [_ v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :default-settings :descriptions-integration] v))))

(rf/reg-event-db ::select-integration-interval
  (fn [db [_ v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :default-settings :integration-interval] v))))

(defn- set-site-text
  "Sets a site's PTV text in `locale`; a cleared field (nil or blank, see
  text-fields/text-field) removes that language's text."
  [db lipas-id field locale v]
  (let [org-id (-get-ptv-org-id db)
        path [:ptv :org org-id :data :sports-sites lipas-id :ptv field]]
    (if (str/blank? v)
      (update-in db path dissoc locale)
      (assoc-in db (conj path locale) v))))

(rf/reg-event-db ::set-summary
  (fn [db [_ {:keys [lipas-id]} locale v]]
    (set-site-text db lipas-id :summary locale v)))

(rf/reg-event-db ::set-description
  (fn [db [_ {:keys [lipas-id]} locale v]]
    (set-site-text db lipas-id :description locale v)))

(defn- merge-translation
  "Merges a translate response into `texts` language by language: the
   response carries only the source and target languages, so a whole-field
   merge would drop the others (e.g. Swedish when translating to English)."
  [texts resp]
  (merge-with merge texts (dissoc resp :shortened)))

(defn- translate-notification
  "Success, naming the texts the backend had to shorten to fit PTV's
   character limits (its :shortened), which may have lost something."
  [tr {:keys [shortened]}]
  {:success? true
   :message (if (seq shortened)
              (tr :notifications/translate-success-shortened
                  (str/join ", " (for [{:keys [field lang]} shortened]
                                   (str (str/lower-case (tr (keyword "ptv" (name field))))
                                        " (" (str/upper-case lang) ")"))))
              (tr :notifications/translate-success))})

(rf/reg-event-fx ::translate-site-descriptions
  (fn [{:keys [db]} [_ lipas-id from-lang to-langs]]
    (let [token (-> db :user :login :token)
          org-id (-get-ptv-org-id db)
          ptv-data (get-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv])
          summary (get-in ptv-data [:summary from-lang])
          description (get-in ptv-data [:description from-lang])]
      (when (and summary description)
        {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] true)
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/translate-to-other-langs")
                :params (cond-> {:from (name from-lang)
                                 :to (set (map name to-langs))
                                 :summary summary
                                 :description description}
                          (get-in ptv-data [:user-instruction from-lang])
                          (assoc :user-instruction (get-in ptv-data [:user-instruction from-lang])))
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::translate-site-descriptions-success lipas-id]
                :on-failure [::translate-site-descriptions-failure]}]]}))))

(rf/reg-event-fx ::translate-site-descriptions-success
  (fn [{:keys [db]} [_ lipas-id resp]]
    (let [tr (:translator db)
          org-id (-get-ptv-org-id db)
          resp (update resp :summary
                       (fn [m] (reduce-kv (fn [acc k v]
                                            (assoc acc k (when v (subs v 0 (min ptv-data/max-summary-length (count v))))))
                                          {} m)))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (update-in [:ptv :org org-id :data :sports-sites lipas-id :ptv] merge-translation resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification
                        (translate-notification tr resp)]]]})))

(rf/reg-event-fx ::translate-site-descriptions-failure
  (fn [{:keys [db]} [_ _resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] false)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

;;; Service location descriptions generation ;;;

(defn- pick-reference-for-site
  "Picks a Finnish style reference from the peer sites already loaded in app-db
   for the site's org. Returns {:summary :description} or nil."
  [db lipas-id]
  (let [edit-data (-> db :sports-sites (get lipas-id) :editing)
        latest-id (-> db :sports-sites (get lipas-id) :latest)
        site (or edit-data (get-in db [:sports-sites lipas-id :history latest-id]))
        org-id (-> site :ptv :org-id)
        types (get-in db [:sports-sites :types])
        type-code (-> site :type :type-code)
        sub-category-id (get-in types [type-code :sub-category])]
    (when org-id
      (let [peers (->> (get-in db [:ptv :org org-id :data :sports-sites])
                       vals
                       (map (fn [s]
                              (let [tc (-> s :type :type-code)]
                                {:lipas-id (:lipas-id s)
                                 :type-code tc
                                 :sub-category-id (get-in types [tc :sub-category])
                                 :summary (-> s :ptv :summary)
                                 :description (-> s :ptv :description)
                                 :last-sync (-> s :ptv :last-sync)}))))]
        (ptv-data/pick-style-reference peers
                                       {:type-code type-code
                                        :sub-category-id sub-category-id
                                        :exclude-lipas-id lipas-id})))))

(rf/reg-event-fx ::generate-descriptions
  (fn [{:keys [db]} [_ lipas-id success-fx failure-fx]]
    (let [token (-> db :user :login :token)
          reference (pick-reference-for-site db lipas-id)]
      {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] true)
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/generate-ptv-descriptions")

              :params (cond-> {:lipas-id lipas-id}
                        reference (assoc :reference reference))
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::generate-descriptions-success lipas-id success-fx]
              :on-failure [::generate-descriptions-failure lipas-id failure-fx]}]]})))

(rf/reg-event-fx ::generate-descriptions-success
  (fn [{:keys [db]} [_ lipas-id extra-fx resp]]
    (let [org-id (-get-ptv-org-id db)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (update-in [:ptv :org org-id :data :sports-sites lipas-id :ptv] merge resp))
       :fx extra-fx})))

(rf/reg-event-fx ::generate-descriptions-failure
  (fn [{:keys [db]} [_ _lipas-id extra-fx resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (assoc-in [:ptv :errors :descriptions] resp))
       :fx (or extra-fx
               [[:dispatch [:lipas.ui.events/set-active-notification notification]]])})))

(rf/reg-event-fx ::generate-descriptions-from-data
  (fn [{:keys [db]} [_ lipas-id]]
    (let [token (-> db :user :login :token)
          edit-data (-> db :sports-sites (get lipas-id) :editing)
          reference (pick-reference-for-site db lipas-id)]
      {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] true)
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/generate-ptv-descriptions-from-data")
              ;; :ptv data isn't used as AI input, and the data might not we valid spec yet?
              :params (cond-> (utils/make-saveable (dissoc edit-data :ptv))
                        reference (assoc :reference reference))
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::generate-descriptions-from-data-success lipas-id]
              :on-failure [::generate-descriptions-from-data-failure lipas-id]}]]})))

(rf/reg-event-fx ::generate-descriptions-from-data-success
  (fn [{:keys [db]} [_ lipas-id resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-lipas :descriptions] false)
             (update-in [:sports-sites lipas-id :editing :ptv] merge resp))}))

(rf/reg-event-fx ::generate-descriptions-from-data-failure
  (fn [{:keys [db]} [_]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::translate-to-other-langs
  (fn [{:keys [db]} [_ lipas-id y]]
    (let [token (-> db :user :login :token)
          edit-data (-> db :sports-sites (get lipas-id) :editing)
          ptv (-> edit-data :ptv)
          data (assoc y
                      :summary (-> (:summary ptv) (get (keyword (:from y))))
                      :description (-> (:description ptv) (get (keyword (:from y)))))]
      {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] true)
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/translate-to-other-langs")
              :params data
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::translate-to-other-langs-success lipas-id]
              :on-failure [::translate-to-other-langs-failure lipas-id]}]]})))

(rf/reg-event-fx ::translate-to-other-langs-success
  (fn [{:keys [db]} [_ lipas-id resp]]
    (let [tr (:translator db)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (update-in [:sports-sites lipas-id :editing :ptv] merge-translation resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification
                        (translate-notification tr resp)]]]})))

(rf/reg-event-fx ::translate-to-other-langs-failure
  (fn [{:keys [db]} [_]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-db ::toggle-sync-all
  (fn [db [_ enabled]]
    (let [org-id (-get-ptv-org-id db)]
      (update-in db
                 [:ptv :org org-id :data :sports-sites]
                 (fn [ms]
                   (reduce (fn [res lipas-id]
                             (assoc-in res [lipas-id :ptv :sync-enabled] enabled))
                           ms
                           (keys ms)))))))

;; Batch description generation ;;

(def ^:private batch-size
  "Max sites per Gemini call. Kept small because the model tends to bail
   out mid-response on larger batches. Style-reference chaining keeps
   output consistent across multiple batches of the same type."
  10)

(def ^:private max-batch-attempts
  "How many times a (type-code + set of missing sites) may be retried.
   Each retry happens as a follow-up batch after the initial pass."
  2)

(defn- partition-batches
  "Group sports sites by :type-code, then split each group into batches
   of `batch-size`. Order: same-type batches stay adjacent so the style
   reference from batch 1 flows into batches 2..N of that type."
  [sports-sites]
  (->> sports-sites
       (group-by :type-code)
       (sort-by key)
       (mapcat (fn [[type-code sites]]
                 (let [ids (mapv :lipas-id sites)]
                   (->> (partition-all batch-size ids)
                        (map-indexed (fn [idx chunk]
                                       {:type-code    type-code
                                        :lipas-ids    (vec chunk)
                                        :batch-idx    idx
                                        :attempt      0}))))))
       vec))

(rf/reg-event-fx ::generate-all-descriptions*
  (fn [{:keys [db]} [_ org-id queue]]
    (let [halt? (get-in db [:ptv :batch-descriptions-generation :halt?] false)]
      (cond
        (or halt? (empty? queue))
        {:db (assoc-in db [:ptv :batch-descriptions-generation :in-progress?] false)}

        :else
        (let [{:keys [type-code lipas-ids attempt]} (first queue)
              token      (-> db :user :login :token)
              references (get-in db [:ptv :batch-descriptions-generation :references] {})
              reference  (get references type-code)]
          {:db (assoc-in db [:ptv :batch-descriptions-generation :in-progress?] true)
           :fx [[:http-xhrio
                 {:method :post
                  :headers {:Authorization (str "Token " token)}
                  :uri (str (:backend-url db) "/actions/generate-ptv-descriptions-batch")
                  :params (cond-> {:lipas-ids (vec lipas-ids)}
                            reference (assoc :reference reference))
                  :format (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :timeout 240000
                  :on-success [::generate-descriptions-batch-success
                               org-id type-code attempt lipas-ids queue]
                  :on-failure [::generate-descriptions-batch-failure]}]]})))))

(rf/reg-event-fx ::generate-descriptions-batch-success
  (fn [{:keys [db]} [_ org-id type-code attempt requested-ids queue resp]]
    (let [sites          (:sites resp)
          returned-ids   (set (map :lipas-id sites))
          missing-ids    (set/difference (set requested-ids) returned-ids)
          db-with-sites  (reduce (fn [acc {:keys [lipas-id] :as s}]
                                   (update-in acc
                                              [:ptv :org org-id :data :sports-sites lipas-id :ptv]
                                              merge
                                              (dissoc s :lipas-id)))
                                 db
                                 sites)
          processed      (get-in db-with-sites [:ptv :batch-descriptions-generation :processed-lipas-ids] #{})
          failed         (get-in db-with-sites [:ptv :batch-descriptions-generation :failed-lipas-ids] #{})
          references     (get-in db-with-sites [:ptv :batch-descriptions-generation :references] {})
          first-site     (first sites)
          new-references (cond-> references
                           (and first-site (not (contains? references type-code)))
                           (assoc type-code {:summary     (get-in first-site [:summary :fi])
                                             :description (get-in first-site [:description :fi])}))
          retry-batch    (when (and (seq missing-ids) (< attempt max-batch-attempts))
                           {:type-code type-code
                            :lipas-ids (vec missing-ids)
                            :batch-idx :retry
                            :attempt   (inc attempt)})
          next-queue     (cond-> (vec (rest queue))
                           retry-batch (conj retry-batch))]
      {:db (-> db-with-sites
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (assoc-in [:ptv :batch-descriptions-generation :processed-lipas-ids]
                         (set/union processed returned-ids))
               (assoc-in [:ptv :batch-descriptions-generation :failed-lipas-ids]
                         (if retry-batch
                           failed
                           (set/union failed missing-ids)))
               (assoc-in [:ptv :batch-descriptions-generation :references]
                         new-references))
       :fx [[:dispatch [::generate-all-descriptions* org-id next-queue]]]})))

(rf/reg-event-fx ::generate-descriptions-batch-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (assoc-in [:ptv :errors :descriptions] resp)
               (assoc-in [:ptv :batch-descriptions-generation :halt?] true)
               (assoc-in [:ptv :batch-descriptions-generation :in-progress?] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::generate-all-descriptions
  (fn [{:keys [db]} [_ sports-sites]]
    (let [org-id    (-get-ptv-org-id db)
          lipas-ids (map :lipas-id sports-sites)
          queue     (partition-batches sports-sites)]
      {:db (update-in db [:ptv :batch-descriptions-generation]
                      merge
                      {:batch-size (count lipas-ids)
                       :halt? false
                       :size (count lipas-ids)
                       :lipas-ids (set lipas-ids)
                       :processed-lipas-ids #{}
                       :failed-lipas-ids #{}
                       :references {}})
       :fx [[:dispatch [::generate-all-descriptions* org-id queue]]]})))

(rf/reg-event-db ::halt-descriptions-generation
  (fn [db _]
    (-> db
        (assoc-in [:ptv :batch-descriptions-generation :halt?] true))))

(rf/reg-event-db ::select-sports-sites-filter
  (fn [db [_ v]]
    (assoc-in db [:ptv :batch-descriptions-generation :sports-sites-filter] v)))

;;; Service descriptions generation ;;;

(rf/reg-event-fx ::generate-service-descriptions
  (fn [{:keys [db]} [_ org-id id overview success-fx failure-fx]]
    (let [token (-> db :user :login :token)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-descriptions] true)
               (assoc-in [:ptv :syncing :service-descriptions id] true))
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/generate-ptv-service-descriptions")

              :params {:city-codes (get-in db [:ptv :selected-org :ptv-data :city-codes])
                       :sub-category-id (parse-long (last (str/split id #"-")))
                       :overview overview}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::generate-service-descriptions-success org-id id success-fx]
              :on-failure [::generate-service-descriptions-failure org-id id failure-fx]}]]})))

(rf/reg-event-fx ::generate-service-descriptions-success
  (fn [{:keys [db]} [_ org-id id extra-fx resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-lipas :service-descriptions] false)
             (update-in [:ptv :syncing :service-descriptions] dissoc id)
             (update-in [:ptv :org org-id :data :service-candidates id] merge resp))
     :fx extra-fx}))

(rf/reg-event-fx ::generate-service-descriptions-failure
  (fn [{:keys [db]} [_ _org-id id extra-fx resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-descriptions] false)
               (update-in [:ptv :syncing :service-descriptions] dissoc id)
               (assoc-in [:ptv :errors :service-descriptions] resp))
       :fx (or extra-fx
               [[:dispatch [:lipas.ui.events/set-active-notification notification]]])})))

(rf/reg-event-fx ::generate-all-service-descriptions*
  (fn [{:keys [db]} [_ org-id ids]]
    (let [halt? (get-in db [:ptv :service-descriptions-generation :halt?] false)
          ids* (get-in db [:ptv :service-descriptions-generation :ids])
          processed (set/difference (set ids*) (set ids))
          on-single-success [[:dispatch [::generate-all-service-descriptions* org-id (rest ids)]]]
          on-single-failure [[:dispatch [::halt-service-descriptions-generation]]]]
      {:db (-> db
               (update-in [:ptv :service-descriptions-generation]
                          merge
                          {:in-progress? (if halt? false (boolean (seq ids)))
                           :processed-ids processed
                           :halt? halt?}))
       :fx [(when (and (not halt?) (seq ids))
              [:dispatch [::generate-service-descriptions
                          org-id
                          (first ids)
                          nil
                          on-single-success
                          on-single-failure]])]})))

(rf/reg-event-fx ::generate-all-service-descriptions
  (fn [{:keys [db]} [_ ms]]
    (let [org-id (-get-ptv-org-id db)
          ids (map :source-id ms)]
      {:db (update-in db [:ptv :service-descriptions-generation]
                      merge
                      {:batch-size (count ids)
                       :halt? false
                       :size (count ids)
                       :ids (set ids)})
       :fx [[:dispatch [::generate-all-service-descriptions* org-id ids]]]})))

(rf/reg-event-db ::halt-service-descriptions-generation
  (fn [db _]
    (-> db
        (assoc-in [:ptv :service-descriptions-generation :halt?] true))))

(rf/reg-event-db ::set-service-candidate-name
  (fn [db [_ id locale v]]
    (let [org-id (-get-ptv-org-id db)]
      ;; A cleared field is "", not nil: the edit sits on top of PTV's
      ;; live text, so a missing key would bring that text back.
      (assoc-in db [:ptv :org org-id :data :service-candidates id :name locale] (or v "")))))

(rf/reg-event-db ::discard-service-edits
  ;; Drops a service's unsaved edits; the panel falls back to PTV's texts.
  (fn [db [_ id]]
    (let [org-id (-get-ptv-org-id db)]
      (update-in db [:ptv :org org-id :data :service-candidates] dissoc id))))

(rf/reg-event-db ::toggle-service-language-removal
  ;; Marks a native service's language version to be removed from PTV on
  ;; the next sync (see lipas.data.ptv/patch-service-texts), or unmarks it.
  (fn [db [_ id locale]]
    (let [org-id (-get-ptv-org-id db)]
      (update-in db [:ptv :org org-id :data :service-candidates id :remove-languages]
                 (fn [xs] (let [xs (set xs)]
                            (if (contains? xs locale) (disj xs locale) (conj xs locale))))))))

(rf/reg-event-db ::set-service-candidate-summary
  (fn [db [_ id locale v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :data :service-candidates id :summary locale] (or v "")))))

(rf/reg-event-db ::set-service-candidate-description
  (fn [db [_ id locale v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :data :service-candidates id :description locale] (or v "")))))

(rf/reg-event-db ::set-service-candidate-user-instruction
  (fn [db [_ id locale v]]
    (let [org-id (-get-ptv-org-id db)]
      (assoc-in db [:ptv :org org-id :data :service-candidates id :user-instruction locale] (or v "")))))

(rf/reg-event-fx ::translate-service-candidate
  (fn [{:keys [db]} [_ source-id from-lang to-langs]]
    (let [token (-> db :user :login :token)
          org-id (-get-ptv-org-id db)
          summary (get-in db [:ptv :org org-id :data :service-candidates source-id :summary from-lang])
          description (get-in db [:ptv :org org-id :data :service-candidates source-id :description from-lang])
          user-instruction (get-in db [:ptv :org org-id :data :service-candidates source-id :user-instruction from-lang])]
      (when (and summary description)
        {:db (-> db
                 (assoc-in [:ptv :loading-from-lipas :descriptions] true)
                 (assoc-in [:ptv :syncing :service-descriptions source-id] true))
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/translate-to-other-langs")
                :params (cond-> {:from (name from-lang)
                                 :to (set (map name to-langs))
                                 :summary summary
                                 :description description}
                          user-instruction (assoc :user-instruction user-instruction))
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::translate-service-candidate-success source-id]
                :on-failure [::translate-service-candidate-failure source-id]}]]}))))

(rf/reg-event-fx ::translate-service-candidate-success
  (fn [{:keys [db]} [_ source-id resp]]
    (let [tr (:translator db)
          org-id (-get-ptv-org-id db)
          resp (update resp :summary
                       (fn [m] (reduce-kv (fn [acc k v]
                                            (assoc acc k (when v (subs v 0 (min ptv-data/max-summary-length (count v))))))
                                          {} m)))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (update-in [:ptv :syncing :service-descriptions] dissoc source-id)
               (update-in [:ptv :org org-id :data :service-candidates source-id] merge-translation resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification
                        (translate-notification tr resp)]]]})))

(rf/reg-event-fx ::translate-service-candidate-failure
  (fn [{:keys [db]} [_ source-id _resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (update-in [:ptv :syncing :service-descriptions] dissoc source-id))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::translate-service-candidate-with-texts
  (fn [{:keys [db]} [_ source-id from-lang to-langs {:keys [summary description user-instruction]}]]
    (let [token (-> db :user :login :token)]
      (when (and summary description)
        {:db (-> db
                 (assoc-in [:ptv :loading-from-lipas :descriptions] true)
                 (assoc-in [:ptv :syncing :service-descriptions source-id] true))
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/translate-to-other-langs")
                :params (cond-> {:from (name from-lang)
                                 :to (set (map name to-langs))
                                 :summary summary
                                 :description description}
                          user-instruction (assoc :user-instruction user-instruction))
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::translate-service-candidate-success source-id]
                :on-failure [::translate-service-candidate-failure source-id]}]]}))))

;;; Create Services in PTV ;;;

(rf/reg-event-fx ::create-ptv-service
  ;; Optional 7th arg lipas-id: when set, the newly created service's id is
  ;; auto-added to that site's :ptv :service-ids on success.
  (fn [{:keys [db]} [_ org-id id data success-fx failure-fx lipas-id]]
    (let [token (-> db :user :login :token)
          ptv-config (or (get-in db [:ptv :selected-org :ptv-data])
                         (some #(when (= org-id (get-in % [:ptv-data :org-id]))
                                  (:ptv-data %))
                               (get-in db [:ptv :ptv-orgs])))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :services] true)
               (assoc-in [:ptv :syncing :service id] true))
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/save-ptv-service")
              :params (merge (select-keys ptv-config [:org-id :city-codes])
                             data)
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::create-ptv-service-success org-id id success-fx lipas-id]
              :on-failure [::create-ptv-service-failure org-id id failure-fx]}]]})))

(rf/reg-event-fx ::create-ptv-service-success
  (fn [{:keys [db]} [_ org-id id extra-fx lipas-id resp]]
    (let [tr (:translator db)
          new-service-id (:id resp)
          existing-ids (set (get-in db [:sports-sites lipas-id :editing :ptv :service-ids]))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :services] false)
               (update-in [:ptv :syncing :service] dissoc id)
               (assoc-in [:ptv :org org-id :data :services (:id resp)] resp)
               (update-in [:ptv :org org-id :data :service-candidates id]
                          #(-> % (assoc :created-in-ptv true) (dissoc :remove-languages)))
               (update-in [:ptv :org org-id :data :manual-services]
                          dissoc (:sourceId resp)))
       :fx (cond-> (into (or (seq extra-fx) [])
                         [[:dispatch [:lipas.ui.events/set-active-notification
                                      {:message (tr :notifications/save-success) :success? true}]]
                          ;; the write is the next review's base, and its choices are spent
                          [:dispatch [::fetch-ptv-service-bases org-id]]
                          [:dispatch [::forget-review-choices id]]])
             (and lipas-id new-service-id)
             (conj [:dispatch [:lipas.ui.sports-sites.events/edit-field
                               lipas-id [:ptv :service-ids]
                               (vec (conj existing-ids new-service-id))]]))})))

(rf/reg-event-fx ::create-ptv-service-failure
  (fn [{:keys [db]} [_ org-id id extra-fx resp]]
    (let [tr (:translator db)
          modified? (ptv-modified-error? resp)
          ;; Refused because the service was edited in PTV after this page
          ;; loaded it: reload the services so the panel shows PTV's current
          ;; texts under the user's unsaved edits, which are kept.
          changed-in-ptv? (= :changed-in-ptv (some-> resp :response :type keyword))
          notification (ptv-sync-error-notification tr resp)
          ;; Find the service to update its cached publishingStatus: `id` is
          ;; the PTV id for a service made in PTV, the source-id otherwise
          services (get-in db [:ptv :org org-id :data :services])
          service-id (if (contains? services id)
                       id
                       (some (fn [[sid svc]]
                               (when (= id (:sourceId svc)) sid))
                             services))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :services] false)
               (update-in [:ptv :syncing :service] dissoc id)
               (assoc-in [:ptv :errors :services-creation] resp)
               (cond->
                 (and modified? service-id)
                 (assoc-in [:ptv :org org-id :data :services service-id :publishingStatus] "Modified")))
       :fx (cond-> (into (or (seq extra-fx) [])
                         [[:dispatch [:lipas.ui.events/set-active-notification notification]]])
             changed-in-ptv? (conj [:dispatch [::fetch-ptv-services (get-in db [:ptv :selected-org])]]))})))

(rf/reg-event-fx ::create-all-ptv-services*
  (fn [{:keys [db]} [_ org-id ids]]
    (let [halt? (get-in db [:ptv :services-creation :halt?] false)
          ids* (get-in db [:ptv :services-creation :ids])
          processed (set/difference (set ids*) (set ids))
          on-single-success [[:dispatch [::create-all-ptv-services* org-id (rest ids)]]
                             [:dispatch [::assign-services-to-sports-sites]]]
          on-single-failure [[:dispatch [::halt-services-creation]]]]
      {:db (-> db
               (update-in [:ptv :services-creation]
                          merge
                          {:in-progress? (if halt? false (boolean (seq ids)))
                           :processed-ids processed
                           :halt? halt?}))
       :fx [(when (and (not halt?) (seq ids))
              [:dispatch [::create-ptv-service
                          org-id
                          (first ids)
                          (get-in db [:ptv :services-creation :data (first ids)])
                          on-single-success
                          on-single-failure]])]})))

(rf/reg-event-fx ::create-all-ptv-services
  (fn [{:keys [db]} [_ ms]]
    (let [org-id (-get-ptv-org-id db)
          ;; A candidate linked to an existing PTV service is covered by
          ;; that service, which the wizard never writes to
          ms (remove :linked? ms)
          ids (map :source-id ms)]
      {:db (update-in db [:ptv :services-creation]
                      merge
                      {:batch-size (count ids)
                       :halt? false
                       :size (count ids)
                       ;; TODO: Is this necessary? Maybe?
                       :data (utils/index-by :source-id ms)
                       :ids (set ids)})
       :fx [[:dispatch [::create-all-ptv-services* org-id ids]]]})))

(rf/reg-event-db ::halt-services-creation
  (fn [db _]
    (-> db
        (assoc-in [:ptv :services-creation :halt?] true))))

(rf/reg-event-fx ::link-candidate-to-existing-service
  ;; Covers a sub-category with a service already in PTV: its sites get the
  ;; service (::assign-services-to-sports-sites), nothing is written to the
  ;; service itself.
  (fn [{:keys [db]} [_ source-id service-id]]
    (let [org-id (-get-ptv-org-id db)]
      {:db (-> db
               (assoc-in [:ptv :org org-id :data :service-mappings source-id]
                         {:service-id service-id})
               (update-in [:ptv :org org-id :data :manual-services] dissoc source-id))
       :fx [[:dispatch [::assign-services-to-sports-sites]]]})))

(rf/reg-event-fx ::unlink-candidate-from-existing-service
  (fn [{:keys [db]} [_ source-id]]
    (let [org-id (-get-ptv-org-id db)
          mapped-service-id (get-in db [:ptv :org org-id :data :service-mappings source-id :service-id])
                         ;; Clear the mapped service-id from affected sports sites
          sports-sites (get-in db [:ptv :org org-id :data :sports-sites])
          sports-sites (reduce-kv
                         (fn [m lipas-id site]
                           (let [sids (get-in site [:ptv :service-ids])]
                             (if (and mapped-service-id (some #{mapped-service-id} sids))
                               (assoc-in m [lipas-id :ptv :service-ids]
                                         (vec (remove #{mapped-service-id} sids)))
                               m)))
                         sports-sites
                         sports-sites)]
      {:db (-> db
               (update-in [:ptv :org org-id :data :service-mappings] dissoc source-id)
               (assoc-in [:ptv :org org-id :data :sports-sites] sports-sites))
       :fx [[:dispatch [::assign-services-to-sports-sites]]]})))

(rf/reg-event-db ::assign-services-to-sports-sites
  (fn [db _]
    (let [org-id (-get-ptv-org-id db)
          types (get-in db [:sports-sites :types])
          source-id->service (->> (get-in db [:ptv :org org-id :data :services])
                                  vals
                                  (utils/index-by :sourceId))
          service-mappings (get-in db [:ptv :org org-id :data :service-mappings])
          sports-sites (get-in db [:ptv :org org-id :data :sports-sites])
          sports-sites (reduce-kv
                         (fn [sports-sites lipas-id sports-site]
                           (let [;; the next function needs this!
                                 ;; this is "really" added to the :ptv data later in ::create-ptv-service-location
                                 sports-site (assoc-in sports-site [:ptv :org-id] org-id)
                                               ;; Match by sourceId (auto-created services)
                                 lipas-service-ids (ptv-data/sports-site->service-ids types source-id->service sports-site)
                                               ;; Match by manual mapping
                                 sub-cat-id (-> sports-site :type :type-code types :sub-category)
                                 mapped-source-id (ptv-data/->service-source-id org-id sub-cat-id)
                                 mapped-service-id (get-in service-mappings [mapped-source-id :service-id])
                                 all-service-ids (cond-> (set lipas-service-ids)
                                                   mapped-service-id (conj mapped-service-id))]
                             (if (seq all-service-ids)
                               (update-in sports-sites [lipas-id :ptv :service-ids] (fn [x]
                                                                                      (vec (into (set x) all-service-ids))))
                               sports-sites)))
                         sports-sites
                         sports-sites)]
      (assoc-in db [:ptv :org org-id :data :sports-sites] sports-sites))))

;;; Create service locations in PTV ;;;

(defn service-location-payload
  "Build the exact request body posted to /actions/save-ptv-service-location
  for `lipas-id`. Shared by the sync event and the client-side schema
  validation (see ::service-location-schema-errors) so the validation always
  matches what actually gets sent."
  [db lipas-id]
  (let [org-id (-get-ptv-org-id db)
        ;; Add default org-id for service-ids linking
        sports-site (-> (get-in db [:ptv :org org-id :data :sports-sites lipas-id])
                        (update :ptv #(merge {:org-id org-id} %)))
        ;; Always use org languages (site typically doesn't store this)
        org-languages (get-in db [:ptv :selected-org :ptv-data :supported-languages] ptv-data/fallback-languages)
        channel (get-in db [:ptv :org org-id :data :service-channels
                            (-> sports-site :ptv :service-channel-ids first)])
        ptv (merge (select-keys (:default-settings (:ptv db))
                                [:sync-enabled])
                   {:service-channel-ids []}
                   (select-keys (:ptv sports-site)
                                [:org-id
                                 :sync-enabled
                                 :service-channel-ids
                                 :service-ids
                                 :summary
                                 :description])
                   {:languages org-languages})
        ;; A cleared text (nil, also on sites saved before setters removed
        ;; it) is no text, not an invalid one
        ptv (-> ptv
                (update :summary ptv-data/drop-blank-texts)
                (update :description ptv-data/drop-blank-texts))]
    (cond-> {:lipas-id lipas-id
             :org-id org-id
             :ptv ptv}
      ;; the site's own data the review took from PTV: the backend writes
      ;; it into the site, which the sync then sends
      (get-in db [:ptv :review :site-patch lipas-id])
      (assoc :site-patch (get-in db [:ptv :review :site-patch lipas-id]))

      ;; what the review was made against: a location changed in PTV since
      ;; is refused, not overwritten unseen
      (review-data/reviewed channel)
      (assoc :reviewed (review-data/reviewed channel)))))

(rf/reg-event-fx ::create-ptv-service-location
  (fn [{:keys [db]} [_ lipas-id success-fx failure-fx]]
    (let [token (-> db :user :login :token)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-locations] true)
               (assoc-in [:ptv :syncing :service-location lipas-id] true))
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/save-ptv-service-location")
              :params (service-location-payload db lipas-id)
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::create-ptv-service-location-success lipas-id success-fx]
              :on-failure [::create-ptv-service-location-failure lipas-id failure-fx]}]]})))

(defn- reload-map-site?
  "Whether the site the PTV tool just wrote is loaded in the map, and not
   under an edit there: it holds the revision before the write (and what a
   sync took from PTV), so an edit would start from it and its save revert
   the write."
  [db lipas-id]
  (and (get-in db [:sports-sites lipas-id :latest])
       (not (get-in db [:sports-sites lipas-id :editing]))))

(defn- reload-map-site-fx
  "Reloads the site in the map (reload-map-site?), and its site-form review
   when one was fetched."
  [db lipas-id]
  [:dispatch [:lipas.ui.sports-sites.events/get lipas-id
              (when (get-in db (review-data/site-path lipas-id))
                [[:lipas.ui.map.events/fetch-ptv-site-review lipas-id]])]])

(rf/reg-event-fx ::create-ptv-service-location-success
  (fn [{:keys [db]} [_ lipas-id extra-fx {:keys [ptv-resp ptv]}]]
    (let [tr (:translator db)
          org-id (-get-ptv-org-id db)
                         ;; Remove old service-channel entry if ID changed
          old-channel-id (get-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-channel-ids 0])
          new-channel-id (:id ptv-resp)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-locations] false)
               (update-in [:ptv :syncing :service-location] dissoc lipas-id)
               (assoc-in [:ptv :org org-id :data :service-channels new-channel-id] ptv-resp)
                              ;; Clean up old channel entry if ID changed
               (cond->
                 (and old-channel-id (not= old-channel-id new-channel-id))
                 (update-in [:ptv :org org-id :data :service-channels] dissoc old-channel-id))
                              ;; Update the lipas TS also, it will be the same TS as PTV last-sync now
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :event-date] (:last-sync ptv))
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv] ptv)
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv-persisted-sync?]
                         (persisted-sync? ptv))
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv-saved]
                         (saved-snapshot ptv))
                              ;; The sync persisted the draft — refresh the snapshot audit
                              ;; whose-move states are derived from, so the audit status
                              ;; (row bucket, feedback alert) moves only now, not on keystrokes.
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv-persisted]
                         (select-keys ptv ptv-data/site-audited-field-keys))
               ;; What this sync sent is the next review's base, and the
               ;; choices made for it are spent.
               (as-> db (let [synced (ptv-review/->review-base
                                       (get-in db [:ptv :org org-id :data :sports-sites lipas-id]))]
                          (-> db
                              (assoc-in [:ptv :org org-id :data :sync-bases lipas-id] synced)
                              (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :review-saved]
                                        synced))))
               (update-in [:ptv :review :choices] dissoc lipas-id)
               (update-in [:ptv :review :undo] dissoc lipas-id)
               (update-in [:ptv :review :open] dissoc lipas-id)
               (update-in [:ptv :review :site-patch] dissoc lipas-id))
       :fx (cond-> (into (or (seq extra-fx) [])
                         [[:dispatch [:lipas.ui.events/set-active-notification
                                      {:message (tr :notifications/save-success) :success? true}]]])
             (reload-map-site? db lipas-id) (conj (reload-map-site-fx db lipas-id)))})))

(rf/reg-event-fx ::create-ptv-service-location-failure
  (fn [{:keys [db]} [_ lipas-id extra-fx resp]]
    (let [tr (:translator db)
          modified? (ptv-modified-error? resp)
          notification (ptv-sync-error-notification tr resp)
          org-id (-get-ptv-org-id db)
          channel-id (get-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-channel-ids 0])]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-locations] false)
               (update-in [:ptv :syncing :service-location] dissoc lipas-id)
               (update-in [:ptv :review :site-patch] dissoc lipas-id)
               (assoc-in [:ptv :errors :service-locations-creation] resp)
               (cond->
                 (and modified? channel-id)
                 (assoc-in [:ptv :org org-id :data :service-channels channel-id :publishingStatus] "Modified")))
       :fx (cond-> (into (or (seq extra-fx) [])
                         [[:dispatch [:lipas.ui.events/set-active-notification notification]]])
             ;; changed in PTV since the review: show the review on fresh data
             (changed-since-review? resp)
             (into [[:dispatch [::refresh-service-channel org-id channel-id]]
                    [:dispatch [::show-review lipas-id]]]))})))

;;; Sync review ;;;
;; The review compares PTV with the saved site; a decision is made by
;; making the edit hold one side's version (or a text of one's own), so
;; the form below shows it at once. Only "keep Lipas's version" leaves a
;; record (review-data/choices-path): the edit alone can't tell it from
;; not having looked.

(defn- forget-choices
  "`db` without the choices made in the review of `id` (a lipas-id, or a
   service's edit key), and what they wrote (review-data/undo-path)."
  [db id]
  (-> db
      (assoc-in (review-data/choices-path id) {})
      (update-in [:ptv :review :undo] dissoc id)))

(defn- draft-path
  "Where the edit a review writes into lives: the PTV tool's copy of the
   site, or the site form's edit buffer."
  [db target lipas-id]
  (case target
    :ptv-tool [:ptv :org (-get-ptv-org-id db) :data :sports-sites lipas-id]
    :site-form [:sports-sites lipas-id :editing]))

(rf/reg-event-fx ::review-take
  ;; Make the edit hold `side`'s version (:ptv, or :lipas for the saved
  ;; one) of each of `rows`; :edited confirms the value the edit already
  ;; holds, of the user's own, against PTV's value now.
  (fn [{:keys [db]} [_ target lipas-id rows side]]
    (let [path (draft-path db target lipas-id)
          draft (get-in db path)
          edited (if (= :edited side)
                   draft
                   (reduce #(ptv-review/apply-review-writes %1 (ptv-review/review-writes %1 %2 side))
                           draft
                           rows))
          ;; {path value}: what the choice writes into the edit
          writes (when-not (= :edited side)
                   (into {}
                         (comp (mapcat #(keys (ptv-review/review-writes draft % side)))
                               (distinct)
                               (map (fn [p] [p (get-in edited p)])))
                         rows))
          db (-> db
                 (update-in (review-data/choices-path lipas-id)
                            (fn [choices]
                              (reduce (fn [choices row]
                                        (case side
                                          :lipas (assoc choices (:key row)
                                                        {:choice :lipas :seen (ptv-review/review-choice-seen row)})
                                          :edited (assoc choices (:key row) (ptv-review/edited-choice row))
                                          (dissoc choices (:key row))))
                                      choices
                                      rows)))
                 (update-in (review-data/undo-path lipas-id) ptv-review/record-review-writes draft writes))]
      (case target
        :ptv-tool {:db (assoc-in db path edited)}
        ;; through edit-fields, which also recalculates derived fields
        :site-form {:db db
                    :fx (when (seq writes)
                          [[:dispatch [:lipas.ui.sports-sites.events/edit-fields lipas-id writes]]])}))))

(rf/reg-event-fx ::review-take-value
  ;; Make the edit hold `value`, one of PTV's values of a one-slot `row`,
  ;; or for other web pages {:slot :url}: the URL taken into that slot.
  (fn [{:keys [db]} [_ target lipas-id row value]]
    (let [path (draft-path db target lipas-id)
          writes (ptv-review/review-value-writes row value)
          db (update-in db (review-data/undo-path lipas-id)
                        ptv-review/record-review-writes (get-in db path) writes)
          ;; Taking another of PTV's values decides the row by itself (the
          ;; edit holds it). Lipas's own value, which PTV also has, leaves
          ;; the edit as it was: that is keeping Lipas's version.
          db (update-in db (review-data/choices-path lipas-id)
                        (fn [choices]
                          (if (and (= :one-slot (:kind row)) (ptv-review/lipas-value? row value))
                            (assoc choices (:key row)
                                   {:choice :lipas :seen (ptv-review/review-choice-seen row)})
                            (dissoc choices (:key row)))))]
      (case target
        :ptv-tool {:db (update-in db path ptv-review/apply-review-writes writes)}
        :site-form {:db db
                    :fx [[:dispatch [:lipas.ui.sports-sites.events/edit-fields lipas-id writes]]]}))))

(rf/reg-event-fx ::review-take-sites
  ;; Bulk over the PTV tool's sites: `side` for the rows of each
  ;; {:lipas-id :rows}.
  (fn [_ [_ sites side]]
    {:fx (for [{:keys [lipas-id rows]} sites]
           [:dispatch [::review-take :ptv-tool lipas-id rows side]])}))

(rf/reg-event-fx ::review-reset
  ;; "Kumoa valinnat": the choices made in the review are forgotten, and
  ;; what they wrote into the edit is taken back, where the user hasn't
  ;; typed over it since. Text the user wrote stays.
  (fn [{:keys [db]} [_ target lipas-id]]
    (let [path (draft-path db target lipas-id)
          writes (ptv-review/undo-review-writes (get-in db (review-data/undo-path lipas-id))
                                                (get-in db path))
          db (forget-choices db lipas-id)]
      (case target
        :ptv-tool {:db (update-in db path ptv-review/apply-review-writes writes)}
        :site-form {:db db
                    :fx (when (seq writes)
                          [[:dispatch [:lipas.ui.sports-sites.events/edit-fields lipas-id writes]]])}))))

(rf/reg-event-db ::forget-review-choices
  (fn [db [_ id]]
    (forget-choices db id)))

(rf/reg-event-db ::set-review-open
  (fn [db [_ lipas-id open?]]
    (assoc-in db (review-data/open-path lipas-id) open?)))

(rf/reg-event-fx ::show-review
  ;; Opened from a confirmation: the panel may be out of sight. The focus
  ;; moves to the first change still to decide.
  (fn [{:keys [db]} [_ lipas-id]]
    {:db (assoc-in db (review-data/open-path lipas-id) true)
     :fx [[:lipas.ui.effects/scroll-into-view! (review-data/panel-dom-id lipas-id)]
          [:lipas.ui.effects/focus! (review-data/first-open-dom-id lipas-id)]]}))

(rf/reg-event-fx ::sync-with-review
  ;; "Synkronoi nyt" with the site's review `rows` (annotated, see
  ;; ptv-review/annotate-review): while a change made in PTV is undecided,
  ;; ask before overwriting it; otherwise sync the edit, which holds the
  ;; decisions. `overwrite?` is the user's answer: undecided rows keep the
  ;; saved Lipas version.
  (fn [{:keys [db]} [_ lipas-id rows overwrite?]]
    (let [tr (:translator db)
          org-id (-get-ptv-org-id db)
          site (get-in db [:ptv :org org-id :data :sports-sites lipas-id])
          open (ptv-review/open-review-rows rows (get-in db (review-data/choices-path lipas-id)))]
      (if (and (seq open) (not overwrite?))
        {:fx [[:dispatch [:lipas.ui.events/confirm-with
                          (review-data/overwrite-confirmation
                            tr
                            {:open open
                             :languages (review-data/language-removals rows)
                             :on-overwrite #(rf/dispatch [::sync-with-review lipas-id rows true])
                             :on-review #(rf/dispatch [::show-review lipas-id])})]]]}
        ;; The sync sends the saved site: what the review took from PTV
        ;; into the site's own data travels with it for the backend to write.
        {:db (assoc-in db [:ptv :review :site-patch lipas-id]
                       (ptv-review/review-site-patch (:review-saved site) site))
         :fx [[:dispatch [::create-ptv-service-location lipas-id [] []]]]}))))

;;; Explicit archive (publishingStatus "Deleted") ;;;

(rf/reg-event-fx ::archive-ptv-service-location
  ;; Explicit, user-requested archive of a previously-published service-location.
  ;; Posts the same payload as a sync but with :archive? true, so the backend
  ;; PUTs publishingStatus "Deleted" while preserving the channel link (a later
  ;; re-activation re-publishes the same channel). Reuses the sync failure
  ;; handler so PTV errors (incl. name conflicts) surface the same way.
  (fn [{:keys [db]} [_ lipas-id]]
    (let [token (-> db :user :login :token)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-locations] true)
               (assoc-in [:ptv :syncing :service-location lipas-id] true))
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/save-ptv-service-location")
              ;; status only: no review choices are written into the site
              :params (-> (service-location-payload db lipas-id)
                          (dissoc :site-patch)
                          (assoc :archive? true))
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::archive-ptv-service-location-success lipas-id]
              :on-failure [::create-ptv-service-location-failure lipas-id []]}]]})))

(rf/reg-event-fx ::archive-ptv-service-location-success
  (fn [{:keys [db]} [_ lipas-id {:keys [ptv]}]]
    (let [tr (:translator db)
          org-id (-get-ptv-org-id db)]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-locations] false)
               (update-in [:ptv :syncing :service-location] dissoc lipas-id)
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv] ptv)
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv-persisted-sync?]
                         (persisted-sync? ptv))
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv-saved]
                         (saved-snapshot ptv)))
       :fx (cond-> [[:dispatch [:lipas.ui.events/set-active-notification
                                {:message (tr :ptv.actions/archived-in-ptv) :success? true}]]]
             (reload-map-site? db lipas-id) (conj (reload-map-site-fx db lipas-id)))})))

(rf/reg-event-fx ::create-all-ptv-service-locations*
  (fn [{:keys [db]} [_ org-id ids]]
    (let [halt? (get-in db [:ptv :service-locations-creation :halt?] false)
          ids* (get-in db [:ptv :service-locations-creation :ids])
          processed (set/difference (set ids*) (set ids))
          on-single-success [[:dispatch [::create-all-ptv-service-locations* org-id (rest ids)]]]
          ;; a failing PTV is likely to fail the next one too, and a 502 can
          ;; leave a draft behind: stop and say where (::resume-service-locations-creation)
          on-single-failure [[:dispatch [::halt-service-locations-creation (first ids)]]]]
      {:db (-> db
               (update-in [:ptv :service-locations-creation]
                          merge
                          {:in-progress? (if halt? false (boolean (seq ids)))
                           :processed-ids processed
                           :halt? halt?}))
       :fx [(when (and (not halt?) (seq ids))
              [:dispatch [::create-ptv-service-location
                          (first ids)
                          on-single-success
                          on-single-failure]])]})))

(defn ptv-meta-entry
  "The /actions/save-ptv-meta entry for `site`: the same editable keys the
  sync path persists (shared source of truth); the backend preserves the
  server-owned lifecycle keys (:source-id, :publishing-status,
  :previous-type-code) itself. `utils/clean` drops empty collections, so the
  required id vectors are defaulted back — without them an unlinked site's
  entry is invalid."
  [site]
  (merge {:service-ids [] :service-channel-ids []}
         (utils/clean (select-keys site ptv-data/persisted-ptv-keys))))

(def ^:private valid-ptv-meta?
  "Would `entry` pass the backend's ptv-meta validation? Extra keys are
  stripped first, as the backend does."
  (let [decode (m/decoder ptv-schema/ptv-meta (mt/strip-extra-keys-transformer))
        valid? (m/validator ptv-schema/ptv-meta)]
    (comp valid? decode)))

(defn- with-review-site-patches
  "`db` with what the sync of each of `lipas-ids` writes into the site's
   own data, from the choices made in its review."
  [db org-id lipas-ids]
  (reduce (fn [db lipas-id]
            (let [site (get-in db [:ptv :org org-id :data :sports-sites lipas-id])]
              (assoc-in db [:ptv :review :site-patch lipas-id]
                        (ptv-review/review-site-patch (:review-saved site) site))))
          db
          lipas-ids))

(rf/reg-event-fx ::create-all-ptv-service-locations
  ;; The view blocks the export while a site to sync has an undecided
  ;; change made in PTV; the edits hold the decisions.
  (fn [{:keys [db]} [_ sports-sites]]
    (let [org-id (-get-ptv-org-id db)

          {:keys [to-sync to-save]} (group-by (fn [m]
                                                (if (:sync-enabled m)
                                                  :to-sync
                                                  :to-save))
                                              sports-sites)
          ids (map :lipas-id to-sync)
          db (with-review-site-patches db org-id ids)
          ;; Only sites with something persistable: an untouched site (no
          ;; texts yet) or one with over-long texts would fail the backend's
          ;; ptv-meta validation and take the whole meta save down with it.
          to-save (filter (comp valid-ptv-meta? ptv-meta-entry) to-save)]

      #_(println "To sync: " (count to-sync))
      #_(println "to save: " (count to-save))

      {:db (assoc-in db [:ptv :service-locations-creation]
                     {:batch-size (count ids)
                      :halt? false
                      :in-progress? true
                      :size (count ids)
                      ;; in order, for resuming after a failure
                      :ids (vec ids)
                      :processed-ids #{}})
       :fx [[:dispatch [::create-all-ptv-service-locations* org-id ids]]
            [:dispatch [::save-ptv-meta to-save]]]})))

(rf/reg-event-db ::halt-service-locations-creation
  ;; After ::create-ptv-service-location-failure, which stored the error.
  ;; The sites not reached drop their review patches; a resume makes them
  ;; again from the choices of that time.
  (fn [db [_ failed-lipas-id]]
    (let [{:keys [ids processed-ids]} (get-in db [:ptv :service-locations-creation])]
      (-> db
          (update-in [:ptv :review :site-patch] #(apply dissoc % (remove (set processed-ids) ids)))
          (update-in [:ptv :service-locations-creation] merge
                     {:halt? true
                      :in-progress? false
                      :failed {:lipas-id failed-lipas-id
                               :error (get-in db [:ptv :errors :service-locations-creation])}})))))

(rf/reg-event-fx ::resume-service-locations-creation
  ;; Goes on from the site that failed with those not exported yet; the
  ;; progress keeps counting the whole export.
  (fn [{:keys [db]} _]
    (let [org-id (-get-ptv-org-id db)
          {:keys [ids processed-ids failed]} (get-in db [:ptv :service-locations-creation])
          ;; the failed one first, then the rest in the original order
          remaining (distinct (cons (:lipas-id failed)
                                    (remove (set processed-ids) ids)))]
      {:db (-> db
               (with-review-site-patches org-id remaining)
               (update-in [:ptv :service-locations-creation] merge
                          {:halt? false :in-progress? true :failed nil}))
       :fx [[:dispatch [::create-all-ptv-service-locations* org-id remaining]]]})))

(rf/reg-event-fx ::save-ptv-meta
  (fn [{:keys [db]} [_ sports-sites]]
    ;; This event is used to save :ptv data for sites which have :sync-enabled false
    (when (seq sports-sites)
      (let [token (-> db :user :login :token)
            params (reduce (fn [m site]
                             (assoc m (:lipas-id site) (ptv-meta-entry site)))
                           {}
                           sports-sites)]
        {:db (assoc-in db [:ptv :save-in-progress] true)
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/save-ptv-meta")
                :params params
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::save-ptv-meta-success params]
                :on-failure [::save-ptv-meta-failure]}]]}))))

(rf/reg-event-fx ::save-ptv-meta-success
  (fn [{:keys [db]} [_ saved _resp]]
    (let [tr (:translator db)
          org-id (-get-ptv-org-id db)
          notification {:message (tr :notifications/save-success)
                        :success? true}]
      {:db (-> db
               (assoc-in [:ptv :save-in-progress] false)
               (update-in [:ptv :org org-id :data :sports-sites]
                          (fn [sites]
                            (reduce-kv (fn [sites lipas-id ptv-meta]
                                         (cond-> sites
                                           (contains? sites lipas-id)
                                           (-> (assoc-in [lipas-id :ptv-persisted-sync?]
                                                         (persisted-sync? ptv-meta))
                                               (update-in [lipas-id :review-saved :ptv] merge
                                                          (select-keys ptv-meta [:summary :description :service-ids]))
                                               (assoc-in [lipas-id :ptv-saved]
                                                         (saved-snapshot ptv-meta)))))
                                       sites
                                       saved))))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::save-ptv-meta-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/save-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :save-in-progress] false)
               (assoc-in [:ptv :errors :save] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

;;; The Palvelut tab's review ;;;

(rf/reg-event-fx ::fetch-ptv-service-bases
  ;; What PTV returned after Lipas's last write of each service (Lipas's
  ;; mirror): the base of the Palvelut tab's review (ptv-review/service-review).
  (fn [{:keys [db]} [_ org-id]]
    (when org-id
      {:fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " (-> db :user :login :token))}
              :uri (str (:backend-url db) "/actions/fetch-ptv-service-bases")
              :params {:org-id org-id}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::fetch-ptv-service-bases-success org-id]
              :on-failure [::fetch-ptv-service-bases-failure org-id]}]]})))

(rf/reg-event-db ::fetch-ptv-service-bases-success
  (fn [db [_ org-id resp]]
    (assoc-in db [:ptv :org org-id :data :service-bases] resp)))

(rf/reg-event-db ::fetch-ptv-service-bases-failure
  ;; without bases every edit reads as Lipas's: no conflicts can be told
  (fn [db _] db))

(defn- service-edit-path
  "Where a service's edit (its unsaved texts, {field {locale text}}) lives."
  [org-id edit-key]
  [:ptv :org org-id :data :service-candidates edit-key])

(defn- assoc-service-texts
  "A service's edit with `writes` ({[field locale] text}) made."
  [edit writes]
  (reduce-kv assoc-in edit writes))

(rf/reg-event-fx ::service-review-take
  ;; A service's edit (its unsaved texts, at `edit-key`) holding `side`'s
  ;; version of each of `rows`: PTV's text replaces the edit's, or the
  ;; user's own edit is kept, which is recorded against PTV's text.
  (fn [{:keys [db]} [_ org-id edit-key rows side]]
    (let [path (service-edit-path org-id edit-key)
          ;; {[field locale] text}: PTV's texts into the edit
          writes (when (= :ptv side)
                   (into {} (for [{:keys [field locale ptv]} rows] [[field locale] ptv])))
          db (-> db
                 (update-in (review-data/choices-path edit-key)
                            (fn [choices]
                              (reduce (fn [choices row]
                                        (if (= :lipas side)
                                          (assoc choices (:key row) (ptv-review/service-kept-choice row))
                                          (dissoc choices (:key row))))
                                      choices
                                      rows)))
                 (update-in (review-data/undo-path edit-key)
                            ptv-review/record-review-writes (get-in db path) writes))]
      {:db (update-in db path assoc-service-texts writes)})))

(rf/reg-event-db ::service-review-reset
  ;; "Kumoa valinnat" for a service, as ::review-reset for a site.
  (fn [db [_ org-id edit-key]]
    (let [path (service-edit-path org-id edit-key)
          writes (ptv-review/undo-review-writes (get-in db (review-data/undo-path edit-key))
                                                (get-in db path))]
      (-> db
          (forget-choices edit-key)
          (update-in path assoc-service-texts writes)))))

;;; Sites waiting to be archived in PTV (D2) ;;;

(rf/reg-event-fx ::fetch-awaiting-archive
  ;; Sites removed in Lipas (or no longer PTV's) while still published in
  ;; PTV: an editor without PTV rights can't archive them, a PTV manager does
  ;; here. Not among the integration candidates, hence a query of their own.
  (fn [{:keys [db]} [_ org-id]]
    (when org-id
      {:fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " (-> db :user :login :token))}
              :uri (str (:backend-url db) "/actions/get-ptv-awaiting-archive")
              :params {:org-id org-id}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::fetch-awaiting-archive-success org-id]
              :on-failure [::fetch-awaiting-archive-failure org-id]}]]})))

(rf/reg-event-db ::fetch-awaiting-archive-success
  (fn [db [_ org-id resp]]
    (assoc-in db [:ptv :org org-id :data :awaiting-archive] resp)))

(rf/reg-event-db ::fetch-awaiting-archive-failure
  ;; nothing to show: the list is a convenience, PTV is untouched
  (fn [db [_ org-id _resp]]
    (update-in db [:ptv :org org-id :data] dissoc :awaiting-archive)))

(rf/reg-event-fx ::archive-awaiting
  ;; "Arkistoi PTV:ssä": a status-only archive of a site waiting for it
  (fn [{:keys [db]} [_ org-id lipas-id]]
    {:db (assoc-in db [:ptv :syncing :service-location lipas-id] true)
     :fx [[:http-xhrio
           {:method :post
            :headers {:Authorization (str "Token " (-> db :user :login :token))}
            :uri (str (:backend-url db) "/actions/archive-ptv-site")
            :params {:org-id org-id :lipas-id lipas-id}
            :format (ajax/transit-request-format)
            :response-format (ajax/transit-response-format)
            :on-success [::archive-awaiting-success org-id lipas-id]
            :on-failure [::archive-awaiting-failure lipas-id]}]]}))

(rf/reg-event-fx ::archive-awaiting-success
  (fn [{:keys [db]} [_ org-id lipas-id _resp]]
    {:db (-> db
             (update-in [:ptv :syncing :service-location] dissoc lipas-id)
             (update-in [:ptv :org org-id :data :awaiting-archive]
                        (fn [sites] (vec (remove #(= lipas-id (:lipas-id %)) sites)))))
     :fx [[:dispatch [:lipas.ui.events/set-active-notification
                      {:message ((:translator db) :ptv.actions/archived-in-ptv) :success? true}]]]}))

(rf/reg-event-fx ::archive-awaiting-failure
  (fn [{:keys [db]} [_ lipas-id resp]]
    {:db (update-in db [:ptv :syncing :service-location] dissoc lipas-id)
     :fx [[:dispatch [:lipas.ui.events/set-active-notification
                      (ptv-sync-error-notification (:translator db) resp)]]]}))

;;; Synkronoi kaikki valmiit ;;;

(rf/reg-event-fx ::sync-all-ready
  ;; Clears the backlog of sites ready to sync (D2: editors' saves wait for a
  ;; PTV manager) without opening each row. Same gate as one site's sync:
  ;; a site with a change made in PTV still waiting for a choice is skipped,
  ;; and the confirmation names it.
  (fn [{:keys [db]} [_ ready awaiting]]
    (let [tr (:translator db)]
      {:fx [[:dispatch [:lipas.ui.events/confirm-with
                        {:title (tr :ptv/sync-all-ready-title (count ready))
                         :message [:div
                                   [:p {:style {:margin-top 0}}
                                    (tr :ptv/sync-all-ready-intro (count ready))]
                                   (when (seq awaiting)
                                     [:<>
                                      [:p (tr :ptv/sync-all-ready-skipped)]
                                      (into [:ul] (for [{:keys [name]} awaiting] [:li name]))])]
                         :confirm-label (tr :ptv/sync-all-ready-confirm)
                         :on-confirm #(rf/dispatch [::create-all-ptv-service-locations ready])}]]]})))

;;; Fresh PTV data for an opened row ;;;

(rf/reg-event-fx ::refresh-service-channel
  ;; The PTV tool's channels are as they were when the dialog opened; an
  ;; opened row refetches its own (one GET), so its review isn't as old.
  ;; `then`, an event, gets the location's publishing status once fetched
  ;; (:failed when it couldn't be).
  (fn [{:keys [db]} [_ org-id channel-id then]]
    (when (and org-id channel-id)
      (let [token (-> db :user :login :token)]
        {:fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/fetch-ptv-service-channel")
                :params {:org-id org-id :service-channel-id channel-id}
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::refresh-service-channel-success org-id channel-id then]
                :on-failure [::refresh-service-channel-failure org-id channel-id then]}]]}))))

(rf/reg-event-fx ::refresh-service-channel-success
  (fn [{:keys [db]} [_ org-id channel-id then resp]]
    (cond-> {:db (-> db
                     (assoc-in [:ptv :org org-id :data :service-channels channel-id] resp)
                     (update-in [:ptv :unreachable-channels] disj channel-id))}
      then (assoc :fx [[:dispatch (conj then (:publishingStatus resp))]]))))

(rf/reg-event-fx ::refresh-service-channel-failure
  ;; An archived location 404s: PTV no longer lists it, nothing to refresh.
  ;; Any other failure leaves the review on older data, which ① says.
  (fn [{:keys [db]} [_ _org-id channel-id then resp]]
    (cond-> {:db (if (= 404 (:status resp))
                   db
                   (update-in db [:ptv :unreachable-channels] (fnil conj #{}) channel-id))}
      then (assoc :fx [[:dispatch (conj then :failed)]]))))

(rf/reg-event-fx ::recheck-draft
  ;; "Tarkista uudelleen" on the notice of a draft in PTV, after the user
  ;; has published or discarded it there: the location fetched again (the
  ;; PTV tool's copy, or the site form's two), and the user told if the
  ;; draft is still there. Without it the copies stay as they were until
  ;; the row or the form is opened again.
  (fn [{:keys [db]} [_ {:keys [org-id channel-id lipas-id site-form?]}]]
    (let [then [::draft-rechecked channel-id]]
      {:db (update-in db [:ptv :rechecking] (fnil conj #{}) channel-id)
       :fx (if site-form?
             [[:dispatch [::fetch-site-service-channel lipas-id org-id channel-id then]]
              [:dispatch [:lipas.ui.map.events/fetch-ptv-site-review lipas-id]]]
             [[:dispatch [::refresh-service-channel org-id channel-id then]]])})))

(rf/reg-event-fx ::draft-rechecked
  ;; `status` is PTV's publishing status of the location now ("Modified"
  ;; while the draft is there), or :failed.
  (fn [{:keys [db]} [_ channel-id status]]
    (let [tr (:translator db)
          message (case status
                    "Modified" (tr :ptv.review/draft-still-there)
                    :failed (tr :ptv.review/recheck-failed)
                    nil)]
      (cond-> {:db (update-in db [:ptv :rechecking] disj channel-id)}
        message (assoc :fx [[:dispatch [:lipas.ui.events/set-active-notification
                                        {:message message :severity :warning}]]])))))

(rf/reg-event-fx ::republish
  ;; "Julkaise uudelleen" for a location archived in PTV: a sync re-publishes
  ;; the same location. With sync off it is turned on first, and the sync is
  ;; then the user's to start.
  (fn [_ [_ site]]
    (if (:sync-enabled site)
      {:fx [[:dispatch [::sync-with-review (:lipas-id site) (-> site :review :rows)]]]}
      {:fx [[:dispatch [::toggle-sync-enabled site true :persist? true]]]})))

;;; Double-link detection (single-site PTV tab) ;;;

(rf/reg-event-fx ::check-double-link
  ;; Ask the backend whether another sports-site is linked to the same PTV
  ;; service-location. The single-site view can't detect this locally (it loads
  ;; neither the org's channels nor its sibling sites).
  (fn [{:keys [db]} [_ lipas-id service-channel-id]]
    (let [token (-> db :user :login :token)]
      {:fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/check-ptv-service-channel-link")
              :params {:lipas-id lipas-id
                       :service-channel-id service-channel-id}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::check-double-link-success lipas-id]
              :on-failure [::check-double-link-failure lipas-id]}]]})))

(rf/reg-event-db ::check-double-link-success
  (fn [db [_ lipas-id resp]]
    (assoc-in db [:ptv :double-link-check lipas-id] (:other-sites resp))))

(rf/reg-event-db ::check-double-link-failure
  (fn [db [_ lipas-id _resp]]
    (update-in db [:ptv :double-link-check] dissoc lipas-id)))

;;; Linked channel for the single-site PTV tab ;;;

(rf/reg-event-fx ::fetch-site-service-channel
  ;; The single-site view loads none of the org's channels, but warning about
  ;; PTV texts the next sync would remove needs the linked one. `then` as
  ;; for ::refresh-service-channel.
  (fn [{:keys [db]} [_ lipas-id org-id service-channel-id then]]
    (let [token (-> db :user :login :token)]
      {:fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/fetch-ptv-service-channel")
              :params {:org-id org-id
                       :service-channel-id service-channel-id}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::fetch-site-service-channel-success lipas-id then]
              :on-failure [::fetch-site-service-channel-failure lipas-id then]}]]})))

(rf/reg-event-fx ::fetch-site-service-channel-success
  (fn [{:keys [db]} [_ lipas-id then resp]]
    (cond-> {:db (assoc-in db [:ptv :site-service-channel lipas-id] resp)}
      then (assoc :fx [[:dispatch (conj then (:publishingStatus resp))]]))))

(rf/reg-event-fx ::fetch-site-service-channel-failure
  ;; An archived channel (PTV 404s): nothing to warn about. Any other
  ;; failure keeps what an earlier fetch got.
  (fn [{:keys [db]} [_ lipas-id then resp]]
    (cond-> {:db (if (= 404 (:status resp))
                   (update-in db [:ptv :site-service-channel] dissoc lipas-id)
                   db)}
      then (assoc :fx [[:dispatch (conj then :failed)]]))))

(rf/reg-event-fx ::set-manual-services
  (fn [{:keys [db]} [_ org-id source-ids subcategories]]
    (let [x (into {} (map (juxt :source-id identity) subcategories))]
      {:db (assoc-in db [:ptv :org org-id :data :manual-services]
                     (reduce (fn [acc source-id]
                               (assoc acc source-id (get x source-id)))
                             {}
                             source-ids))})))

(comment

  (require '[re-frame.db])
  (-> re-frame.db/app-db
      deref
      (-get-ptv-org-id))
  ;; => "7b83257d-06ad-4e3b-985d-16a5c9d3fced"

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data :sports-sites]))

  (def sdefs
    (-> re-frame.db/app-db
        deref
        (get-in [:ptv :org ptv-data/uta-org-id-test :data])
        :service-candidates))

  (def sss
    (-> re-frame.db/app-db
        deref
        (get-in [:ptv :org ptv-data/uta-org-id-test :data])
        :services
        vals
        (->> (filter :sourceId)
             (map #(select-keys % [:sourceId :serviceNames])))))

  (for [{:keys [sub-category] :as m} (vals sdefs)]
    (assoc m :id (->> sss
                      vals
                      (some (fn [x]
                              (when (->> x
                                         :serviceNames
                                         (some (fn [y] (= (:value y) sub-category))))
                                (:id x)))))))

  (-> re-frame.db/app-db
      deref
      (get-in [:sports-sites :types]))

  (-> re-frame.db/app-db
      deref
      :user :login :token)

  (-> re-frame.db/app-db
      deref
      :ptv :services-creation)

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :sports-sites
      (get 506497)
      :ptv)

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :sports-sites
      vals
      (->>
        (group-by (fn [{:keys [ptv]}]
                    (if (:sync-enabled ptv)
                      :sync
                      :save)))))

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :service-channels
      (get "f0825665-edd3-4d9a-95a7-dacfe7a75f9b"))

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :service-channels
      vals
      (->>
        (map :id)))

  (rf/dispatch [::fetch-services {:id ptv-data/uta-org-id-test}])
  (rf/dispatch [::select-org nil]))

;; PTV Audit events

(rf/reg-event-db ::set-audit-sort
  (fn [db [_ v]]
    (assoc-in db [:ptv :audit :sort-by] v)))

(rf/reg-event-db ::select-audit-tab
  (fn [db [_ v]]
    (-> db
        (assoc-in [:ptv :audit :selected-tab] v)
        ;; The previous selection may not exist in the new tab's list —
        ;; keeping its form visible reads as the wrong tab's content.
        (update :ptv dissoc :selected-audit-site :selected-audit-service))))

;; Audit edits go into a draft ([:ptv :audit :site-draft/:service-draft]),
;; never into the fetched org data. The item lists and the whose-move bucket
;; tabs read that org data, so writing verdicts straight into it re-coloured
;; an item's status dot on the first radio click and could move the item to
;; another tab while its form was still open — auditors read that as "already
;; saved". The draft is seeded from the persisted record on the first edit
;; (keeping :audited-content and the backend metadata), replaced wholesale by
;; the server's response on save, and dropped on org switch.
;;
;; The draft's presence is also the dirty flag: every save appends a revision
;; and re-anchors the verdict snapshots to the current content, so the save
;; button requires actual input (or a stale verdict to re-confirm).

(defn- -site-persisted-audit
  [db lipas-id]
  (get-in db [:ptv :org (-get-ptv-org-id db)
              :data :site-audits lipas-id]))

(defn- -service-persisted-audit
  [db service-id]
  (get-in db [:ptv :org (-get-ptv-org-id db)
              :data :service-audits (str service-id)]))

(defn- -edit-audit-draft
  "Apply `f` to the draft at `draft-path`, seeding it from `persisted` when
  this is the first edit."
  [db draft-path persisted f]
  (assoc-in db draft-path (f (or (get-in db draft-path) persisted {}))))

(defn- -put-status
  [audit field status]
  (-> audit
      (assoc-in [field :status] status)
      ;; Initialize feedback to empty string if not present (schema requires it)
      (update-in [field :feedback] #(or % ""))))

(rf/reg-event-db ::update-audit-feedback
  (fn [db [_ lipas-id field value]]
    (-edit-audit-draft db
                       [:ptv :audit :site-draft lipas-id]
                       (-site-persisted-audit db lipas-id)
                       #(assoc-in % [field :feedback] value))))

(rf/reg-event-db ::update-audit-status
  (fn [db [_ lipas-id field status]]
    (-edit-audit-draft db
                       [:ptv :audit :site-draft lipas-id]
                       (-site-persisted-audit db lipas-id)
                       #(-put-status % field status))))

(rf/reg-event-fx ::save-ptv-audit
  (fn [{:keys [db]} [_ lipas-id audit-data contents]]
    ;; Validation strategy (defense in depth):
    ;; 1. UI input constraints (TextField maxLength)
    ;; 2. Save button state (::site-audit-data-valid? subscription using Malli)
    ;; 3. Backend validation (final safety check using same schema)
    ;; We rely on (2) to prevent invalid submissions, so no validation here.
    (let [token (-> db :user :login :token)]
      (if-not (seq audit-data)
        nil ; No audit data, do nothing
        {:db (assoc-in db [:ptv :audit :saving?] true)
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/save-ptv-audit")
                :params {:lipas-id lipas-id
                         ;; verdicts given now are anchored to the shown content
                         :audit (-> (select-keys audit-data [:summary :description])
                                    (ptv-data/anchor-audit-snapshots
                                      (-site-persisted-audit db lipas-id)
                                      contents))}
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::save-ptv-audit-success lipas-id]
                :on-failure [::save-ptv-audit-failure]}]]}))))

(rf/reg-event-fx ::save-ptv-audit-success
  (fn [{:keys [db]} [_ lipas-id resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/save-success)
                        :success? true}
          org-id (-get-ptv-org-id db)]
      {:db (-> db
               (assoc-in [:ptv :audit :saving?] false)
               (assoc-in [:ptv :org org-id :data :site-audits lipas-id] resp)
               (update-in [:ptv :audit :site-draft] dissoc lipas-id)
               ;; new audit activity re-arms the notification button
               (assoc-in [:ptv :audit :notification-sent?] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::save-ptv-audit-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/save-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :audit :saving?] false)
               (assoc-in [:ptv :errors :audit] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-db ::select-audit-site
  (fn [db [_ site]]
    (assoc-in db [:ptv :selected-audit-site] site)))

(rf/reg-event-fx ::send-audit-notification
  ;; Contents are derived by the backend at send time — the client only
  ;; names the org (see ::open-notification-dialog for the preview).
  (fn [{:keys [db]} [_ org-id]]
    (let [token (-> db :user :login :token)
                         ;; Ensure org-id is a UUID
          org-id (if (string? org-id) (uuid org-id) org-id)]
      {:db (assoc-in db [:ptv :audit :sending-notification?] true)
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/send-audit-notification")
              :params {:org-id org-id}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::send-notification-success]
              :on-failure [::send-notification-failure]}]]})))

(rf/reg-event-fx ::send-notification-success
  (fn [{:keys [db]} [_ result]]
    (let [sent-count (:sent result)
          tr (:translator db)
          message (if (pos? sent-count)
                    (str/replace (tr :ptv.audit/notification-sent-success) "%1$s" (str sent-count))
                    (tr :ptv.audit/no-recipients-found)) ; Assuming this key already exists or will be added
          notification {:message message
                        :success? (pos? sent-count)}]
      {:db (-> db
               (assoc-in [:ptv :audit :sending-notification?] false)
               (cond-> (pos? sent-count) (assoc-in [:ptv :audit :notification-sent?] true)))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::send-notification-failure
  (fn [{:keys [db]} [_ error]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/save-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :audit :sending-notification?] false)
               (assoc-in [:ptv :errors :send-notification] error))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

;; PTV Service audit events
;;
;; Service audits live at [:ptv :org <ptv-org-id> :data :service-audits]
;; keyed by (str service-id), mirroring the :services cache keyed by PTV
;; uuid; the subs join them with the live service list. (Audit drafts live
;; apart, under [:ptv :audit :service-draft].)

(rf/reg-event-fx ::fetch-ptv-service-audits
  (fn [{:keys [db]} [_ lipas-org]]
    (let [token (-> db :user :login :token)
          ptv-org-id (get-in lipas-org [:ptv-data :org-id])
          lipas-org-id (:id lipas-org)]
      (when (and ptv-org-id lipas-org-id)
        {:fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/fetch-ptv-service-audits")
                :params {:org-id (if (string? lipas-org-id) (uuid lipas-org-id) lipas-org-id)}
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::fetch-ptv-service-audits-success ptv-org-id]
                :on-failure [::fetch-ptv-service-audits-failure]}]]}))))

(rf/reg-event-db ::fetch-ptv-service-audits-success
  (fn [db [_ ptv-org-id resp]]
    (update-in db [:ptv :org ptv-org-id :data :service-audits]
               -merge-audits (into {} (map (juxt :service-id :audit)) resp))))

(rf/reg-event-fx ::fetch-ptv-service-audits-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (assoc-in db [:ptv :errors :service-audits] resp)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-db ::update-service-audit-status
  (fn [db [_ service-id field status]]
    (-edit-audit-draft db
                       [:ptv :audit :service-draft (str service-id)]
                       (-service-persisted-audit db service-id)
                       #(-put-status % field status))))

(rf/reg-event-db ::update-service-audit-feedback
  (fn [db [_ service-id field value]]
    (-edit-audit-draft db
                       [:ptv :audit :service-draft (str service-id)]
                       (-service-persisted-audit db service-id)
                       #(assoc-in % [field :feedback] value))))

(rf/reg-event-fx ::save-ptv-service-audit
  (fn [{:keys [db]} [_ {:keys [service-id source-id audit-data contents]}]]
    (let [token (-> db :user :login :token)
          lipas-org-id (get-in db [:ptv :selected-org :id])]
      (when (seq audit-data)
        {:db (assoc-in db [:ptv :audit :saving?] true)
         :fx [[:http-xhrio
               {:method :post
                :headers {:Authorization (str "Token " token)}
                :uri (str (:backend-url db) "/actions/save-ptv-service-audit")
                :params {:org-id (if (string? lipas-org-id) (uuid lipas-org-id) lipas-org-id)
                         :service-id (if (string? service-id) (uuid service-id) service-id)
                         :source-id source-id
                         :audit (-> (select-keys audit-data [:summary :description :user-instruction])
                                    (ptv-data/anchor-audit-snapshots
                                      (-service-persisted-audit db service-id)
                                      contents))}
                :format (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success [::save-ptv-service-audit-success (str service-id)]
                :on-failure [::save-ptv-audit-failure]}]]}))))

(rf/reg-event-fx ::save-ptv-service-audit-success
  (fn [{:keys [db]} [_ service-id resp]]
    (let [tr (:translator db)
          org-id (-get-ptv-org-id db)
          notification {:message (tr :notifications/save-success)
                        :success? true}]
      {:db (-> db
               (assoc-in [:ptv :audit :saving?] false)
               (assoc-in [:ptv :org org-id :data :service-audits service-id] resp)
               (update-in [:ptv :audit :service-draft] dissoc service-id)
               ;; new audit activity re-arms the notification button
               (assoc-in [:ptv :audit :service-notification-sent?] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-db ::select-audit-section
  (fn [db [_ v]]
    (-> db
        (assoc-in [:ptv :audit :selected-section] v)
        ;; see ::select-audit-tab — don't carry a selection across views
        (update :ptv dissoc :selected-audit-site :selected-audit-service))))

(rf/reg-event-db ::select-audit-service
  (fn [db [_ service]]
    (assoc-in db [:ptv :selected-audit-service] service)))

(rf/reg-event-fx ::send-service-audit-notification
  ;; Contents are derived by the backend at send time, like
  ;; ::send-audit-notification.
  (fn [{:keys [db]} [_ org-id]]
    (let [token (-> db :user :login :token)
          ;; Ensure org-id is a UUID
          org-id (if (string? org-id) (uuid org-id) org-id)]
      {:db (assoc-in db [:ptv :audit :sending-notification?] true)
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/send-service-audit-notification")
              :params {:org-id org-id}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::send-service-notification-success]
              :on-failure [::send-notification-failure]}]]})))

(rf/reg-event-fx ::send-service-notification-success
  (fn [{:keys [db]} [_ result]]
    (let [sent-count (:sent result)
          tr (:translator db)
          message (if (pos? sent-count)
                    (str/replace (tr :ptv.audit/notification-sent-success) "%1$s" (str sent-count))
                    (tr :ptv.audit/no-recipients-found))
          notification {:message message
                        :success? (pos? sent-count)}]
      {:db (-> db
               (assoc-in [:ptv :audit :sending-notification?] false)
               (cond-> (pos? sent-count) (assoc-in [:ptv :audit :service-notification-sent?] true)))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

;; Confirmation dialog shown before sending the audit notification: the
;; auditor sees the recipients (the org's PTV managers) and the derived
;; contents (which items await municipality fixes) before committing —
;; both come from the backend preview endpoint, which is the same
;; derivation the send endpoint runs.

(rf/reg-event-fx ::open-notification-dialog
  (fn [{:keys [db]} [_ org-id section]]
    (let [token (-> db :user :login :token)
          org-id (if (string? org-id) (uuid org-id) org-id)]
      {:db (assoc-in db [:ptv :audit :notification-dialog]
                     {:open? true :loading? true :recipients nil})
       :fx [[:http-xhrio
             {:method :post
              :headers {:Authorization (str "Token " token)}
              :uri (str (:backend-url db) "/actions/get-ptv-audit-notification-preview")
              :params {:org-id org-id
                       :section section}
              :format (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success [::fetch-notification-preview-success]
              :on-failure [::fetch-notification-preview-failure]}]]})))

(rf/reg-event-db ::fetch-notification-preview-success
  (fn [db [_ resp]]
    (update-in db [:ptv :audit :notification-dialog]
               merge {:loading? false
                      :recipients (vec (:recipients resp))
                      :action-items (vec (:action-items resp))
                      :approved-count (:approved-count resp)})))

(rf/reg-event-fx ::fetch-notification-preview-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)
          notification {:message (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (update-in [:ptv :audit :notification-dialog]
                          merge {:loading? false :recipients []})
               (assoc-in [:ptv :errors :notification-preview] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-db ::close-notification-dialog
  (fn [db _]
    (assoc-in db [:ptv :audit :notification-dialog] nil)))
