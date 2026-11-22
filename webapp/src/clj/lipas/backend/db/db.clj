(ns lipas.backend.db.db
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [hikari-cp.core :as hikari]
    [lipas.backend.db.city :as city]
    [lipas.backend.db.loi :as loi]
    [lipas.backend.db.reminder :as reminder]
    [lipas.backend.db.sports-site :as sports-site]
    [lipas.backend.db.subsidy :as subsidy]
    [lipas.backend.db.user :as user]
    [lipas.backend.db.utils :as db-utils]
    [lipas.backend.db.versioned-data :as versioned-data]
    [lipas.utils :as utils]))

;; User ;;

(defn get-users [db-spec]
  (->> (user/all-users db-spec)
       (map user/unmarshall)
       (map #(dissoc % :password))))

(defn users-with-permissions-matching
  "Active users whose stored roles reference any of the given context values — a
  candidate pre-filter for the \"who can edit site Z\" legacy-user lookup
  (design-spec §6 step 4). Filters with jsonb containment on `account.permissions`
  (both `city-code`/`city_code` key spellings, since the raw column carries both;
  the app only normalizes on read), narrowing the table to a small candidate set
  the caller then confirms with the exact `check-privilege`. Containment params
  are Clojure maps — `ISQLValue` turns them into jsonb, so no string building.
  Backed by the `account_permissions_gin` index for the selective predicates.
  Returns unmarshalled users (kebab-normalized + conformed permissions)."
  [db-spec {:keys [city-code type-code lipas-id activities]}]
  (let [contain (fn [k v] {:roles [{k [v]}]})
        params  (concat
                  (when city-code (for [k [:city-code :city_code]] (contain k city-code)))
                  (when type-code (for [k [:type-code :type_code]] (contain k type-code)))
                  (when lipas-id  (for [k [:lipas-id :lipas_id]] (contain k lipas-id)))
                  (for [a activities] (contain :activity a)))]
    (when (seq params)
      (let [sql (str "SELECT * FROM account WHERE status = 'active' AND ("
                     (str/join " OR " (repeat (count params) "permissions @> ?")) ")")]
        (->> (jdbc/query db-spec (into [sql] params))
             (map user/unmarshall)
             (map #(dissoc % :password)))))))

(comment
  (get-users (:lipas/db integrant.repl.state/system))
  (get-orgs (:lipas/db integrant.repl.state/system)))

(defn get-user-by-id [db-spec params]
  (when (uuid? (utils/->uuid-safe (:id params)))
    (-> (user/get-user-by-id db-spec params)
        (user/unmarshall))))

(defn get-user-by-email [db-spec params]
  (-> (user/get-user-by-email db-spec params)
      (user/unmarshall)))

(defn get-user-by-username [db-spec params]
  (-> (user/get-user-by-username db-spec params)
      (user/unmarshall)))

(defn add-user! [db-spec user]
  (->> user
       ;; Unverified unless the caller says otherwise (hugsql wants every
       ;; parameter present).
       (merge {:email-verified-at nil :email-verified-via nil})
       (user/marshall)
       (user/insert-user! db-spec)))

(defn change-user-email!
  "Moves the account to its newly proven address, provided it still has
  `old-email`. Returns the number of rows changed (0 or 1); see
  lipas.backend.core/confirm-email-change!."
  [db-spec {:keys [id old-email email username]}]
  (user/change-user-email! db-spec {:id        id
                                    :old_email old-email
                                    :email     email
                                    :username  username}))

(defn mark-user-email-verified!
  "Records that the user's email was proven `via` (\"registration\"/\"login\").
  No-op when the account is already verified — the first proof wins."
  [db-spec {:keys [id]} via]
  (user/mark-user-email-verified! db-spec {:id id :email_verified_via via}))

(defn update-user-permissions! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-permissions! db-spec)))

(defn update-user-status! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-status! db-spec)))

(defn update-user-history! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-history! db-spec)))

(defn update-user-data! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-data! db-spec)))

(defn reset-user-password! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-password! db-spec)))

