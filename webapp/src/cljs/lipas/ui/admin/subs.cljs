(ns lipas.ui.admin.subs
  (:require [clojure.string :as str]
            [lipas.roles :as roles]
            [lipas.ui.subs :as ui-subs]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

(rf/reg-sub ::users
  ;; The root lives in base (dev-tools impersonation reads it too) — see
  ;; "Cross-module root subs" in lipas.ui.subs.
  :<- [::ui-subs/admin-users]
  (fn [users _]
    users))

(rf/reg-sub ::users-status
  (fn [db _]
    (-> db :admin :users-status)))

(rf/reg-sub ::users-filter
  (fn [db _]
    (-> db :admin :users-filter)))

(defn ->users-list-entry
  [tr user]
  {:id (-> user :id)
   :email (-> user :email)
   :firstname (-> user :user-data :firstname)
   :lastname (-> user :user-data :lastname)
   :roles (->> user :permissions :roles
               (map (fn [x]
                      (tr (keyword :lipas.user.permissions.roles.role-names (:role x)))))
               (str/join ", "))})

(rf/reg-sub ::users-list
  :<- [::users]
  :<- [::users-status]
  :<- [::users-filter]
  :<- [:lipas.ui.subs/translator]
  (fn [[users status filter-text tr] _]
    (let [users (->> users
                     vals
                     (filter (comp #{status} :status))
                     (map (partial ->users-list-entry tr)))]
      (if (seq filter-text)
        (filter
          #(-> %
               str
               str/lower-case
               (str/includes? (str/lower-case filter-text))) users)
        users))))

(rf/reg-sub ::archived-users-list
  :<- [::archived-users]
  :<- [::users-filter]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  :<- [:lipas.ui.sports-sites.subs/active-types]
  (fn [[users filter-text cities types] _]
    (let [users (->> users (map (partial ->users-list-entry cities types)))]
      (if (not-empty filter-text)
        (filter
          #(-> %
               str
               str/lower-case
               (str/includes? (str/lower-case filter-text))) users)
        users))))

(rf/reg-sub ::selected-user
  (fn [db _]
    (get-in db [:admin :users (-> db :admin :selected-user)])))

(rf/reg-sub ::editing-user
  (fn [db _]
    (get-in db [:admin :editing-user])))

(rf/reg-sub ::edit-role
  (fn [db _]
    (if-let [idx (:edit-role (:admin db))]
      (assoc (get-in db [:admin :editing-user :permissions :roles idx]) :editing? true)
      (get-in db [:admin :new-role]))))

(defn prettify-timestamp [s]
  (-> s
      (str/replace "T" " ")
      (str/split ".")
      first))

(rf/reg-sub ::user-history
  :<- [::editing-user]
  (fn [user _]
    (->> user :history :events
         (map #(update % :event-date prettify-timestamp))
         (sort-by :event-date cutils/reverse-cmp))))

;; NOTE: the ::types-list/::cities-list/::sites-list/::activities-list option
;; subs live in lipas.ui.roles.editor (the shared role-spec editor) — the
;; copies that used to sit here are gone (F24).

(rf/reg-sub ::magic-link-dialog-open?
  (fn [db _]
    (-> db :admin :magic-link-dialog-open?)))

(rf/reg-sub ::magic-link-variants
  (fn [db _]
    (-> db :admin :magic-link-variants)))

(rf/reg-sub ::selected-magic-link-variant
  (fn [db _]
    (-> db :admin :selected-magic-link-variant)))

(rf/reg-sub ::selected-colors
  (fn [db _]
    (-> db :admin :color-picker)))

(rf/reg-sub ::selected-tab
  (fn [db _]
    (-> db :admin :selected-tab)))

;;; Orgs ;;;

(rf/reg-sub ::orgs
  ;; The root lives in base (profile role-context display reads it too)
  ;; — see "Cross-module root subs" in lipas.ui.subs.
  :<- [::ui-subs/admin-orgs]
  (fn [orgs _]
    orgs))

;; Resolves an org-id (role context value) to its org for name display in the
;; user roles list — see lipas.ui.user.subs/context-value-name. Org roles are no
;; longer hand-assigned, but this keeps any historical org-id context readable.
(rf/reg-sub ::org
  :<- [::orgs]
  (fn [orgs [_ id]]
    (get orgs id)))

;; Site History subscriptions

(rf/reg-sub ::site-history-search-id
  (fn [db _]
    (get-in db [:admin :site-history :search-id])))

(rf/reg-sub ::site-history-results
  (fn [db _]
    (get-in db [:admin :site-history :results])))

(rf/reg-sub ::site-history-loading?
  (fn [db _]
    (get-in db [:admin :site-history :loading?] false)))

(rf/reg-sub ::site-history-error
  (fn [db _]
    (get-in db [:admin :site-history :error])))
