(ns lipas.ui.org.subs
  (:require [clojure.string :as str]
            [lipas.roles :as roles]
            [lipas.schema.org :as org-schema]
            [malli.core :as m]
            [re-frame.core :as rf]))

(rf/reg-sub ::user-orgs
  (fn [db _]
    (:orgs (:user db))))

(rf/reg-sub ::user-orgs-by-id
  :<- [::user-orgs]
  (fn [orgs _]
    (into {} (map (juxt :id identity) orgs))))

(rf/reg-sub ::user-org-by-id
  :<- [::user-orgs-by-id]
  (fn [orgs [_ id]]
    (get orgs id)))

(rf/reg-sub ::editing-org
  (fn [db _]
    (get-in db [:org :editing-org])))

(defn- empty-string->nil [x]
  (if (and (string? x) (str/blank? x))
    nil
    x))

(defn- prepare-org-for-validation [org]
  (when org
    (-> org
        ;; Convert string ID to UUID for validation
        (update :id #(if (string? %) (uuid %) %))
        ;; Remove old phone field if it exists
        (update :data #(dissoc % :phone))
        ;; Convert empty strings to nil in contact fields
        (update-in [:data :primary-contact :phone] empty-string->nil)
        (update-in [:data :primary-contact :email] empty-string->nil)
        (update-in [:data :primary-contact :website] empty-string->nil)
        (update-in [:data :primary-contact :reservations-link] empty-string->nil))))

(rf/reg-sub ::org-validation-errors
  :<- [::editing-org]
  (fn [org _]
    (when-let [prepared-org (prepare-org-for-validation org)]
      (m/explain org-schema/org-form-validation prepared-org))))

(rf/reg-sub ::org-valid?
  :<- [::org-validation-errors]
  (fn [errors _]
    (nil? errors)))

(rf/reg-sub ::current-tab
  (fn [db _]
    (get-in db [:org :current-tab] "contact")))

(rf/reg-sub ::org-users
  (fn [db _]
    (:users (:org db))))

(rf/reg-sub ::all-users
  (fn [db _]
    (:all-users (:org db))))

(rf/reg-sub ::all-users-options
  :<- [::all-users]
  (fn [users _]
    (map (fn [{:keys [id email]}]
           {:value id
            :label email})
         users)))

(rf/reg-sub ::add-user-form
  (fn [db _]
    (get-in db [:org :add-user-form])))

(rf/reg-sub ::add-user-email-form
  (fn [db _]
    (get-in db [:org :add-user-email-form])))

(rf/reg-sub ::is-lipas-admin
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user _]
    (roles/check-privilege user {} :users/manage)))

(rf/reg-sub ::is-org-admin?
  (fn [[_ org-id] _]
    (rf/subscribe [:lipas.ui.user.subs/check-privilege {:org-id org-id} :org/manage]))
  (fn [v _]
    v))

(rf/reg-sub ::is-org-member?
  (fn [[_ org-id] _]
    (rf/subscribe [:lipas.ui.user.subs/check-privilege {:org-id org-id} :org/member]))
  (fn [v _]
    v))
