(ns lipas.backend.ptv.handler
  (:require [clojure.string :as str]
            [lipas.backend.db.db :as db]
            [lipas.backend.org :as org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.rate-limit :as rate-limit]
            [lipas.data.ptv :as ptv-data]
            [lipas.roles :as roles]
            [lipas.schema.ptv :as lipas-ptv-schema]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.ptv :as ptv-schema]
            [taoensso.timbre :as log]))

;; Schemas moved to lipas.schema.sports-sites.ptv

;;; PTV read access ;;;
;;
;; The PTV read endpoints are addressed by an ORGANISATION, so the gate has to
;; be scoped to the organisation the request actually names. The original check
;; only asked "does this caller hold :ptv/manage for any city they happen to
;; have?", which let a ptv-manager scoped to one municipality read every other
;; organisation's PTV data.
;;
;; The only concrete organisation -> municipality bridge in the data model is
;; the LIPAS org document's `[:ptv-data :city-codes]` (see
;; `lipas.schema.org/ptv-data`; `lipas.data.ptv/resolve-ptv-org-id` matches a
;; site against orgs through the same field). Org MEMBERSHIP cannot be used:
;; `:ptv-manager` is a hand-assignable, city-scoped role (`lipas.roles/roles`)
;; and real PTV managers are typically not members of the org whose PTV data
;; they maintain.
;;
;; The rule therefore is:
;;   - `:ptv/audit`  -> global read. Auditors review every organisation; this is
;;                     intentional and is what the pre-existing comment meant.
;;   - anyone else   -> must hold `:ptv/manage` for one of the municipalities
;;                     the named organisation's PTV config covers.
;;   - an `:org-id` that resolves to no LIPAS org -> denied (no permissive
;;                     fallthrough).
;;
;; Two different `:org-id` conventions are in play, so there is no single
;; predicate:
;;   - a PTV organisation UUID (fetch-ptv-org, fetch-ptv-services,
;;     fetch-ptv-service-channels, fetch-ptv-service-collections)
;;     -> `org/get-org-by-ptv-org-id`
;;   - the LIPAS org uuid (fetch-ptv-service-audits, same convention as the
;;     audit-notification endpoints) -> `org/get-org`
;; and `check-ptv-service-channel-link` names a sports-site instead, so it is
;; scoped to that site's municipality.

