(ns lipas.backend.ptv.handler
  (:require [clojure.string :as str]
            [lipas.backend.db.db :as db]
            [lipas.backend.org :as org]
            [lipas.backend.ptv.core :as ptv-core]
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
      :parameters {:body [:map
                          [:lipas-id #'sports-sites-schema/lipas-id]
                          [:reference {:optional true}
                           [:maybe [:map
                                    [:summary :string]
                                    [:description :string]]]]]}
      :handler
      (fn [req]
        (let [{:keys [lipas-id reference]} (-> req :parameters :body)]
          {:status 200
           :body (ptv-core/generate-ptv-descriptions
                   search lipas-id {:reference reference})}))}}]

   ["/actions/generate-ptv-descriptions-from-data"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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
      :parameters {:body [:map
                          [:lipas-ids [:vector #'sports-sites-schema/lipas-id]]
                          [:reference {:optional true}
                           [:maybe [:map
                                    [:summary :string]
                                    [:description :string]]]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/generate-ptv-descriptions-batch
                 search
                 (-> req :parameters :body))})}}]

   ["/actions/translate-to-other-langs"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:from :string]
                          [:to [:set :string]]
                          [:summary :string]
                          [:description :string]
                          [:user-instruction {:optional true} [:maybe :string]]]}
      :handler
      (fn [req]
        {:status 200
         :body (ptv-core/translate-to-other-langs
                 (-> req :parameters :body))})}}]

   ["/actions/generate-ptv-service-descriptions"
    {:post
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
      :parameters {:body [:map
                          [:city-codes [:vector :int]]
                          [:sub-category-id :int]
                          [:overview {:optional true
                                      :description "Use this to replace the AI input with non-saved site information"}
                           [:maybe
                            [:map
                             [:city-name (ptv-schema/localized-string-schema nil)]
                             [:service-name (ptv-schema/localized-string-schema nil)]
                             [:sports-facilties [:vector
                                                 [:map
                                                  [:type :string]]]]]]]]}
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
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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
     {:require-privilege [{:city-code ::roles/any} :ptv/manage]
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
