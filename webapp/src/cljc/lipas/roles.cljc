(ns lipas.roles)

(def privileges
  {:create {:doc "Oikeus lisätä liikuntapaikkoja"}
   :view {:doc "Oikeus nähdä liikuntapaikat ja niihin liittyvät perustiedot
               ja lisätiedot"}
   :edit {:doc "Oikeus muokata liikuntapaikkoja"}

   :view-activity {:doc "Nähdä UTP tiedot"}
   :edit-activity {:doc "Oikeus muokata UTP tietoja"}

   :create-loi {:doc "Oikeus luoda Muita kohteita"}
   :view-loi {:doc "Oikeus nähdä Muita kohteita"}
   :edit-loi {:doc "Oikeus muokata Muita kohteita"}

   :view-floorball {:doc "Oikeus nähdä Salibandy lisätiedot"}
   :edit-floorball {:doc "Oikeus muokata Salibandy lisätietoja"}

   :analysis-tool {:doc "Oikeus käyttää analyysityökalua"}

   :user-self {:doc "Oikeus omiin käyttäjätietoihin (salasanan vaihto jne)"}
   :user-management {:doc "Käyttäjien hallinta (admin)"}})

(def roles
  {:admin
   ;; all privileges
   {:privileges (set (keys privileges))}

   ;; Unsigned users, basis for everyone
   :default
   {:privileges #{:view
                  ;; New feature currently hidden,
                  ;; will later be enabled for everyone.
                  ;; :view-loi
                  :view-floorball}}

   :basic-manager
   {:privileges #{:create
                  :edit
                  :view-activity
                  :analysis-tool}}

   :activities-manager
   {:privileges #{:view-activity
                  :edit-activity
                  :view-loi ;; Temporarily only enabled here
                  :create-loi
                  :edit-loi}}

   :floorball-manager
   {:privileges #{:edit-floorball}}})

(defn get-privileges [active-roles]
  (set (mapcat (fn [role-k]
                 (:privileges (get roles role-k)))
               active-roles)))

(defn select-role
  "Check if the role is active with given selection.

  When a role is missing (or has a nil value) for one of
  the conditions, it is always active."
  [selection role]
  (when (and (or (nil? (:city-code role))
                 (= (:city-code selection) (:city-code role)))
             (or (nil? (:type-code role))
                 (= (:type-code selection) (:type-code role)))
             (or (nil? (:activity role))
                 (= (:activity selection) (:activity role)))
             (or (nil? (:lipas-id role))
                 (= (:lipas-id selection) (:lipas-id role))))
    ;; Cast to keyword, DB and JSON return string values
    (keyword (:role role))))

(defn check-privilege
  "Check if given user has the asked privilege
  which is active for defined context.

  Context:
  - :site
  - :activity"
  [user {:keys [site activity] :as _role-context} required-privilege]
  (let [selection {:type-code (-> site :type :type-code)
                   :lipas-id (:lipas-id site)
                   :city-code (-> site :location :city :city-code)
                   :activity activity}

        ;; TODO: Later this can be extended to check roles from org
        active-roles (->> (:permissions user)
                          (:roles)
                          (keep (fn [role]
                                  (select-role selection role)))
                          (into #{:default}))
        privileges (get-privileges active-roles)]
    (contains? privileges required-privilege)))
