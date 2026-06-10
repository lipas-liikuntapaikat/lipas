(ns lipas.backend.core
  (:require [buddy.hashers :as hashers]
            [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [next.jdbc :as next-jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as excel]
            [lipas.backend.api.v1.locations :as legacy-locations]
            [lipas.backend.api.v1.sports-place :as legacy-sports-place]
            [lipas.backend.api.v1.transform :as legacy-transform]
            [lipas.backend.accessibility :as accessibility]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.analysis.reachability :as reachability]
            [lipas.backend.db.db :as db]
            [lipas.backend.email :as email]
            [lipas.backend.geom-utils :refer [feature-coll->geom-coll]]
            [lipas.backend.gis :as gis]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.newsletter :as newsletter]
            [lipas.backend.org :as org]
            [lipas.backend.search :as search]
            [lipas.data-model-export :as data-model-export]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.owners :as owners]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.i18n.core :as i18n]
            [lipas.integration.utp.cms :as utp-cms]
            [lipas.jobs.core :as jobs]
            [lipas.reports :as reports]
            [lipas.roles :as roles]
            [lipas.utils :as utils]
            [taoensso.timbre :as log])
  (:import
    [java.io OutputStreamWriter]))

(def cache "Simple atom cache for things that (hardly) never change."
  (atom {}))

(def cities (utils/index-by :city-code cities/all))
(def types types/all)
(def admins admins/all)
(def owners owners/all)

;;; Jobs ;;;

(defn get-job-admin-metrics
  "Get comprehensive job queue metrics for admin dashboard."
  [db opts]
  (jobs/get-admin-metrics db opts))

(defn get-job-queue-health
  "Get current job queue health for admin monitoring."
  [db]
  (jobs/get-queue-health db))

;;; User ;;;

(defn username-exists? [db user]
  (some? (db/get-user-by-username db user)))

(defn email-exists? [db user]
  (some? (db/get-user-by-email db user)))

(defn add-user! [db user]
  (when (username-exists? db user)
    (throw (ex-info "Username is already in use!"
                    {:type :username-conflict})))

  (when (email-exists? db user)
    (throw (ex-info "Email is already in use!"
                    {:type :email-conflict})))

  (let [defaults {:permissions {:roles []}
                  :status "active"
                  :username (:email user)
                  :user-data {}
                  :password (str (utils/gen-uuid))}
        user (-> (merge defaults user)
                 (update :password hashers/encrypt))]

    (db/add-user! db user)
    {:status "OK"}))

(defn- add-user-event!
  ([db user evt-name]
   (add-user-event! db user evt-name {}))
  ([db user evt-name data]
   (let [user (db/get-user-by-id db user)
         defaults {:event-date (utils/timestamp) :event evt-name}
         evt (merge defaults data)
         user (update-in user [:history :events] conj evt)]
     (db/update-user-history! db user))))

(defn login! [db user]
  (add-user-event! db user "login"))

(defn register! [db emailer user]
  (add-user! db user)
  (email/send-register-notification! emailer
                                     "lipasinfo@jyu.fi"
                                     (dissoc user :password))
  {:status "OK"})

(defn publish-users-drafts! [db {:keys [id] :as user}]
  (let [drafts (->> (db/get-users-drafts db user)
                    (filter (fn [draft]
                              (roles/check-privilege user (roles/site-roles-context draft) :site/create-edit))))]
    (log/info "Publishing" (count drafts) "drafts from user" id)
    (doseq [draft drafts]
      (db/upsert-sports-site! db user (assoc draft :status "active")))))

(defn get-user [db identifier]
  (or (db/get-user-by-email db {:email identifier})
      (db/get-user-by-username db {:username identifier})
      (db/get-user-by-id db {:id identifier})))

(defn get-user! [db identifier]
  (if-let [user (get-user db identifier)]
    user
    (throw (ex-info "User not found."
                    {:type :user-not-found}))))

(defn get-users [db]
  (db/get-users db))

(defn create-magic-link [url user]
  (let [token (jwt/create-token user :terse? true :valid-seconds (* 7 24 60 60))]
    {:link (str url "?token=" token)
     :valid-days 7}))

(defn- send-permissions-updated-email!
  [emailer login-url {:keys [email] :as user}]
  (let [link (create-magic-link login-url user)]
    (email/send-permissions-updated-email! emailer email link)))

(defn update-user-permissions!
  [db emailer {:keys [id permissions login-url]}]
  (let [user (get-user! db id)
        old-perms (-> user :permissions)
        new-user (assoc user :permissions permissions)]
    (db/update-user-permissions! db new-user)
    (publish-users-drafts! db new-user)
    (send-permissions-updated-email! emailer login-url user)
    (add-user-event! db new-user "permissions-updated"
                     {:from old-perms :to permissions})))

(defn update-user-status!
  [db {:keys [id status] :as user}]
  (let [user (db/get-user-by-id db user)
        old-status (-> user :status)
        new-user (assoc user :status status)]
    (db/update-user-status! db new-user)
    (add-user-event! db new-user "status-changed"
                     {:from old-status :to status})
    new-user))

(defn send-password-reset-link! [db emailer {:keys [email reset-url]}]
  (if-let [user (db/get-user-by-email db {:email email})]
    (let [params (create-magic-link reset-url user)]
      (email/send-reset-password-email! emailer email params)
      (add-user-event! db user "password-reset-link-sent"))
    (throw (ex-info "User not found" {:type :email-not-found}))))

(defn send-magic-link! [db emailer {:keys [user login-url variant]}]
  (let [email (-> user :email)
        magic-link (create-magic-link login-url user)]
    (email/send-magic-login-email! emailer email variant magic-link)
    (add-user-event! db user "magic-link-sent")))

(defn reset-password! [db user password]
  (db/reset-user-password! db (assoc user :password
                                     (hashers/encrypt password)))
  (add-user-event! db user "password-reset"))

;;; Reminders ;;;

(defn get-users-pending-reminders! [db {:keys [id]}]
  (db/get-users-pending-reminders db id))

(defn add-reminder! [db user m]
  (let [m (assoc m :status "pending" :account-id (:id user))]
    (db/add-reminder! db m)))