(defn- ->city-code
  "Normalise one city-code to a `long`.

  Both sides of the comparison arrive from JSON: role contexts come out of the
  JWT as `Long` (`roles/conform-roles`), while an org's `[:ptv-data
  :city-codes]` comes out of jsonb as `Integer`. Those two compare fine in
  Clojure, but a STRING never matches a number in a set lookup, which would
  turn this whole check into a silent deny-all. Normalising every value through
  here keeps that impossible. Returns nil for anything unparseable."
  [x]
  (cond
    (integer? x) (long x)
    (string? x) (parse-long (str/trim x))
    :else nil))

(defn- ptv-auditor?
  "Global PTV read: auditors review every organisation."
  [user]
  (roles/check-privilege user {} :ptv/audit))

(defn- manages-city?
  "True when `user` holds `:ptv/manage` for at least one of `city-codes`."
  [user city-codes]
  (boolean (some (fn [city-code]
                   (when-let [cc (->city-code city-code)]
                     (roles/check-privilege user {:city-code cc} :ptv/manage)))
                 city-codes)))

(defn- manages-org-ptv?
  "True when `user` holds `:ptv/manage` for a municipality that belongs to `org`
  (a LIPAS org map). A missing org, or an org with no PTV municipalities
  configured, grants nothing."
  [user org]
  (boolean (some->> org :ptv-data :city-codes (manages-city? user))))

(defn- deny
  "Logs why a PTV read was refused and returns false. Info level: PTV traffic is
  low volume and a wrongly scoped role is an operational question, not an error.
  Without this a mis-scoped role would just look like a broken feature. Logs the
  account id rather than the email — the id is enough to trace a report and
  keeps addresses out of the logs."
  [user what]
  (log/infof "PTV read denied for user %s: %s" (:id user) what)
  false)

(defn- org-read-access?
  "Shared body of the org-scoped read gate. `resolve-org` is called with no args
  and must return the LIPAS org the request names (or nil)."
  [user org-id resolve-org]
  (or (ptv-auditor? user)
      (if-let [org (resolve-org)]
        (or (manages-org-ptv? user org)
            (deny user (str "no :ptv/manage for any municipality of org " (:id org))))
        (deny user (str "org-id " (pr-str org-id) " does not resolve to a LIPAS org")))))

(defn ptv-org-read-access?
  "Gate for endpoints whose body `:org-id` is a PTV organisation UUID."
  [db]
  (fn [req]
    (let [org-id (-> req :parameters :body :org-id)]
      (org-read-access? (:identity req) org-id
                        #(org/get-org-by-ptv-org-id db org-id)))))

(defn lipas-org-read-access?
  "Gate for endpoints whose body `:org-id` is the LIPAS org uuid."
  [db]
  (fn [req]
    (let [org-id (-> req :parameters :body :org-id)]
      (org-read-access? (:identity req) org-id
                        #(org/get-org db org-id)))))

(defn site-ptv-read-access?
  "Gate for endpoints that name a sports-site (`:lipas-id`) rather than an org.
  Scoped to the site's own municipality, the same context site-level
  authorization uses elsewhere (`roles/site-roles-context`). An unknown
  lipas-id is denied."
  [db]
  (fn [req]
    (let [user (:identity req)
          lipas-id (-> req :parameters :body :lipas-id)]
      (or (ptv-auditor? user)
          (if-let [site (db/get-sports-site db lipas-id)]
            (or (manages-city? user [(-> site :location :city :city-code)])
                (deny user (str "no :ptv/manage for the municipality of site " lipas-id)))
            (deny user (str "lipas-id " (pr-str lipas-id) " not found")))))))

(defn ptv-org-write-access?
  "Gate for endpoints that WRITE an organisation's PTV data.

  Deliberately NOT `ptv-org-read-access?`. That gate short-circuits on
  `:ptv/audit`, which is correct for reading — auditors review every
  organisation — and would be a privilege ESCALATION here: `:ptv-auditor`
  carries only `:ptv/audit` and cannot write anything today, so reusing the read
  gate would silently hand every auditor write access to every org's PTV data.

  LIPAS admins are admitted explicitly. They already hold unrestricted
  `:ptv/manage`, so they pass the city-code check for any org that has PTV
  municipalities configured; the explicit branch only covers the degenerate case
  of an org whose `[:ptv-data :city-codes]` is empty, where nobody would
  otherwise be able to fix it."
  [db]
  (fn [req]
    (let [user (:identity req)
          org-id (-> req :parameters :body :org-id)]
      (or (roles/check-role user :admin)
          (if-let [org (org/get-org-by-ptv-org-id db org-id)]
            (or (manages-org-ptv? user org)
                (deny user (str "no :ptv/manage for any municipality of org "
                                (:id org) " (write)")))
            (deny user (str "org-id " (pr-str org-id)
                            " does not resolve to a LIPAS org (write)")))))))

(defn ptv-meta-write-access?
  "Gate for `/actions/save-ptv-meta`, whose body is `{lipas-id -> ptv-meta}` and
  therefore carries one `:org-id` per ENTRY rather than one for the request.

  EVERY org-id present must be writable. All-must-pass rather than some: with
  `some`, a caller could hide a foreign organisation inside an otherwise
  legitimate batch and have it written anyway."
  [db]
  (fn [req]
    (let [user (:identity req)
          org-ids (->> req :parameters :body vals (keep :org-id) distinct)]
      (or (roles/check-role user :admin)
          (if (empty? org-ids)
            (deny user "save-ptv-meta body carries no :org-id")
            (every? (fn [org-id]
                      (if-let [org (org/get-org-by-ptv-org-id db org-id)]
                        (or (manages-org-ptv? user org)
                            (deny user (str "no :ptv/manage for any municipality of org "
                                            (:id org) " (save-ptv-meta)")))
                        (deny user (str "org-id " (pr-str org-id)
                                        " does not resolve to a LIPAS org (save-ptv-meta)"))))
                    org-ids))))))

(defn ptv-feature-read-access?
  "Gate for `/actions/get-ptv-integration-candidates`.

  Deliberately NOT org-scoped, unlike the gates above. The endpoint is a fixed
  filter over the public sports-site search index — `/actions/search` serves the
  very same documents to anonymous callers — so a caller asking about a
  municipality it doesn't manage learns nothing it couldn't read
  unauthenticated, and there is nothing cross-tenant to protect here. Scoping it
  to the requested `:city-codes` would instead risk breaking legitimate use: the
  frontend sends the whole selected org's `[:ptv-data :city-codes]` vector,
  which for a multi-municipality org can be wider than any single
  ptv-manager's city scope. So this stays at 'holds :ptv/manage somewhere, or
  :ptv/audit' — the same strength as before, just stated honestly."
  [req]
  (let [user (:identity req)]
    (or (ptv-auditor? user)
        (roles/check-privilege user {:city-code ::roles/any} :ptv/manage))))

(defn routes [{:keys [db search ptv emailer] :as _ctx}]
  [""
   {#_#_:middleware [mw/token-auth mw/auth]
    :tags ["ptv"]
    :no-doc false}

   ["/actions/get-ptv-integration-candidates"
    {:post
     {:require-privilege ptv-feature-read-access?
      :parameters {:body [:map
                          [:city-codes [:vector :int]]
                          [:type-codes {:optional true} [:vector :int]]
                          [:owners [:vector :string]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/get-ptv-integration-candidates search (-> req :parameters :body))})}}]

   ["/actions/generate-ptv-descriptions"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      ;; One click of "Luo tekoälyllä" on one site = one Gemini call
      ;; (lipas.ui.ptv.events/generate-descriptions, dispatched only from
      ;; per-site buttons in views.cljs — never from a loop). The bulk path
      ;; is the -batch route below.
      ;;
      ;; 600/h is far above any hand-driven use (that would be 10 clicks a
      ;; minute, sustained). Set deliberately high: the point of this budget is
      ;; to stop a runaway retry loop or a scripted caller from running up an
      ;; unbounded bill, NOT to ration normal work. A limit that a real user can
      ;; reach costs more in support than it saves in tokens.
      :rate-limit {:key :user :window-ms rate-limit/hour-ms :max 600}
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          ;; The reference is a PEER SITE's already-saved PTV
                          ;; text (pick-reference-for-site), so it can never
                          ;; legitimately exceed PTV's own field limits — which
                          ;; ptv-meta already enforces on save. Mirroring them
                          ;; here bounds the prompt without inventing a number.
                          [:reference {:optional true}
                           [:maybe [:map
                                    [:summary [:string {:max ptv-data/max-summary-length}]]
                                    [:description [:string {:max ptv-data/max-description-length}]]]]]]}
      :handler
      (fn [req]
        (let [{:keys [lipas-id reference]} (-> req :parameters :body)]
          {:status 200
           :body (ptv-core/generate-ptv-descriptions
                   search lipas-id {:reference reference})}))}}]

   ["/actions/generate-ptv-descriptions-from-data"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      ;; Same per-site button, from the sports-site editor's PTV tab
      ;; (generate-descriptions-from-data). Body volume is already bounded by
      ;; the sports-site schema.
      :rate-limit {:key :user :window-ms rate-limit/hour-ms :max 600}
      :parameters {:body #'sports-sites-schema/new-or-existing-sports-site}
      :handler
      (fn [req]
        (let [body (-> req :parameters :body)
              reference (:reference body)
              doc (dissoc body :reference)]
          {:status 200
           :body (ptv-core/generate-ptv-descriptions-from-data
                   doc {:reference reference})}))}}]

   ["/actions/generate-ptv-descriptions-batch"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      ;; The bulk path: "Luo kuvaukset kaikille" walks the org's candidate
      ;; sites in batches of 10 (lipas.ui.ptv.events/batch-size), one request
      ;; per batch, sequentially, with up to 2 retries per batch.
      ;;
      ;; Measured worst case from the search index: the largest
      ;; municipality (city-code 91) has 2369 PTV-eligible sites, i.e. 237
      ;; batches for a full run. That run spans hours anyway — each call is a
      ;; whole Gemini completion (the FE allows it 240 s) and the queue is
      ;; strictly sequential — so this cannot be reached by a legitimate run,
      ;; while still capping a runaway retry loop or a scripted caller.
      ;;
      ;; At 3000/h the ceiling is effectively non-binding for the sequential
      ;; queue; it is a runaway backstop rather than a cost cap. That is the
      ;; intended trade here (see below).
      ;;
      ;; Deliberately generous: the FE HALTS the whole queue on any failure
      ;; (generate-descriptions-batch-failure sets :halt?), so a 429 mid-run
      ;; costs the manager the entire remaining pass.
      :rate-limit {:key :user :window-ms rate-limit/hour-ms :max 3000}
      :parameters {:body [:map
                          ;; One request = one Gemini call carrying every
                          ;; site's document, so the vector length IS the
                          ;; prompt size. The FE sends 10; 25 leaves room for
                          ;; tuning batch-size upward without a schema change,
                          ;; and bounds fan-out per request.
                          [:lipas-ids [:vector {:max 25} #'sports-sites-schema/lipas-id]]
                          [:reference {:optional true}
                           [:maybe [:map
                                    [:summary [:string {:max ptv-data/max-summary-length}]]
                                    [:description [:string {:max ptv-data/max-description-length}]]]]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/generate-ptv-descriptions-batch
                 search
                 (-> req :parameters :body))})}}]

   ["/actions/translate-to-other-langs"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      ;; Explicit per-site / per-service "Käännä muille kielille" button; one
      ;; press translates into every other org language in a single call. No
      ;; loop dispatches it.
      :rate-limit {:key :user :window-ms rate-limit/hour-ms :max 600}
      :parameters {:body [:map
                          ;; :from and :to are interpolated straight into the
                          ;; prompt (ai/translate-to-other-langs), so they get
                          ;; the tightest bound of anything here. Values come
                          ;; from the org's :supported-languages, which
                          ;; lipas.schema.org already restricts to
                          ;; fi/se/en — 10 chars is generous for a language
                          ;; code and leaves no room for an injected
                          ;; instruction. Kept as :string rather than an enum
                          ;; so an unexpected-but-harmless code can't turn a
                          ;; working translation into a 400.
                          [:from [:string {:min 1 :max 10}]]
                          [:to [:set {:max 5} [:string {:min 1 :max 10}]]]
                          ;; PTV's own per-field ceiling is 5000 chars
                          ;; (lipas.data.ptv, mirroring the API's 400s), and
                          ;; that is the bound used here for all three texts.
                          ;;
                          ;; NOT max-summary-length (150) for :summary on
                          ;; purpose: generation can overshoot 150 and the FE
                          ;; only clamps the RESULT
                          ;; (translate-site-descriptions-success), so an
                          ;; over-long draft summary is a normal editing state.
                          ;; Rejecting it here would break the translate step
                          ;; the user is trying to run, with a generic "haku
                          ;; epäonnistui" notification. PTV rejects the text at
                          ;; sync time, which is where that belongs.
                          [:summary [:string {:max ptv-data/max-description-length}]]
                          [:description [:string {:max ptv-data/max-description-length}]]
                          [:user-instruction {:optional true}
                           [:maybe [:string {:max ptv-data/max-user-instruction-length}]]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/translate-to-other-langs
                 (-> req :parameters :body))})}}]

   ["/actions/generate-ptv-service-descriptions"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      ;; Per-service-candidate button, plus a "generate all" loop over the
      ;; org's service candidates — one per LIPAS sub-category, of which there
      ;; are 28 (count of lipas.data.types/sub-categories). 600/h allows many
      ;; full runs per hour on top of ad-hoc single regenerations — sized to be
      ;; unreachable by hand, so it only catches a loop that has gone wrong.
      :rate-limit {:key :user :window-ms rate-limit/hour-ms :max 600}
      :parameters {:body [:map
                          ;; Every city-code widens the ES query that builds
                          ;; the prompt's aggregate overview. Finland has ~309
                          ;; municipalities, so 400 can't reject a real org's
                          ;; PTV config.
                          [:city-codes [:vector {:max 400} :int]]
                          [:sub-category-id :int]
                          ;; :overview replaces the ES-derived input entirely,
                          ;; i.e. it is caller-supplied prompt content. No FE
                          ;; call site sends it today (all pass nil); it exists
                          ;; for previewing unsaved data. Names are short
                          ;; labels (city / sub-category), and the facility
                          ;; list is one entry per site in the service — capped
                          ;; near the largest measured municipality's eligible
                          ;; site count (2369).
                          [:overview {:optional true
                                      :description "Use this to replace the AI input with non-saved site information"}
                           [:maybe
                            [:map
                             [:city-name (ptv-schema/localized-string-schema {:max 200})]
                             [:service-name (ptv-schema/localized-string-schema {:max 200})]
                             [:sports-facilties [:vector {:max 2500}
                                                 [:map
                                                  [:type [:string {:max 200}]]]]]]]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/generate-ptv-service-descriptions search (-> req :parameters :body))})}}]

   ["/actions/fetch-ptv-org"
    {:post
     {:require-privilege (ptv-org-read-access? db)
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-org ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-collections"
    {:post
     {:require-privilege (ptv-org-read-access? db)
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-service-collections ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/save-ptv-service"
    {:post
     {:require-privilege (ptv-org-write-access? db)
      :parameters {:body [:map
                          [:org-id :string]
                          [:city-codes [:vector :int]]
                          [:source-id {:optional true} [:maybe :string]]
                          [:sub-category-id {:optional true} [:maybe :int]]
                          [:languages [:vector [:enum "fi" "se" "en"]]]
                          [:summary (ptv-schema/localized-string-schema {:max 150})]
                          [:description (ptv-schema/localized-string-schema nil)]
                          [:user-instruction {:optional true} (ptv-schema/localized-string-schema nil)]
                          [:service-name {:optional true} [:maybe :string]]
                          [:service-id {:optional true} [:maybe :string]]]}
      :handler
      (fn [req]
        (try
          {:status 200
           :body (ptv-core/upsert-ptv-service! db ptv (:identity req) (-> req :parameters :body))}
          (catch clojure.lang.ExceptionInfo e
            (if-let [ptv-err (ptv-core/parse-ptv-error e)]
              (do
                (log/warnf "save-ptv-service failed (org: %s, source-id: %s, service-id: %s): %s"
                           (-> req :parameters :body :org-id)
                           (-> req :parameters :body :source-id)
                           (-> req :parameters :body :service-id)
                           (pr-str ptv-err))
                {:status 409
                 :body ptv-err})
              (throw e)))))}}]

   ["/actions/fetch-ptv-services"
    {:post
     {:require-privilege (ptv-org-read-access? db)
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-services ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channels"
    {:post
     {:require-privilege (ptv-org-read-access? db)
      :parameters {:body [:map
                          [:org-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-service-channels ptv (-> req :parameters :body :org-id))})}}]

   ["/actions/fetch-ptv-service-channel"
    {:post
     {:require-privilege (ptv-org-read-access? db)
      :parameters {:body [:map
                          [:org-id :string]
                          [:service-channel-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/fetch-ptv-service-channel ptv
                                                   (-> req :parameters :body :org-id)
                                                   (-> req :parameters :body :service-channel-id))})}}]

   ["/actions/check-ptv-service-channel-link"
    {:post
     {:require-privilege (site-ptv-read-access? db)
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          [:service-channel-id :string]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/check-service-channel-link
                 db
                 (select-keys (-> req :parameters :body) [:lipas-id :service-channel-id]))})}}]

   ["/actions/save-ptv-service-location"
    {:post
     {:require-privilege (ptv-org-write-access? db)
      :parameters {:body ptv-schema/create-ptv-service-location}
      :handler
      (fn [req]
        (try
          {:status 200
           :body (ptv-core/upsert-ptv-service-location! db ptv search (:identity req) (-> req :parameters :body))}
          (catch clojure.lang.ExceptionInfo e
            (cond
              ;; A different sports-site already owns this service-location.
              (= :double-link (:type (ex-data e)))
              (do
                (log/warnf "save-ptv-service-location rejected (double-link, lipas-id: %s): %s"
                           (-> req :parameters :body :lipas-id)
                           (pr-str (ex-data e)))
                {:status 409
                 :body (ex-data e)})

              (ptv-core/parse-ptv-error e)
              (let [ptv-err (ptv-core/parse-ptv-error e)]
                (log/warnf "save-ptv-service-location failed (lipas-id: %s, org: %s): %s"
                           (-> req :parameters :body :lipas-id)
                           (-> req :parameters :body :org-id)
                           (pr-str ptv-err))
                {:status 409
                 :body ptv-err})

              :else
              (throw e)))))}}]

   ["/actions/save-ptv-meta"
    {:post
     {:require-privilege (ptv-meta-write-access? db)
      :parameters {:body [:map-of :int #'ptv-schema/ptv-meta]}
      :handler
      (fn [req]
        (try
          {:status 200
           :body (ptv-core/save-ptv-integration-definitions db search (:identity req) (-> req :parameters :body))}
          (catch clojure.lang.ExceptionInfo e
            (if (= :double-link (:type (ex-data e)))
              (do
                (log/warnf "save-ptv-meta rejected (double-link): %s" (pr-str (ex-data e)))
                {:status 409
                 :body (ex-data e)})
              (throw e)))))}}]

   ["/actions/save-ptv-audit"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          [:audit #'ptv-schema/audit-data]]}
      :handler
      (fn [req]
        (let [body (-> req :parameters :body)]
          (if-let [result (ptv-core/save-ptv-audit db search (:identity req) body)]
            {:status 200 :body result}
            {:status 404 :body {:error "Sports site not found"}})))}}]

   ;; Notification contents (which items await municipality fixes) are
   ;; derived server-side both here and at send time — the client only
   ;; says which org and section, and the preview shows exactly what the
   ;; send will email.
   ["/actions/send-audit-notification"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map [:org-id :uuid]]}
      :handler
      (fn [req]
        (if-let [result (ptv-core/send-audit-notification!
                          db search emailer (-> req :parameters :body :org-id))]
          {:status 200 :body result}
          {:status 404 :body {:error "Organization not found"}}))}}]

   ["/actions/get-ptv-audit-notification-preview"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map
                          [:org-id :uuid]
                          [:section [:enum "sites" "services"]]]}
      :handler
      (fn [req]
        (let [{:keys [org-id section]} (-> req :parameters :body)]
          (if-let [result (ptv-core/get-audit-notification-preview
                            db search ptv org-id section)]
            {:status 200 :body result}
            {:status 404 :body {:error "Organization not found"}})))}}]

   ["/actions/fetch-ptv-service-audits"
    {:post
     {:require-privilege (lipas-org-read-access? db)
      :parameters {:body #'lipas-ptv-schema/fetch-service-audits-body}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/get-ptv-service-docs db (-> req :parameters :body :org-id))})}}]

   ["/actions/save-ptv-service-audit"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body #'lipas-ptv-schema/save-service-audit-body}
      :handler
      (fn [req]
        (let [body (-> req :parameters :body)]
          (if-let [result (ptv-core/save-ptv-service-audit db ptv (:identity req) body)]
            {:status 200 :body result}
            {:status 404 :body {:error "Service not found"}})))}}]

   ["/actions/send-service-audit-notification"
    {:post
     {:require-privilege :ptv/audit
      :parameters {:body [:map [:org-id :uuid]]}
      :handler
      (fn [req]
        (if-let [result (ptv-core/send-service-audit-notification!
                          db ptv emailer (-> req :parameters :body :org-id))]
          {:status 200 :body result}
          {:status 404 :body {:error "Organization not found"}}))}}]])