(defn update-user-email! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-email! db-spec)))

(defn update-user-username! [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-username! db-spec)))

(defn get-user-tokens-valid-from
  "The user's token revocation point as a `java.sql.Timestamp`, or nil when the
  account was never revoked (column NULL) or has no row at all.

  Returns the bare value rather than a row map: the only caller is the
  per-request revocation check, and `user/unmarshall` would be wrong here (it
  expects the full user shape). The raw hugsql key is snake_case."
  [db-spec params]
  (:tokens_valid_from (user/get-user-tokens-valid-from db-spec params)))

(defn update-user-tokens-valid-from!
  "Sets the user's token revocation point. Call it through
  `lipas.backend.token-revocation/revoke!`, which also drops the cached value."
  [db-spec user]
  (->> user
       (user/marshall)
       (user/update-user-tokens-valid-from! db-spec)))

;; Sports Site ;;

(def closed-status
  "Document status of a permanently closed (decommissioned) site or LOI."
  "out-of-service-permanently")

(defn- ->instant ^java.time.Instant [t]
  (cond
    (instance? java.time.Instant t) t
    (instance? java.util.Date t) (.toInstant ^java.util.Date t)
    (instance? java.time.OffsetDateTime t) (.toInstant ^java.time.OffsetDateTime t)
    :else (java.time.Instant/parse (str t))))

