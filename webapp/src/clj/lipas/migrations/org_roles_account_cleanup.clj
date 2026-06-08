(ns lipas.migrations.org-roles-account-cleanup
  "Drop the now-redundant org-scoped roles (org-admin / org-user / org-editor)
  from `account.permissions.roles`. After this, **org membership is the single
  source** for org-scoped roles: they are projected at login from the org
  document (membership ⇒ org-user baseline; the reserved \"admin\" member role ⇒
  org-admin; org-editor only ever via a catalog template). See
  `lipas.backend.org/member->roles`.

  Safe because `org-event-log` already backfilled a membership entry for every
  account org role (verified: every account org-role tuple is reproduced by
  membership projection, no org-ids missing, none empty). This migration is the
  one `org-event-log`'s docstring deferred — \"a later migration drops them once
  derive-at-login is verified\".

  Self-contained on purpose (raw jsonb, no `org/*` calls — the from-scratch
  lesson). **Defensive:** an org role is stripped only when current membership
  actually covers all its org-ids; an uncovered one is kept and logged so the
  migration can never silently revoke access. Runs after `org-roles-unify`."
  (:require [lipas.backend.db.utils] ; jsonb <-> Clojure protocol extensions
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as log]))

(def ^:private org-scoped #{"org-admin" "org-user" "org-editor"})

(defn- membership-set
  "#{[org-id-str user-id-str]} over the current org documents — the ground truth
  for who is a member of what."
  [db]
  (into #{}
        (for [{:keys [org-id document]}
              (jdbc/execute! db ["SELECT org_id, document FROM org_current"]
                             {:builder-fn rs/as-unqualified-kebab-maps})
              m (:members document)]
          [(str org-id) (str (:user-id m))])))

(defn- accounts-with-org-roles [db]
  (jdbc/execute! db
                 ["SELECT id, permissions FROM account
                   WHERE permissions->'roles' @> '[{\"role\":\"org-admin\"}]'
                      OR permissions->'roles' @> '[{\"role\":\"org-user\"}]'
                      OR permissions->'roles' @> '[{\"role\":\"org-editor\"}]'"]
                 {:builder-fn rs/as-unqualified-kebab-maps}))

(defn migrate-up [{:keys [db]}]
  (log/info "org-roles-account-cleanup: dropping redundant account-level org roles")
  (let [members (membership-set db)
        accts   (accounts-with-org-roles db)]
    (loop [accts accts, stripped 0, kept 0]
      (if-let [{:keys [id permissions]} (first accts)]
        (let [uid       (str id)
              roles     (:roles permissions)
              ;; org-ids in account roles are stored under :org_id (snake)
              covered?  (fn [r] (every? #(contains? members [(str %) uid]) (:org_id r)))
              org-role? (fn [r] (org-scoped (:role r)))
              keep?     (fn [r]
                          (cond
                            (not (org-role? r)) true            ; non-org roles untouched
                            (covered? r)        false           ; redundant → strip
                            :else
                            (do (log/warn "org-roles-account-cleanup: KEEPING uncovered org role"
                                          {:account uid :role r})
                                true)))                         ; defensive: no silent revoke
              new-roles (vec (filter keep? roles))
              dropped   (- (count roles) (count new-roles))]
          (when (pos? dropped)
            (jdbc/execute! db ["UPDATE account SET permissions = ? WHERE id = ?"
                               (assoc permissions :roles new-roles) id]))
          (recur (rest accts) (+ stripped dropped)
                 (+ kept (- (count (filter org-role? roles)) dropped))))
        (log/info "org-roles-account-cleanup: stripped" stripped
                  "redundant org roles;" kept "uncovered kept")))))

(defn migrate-down [{:keys [db]}]
  ;; Best-effort inverse: rebuild account-level org-admin/org-user from current
  ;; membership (the pre-cleanup redundant state). org-editor was projection-only
  ;; and never lived on accounts, so it is not restored.
  (log/info "org-roles-account-cleanup: rebuilding account org roles from membership")
  (let [orgs (jdbc/execute! db ["SELECT org_id, document FROM org_current"]
                            {:builder-fn rs/as-unqualified-kebab-maps})
        ;; user-id-str -> {:admins #{org} :users #{org}}
        by-user (reduce
                  (fn [acc {:keys [org-id document]}]
                    (reduce (fn [acc m]
                              (let [uid   (str (:user-id m))
                                    admin? (contains? (set (:roles m)) "admin")]
                                (cond-> (update-in acc [uid :users] (fnil conj #{}) (str org-id))
                                  admin? (update-in [uid :admins] (fnil conj #{}) (str org-id)))))
                            acc (:members document)))
                  {} orgs)]
    (doseq [[uid {:keys [admins users]}] by-user]
      (when-let [acct (first (jdbc/execute! db
                                            ["SELECT id, permissions FROM account WHERE id = ?::uuid" uid]
                                            {:builder-fn rs/as-unqualified-kebab-maps}))]
        (let [perms     (:permissions acct)
              others    (remove #(org-scoped (:role %)) (:roles perms))
              rebuilt   (cond-> (vec others)
                          (seq users)  (conj {:role "org-user"  :org_id (vec users)})
                          (seq admins) (conj {:role "org-admin" :org_id (vec admins)}))]
          (jdbc/execute! db ["UPDATE account SET permissions = ? WHERE id = ?::uuid"
                             (assoc perms :roles rebuilt) uid]))))))

(comment
  (require '[user])
  (def test-db (:lipas/db integrant.repl.state/system))
  (migrate-up {:db test-db})
  (migrate-down {:db test-db}))
