(ns lipas.roles)

;; :doc here is just a developer comment, translations are elsewhere (if we even need
;; translations for privileges)
;; Use namespaced keys for easier searching in the codebase
(def privileges
  {:site/create-edit {:doc "Oikeus lisätä ja muokata liikuntapaikkoja"}
   ;; TODO: Not yet checked anywhere
   :site/view {:doc "Oikeus nähdä liikuntapaikat ja niihin liittyvät perustiedot ja lisätiedot"}
   :site/edit-any-status {:doc "Oikeus muokata liikuntapaikkoja jotka esim. poistettu pysyvästi käytöstä"}

   :activity/view {:doc "Nähdä UTP tiedot"}
   :activity/edit {:doc "Oikeus muokata UTP tietoja"}

   :loi/create-edit {:doc "Oikeus luoda ja muokata Muita kohteita"}
   :loi/view {:doc "Oikeus nähdä Muita kohteita"}

   :floorball/view {:doc "Oikeus nähdä Salibandy lisätiedot"}
   :floorball/edit {:doc "Oikeus muokata Salibandy lisätietoja"}

   :analysis-tool/use {:doc "Oikeus käyttää analyysityökalua"}

   :users/manage {:doc "Käyttäjien hallinta (admin)"}

   :ptv/manage {:doc ""}})

(def basic #{:site/create-edit
             :activity/view
             :analysis-tool/use})

(def roles
  {:admin
   ;; all privileges
   {:sort 0
    :assignable true
    :privileges (set (keys privileges))
    ;; This is kind of duplicated from specs, not sure if needed or
    ;; if the UI can introspect the spec.
    ;; These are also used to get the ORDER of UI fields for edit.
    :required-context-keys []
    :optional-context-keys []}

   ;; Unsigned users, basis for everyone
   ;; Can't be assigned to users on the user-management
   :default
   {:assignable false
    :privileges #{:site/view
                  ;; New feature currently hidden,
                  ;; will later be enabled for everyone.
                  ;; :loi/view
                  :floorball/view}
    :required-context-keys []
    :optional-context-keys []}

   ;; Basic privileges
   :type-manager
   {:sort 10
    :assignable true
    :privileges basic
    :required-context-keys [:type-code]
    :optional-context-keys [:city-code]}

   :city-manager
   {:sort 11
    :assignable true
    :privileges basic
    :required-context-keys [:city-code]
    :optional-context-keys [:type-code]}

   :site-manager
   {:sort 12
    :assignable true
    :privileges basic
    :required-context-keys [:lipas-id]
    :optional-context-keys []}

   :activities-manager
   {:sort 20
    :assignable true
    :privileges #{:activity/view
                  :activity/edit

                  :loi/view ;; Temporarily only enabled here
                  :loi/create-edit}
    :required-context-keys [:activity]
    :optional-context-keys [:city-code :type-code]}

   :floorball-manager
   {:sort 30
    :assignable true
    :privileges #{:floorball/edit}
    :required-context-keys []
    :optional-context-keys [:type-code]}})

(defn role-sort-fn
  [{:keys [role]}]
  (:sort (get roles role)))

(defn get-privileges
  "Get set of privileges for given list of roles"
  [active-roles]
  (set (mapcat (fn [role-k]
                 (:privileges (get roles role-k)))
               active-roles)))

(defn select-role
  "Check if the role is active with given role-context.

  When a role is missing (or has a nil value) for one of
  the conditions, it is always active.
  ::any value can be used to check if there are any
  role-context where the privilege is given,
  e.g. to check for :create privilege before
  city-code or type-code is selected (available
  values for those are then filtered using privileges
  in the create form.)"
  [role-context role]
  (when (and (or (nil? (:city-code role))
                 (= ::any (:city-code role-context))
                 (contains? (:city-code role) (:city-code role-context)))
             (or (nil? (:type-code role))
                 (= ::any (:type-code role-context))
                 (contains? (:type-code role) (:type-code role-context)))
             (or (nil? (:activity role))
                 (= ::any (:activity role-context))
                 (contains? (:activity role) (:activity role-context)))
             (or (nil? (:lipas-id role))
                 (= ::any (:lipas-id role-context))
                 (contains? (:lipas-id role) (:lipas-id role-context))))
    (:role role)))

(defn check-privilege
  "Check if given user has the asked privilege
  which is active for defined context.

  Context:
  - :city-code
  - :type-code
  - :lipas-id
  - :activity"
  [user {:as role-context} required-privilege]
  (let [active-roles (->> (:permissions user)
                          (:roles)
                          (keep (fn [role] (select-role role-context role)))
                          (into #{:default}))
        privileges (get-privileges active-roles)
        overrides (:dev/overrides user)]
    (if (contains? overrides required-privilege)
      (get overrides required-privilege)
      (contains? privileges required-privilege))))

(defn site-roles-context
  "Create role-context for given site"
  [site]
  {:lipas-id  (:lipas-id site)
   :type-code (-> site :type :type-code)
   :city-code (-> site :location :city :city-code)})

(defn check-role
  "Check if user has the given role USUALLY
  this should not be used to grant privileges.
  Admin role is ocassionally checked for tracking etc. non
  authorization purposes."
  [user role]
  (->> user
       :permissions
       :roles
       (some (fn [x]
               (= role (:role x))))))

(defn conform-roles [roles]
  ;; Doesn't seem to work due to multi-spec?
  ;; :type is conformed, but coll-of :into #{} doesn't work
  ;; (st/conform! :lipas.user.permissions/roles roles st/json-transformer)
  (mapv (fn [role]
          (cond-> (update role :role keyword)
            (:type-code role) (update :type-code set)
            (:city-code role) (update :city-code set)
            (:lipas-id role) (update :lipas-id set)
            (:activity role) (update :activity set)))
        roles))