(defn event-date->decommission-year
  "The closure year an `event-date` stands for, when nothing better is known.

  The removal dialog dates a closure in a past year as Dec 31
  23:59:59.999Z of that year: those count in UTC, where they are still in
  the chosen year (in Helsinki time they're already the next one). Any
  other timestamp is a real moment and counts in Finnish time."
  [event-date]
  (let [instant (->instant event-date)
        utc (.atZone instant java.time.ZoneOffset/UTC)
        year-end-marker? (and (= 12 (.getMonthValue utc))
                              (= 31 (.getDayOfMonth utc))
                              (= (java.time.LocalTime/of 23 59 59 999000000)
                                 (.toLocalTime utc)))]
    (if year-end-marker?
      (.getYear utc)
      (.getYear (.atZone instant (java.time.ZoneId/of "Europe/Helsinki"))))))

(defn row-event-date
  "The event-date to give a copy of revision `row`: its event_date column.
  The document's :event-date is used when it's the same instant (the normal
  case), keeping its formatting; the UI keys history by that string. Some
  revisions' documents disagree with their column (seen in prod), and a copy
  must land on the column's date to outrank the original."
  [row doc]
  (let [column (->instant (:event_date row))
        in-doc (:event-date doc)]
    (if (and in-doc (= column (try (->instant in-doc) (catch Exception _ nil))))
      in-doc
      (str column))))

(defn- closure-copy
  "Revision `row` carried over to the closed status of `closure`: its
  document with the closed status and the closure's year, at the row's own
  event_date. Every other field stays as it was at that date."
  [row unmarshall closure]
  (let [doc (unmarshall row)
        year (:decommission-year closure)]
    (cond-> (assoc doc
                   :status closed-status
                   :event-date (row-event-date row doc))
      year (assoc :decommission-year year)
      (not year) (dissoc :decommission-year))))

(defn rebase-onto-closure!
  "Carries a backdated closure forward over the revisions dated after it.

  Revisions are full snapshots ordered by event_date, so a closure dated
  into the past (\"closed in 2018\", saved in 2025) is outranked by every
  revision dated after it, and the site would keep looking active. Instead
  of rewriting those revisions, append a corrected copy of each: same
  event_date, newer created_at (so it wins at that date), closed status and
  :decommission-year, every other field as recorded. Originals stay as they
  are. Every later revision gets a copy, whatever its status: the closure is
  the newest decision, and the closer saw what they were overriding.

  One copy per event_date (the revision that wins there): rows inserted in
  one transaction share created_at, so two copies at the same event_date
  would tie in sports_site_current.

  Returns the newest copy (the site's current revision now), or nil when
  nothing is dated after the closure."
  [tx user closure]
  (let [params {:lipas_id (:lipas-id closure) :event_date (:event-date closure)}]
    (->> (sports-site/get-revisions-dated-after tx params)
         (reduce (fn [_ row]
                   (-> (closure-copy row sports-site/unmarshall closure)
                       ;; Written on behalf of whoever the closure was
                       (assoc :acting-org-id (:acting-org-id closure))
                       (sports-site/marshall user "published")
                       (->> (sports-site/insert-sports-site-rev! tx))
                       (sports-site/unmarshall)))
                 nil))))

(defn upsert-sports-site!
  "Inserts `sports-site` as a new revision. A published permanent closure
  dated before existing revisions is carried forward over them (see
  `rebase-onto-closure!`).

  Returns the site's current revision after the save: the inserted one, or
  the newest closure copy when the closure was backdated."
  ([db-spec user sports-site]
   (upsert-sports-site! db-spec user sports-site false))
  ([db-spec user sports-site draft?]
   (jdbc/with-db-transaction [tx db-spec]
     (let [status (if draft? "draft" "published")
           lipas-id (or (:lipas-id sports-site)
                        (:nextval (sports-site/next-lipas-id tx)))
           sports-site (assoc sports-site :lipas-id lipas-id)
           inserted (->> (sports-site/marshall sports-site user status)
                         (sports-site/insert-sports-site-rev! tx)
                         (sports-site/unmarshall))]
       (or (when (and (not draft?) (= closed-status (:status sports-site)))
             (rebase-onto-closure! tx user sports-site))
           inserted)))))

(defn upsert-sports-sites! [db-spec user sports-sites]
  (jdbc/with-db-transaction [tx db-spec]
    (doseq [sports-site sports-sites]
      (let [lipas-id (or (:lipas-id sports-site)
                         (:nextval (sports-site/next-lipas-id tx)))
            sports-site (assoc sports-site :lipas-id lipas-id)]
        (->> (sports-site/marshall sports-site user)
             (sports-site/insert-sports-site-rev! tx))))))

(defn get-sports-site [db-spec lipas-id]
  (let [params (-> {:lipas-id lipas-id}
                   db-utils/->snake-case-keywords)]
    (-> (sports-site/get db-spec params)
        sports-site/unmarshall)))

(defn get-sports-site-history [db-spec lipas-id]
  (let [params (-> {:lipas-id lipas-id}
                   db-utils/->snake-case-keywords)]
    (->> (sports-site/get-history db-spec params)
         (map sports-site/unmarshall))))

(defn get-sports-site-edit-history
  "Lightweight per-revision edit history (event-date + author-id + status, no
  documents) for a single sports-site, newest first."
  [db-spec lipas-id]
  (sports-site/get-edit-history db-spec {:lipas_id lipas-id}))

(defn get-full-sports-site-history [db-spec lipas-id]
  (let [params (-> {:lipas-id lipas-id}
                   db-utils/->snake-case-keywords)]
    (->> (sports-site/get-full-history db-spec params)
         (map sports-site/unmarshall-history-row))))

(defn get-sports-sites-by-type-code
  [db-spec type-code {:keys [revs raw?] :or {revs "latest" raw? false}}]
  (let [db-fn (cond
                (= revs "all") sports-site/get-by-type-code
                (= revs "latest") sports-site/get-latest-by-type-code
                (= revs "yearly") sports-site/get-yearly-by-type-code
                (number? revs) sports-site/get-by-type-code-and-year)
        params (-> (merge {:type-code type-code}
                          (when (number? revs)
                            {:year revs}))
                   db-utils/->snake-case-keywords)]
    (cond->> (db-fn db-spec params)
      (not raw?) (map sports-site/unmarshall))))

(defn get-sports-sites-by-city-code
  [db-spec city-code]
  (let [params (-> {:city-code (str city-code)} db-utils/->snake-case-keywords)]
    (->> params
         (sports-site/get-latest-by-city-code db-spec)
         (map sports-site/unmarshall))))

(defn get-sports-sites-by-ptv-service-channel-id
  "Returns [{:lipas-id _ :name _} ...] for current sports-site revisions whose
  PTV :service-channel-ids contain `service-channel-id`. Used to detect
  double-linking (more than one site bound to the same PTV service-location)."
  [db-spec service-channel-id]
  (->> (sports-site/get-lipas-ids-by-ptv-service-channel-id
         db-spec {:service_channel_id service-channel-id})
       (map (fn [{:keys [lipas_id name]}]
              {:lipas-id lipas_id :name name}))))

(defn get-ptv-adoption
  "One row per site that has ever had a :ptv subtree: `:first-month` of its
  first :ptv revision, `:first-published-month` of its first revision published
  in PTV (nil if never), `:left-month` of leaving PTV after its last
  publication (nil if still in PTV; all \"YYYY-MM\", Helsinki time), and
  `:site`, the current revision's name/status/owner/type/:ptv."
  [db-spec]
  (->> (sports-site/get-ptv-adoption db-spec)
       (map (fn [{:keys [lipas_id first_month first_published_month left_month city_code site]}]
              {:lipas-id lipas_id
               :first-month first_month
               :first-published-month first_published_month
               :left-month left_month
               :city-code (some-> city_code parse-long)
               :site site}))))

(defn get-users-drafts [db user]
  (let [params {:author-id (:id user) :status "draft"}]
    (->> params
         utils/->snake-case-keywords
         (sports-site/get-by-author-and-status db)
         (map sports-site/unmarshall))))

(defn get-last-modified [db lipas-ids]
  (if (empty? lipas-ids)
    lipas-ids
    (sports-site/get-last-modified db {:lipas_ids lipas-ids})))

(defn get-sports-sites-modified-since [db timestamp]
  (->> (sports-site/get-modified-since db {:timestamp timestamp})
       (map sports-site/unmarshall)))

(defn get-sports-sites-by-lipas-ids [db lipas-ids]
  (when-not (empty? lipas-ids)
    (->> {:lipas-ids lipas-ids}
         utils/->snake-case-keywords
         (sports-site/get-latest-by-ids db)
         (map sports-site/unmarshall))))

;; City ;;

(defn add-city! [db-spec city]
  (->> (city/marshall city)
       (city/insert! db-spec)))

(defn get-cities [db-spec]
  (->> (city/get-all db-spec)
       (map city/unmarshall)))

(defn get-cities-by-type-codes [db-spec city-codes]
  (->> {:city-codes city-codes}
       utils/->snake-case-keywords
       (city/get-by-city-codes db-spec)
       (map city/unmarshall)))

;; Subsidies ;;

(defn add-subsidy! [db-spec subsidy]
  (->> (subsidy/marshall subsidy)
       (subsidy/insert! db-spec)))

(defn get-subsidies [db-spec]
  (->> (subsidy/get-all db-spec)
       (map subsidy/unmarshall)))

(defn get-subsidies-by-year [db-spec years]
  (->> {:years years}
       (subsidy/get-by-years db-spec)
       (map subsidy/unmarshall)))

;; Reminders ;;

(defn add-reminder! [db-spec params]
  (->> params
       reminder/marshall
       (reminder/insert! db-spec)
       reminder/unmarshall))

(defn update-reminder-status! [db-spec params]
  (reminder/update-status! db-spec params))

(defn get-overdue-reminders [db-spec]
  (->> (reminder/get-overdue db-spec)
       (map reminder/unmarshall)))

(defn get-users-pending-reminders [db-spec user-id]
  (->> {:status "pending" :account-id user-id}
       utils/->snake-case-keywords
       (reminder/get-by-user-and-status db-spec)
       (map reminder/unmarshall)))

;; Loi's ;;

(defn get-lois [db]
  (->> (loi/get-latest db)
       (map loi/unmarshall)))

(defn get-lois-by-type
  [db {:keys [loi-type]}]
  (->> (loi/get-latest-by-loi-type db {:loi-type loi-type})
       (map loi/unmarshall)))

(defn get-lois-by-ids
  [db ids]
  (->> (loi/get-latest-by-ids db {:ids (map utils/->uuid ids)})
       (map loi/unmarshall)))

(defn- rebase-loi-onto-closure!
  "LOI twin of `rebase-onto-closure!`: appends a closed copy of each LOI
  revision dated after the backdated `closure`, one per event_date."
  [tx user closure]
  (doseq [row (loi/get-loi-revisions-dated-after
                tx {:id (:id closure) :event_date (:event-date closure)})]
    (-> (closure-copy row loi/unmarshall closure)
        (assoc :author-id (:id user))
        (loi/marshall user)
        (->> (loi/insert-loi-rev! tx)))))

(defn upsert-loi!
  "Inserts `loi` as a new revision and returns the inserted row. A backdated
  permanent closure is carried forward over later revisions like a site's
  (`rebase-onto-closure!`), so the current revision may then be a copy:
  read it back from loi_current rather than using `loi`."
  [db user loi]
  (jdbc/with-db-transaction [tx db]
    (let [inserted (-> loi
                       (assoc :author-id (:id user))
                       (loi/marshall user)
                       (->> (loi/insert-loi-rev! tx)))]
      (when (= closed-status (:status loi))
        (rebase-loi-onto-closure! tx user loi))
      inserted)))

;; Versioned Data ;;

(defn get-versioned-data
  [db-spec type status]
  (->> {:status status :type type}
       (versioned-data/get-latest-by-type-and-status db-spec)
       (versioned-data/unmarshall)))

(defn add-versioned-data!
  [db-spec type status body]
  (->> (versioned-data/marshall type status body)
       (versioned-data/insert! db-spec)
       (versioned-data/unmarshall)))

(defn list-versioned-data
  "Version metadata (id, event-date, status, type) without bodies,
   newest first."
  [db-spec type limit]
  (->> (versioned-data/list-by-type db-spec {:type type :limit limit})
       (mapv (fn [row]
               {:id         (str (:id row))
                :event-date (str (:event_date row))
                :status     (:status row)
                :type       (:type row)}))))

(defn get-versioned-data-by-id
  "Single version with body, or nil. Returns
   {:id ... :event-date ... :status ... :body <unmarshalled>}."
  [db-spec id]
  (when-let [row (versioned-data/get-by-id db-spec {:id id})]
    {:id         (str (:id row))
     :event-date (str (:event_date row))
     :status     (:status row)
     :body       (versioned-data/unmarshall row)}))

;; DB connection pooling ;;

(defn- ->hikari-opts [{:keys [dbtype dbname host user port password]}]
  {:adapter dbtype
   :username user
   :password password
   :database-name dbname
   :server-name host
   :port-number port})

(defn setup-connection-pool [db-spec]
  {:datasource (hikari/make-datasource (->hikari-opts db-spec))})

(defn stop-connection-pool [{:keys [datasource]}]
  (hikari/close-datasource datasource))

(comment
  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (get-users-drafts db-spec {:id "a112fd21-9470-480a-8961-6ddd308f58d9"})
  (hikari/validate-options (->hikari-opts db-spec))
  (def cp1 (setup-connection-pool db-spec))
  (get-user-by-email cp1 {:email "admin@lipas.fi"})
  (add-reminder! db-spec {:account-id "94b1344e-6e06-4ebb-bfd8-1be28b2f511e"
                          :event-date "2019-01-01T00:00:00.000Z"
                          :status "pending"
                          :body {:message "Muista banaani"}})
  (get-sports-sites-by-city-code db-spec 911))
