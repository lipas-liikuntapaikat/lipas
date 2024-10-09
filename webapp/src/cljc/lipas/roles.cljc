(ns lipas.roles)

;; :doc here is just a developer comment, translations are elsewhere (if we even need
;; translations for privileges)
;; Use namespaced keys for easier searching in the codebase
(def privileges
  {:site/create-edit {:doc "Oikeus lisätä ja muokata liikuntapaikkoja"}
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

(def roles
  {:admin
   ;; all privileges
   {:assignable true
    :privileges (set (keys privileges))}

   ;; Unsigned users, basis for everyone
   ;; Can't be assigned to users on the user-management
   :default
   {:assignable false
    :privileges #{:view
                  ;; New feature currently hidden,
                  ;; will later be enabled for everyone.
                  ;; :loi/view
                  :floorball/view}}

   :basic-manager
   {:assignable true
    :privileges #{:site/create-edit
                  :activity/view
                  :analysis-tool/use}}

   :activities-manager
   {:assignable true
    :privileges #{:activity/view
                  :activity/edit

                  :loi/view ;; Temporarily only enabled here
                  :loi/create-edit}}

   :floorball-manager
   {:assignable true
    :privileges #{:floorball/edit}}})

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
                 (= (:city-code role-context) (:city-code role)))
             (or (nil? (:type-code role))
                 (= ::any (:type-code role-context))
                 (= (:type-code role-context) (:type-code role)))
             (or (nil? (:activity role))
                 (= ::any (:activity role-context))
                 (= (:activity role-context) (:activity role)))
             (or (nil? (:lipas-id role))
                 (= ::any (:lipas-id role-context))
                 (= (:lipas-id role-context) (:lipas-id role))))
    ;; Cast to keyword, DB and JSON return string values
    ;; NOTE: Or BE cnd FE ould conform the value
    (keyword (:role role))))

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
        privileges (get-privileges active-roles)]
    (contains? privileges required-privilege)))

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
               ;; NOTE: Could also check that role doesn't have any role-context?
               ;; SHOULDN'T matter for admin role.
               (= role (keyword (:role x)))))))