(defn update-reminder-status! [db user {:keys [id] :as params}]
  (let [exists (->> user
                    (get-users-pending-reminders! db)
                    (map :id)
                    (some #{id}))]

    (when-not exists
      (throw (ex-info "Reminder not found" {:type :reminder-not-found})))

    (db/update-reminder-status! db params)
    {:status "OK"}))

(defn update-user-data!
  [db user user-data]
  (db/update-user-data! db (assoc user :user-data user-data))
  user-data)

(defn gdpr-remove-user!
  "Removes personal data associated with the user and archives the user."
  [db user]
  (jdbc/with-db-transaction [tx db]
    (let [username (str "gdpr_removed_" (java.util.UUID/randomUUID))
          email (str username "@lipas.fi")]
      (db/update-user-username! tx (assoc user :username username))
      (db/update-user-email! tx (assoc user :email email))
      (update-user-data! tx user {})
      (add-user-event! tx user "GDPR removal")
      (update-user-status! tx (assoc user :status "archived"))))
  {:status "OK"})

(defn gdpr-remove?
  "User data is removed if the user has been inactive for > 5 years."
  [now {:keys [created-at history] :as user}]
  (let [created-at+5y (-> (.toInstant created-at)
                          (.atZone (java.time.ZoneId/of "UTC"))
                          (.plus 5 java.time.temporal.ChronoUnit/YEARS)
                          (.toInstant))]
    (and (not (str/ends-with? (:email user) "@lipas.fi"))
         (.isAfter now created-at+5y)
         (let [last-event (->> history :events (map :event-date) (sort utils/reverse-cmp) first)]
           (or (nil? last-event)
               (let [last-event+5y (-> (java.time.Instant/parse last-event)
                                       (.atZone (java.time.ZoneId/of "UTC"))
                                       (.plus 5 java.time.temporal.ChronoUnit/YEARS)
                                       (.toInstant))]
                 (.isAfter now last-event+5y)))))))

(defn process-gdpr-removals!
  [db]
  (let [now (java.time.Instant/now)
        users (->> (get-users db)
                   (filter (partial gdpr-remove? now)))]
    (log/info "Found" (count users) "users to GDPR remove.")

    (doseq [user users]
      (log/info "GDPR removing user" (:id user))
      (log/info (:created-at user)
                (->> user :history :events (map :event-date) (sort utils/reverse-cmp) first))
      #_(gdpr-remove-user! db user))

    (log/info "GDPR removals DONE!")))

;;; Sports-sites ;;;

(defn- deref-fids
  [sports-site route]
  (let [fids (-> route :fids set)
        geoms (when (seq fids)
                {:type "FeatureCollection"
                 :features (->> (get-in sports-site [:location :geometries :features])
                                (filterv #(contains? fids (:id %))))})]
    (if geoms
      (assoc route :geometries geoms)
      route)))

(defn enrich-activities
  [sports-site]
  (if (:activities sports-site)
    (update sports-site :activities
            (fn [activities]
              (reduce-kv (fn [m k v]
                           (if (and (get-in m [k :routes])
                                    (get-in sports-site [:activities k :routes]))
                             (update-in m [k :routes] (fn [routes]
                                                        (mapv #(deref-fids sports-site %) routes)))
                             m))
                         activities
                         activities)))
    sports-site))

(defn get-sports-site
  ([db lipas-id] (get-sports-site db lipas-id :none))
  ([db lipas-id locale]
   (let [m (-> (db/get-sports-site db lipas-id)
               (enrich-activities))]
     (cond
       (#{:fi :en :se} locale) (i18n/localize locale m)
       (#{:all} locale) (i18n/localize2 [:fi :se :en] m)
       :else m))))

(defn get-sports-site2
  ([search lipas-id] (get-sports-site2 search lipas-id :none))
  ([{:keys [client indices]} lipas-id locale]
   (let [idx (get-in indices [:sports-site :search])
         doc (try
               (search/fetch-document client idx lipas-id)
               (catch Exception ex
                 (if (= 404 (-> ex ex-data :status))
                   nil
                   (throw ex))))
         m (some-> doc
                   (get-in [:body :_source])
                   (enrich-activities))]
     (cond
       (nil? m) m
       (#{:fi :en :se} locale) (i18n/localize locale m)
       (#{:all} locale) (i18n/localize2 [:fi :se :en] m)
       :else m))))

(defn- new? [sports-site]
  (nil? (:lipas-id sports-site)))

(defn- check-permissions! [user sports-site draft?]
  (let [ctx (roles/site-roles-context sports-site)]
    (when-not (or draft?
                  (new? sports-site)
                  ;; Save endpoint gate: a general editor (:site/create-edit) OR an
                  ;; aspect-specific editor (:site/save-api — activities/floorball/itrs)
                  ;; may persist a change. The OR lets general editors carry just
                  ;; create-edit (no redundant save-api); see lipas.roles/basic.
                  (roles/check-privilege user ctx :site/save-api)
                  (roles/check-privilege user ctx :site/create-edit))
      (throw (ex-info "User doesn't have enough permissions!"
                      {:type :no-permission})))))

;; Moved to lipas.data.owners so the FE form can derive the same locked :owner
;; when an org is selected as a site's owner. Aliased here for existing callers.
(def org-type->owner owners/org-type->owner)

(defn check-owner-lock!
  "When a site is org-owned, its :owner enum is locked to the value implied by
  the owning org's :type. Throws :owner-locked if the site's :owner diverges.
  No-op for non-org-owned (legacy) sites — zero cost on the common path."
  [db {:keys [owner owner-org-id] :as _sports-site}]
  (when owner-org-id
    (when-let [expected (some-> (org/get-org db owner-org-id) :type org-type->owner)]
      (when (and owner (not= owner expected))
        (throw (ex-info (str "Site :owner is locked to '" expected
                             "' because the site is owned by an organization of type")
                        {:type :owner-locked :expected expected :got owner
                         :owner-org-id (str owner-org-id)}))))))

;;; --- Ownership & edit-grant authorization (business rules) ------------------
;;; Single source of truth for WHO may change a site's `:owner-org-id` and
;;; `:edit-grants`. These two fields are security-sensitive (they decide cross-org
;;; edit access), yet they ride on the same document as ordinary content, so the
;;; generic save endpoint could otherwise be used to grant oneself access. Pure
;;; predicates over the acting user and the site's CURRENT (stored) ownership —
;;; never the submitted body. Consulted by both the save path
;;; (`upsert-sports-site!`) and the dedicated grant/revoke endpoints, so the rule
;;; lives in exactly one place (unit-tested in business-logic-test).

(defn lipas-admin?
  "True if the user holds the global :users/manage privilege (LIPAS admin)."
  [user]
  (roles/check-privilege user {} :users/manage))

(defn owns-site-org?
  "True if the user is an admin (:org/manage) of the org `owner-org-id` — i.e. an
  admin of the org that owns the site."
  [user owner-org-id]
  (boolean
    (and owner-org-id
         (roles/check-privilege user {:org-id #{(str owner-org-id)}} :org/manage))))

(defn ownership-change-authorized?
  "May `user` move a site's owner-org from `prev` to `next`?
   - unchanged          → always ok (no-op)
   - creating a site    → may claim an org they can `:site/create-edit` on (or none)
   - existing site change → LIPAS admin only; everyone else uses the take-over flow."
  [user creating? prev next]
  (cond
    (= (some-> prev str) (some-> next str)) true
    creating? (or (nil? next)
                  (roles/check-privilege user {:org-id #{(str next)}} :site/create-edit))
    :else (lipas-admin? user)))

(defn edit-grant-change-authorized?
  "May `user` change a site's `:edit-grants`? Only a LIPAS admin or an admin of
  the org that owns the site (`owner-org-id`)."
  [user owner-org-id]
  (or (lipas-admin? user)
      (owns-site-org? user owner-org-id)))

(defn- check-sports-site-exists! [db lipas-id]
  (when (empty? (db/get-sports-site db lipas-id))
    (throw (ex-info "Sports site not found"
                    {:type :sports-site-not-found
                     :lipas-id lipas-id}))))

(defn upsert-sports-site!*
  "Should be used only when data is from trusted sources (migrations
  etc.). Doesn't check users permissions or if lipas-id exists."
  ([db user sports-site]
   (upsert-sports-site!* db user sports-site false))
  ([db user sports-site draft?]
   (db/upsert-sports-site! db user sports-site draft?)))

(defn ensure-permission!
  "Checks if user has access to sports-site and if not explicit access
  is added to users permissions.

  Motivation is to ensures that user who creates the sports-site has
  permission to it."
  [db user {:keys [lipas-id status] :as sports-site}]
  (let [regular-permission (roles/check-privilege user (roles/site-roles-context sports-site) :site/create-edit)
        is-planning (= "planning" status)
        planning-permission (and is-planning (roles/check-privilege user {} :analysis-tool/use))]
    (when (and (not regular-permission)
               (not planning-permission))
      (let [user (update-in user [:permissions :roles]
                            (fnil conj [])
                            {:role :site-manager
                             :lipas-id #{lipas-id}})]
        (db/update-user-permissions! db user)))))

(defn upsert-sports-site!
  ([db user sports-site]
   (upsert-sports-site! db user sports-site false))
  ([db user sports-site draft?]
   (let [lipas-id  (:lipas-id sports-site)
         ;; Authoritative current state. nil ⇒ no such revision (a create, or an
         ;; edit of a not-yet-existing id — the latter 404s after authz below).
         stored    (when lipas-id (not-empty (get-sports-site db lipas-id)))
         creating? (nil? stored)
         ;; The server owns :owner-org-id / :edit-grants: when the submitted
         ;; body OMITS the key entirely (clients that don't round-trip them —
         ;; integrations, GET-modify-POST scripts), carry the stored value
         ;; forward instead of treating absence as removal. An explicit nil
         ;; (key present) is a genuine removal attempt and is authorized below.
         sports-site (if stored
                       (merge (select-keys stored [:owner-org-id :edit-grants])
                              sports-site)
                       sports-site)]
     ;; 1. Content-edit permission. For an existing site the privilege must hold
     ;;    for BOTH the stored revision (a scoped editor can't touch sites
     ;;    outside their scope, and org-owned-site editors keep edit rights via
     ;;    the stored :owner-org-id even when the body changes content) AND the
     ;;    submitted document (the editor can't relocate/retype a site out of —
     ;;    or into — a scope they don't hold). Note the body is checked AFTER
     ;;    the carry-forward above, so injected :owner-org-id/:edit-grants can't
     ;;    grant access (any change to them is separately authorized in step 2).
     ;;    For a create (no stored revision) the body alone is checked,
     ;;    preserving the legacy permission check (and its 403-before-404
     ;;    ordering) for a posted-but-missing lipas-id.
     (check-permissions! user (or stored sports-site) draft?)
     (when stored
       (check-permissions! user sports-site draft?))
     ;; 2. Ownership & edit-grant invariants: a body that changes either
     ;;    security-sensitive field must be authorized to do so (business rule).
     (let [prev-owner  (:owner-org-id stored)
           next-owner  (:owner-org-id sports-site)
           prev-grants (set (map str (:edit-grants stored)))
           next-grants (set (map str (:edit-grants sports-site)))]
       (when-not (ownership-change-authorized? user creating? prev-owner next-owner)
         (throw (ex-info "Not authorized to change site ownership"
                         {:type :no-permission :reason :ownership-change
                          :prev-owner-org-id (some-> prev-owner str)
                          :next-owner-org-id (some-> next-owner str)})))
       (when (and (not= prev-grants next-grants)
                  (not (edit-grant-change-authorized? user prev-owner)))
         (throw (ex-info "Not authorized to change site edit-grants"
                         {:type :no-permission :reason :edit-grant-change}))))
     ;; 3. An edit (lipas-id given) of a site that doesn't exist ⇒ 404 — after
     ;;    authz, matching the pre-existing ordering.
     (when (and lipas-id creating?)
       (check-sports-site-exists! db lipas-id))
     (check-owner-lock! db sports-site)
     (let [resp (upsert-sports-site!* db user sports-site draft?)]
       (when creating?
         (ensure-permission! db user resp))
       resp))))

(declare index! org-names)

(defn- set-site-edit-grants!
  "Append a site revision setting :edit-grants to `grants`, acting on behalf of
  `acting-org-id`, and reindex. Trusted path — the caller authorizes (owner-org
  admin only)."
  [db search user lipas-id grants acting-org-id]
  (let [site    (get-sports-site db lipas-id)
        updated (assoc site
                       ;; fresh timestamp — reusing the stored :event-date would
                       ;; collide with the previous revision (FE keys history by
                       ;; event-date) and misdate the grant in the event log
                       :event-date (utils/timestamp)
                       :edit-grants (vec (distinct (map str grants)))
                       :acting-org-id (some-> acting-org-id str))
        resp    (upsert-sports-site!* db user updated)]
    (index! search resp false (org-names db))
    resp))

(defn- check-edit-grant-authorized!
  "Authorize a grant/revoke against the site's CURRENT owner (business rule):
  only a LIPAS admin or an admin of the owning org may change edit-grants."
  [user site]
  (when-not (edit-grant-change-authorized? user (:owner-org-id site))
    (throw (ex-info "Not authorized to change edit access on this site"
                    {:type :no-permission :reason :edit-grant-change
                     :lipas-id (:lipas-id site)}))))

(defn- get-existing-site!
  "Current revision of `lipas-id`, or throw :site-not-found (404). Guards the
  grant/revoke paths from upserting a fragment revision for an unknown id
  (which would allocate a fresh lipas-id from the sequence and blow up on a
  NOT NULL constraint)."
  [db lipas-id]
  (or (not-empty (get-sports-site db lipas-id))
      (throw (ex-info "Site not found"
                      {:type :site-not-found :lipas-id lipas-id}))))

(defn grant-site-edit!
  "Grant another org edit access to a site (cross-org collaboration). Only the
  owning org's admin (or a LIPAS admin) may grant — enforced here, not in the
  route, so every caller goes through the same rule."
  [db search user lipas-id grantee-org-id acting-org-id]
  (let [site   (get-existing-site! db lipas-id)
        _      (check-edit-grant-authorized! user site)
        grants (conj (set (map str (:edit-grants site))) (str grantee-org-id))]
    (set-site-edit-grants! db search user lipas-id grants acting-org-id)))

(defn revoke-site-edit!
  "Revoke an org's edit access to a site. Same authority as granting."
  [db search user lipas-id grantee-org-id acting-org-id]
  (let [site   (get-existing-site! db lipas-id)
        _      (check-edit-grant-authorized! user site)
        grants (disj (set (map str (:edit-grants site))) (str grantee-org-id))]
    (set-site-edit-grants! db search user lipas-id grants acting-org-id)))

(defn email-registered?
  "True iff an account exists for `email`. Returns ONLY a boolean — never the
  account — so it can back the invite-form existence check without exposing user
  data (GDPR). Reachable only through the org-scoped invite flow, never openly."
  [db email]
  (boolean (db/get-user-by-email db {:email email})))

(defn invite-org-member!
  "Org-admin invite (separate from the lipas-admin user-management plane). The
  assignment is hard-validated against the org's catalog BEFORE any side effect.
  If the email has no account, one is created (active, random password) and the
  invitee is emailed a magic login link. The member is added to the org document
  with the (catalog-bounded) assignment. Never touches account.permissions.roles.
  Returns {:user-id .. :new-account? bool}."
  [db emailer org-id {:keys [email] :as assignment} author-id login-url]
  (let [org (org/get-org db org-id)]
    ;; Ceiling check first — reject out-of-catalog assignments before creating
    ;; anything (the structural guarantee is in derive-org-roles; this is early UX).
    (org/validate-assignment! org assignment)
    (let [existing (db/get-user-by-email db {:email email})]
      ;; An invite must never silently REPLACE an existing member's roles (the
      ;; form defaults to [], so a re-invite would demote e.g. an org-admin to a
      ;; plain member). Reject; role changes go through set-member-roles!.
      (when (and existing
                 (some #(= (str (:id existing)) (str (:user-id %))) (:members org)))
        (throw (ex-info "User is already a member of this organization"
                        {:type   :already-member
                         :email  email
                         :org-id (str org-id)})))
      (let [new?     (nil? existing)
            _        (when new? (add-user! db {:email email :username email}))
            user     (or existing (db/get-user-by-email db {:email email}))
            org-name (:name org)]
        (org/add-member! db org-id (:id user) assignment author-id)
        ;; Both cases get an email: new accounts a magic login link to set a password;
        ;; existing accounts a plain "you've been added to org X" notification. The
        ;; membership is already committed, so a delivery failure must NOT fail the
        ;; whole operation (which would leave a member added but report an error).
        ;; Report delivery status instead; a new account without its link can still
        ;; reset its password / be re-invited.
        (let [email-sent?
              (try
                (let [magic (create-magic-link login-url user)]
                  (if new?
                    (email/send-org-invitation-email!
                      emailer email (assoc magic :org-name org-name))
                    ;; Existing accounts also get a magic link (one-click login that
                    ;; lands them already authenticated, with the fresh token carrying
                    ;; the new org role) — mirrors send-permissions-updated-email!.
                    (email/send-org-added-email!
                      emailer email (assoc magic :org-name org-name))))
                true
                (catch Exception e
                  (log/error e "Failed to send org-invitation email"
                             {:email email :org-id (str org-id) :new-account? new?})
                  false))]
          {:user-id (str (:id user)) :new-account? new? :email-sent? email-sent?})))))

(defn get-sports-sites-by-type-code
  ([db type-code]
   (get-sports-sites-by-type-code db type-code {}))
  ([db type-code {:keys [locale] :as opts}]
   (let [data (->> (db/get-sports-sites-by-type-code db type-code opts)
                   (map enrich-activities))]
     (cond
       (#{:fi :en :se} locale) (map (partial i18n/localize locale) data)
       (#{:all} locale) (map (partial i18n/localize2 [:fi :se :en]) data)
       :else data))))

(defn get-sports-site-history [db lipas-id]
  (->> (db/get-sports-site-history db lipas-id)
       (map (fn [sports-site]
              (let [metadata (meta sports-site)]
                (-> sports-site
                    (assoc :author (:author-id metadata))
                    (assoc :doc-status (:doc-status metadata))))))))

;; ES doesn't support indexing FeatureCollections
;; NOTE: feature-coll->geom-coll moved to lipas.backend.geom-utils to avoid circular dependency

(defn feature-type
  [sports-site]
  (-> sports-site :location :geometries :features first :geometry :type))

;; Elasticsearch doesn't like Polygons with consequent duplicate
;; coordinates or self-intersecting polygons so we fix them here.
;; Multimethod was added because there probably will be similar issues
;; with LineStrings once we find them out.
(defmulti fix-geoms feature-type)
(defmethod fix-geoms :default [sports-site] sports-site)
(defmethod fix-geoms "Polygon" [sports-site]
  (update-in sports-site [:location :geometries]
             (comp gis/repair-self-intersecting-polygon
                   gis/dedupe-polygon-coords)))

(defn compute-renovations
  "Materializes :renovations from legacy :renovation-years entries. For
  each year in :renovation-years not already covered by a :renovations
  entry (any type), synthesizes {:year y :type \"major-renovation\"}.
  Lets legacy data appear in the structured renovations UI without a
  one-off DB migration."
  [sports-site]
  (let [covered-years (->> (:renovations sports-site) (map :year) set)
        synthesized (->> (:renovation-years sports-site)
                         (remove covered-years)
                         (map (fn [y] {:year y :type "major-renovation"})))
        merged (->> (concat (:renovations sports-site) synthesized)
                    (sort-by :year)
                    vec)]
    (cond-> sports-site
      (seq merged)
      (assoc :renovations merged))))

(defn compute-renovation-years
  "Computes :renovation-years by merging existing values with years from
  \"major-renovation\" entries in :renovations. Used for backwards
  compatibility so that old API consumers still see renovation years
  derived from the new structured renovations data. Pairs with
  compute-renovations as the inverse direction."
  [sports-site]
  (let [major-renovation-years (->> (:renovations sports-site)
                                    (filter #(= "major-renovation" (:type %)))
                                    (map :year))
        computed (->> (concat (:renovation-years sports-site)
                              major-renovation-years)
                      distinct
                      sort
                      vec)]
    (cond-> sports-site
      (seq computed)
      (assoc :renovation-years computed))))

(defn org-names
  "org-id (string) → org name for every org. Used to denormalize the owner
  org's name into :search-meta on (re)index so anonymous/non-member viewers
  see a name instead of a raw UUID (F15). One small query (org counts are
  tens); bulk index paths resolve this once per batch."
  [db]
  (into {} (map (juxt (comp str :id) :name)) (org/all-orgs db)))

(defn enrich*
  "Enriches sports-site map with :search-meta key where we add data that
  is useful for searching. `org-name-by-id` ({org-id-str → name}, see
  `org-names`) resolves the owner org's display name; without it the
  indexed doc simply lacks :search-meta :owner-org-name (FE falls back to
  the UUID) until the next full reindex."
  ([sports-site] (enrich* sports-site nil))
  ([sports-site org-name-by-id]
   (let [sports-site (fix-geoms sports-site)
         fcoll (-> sports-site :location :geometries)
         geom (-> fcoll :features first :geometry)
         start-coords (case (:type geom)
                        "Point" (-> geom :coordinates)
                        "LineString" (-> geom :coordinates first)
                        "Polygon" (-> geom :coordinates first first))

         center-coords (try (-> fcoll gis/centroid :coordinates)
                            (catch Exception ex
                              (log/warn ex "Failed to calc centroid for lipas-id"
                                        (:lipas-id sports-site) "fcoll" fcoll)
                              nil))

         geom2 (-> fcoll :features last :geometry)
         end-coords (case (:type geom2)
                      "Point" (-> geom2 :coordinates)
                      "LineString" (-> geom2 :coordinates last)
                      "Polygon" (-> geom2 :coordinates last last))

         city-code (-> sports-site :location :city :city-code)
         province (-> city-code cities :province-id cities/provinces)
         avi-area (-> city-code cities :avi-id cities/avi-areas)

         type-code (-> sports-site :type :type-code)
         main-category (-> type-code types :main-category types/main-categories)
         sub-category (-> type-code types :sub-category types/sub-categories)
         field-types (->> sports-site :fields (map :type) distinct)
         latest-audit (some-> sports-site
                              :audits
                              (->> (sort-by :audit-date utils/reverse-cmp))
                              first
                              :audit-date)
         ;; Extract activity keys for search filtering
         activity-keys (when-let [activities (:activities sports-site)]
                         (when (seq activities)
                           (vec (keys activities))))
         sports-site (-> sports-site
                         compute-renovations
                         compute-renovation-years)

         ;; org-management: a site's editor orgs = owner org + any orgs granted
         ;; edit. Indexed for the reverse/bulk direction (Q1 "facilities owned by
         ;; org X" and the :org-editor ES filter in wrap-es-query).
         owner-org-id (some-> sports-site :owner-org-id str)
         editor-org-ids (some-> (concat (some-> sports-site :owner-org-id vector)
                                        (:edit-grants sports-site))
                                (->> (map str))
                                distinct vec not-empty)

         search-meta {:name (utils/->sortable-name (:name sports-site))
                      :admin {:name (-> sports-site :admin admins)}
                      :owner {:name (-> sports-site :owner owners)}
                      :owner-org-id owner-org-id
                      ;; Denormalized owner org name so every viewer (incl.
                      ;; anonymous) sees a name instead of a raw UUID (F15).
                      :owner-org-name (when owner-org-id
                                        (get org-name-by-id owner-org-id))
                      :editor-org-ids editor-org-ids
                      :audits {:latest-audit-date latest-audit}
                      :location
                      {:wgs84-point start-coords
                       :wgs84-center center-coords
                       :wgs84-end end-coords
                       :geometries (feature-coll->geom-coll (gis/strip-z-fcoll fcoll))
                       :city {:name (-> city-code cities :name)}
                       :province {:name (:name province)}
                       :avi-area {:name (:name avi-area)}
                       :simple-geoms (gis/simplify-safe fcoll)}
                      :type
                      {:name (-> type-code types :name)
                       :tags (-> type-code types :tags)
                       :main-category {:name (:name main-category)}
                       :sub-category {:name (:name sub-category)}}
                      :fields
                      {:field-types field-types}
                      :activities activity-keys}]
     (assoc sports-site :search-meta search-meta))))

#_(defn enrich-ice-stadium [{:keys [envelope building] :as ice-stadium}]
    (let [smaterial (-> envelope :base-floor-structure)
          area-m2 (-> building :total-ice-area-m2)]
      (-> ice-stadium
          (cond->
            smaterial (assoc-in [:properties :surface-material] [smaterial])
            area-m2 (assoc-in [:properties :area-m2] area-m2))
          utils/clean
          enrich*)))

#_(defn enrich-swimming-pool [{:keys [building] :as swimming-pool}]
    (-> swimming-pool
        (assoc-in [:properties :area-m2] (-> building :total-water-area-m2))
        utils/clean
        enrich*))

(defn enrich-cycling-route
  ([sports-site] (enrich-cycling-route sports-site nil))
  ([sports-site org-name-by-id]
   (-> sports-site
       (update-in [:location :geometries] gis/sequence-features)
       (enrich* org-name-by-id))))

(defmulti enrich (fn [sports-site & _] (-> sports-site :type :type-code)))
(defmethod enrich :default [sports-site & [org-name-by-id]]
  (enrich* sports-site org-name-by-id))
(defmethod enrich 4412 [sports-site & [org-name-by-id]]
  (enrich-cycling-route sports-site org-name-by-id))
#_(defmethod enrich 2510 [sports-site] (enrich-ice-stadium sports-site))
#_(defmethod enrich 2520 [sports-site] (enrich-ice-stadium sports-site))
#_(defmethod enrich 3110 [sports-site] (enrich-swimming-pool sports-site))
#_(defmethod enrich 3130 [sports-site] (enrich-swimming-pool sports-site))

(defn index!
  "`org-name-by-id` (see `org-names`) denormalizes the owner org's name into
  :search-meta — pass it whenever a db handle is in scope; without it the doc
  is indexed without :owner-org-name (UUID shown until the next reindex)."
  ([search sports-site]
   (index! search sports-site false))
  ([search sports-site sync?]
   (index! search sports-site sync? nil))
  ([{:keys [indices client]} sports-site sync? org-name-by-id]
   (let [idx-name (get-in indices [:sports-site :search])
         data (enrich sports-site org-name-by-id)]
     (search/index! client idx-name :lipas-id data sync?))))

(defn index-legacy-sports-place!
  "Indexes sports-site to legacy Elasticsearch index with legacy data transformation.
  This is used by the legacy API to find sports places."
  ([search sports-site]
   (index-legacy-sports-place! search sports-site false))
  ([{:keys [indices client]} sports-site sync?]
   (let [legacy-data (-> sports-site
                         compute-renovation-years
                         (dissoc :renovations)
                         legacy-transform/->old-lipas-sports-site*
                         (assoc :id (:lipas-id sports-site))
                         (legacy-sports-place/format-sports-place
                           :all
                           legacy-locations/format-location))

         idx-name (get-in indices [:legacy-sports-site :search])]
     ;; Use :sportsPlaceId as the id-fn since the data is in legacy camelCase format
     (search/index! client idx-name :sportsPlaceId legacy-data sync?))))

(defn delete-from-legacy-index!
  "Deletes a sports-site from the legacy Elasticsearch index.
   Used when a site becomes inactive (out-of-service-permanently or incorrect-data)."
  [{:keys [indices client]} lipas-id]
  (let [idx-name (get-in indices [:legacy-sports-site :search])]
    (try
      (search/delete! client idx-name lipas-id)
      (catch Exception e
        ;; Log but don't fail - document might not exist
        (log/debug "Could not delete from legacy index" {:lipas-id lipas-id :error (.getMessage e)})))))

(def ^:private legacy-index-statuses
  "Status values that should be in the legacy index.
   Matches the filter used in search_indexer.clj for full re-indexing."
  #{"active" "out-of-service-temporarily"})

(defn- should-be-in-legacy-index?
  "Returns true if the sports site should be in the legacy index based on its status."
  [sports-site]
  (contains? legacy-index-statuses (:status sports-site)))

(defn search
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:sports-site :search])]
    (search/search client idx-name params)))

(defn org-sites
  "Sites an org owns (filter \"owned\") or may edit (filter \"editable\" = owned ∪
  granted). A thin ES `term` query on the indexed search-meta org fields (Q1).
  Returns flat rows for the org dashboard / card site-count badge.

  With `:count-only? true` no documents are fetched (`:size 0`) — the response
  keeps its `{:total n :sites []}` shape but costs a count, not 2000 docs
  (F18). The FE's owned-sites flow only ever consumes `:total`."
  ([search-comp org-id filter] (org-sites search-comp org-id filter nil))
  ([search-comp org-id filter {:keys [count-only?]}]
   (let [field (if (= "editable" filter)
                 "search-meta.editor-org-ids"
                 "search-meta.owner-org-id")
         params (merge {:track_total_hits true
                        :query {:bool {:filter [{:term {field (str org-id)}}]}}}
                       (if count-only?
                         {:size 0}
                         {:size 2000
                          :_source ["lipas-id" "name" "event-date"
                                    "search-meta.type.name" "search-meta.location.city.name"]}))
         resp (search search-comp params)]
     {:total (get-in resp [:body :hits :total :value])
      :sites (->> resp :body :hits :hits
                  (mapv (fn [h]
                          (let [src (:_source h)]
                            {:lipas-id   (:lipas-id src)
                             :name       (:name src)
                             :event-date (:event-date src)
                             :type-name  (get-in src [:search-meta :type :name :fi])
                             :city-name  (get-in src [:search-meta :location :city :name :fi])}))))})))

(defn org-owned-site-counts
  "Owned-site count per org, as `{org-id-str -> count}`, from one ES `terms`
  aggregation over `search-meta.owner-org-id` (single round trip for all orgs,
  independent of site count). Powers the orgs-list site-count chip. Counts are
  exact (single-shard index); `:size` covers the org-count ceiling (§11.2)."
  [search-comp]
  (let [resp (search search-comp
                     {:size 0
                      :track_total_hits false
                      :aggs {:by_org {:terms {:field "search-meta.owner-org-id"
                                              :size 1000}}}})]
    (->> (get-in resp [:body :aggregations :by_org :buckets])
         (into {} (map (juxt :key :doc_count))))))

(defn- orgs-relevant-to-site
  "Targeted org fetch for site-editors (F20): the given orgs (the site's
  owner/grantees, by id) plus every org with a non-empty role-template catalog.
  An org with no catalog cannot project site privileges to its members beyond
  the owner/grantee mechanism, so it can never appear in the answer — this
  keeps the per-request scan proportional to catalog-bearing orgs instead of
  all orgs. (Today org counts are small either way; the jsonb predicate is the
  cheap insurance, not a full index-backed solution — F20 partially addressed.)"
  [db org-ids]
  (let [ids (->> org-ids (keep utils/->uuid-safe) distinct vec)
        ;; `->` returns NULL for a missing key, so no jsonb `?` operator is
        ;; needed (it would collide with JDBC parameter markers).
        catalog-pred "(document->'role-templates' IS NOT NULL AND document->'role-templates' <> '{}'::jsonb)"
        sql (if (seq ids)
              (str "SELECT org_id, document FROM org_current WHERE org_id IN ("
                   (str/join "," (repeat (count ids) "?")) ") OR " catalog-pred)
              (str "SELECT org_id, document FROM org_current WHERE " catalog-pred))]
    (->> (next-jdbc/execute! db (into [sql] ids)
                             {:builder-fn rs/as-unqualified-kebab-maps})
         (mapv org/unmarshall))))

(defn- catalog-template-roles
  "Project the roles a hypothetical member holding catalog template
  `template-key` in `org` would get — via the exact projection the login path
  bakes into the JWT (org/derive-org-roles; not a reimplementation) — and
  conform them for roles/check-privilege."
  [org template-key]
  (let [uid "00000000-0000-0000-0000-000000000000"]
    (-> (org/derive-org-roles
          uid [(assoc org :members [{:user-id uid :roles [(name template-key)]}])])
        roles/conform-roles)))

(defn site-editors
  "\"Who can edit site Z\" (Q2, design-spec §6): owner org + grantee orgs (off
  the site document) ∪ orgs whose role-template catalog grants editing on this
  site. Catalogs are interpreted through the SAME machinery enforcement uses:
  each template is projected like login would project it and asked
  roles/check-privilege in this site's context (F16) — so catalog
  city/type/site-manager grants are found, and city/type-scoped templates
  are only listed for matching sites. Legacy direct-user scan is the honest
  unindexed caveat — deferred."
  [db lipas-id]
  (let [site         (get-sports-site db lipas-id)
        owner-org-id (some-> site :owner-org-id str)
        grant-ids    (->> (:edit-grants site) (map str) set)
        orgs         (orgs-relevant-to-site db (cons owner-org-id grant-ids))
        org-by-id    (into {} (map (juxt #(str (:id %)) identity)) orgs)
        owner-org    (when-let [o (and owner-org-id (org-by-id owner-org-id))]
                       {:id owner-org-id :name (:name o)})
        grantee-orgs (for [gid grant-ids
                           :let [o (org-by-id gid)]
                           :when o]
                       {:id gid :name (:name o)})
        rc           (roles/site-roles-context site)
        org-grants?  (fn [org privilege]
                       (some (fn [template-key]
                               (roles/check-privilege
                                 {:permissions {:roles (catalog-template-roles org template-key)}}
                                 rc privilege))
                             (keys (:role-templates org))))
        listed-ids   (into grant-ids (when owner-org-id [owner-org-id]))
        ;; Orgs whose catalog grants FULL edit (:site/create-edit) on this site.
        ;; Owner/grantees are excluded — they are already listed above and their
        ;; org-editor templates match precisely via the owner/grant mechanism.
        catalog-editor-orgs
        (for [o orgs
              :let [oid (str (:id o))]
              :when (and (not (listed-ids oid))
                         (org-grants? o :site/create-edit))]
          {:id oid :name (:name o)})
        ;; Orgs whose catalog grants activity editing (:activity/edit) on this
        ;; site (activities-managers are not general editors).
        activity-editor-orgs
        (for [o orgs
              :when (org-grants? o :activity/edit)]
          {:id (str (:id o)) :name (:name o)})
        ;; Legacy direct-permission users (the only unindexed part of Q2): a
        ;; jsonb-containment candidate pre-filter narrows the account table to a
        ;; handful, then the exact check-privilege confirms. Admins are excluded
        ;; (they can edit everything — listing them under every site is noise).
        legacy-users (->> (db/users-with-permissions-matching
                            db {:city-code  (:city-code rc)
                                :type-code  (:type-code rc)
                                :lipas-id   (:lipas-id rc)
                                :activities (:activity rc)})
                          (filter (fn [u] (and (not (roles/check-role u :admin))
                                               (roles/check-privilege u rc :site/create-edit))))
                          (mapv (fn [u] {:email (:email u) :username (:username u)})))]
    {:owner-org            owner-org
     :grantee-orgs         (vec grantee-orgs)
     :catalog-editor-orgs  (vec catalog-editor-orgs)
     :activity-editor-orgs (vec activity-editor-orgs)
     :legacy-users         legacy-users}))

(defn site-edit-history
  "Per-revision edit history for a site (org Kohteet drawer): each revision's
  timestamp + the editor (resolved from author-id), newest first. Reads only
  event-date/author-id/status — no documents — so it stays light even for
  long-lived sites. `:emails?` true resolves authors to their email address —
  pass it ONLY for callers holding :users/manage (the route gates this);
  everyone else gets usernames so the open endpoint can't be used to harvest
  emails (F5)."
  ([db lipas-id] (site-edit-history db lipas-id {}))
  ([db lipas-id {:keys [emails?]}]
   (let [rows  (db/get-sports-site-edit-history db lipas-id)
         ;; shared resolver — the email-masking rule lives in ONE place (F25)
         names (org/resolve-account-names db (map :author_id rows) emails?)]
     (mapv (fn [r]
             {:event-date (str (:event_date r))
              :status     (:status r)
              :author     (get names (str (:author_id r)))})
           rows))))

(defn search-fields
  [{:keys [indices client]}
   {:keys [field-types]}]
  (let [idx-name (get-in indices [:sports-site :search])
        params {:size 1000
                :track_total_hits 60000
                :_source
                {:excludes ["search-meta.*"]}
                :query
                {:bool
                 {:must [{:terms {:status ["active" "out-of-service-temporarily"]}}
                         {:terms {:search-meta.fields.field-types field-types}}]}}}]
    (-> (search/search client idx-name params)
        :body
        :hits
        :hits
        (->> (map :_source)))))

(defn sync-ptv! [tx search ptv-component user props]
  (let [f (resolve 'lipas.backend.ptv.core/sync-ptv!)]
    (f tx search ptv-component user props)))

;; TODO refactor upsert-sports-site!, upsert-sports-site!* and
;; save-sports-site! to form more sensible API.
(defn save-sports-site!
  "Saves sports-site to db and search and appends it to outbound
  integrations queue."
  ([db search ptv user sports-site]
   (save-sports-site! db search ptv user sports-site false))
  ([db search ptv user sports-site draft?]
   ;; :acting-org-id is per-revision audit metadata ("on whose behalf") that
   ;; only trusted internal paths may stamp (set-site-edit-grants!, takeover
   ;; approve! — they call upsert-sports-site!* directly). This fn serves the
   ;; user-facing save endpoint, so strip it here to keep the audit column
   ;; unforgeable via POST /sports-sites (the save schema is :closed false).
   (let [sports-site    (dissoc sports-site :acting-org-id)
         correlation-id (jobs/gen-correlation-id)]
     (jobs/with-correlation-context correlation-id
       (fn []
         ;; Phase 1: All DB operations inside the transaction.
         ;; ES indexing is deliberately outside the transaction so that
         ;; a failure in indexing cannot roll back committed DB data.
         ;; Worst case: data in DB but not in ES, fixable by reindexing.
         (let [resp
               (jdbc/with-db-transaction [tx db]
                 (let [resp (upsert-sports-site! tx user sports-site draft?)
                       route? (-> resp :type :type-code types/all :geometry-type #{"LineString"})]

                   (when-not draft?
                     (log/info "Saving sports site with background jobs"
                               {:lipas-id (:lipas-id resp)
                                :is-route? route?
                                :user (:email user)})

                     (when route?
                       (jobs/enqueue-job! tx "elevation"
                                          {:lipas-id (:lipas-id resp)}
                                          {:correlation-id correlation-id
                                           :priority 70}))

                     ;; Analysis doesn't require elevation information
                     (jobs/enqueue-job! tx "analysis"
                                        {:lipas-id (:lipas-id resp)}
                                        {:correlation-id correlation-id
                                         :priority 80})

                     ;; Webhook Notification

                     ;; NOTE: Webhook is disabled until UTP or someone else
                     ;; starts using it again.

                     #_(jobs/enqueue-job! tx "webhook"
                                          {:lipas-ids [(:lipas-id resp)]
                                           :operation-type (if (new? sports-site) "create" "update")
                                           :initiated-by (:id user)}
                                          {:correlation-id correlation-id
                                           :priority 85}))

                   ;; Sync the site to PTV if
                   ;; - it was previously sent to PTV (we might archive it now if it no longer looks like PTV candidate)
                   ;; - it is PTV candidate now
                   ;; - do nothing (keep the previous data in PTV if site was previously sent there) if sync-enabled is false
                   ;; Note: if site status or something is updated in Lipas, so that the site is no longer candidate,
                   ;; that doesn't trigger update if sync-enabled is false.
                   (if (and (not draft?)
                            (or (:sync-enabled (:ptv resp))
                                (:delete-existing (:ptv resp)))
                            ;; TODO: Check privilage :ptv/basic or such
                            (or (ptv-data/ptv-candidate? resp)
                                (ptv-data/is-sent-to-ptv? resp)))
                     ;; NOTE:  this will create a new sports-site rev.
                     ;; Make it instead update the sports-site already created in the tx?
                     ;; Otherwise each save-sports-site! will create two sports-site revs.
                     (let [{new-ptv :ptv new-event-date :event-date}
                           (sync-ptv! tx search ptv user
                                      {:sports-site resp
                                       :org-id (:org-id (:ptv resp))
                                       :lipas-id (:lipas-id resp)
                                       :ptv (:ptv resp)})]
                       (log/infof "Sports site updated and PTV integration enabled")
                       ;; sync-ptv! wrote a new revision (success or failure)
                       ;; and returned the event-date it used. Adopt both so
                       ;; the outer index! below reindexes that exact
                       ;; revision rather than the pre-sync resp.
                       (-> resp
                           (assoc :event-date new-event-date)
                           (assoc :ptv new-ptv)))
                     resp)))]

           ;; Phase 2: ES indexing after transaction has committed.
           (when-not draft?
             (index! search resp :sync (org-names db))
             (if (should-be-in-legacy-index? resp)
               (index-legacy-sports-place! search resp :sync)
               (delete-from-legacy-index! search (:lipas-id resp))))

           resp))))))

;;; Cities ;;;

(defn get-cities
  ([db]
   (get-cities db false))
  ([db no-cache]
   (or
     (and (not no-cache) (:all-cities @cache))
     (->> (db/get-cities db)
          (swap! cache assoc :all-cities)
          :all-cities))))

(defn get-populations
  [{:keys [indices client]} year]
  (let [idx (get-in indices [:report :city-stats])]
    (-> (search/search client idx {:size 500 ;; Finland has ~300 cities
                                   :_source {:includes ["city-code" "population" "year"]}
                                   :query
                                   {:terms {:year [year]}}})
        (get-in [:body :hits :hits])
        (->> (map :_source)
             (utils/index-by :city-code))
        (update-vals :population))))

;;; Subsidies ;;;

(defn get-subsidies [db]
  (db/get-subsidies db))

(defn query-subsidies
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:report :subsidies])]
    (:body (search/search client idx-name params))))

;;; Reports ;;;

(defn energy-report [db type-code year]
  (let [data (get-sports-sites-by-type-code db type-code {:revs year})]
    (reports/energy-report data)))

(defn sports-sites-report-excel
  [{:keys [indices client]} params fields locale out]
  (let [idx-name (get-in indices [:sports-site :search])
        in-chan (search/scroll client idx-name params)
        locale (or locale :fi)
        headers (mapv #(get-in reports/fields [% locale]) fields)
        data-chan (async/go
                    (loop [res [headers]]
                      (if-let [page (async/<! in-chan)]
                        (recur (-> page :body :hits :hits
                                   (->>
                                     (map (comp (partial reports/->row fields)
                                                (partial i18n/localize locale)
                                                :_source))
                                     (into res))))
                        res)))]
    (->> (async/<!! data-chan)
         (excel/create-workbook "lipas")
         (excel/save-workbook-into-stream! out))))

(defn sports-sites-report-geojson
  [{:keys [indices client]} params fields locale out]
  (let [idx-name (get-in indices [:sports-site :search])
        in-chan (search/scroll client idx-name (update params :_source dissoc :excludes))
        locale (or locale :fi)
        headers (mapv #(get-in reports/fields [% locale]) fields)
        localize (partial i18n/localize locale)
        ->row (partial reports/->row fields)]
    (with-open [writer (OutputStreamWriter. out)]
      (.write writer "{\"type\":\"FeatureCollection\",\"features\":[")
      (loop [page-num 0]
        (when-let [page (async/<!! in-chan)]
          (let [ms (-> page :body :hits :hits)
                feats (mapcat
                        (fn [m]
                          (let [props (-> m
                                          :_source
                                          localize
                                          ->row
                                          (->> (zipmap headers)))]
                            (->> m :_source :location :geometries :features
                                 (map (fn [f] (assoc f :properties props))))))
                        ms)]
            (loop [feat-num 0
                   f (first feats)
                   fs (rest feats)]
              (.write writer (str (when-not (= 0 page-num feat-num) ",")
                                  (json/encode f)))
              (when-let [next-f (first fs)]
                (recur (inc feat-num) next-f (rest fs)))))
          (recur (inc page-num))))
      (.write writer "]}"))))

(defn sports-sites-report-csv
  [{:keys [indices client]} params fields locale out]
  (let [idx-name (get-in indices [:sports-site :search])
        in-chan (search/scroll client idx-name params)
        locale (or locale :fi)
        headers (mapv #(get-in reports/fields [% locale]) fields)
        xform (comp (partial reports/->row fields)
                    (partial i18n/localize locale)
                    :_source)]
    (with-open [writer (OutputStreamWriter. out)]
      (csv/write-csv writer [headers])
      (loop []
        (when-let [page (async/<!! in-chan)]
          (let [ms (-> page :body :hits :hits)]
            (csv/write-csv writer (map xform ms)))
          (recur))))))

(defn finance-report [db {:keys [city-codes]}]
  (let [data (get-cities db)]
    (reports/finance-report city-codes data)))

(defn query-finance-report
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:report :city-stats])]
    (:body (search/search client idx-name params))))

(defn calculate-stats
  [db search* {:keys [city-codes type-codes grouping year]
               :or {grouping "location.city.city-code"}}]
  (let [pop-data (get-populations search* year)
        statuses ["active" "out-of-service-temporarily"]
        query {:size 0,
               :query
               {:bool
                {:filter
                 (into [] (remove nil?)
                       [{:terms {:status statuses}}
                        (when (not-empty type-codes)
                          {:terms {:type.type-code type-codes}})
                        (when (not-empty city-codes)
                          {:terms {:location.city.city-code city-codes}})])}}
               :aggs
               {:grouping
                {:terms {:field (keyword grouping) :size 400}
                 :aggs
                 {:area_m2_stats {:stats {:field :properties.area-m2}}
                  :length_km_stats {:stats {:field :properties.route-length-km}}}}}}
        aggs-data (-> (search search* query) :body :aggregations :grouping :buckets)]
    (if (= "location.city.city-code" grouping)
      (reports/calculate-stats-by-city aggs-data pop-data)
      (reports/calculate-stats-by-type aggs-data pop-data city-codes))))

(defn get-front-page-stats
  [db search*]
  (let [cache-key :front-page-stats
        cached    (get @cache cache-key)
        now       (System/currentTimeMillis)]
    (if (and cached (< (- now (:timestamp cached)) (* 5 60 1000)))
      (:data cached)
      (let [query {:size 0
                   :track_total_hits true
                   :query {:bool {:filter [{:terms {:status ["active" "out-of-service-temporarily"]}}]}}
                   ;; The municipality count is scoped to the last 12 months
                   ;; (distinct cities with a site updated in the window) rather
                   ;; than all-time — the all-time figure is essentially every
                   ;; municipality in Finland and never moves.
                   :aggs {:updated-last-year {:filter {:range {:event-date {:gte "now-365d"}}}
                                              :aggs {:cities {:cardinality {:field :location.city.city-code}}}}}}
            result (-> (search search* query) :body)
            total  (get-in result [:hits :total :value])
            aggs   (:aggregations result)
            ;; Distinct people who edited any site in the last 12 months.
            ;; The author lives in the append-only event log (source of truth),
            ;; not in the enriched ES index, so this comes from the database.
            updaters (-> (jdbc/query db ["SELECT COUNT(DISTINCT author_id) AS n
                                          FROM sports_site
                                          WHERE event_date >= now() - interval '12 months'"])
                         first :n)
            data   {:total-count               total
                    :cities-updated-last-year  (get-in aggs [:updated-last-year :cities :value])
                    :updated-last-year         (get-in aggs [:updated-last-year :doc_count])
                    :updaters-last-year        updaters}]
        (swap! cache assoc cache-key {:data data :timestamp now})
        data))))

(defn data-model-report
  [out]
  (data-model-export/create-excel out))

;;; Accessibility register ;;;

(defn get-accessibility-statements [lipas-id]
  (accessibility/get-statements lipas-id))

(defn get-accessibility-app-url [db user lipas-id]
  (when-let [sports-site (get-sports-site db lipas-id)]
    {:url (accessibility/make-app-url user sports-site)}))

;;; Analysis ;;;

(defn search-schools
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:analysis :schools])]
    (search/search client idx-name params)))

(defn search-population
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:analysis :population])]
    (search/search client idx-name params)))

(defn calc-distances-and-travel-times [search params]
  (reachability/calc-distances-and-travel-times search params))

(defn create-analysis-report [data out]
  (->> data
       (reachability/create-report)
       (apply excel/create-workbook)
       (excel/save-workbook-into-stream! out)))

(defn calc-diversity-indices [search params]
  (diversity/calc-diversity-indices-2 search params))

;;; Newsletter ;;;

(defn get-newsletter [config]
  (newsletter/retrieve config))

(defn subscribe-newsletter [config user]
  (newsletter/subscribe config user))

(defn send-feedback! [emailer feedback]
  (email/send-feedback-email! emailer "lipasinfo@jyu.fi" feedback))

(defn check-sports-site-name [search-cli {:keys [lipas-id name]}]
  (let [query {:size 1
               :_source {:includes ["lipas-id" "name" "status"]}
               :query
               {:bool
                {:must [{:match_phrase {:name.keyword name}}
                        {:terms {:status ["active" "out-of-service-temporarily"]}}]
                 :must_not {:term {:lipas-id lipas-id}}}}}
        resp (search search-cli query)]
    (merge
      {:status (if (-> resp :body :hits :total :value (>= 1))
                 :conflict
                 :ok)}
      (when-let [conflict (-> resp :body :hits :hits first :_source)]
        {:conflict conflict}))))

;;; LOI ;;;

(defn ->lois-es-query
  [{:keys [location loi-statuses]}]
  (let [lon (:lon location)
        lat (:lat location)
        distance (:distance location)
        origin (str lat "," lon)
        decay-factor 2
        offset (str (* distance (* decay-factor 0.5)) "m")
        scale (str (* distance decay-factor) "m")
        size 100
        from 0
        excludes ["search-meta"]
        query {:size size
               :from from
               :sort ["_score"]
               :_source {:excludes excludes}
               :query {:function_score
                       {:score_mode "max"
                        :boost_mode "max"
                        :functions [{:exp
                                     {:search-meta.location.wgs84-point
                                      {:origin origin
                                       :offset offset
                                       :scale scale}}}]
                        :query {:bool
                                {:filter
                                 [{:terms {:status.keyword loi-statuses}}]}}}}}
        default-query {:size size :query {:match_all {}}}]
    (if (and lat lon distance)
      query
      default-query)))

(defn search-lois*
  [{:keys [indices client]} es-query]
  (let [idx-name (get-in indices [:lois :search])]
    (search/search client idx-name es-query)))

(defn search-lois
  [search es-query]
  (-> (search-lois* search es-query)
      :body
      :hits
      :hits
      (->> (map :_source))))

(defn get-loi
  [{:keys [indices client]} loi-id]
  (let [idx-name (get-in indices [:lois :search])]
    (-> (search/fetch-document client idx-name loi-id)
        :body
        :_source
        (dissoc :search-meta))))

(defn enrich-loi
  [{:keys [geometries] :as loi}]
  (let [geom-coll (feature-coll->geom-coll geometries)]
    (-> loi
        (assoc-in [:search-meta :location :geometries] geom-coll)
        (assoc-in [:search-meta :location :wgs84-point] (-> (gis/->flat-coords geometries)
                                                            first)))))

(defn search-lois-with-params
  [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:lois :search])
        es-query (->lois-es-query params)]
    (-> (search/search client idx-name es-query)
        :body
        :hits
        :hits)))

(defn index-loi!
  ([search loi]
   (index-loi! search loi false))
  ([{:keys [indices client]} loi sync?]
   (let [idx-name (get-in indices [:lois :search])
         loi (enrich-loi loi)]
     (search/index! client idx-name :id loi sync?))))

(defn upsert-loi!
  [db search user loi]
  (let [correlation-id (jobs/gen-correlation-id)]
    (jdbc/with-db-transaction [tx db]
      (let [result (db/upsert-loi! tx user loi)]
        (log/info "Saving LOI with background jobs"
                  {:loi-id (:id loi)
                   :user (:email user)})

        ;; Disabled due to webhooks not being used atm
        ;; Enqueue webhook with same correlation ID
        #_(jobs/enqueue-job! tx "webhook"
                             {:loi-ids [(:id loi)]
                              :operation-type (if (nil? (:id loi)) "create" "update")
                              :initiated-by (:id user)}
                             {:correlation-id correlation-id
                              :priority 85})
        (index-loi! search loi :sync)
        result))))

(defn upload-utp-image!
  [{:keys [_filename _data _user] :as params}]
  (utp-cms/upload-image! params))

;;; Types ;;;

(defn get-categories
  []
  (map types/->type (vals types/active)))

(defn get-category
  [type-code]
  (types/->type (types/active type-code)))

;;; Help ;;;

(defn get-help-data
  [db]
  (db/get-versioned-data db "help" "active"))

(defn save-help-data
  [db help-data]
  (db/add-versioned-data! db "help" "active" help-data))

(comment
  (get-categories)
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (def admin (get-user db-spec "admin@lipas.fi"))
  (def search2 {:client (search/create-cli (:search config/default-config))
                :indices (-> config/default-config :search :indices)})
  (def fields ["lipas-id" "name" "admin" "owner" "properties.surface-material"
               "location.city.city-code"])
  (reset! cache {})
  (:all-cities @cache)

  (check-sports-site-name search2 {:lipas-id 89212 :name "Tapanilan Urheilukeskus / Salibandyhalli"})

  (check-sports-site-name search2 {:lipas-id 89211 :name "Tapanilan Urheilukeskus / "})
  (check-sports-site-name search2 {:lipas-id 0 :name "Kirkonkylän kaukalo"})

  (search-fields search2 {:field-types ["floorball-field"]})

  (def results (atom []))

  (async/go
    (let [ch (search/scroll search2 "sports_sites_current" {:query
                                                            {:term {:type.type-code 1170}}})]
      (loop []
        (when-let [page (async/<! ch)]
          ;; todo GeoJSON writing to stream
          (swap! results conj page)
          (recur)))))

  @results
  (count @results)

  (let [statuses ["active" "out-of-service-temporarily"]
        grouping "location.city.city-code"
        type-codes [1340]
        city-codes [992]
        query {:size 0,
               :query
               {:bool
                {:filter
                 (into [] (remove nil?)
                       [{:terms {:status statuses}}
                        (when (not-empty type-codes)
                          {:terms {:type.type-code type-codes}})
                        (when (not-empty city-codes)
                          {:terms {:location.city.city-code city-codes}})])}}
               :aggs
               {:grouping
                {:terms {:field (keyword grouping) :size 400}
                 :aggs {:area_m2_stats {:stats {:field "properties.area-m2"}}}}}}]
    (search search2 query))

  #_(flat-finance-report db-spec [992 175])

  (process-elevation-queue! db-spec search2)
  search2
  (first (get-cities db-spec))
  (time (get-populations db-spec 2017))
  (time (:all-cities @cache))
  (time (cities-report db-spec [992]))
  (time (m2-per-capita-report db-spec search2 [] [])))
