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

;; Orgs the user may set as a site's owner: those where they hold
;; :site/create-edit (org-editor of the org, or lipas-admin). Drives the
;; owner-org autocomplete in the sports-site form. Mirrors the backend gate
;; `owner-org-assignment-authorized?`.
(rf/reg-sub ::ownable-orgs
  :<- [::user-orgs]
  :<- [:lipas.ui.user.subs/user-data]
  (fn [[orgs user] _]
    (filterv (fn [{:keys [id]}]
               ;; :org-id role-context must be a set (set-intersection matcher)
               (roles/check-privilege user {:org-id #{(str id)}} :site/create-edit))
             orgs)))

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
    ;; Default to the most-used "Kohteet" (our-sites) tab, which is shown first.
    (get-in db [:org :current-tab] "our-sites")))

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

(rf/reg-sub ::is-lipas-admin
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user _]
    (roles/check-privilege user {} :users/manage)))

(rf/reg-sub ::is-org-admin?
  (fn [[_ org-id] _]
    ;; :org-id role-context must be a set (set-intersection matcher, like :activity)
    (rf/subscribe [:lipas.ui.user.subs/check-privilege {:org-id #{org-id}} :org/manage]))
  (fn [v _]
    v))

(rf/reg-sub ::is-org-member?
  (fn [[_ org-id] _]
    (rf/subscribe [:lipas.ui.user.subs/check-privilege {:org-id #{org-id}} :org/member]))
  (fn [v _]
    v))

;; --- Capability gating (UX plan §2). Every new control gates through this. ---
(rf/reg-sub ::can?
  (fn [[_ _capability org-id] _]
    [(rf/subscribe [::is-lipas-admin])
     (rf/subscribe [::is-org-admin? org-id])
     (rf/subscribe [::is-org-member? org-id])])
  (fn [[lipas-admin? org-admin? org-member?] [_ capability _org-id]]
    (case capability
      ;; lipas-admin only — the ceiling
      (:org/edit-type+ownership :org/edit-catalog :org/create)
      (boolean lipas-admin?)

      ;; lipas-admin or org-admin
      (:org/edit-contact :org/edit-ptv :org/manage-members :org/grant-site-edit
                         :org/view-history :org/edit-instructions)
      (boolean (or lipas-admin? org-admin?))

      ;; any member
      :org/view
      (boolean (or lipas-admin? org-admin? org-member?))

      false)))

;; --- Role-template catalog (the org's ceiling) ---
(rf/reg-sub ::org-templates
  :<- [::editing-org]
  (fn [org _]
    (:role-templates org)))

(rf/reg-sub ::member-template-options
  :<- [::org-templates]
  (fn [catalog _]
    ;; catalog keys → {:value "<key>" :label "<label or key>"} for multi-select
    (->> catalog
         (map (fn [[k v]]
                {:value (name k)
                 :label (or (:label v) (name k))}))
         (sort-by :label)
         vec)))

(rf/reg-sub ::invite-member-form
  (fn [db _]
    (get-in db [:org :invite-member-form] {})))

(rf/reg-sub ::catalog-editor
  (fn [db _]
    (get-in db [:org :catalog-editor])))

;; --- Our sites (Phase D) ---
(rf/reg-sub ::org-owned-sites
  (fn [db _]
    (get-in db [:org :sites :owned])))

(rf/reg-sub ::org-editable-sites
  (fn [db _]
    (get-in db [:org :sites :editable])))

(rf/reg-sub ::owned-sites-count
  :<- [::org-owned-sites]
  (fn [owned _]
    (or (:total owned) (count (:sites owned)) 0)))

;; in-flight flag for the (slow, synchronous) reclaim/approve op → drives the
;; dialog spinner + button disabling
(rf/reg-sub ::reclaiming?
  (fn [db _]
    (boolean (get-in db [:org :reclaiming?]))))

(rf/reg-sub ::our-sites-filter
  (fn [db _]
    (get-in db [:org :sites :filter] "owned")))

(rf/reg-sub ::our-sites
  :<- [::our-sites-filter]
  :<- [::org-owned-sites]
  :<- [::org-editable-sites]
  (fn [[flt owned editable] _]
    ;; org-sites endpoint returns {:total .. :sites [..]}
    (:sites (if (= flt "editable") editable owned))))

(rf/reg-sub ::site-editors
  (fn [db [_ lipas-id]]
    (get-in db [:org :site-editors lipas-id])))

;; --- History (Phase E) ---
(rf/reg-sub ::org-history
  (fn [db _]
    (get-in db [:org :history])))

;; --- Take-over approval queue (Phase E, lipas-admin) ---
(rf/reg-sub ::takeover-requests
  (fn [db _]
    (get-in db [:org :takeover-requests])))

;; --- Claim impact warning dialog ---
(rf/reg-sub ::claim-dialog
  (fn [db _]
    (get-in db [:org :claim-dialog])))

(rf/reg-sub ::takeover-preview
  (fn [db _]
    (get-in db [:org :takeover-preview])))
