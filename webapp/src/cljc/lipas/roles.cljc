(ns lipas.roles)

(def privileges
  {;; Oikeus lisätä ja muokata liikuntapaikkoja
   :create {}
   ;; Oikeus nähdä kaikki liikuntapaikat, muut kohteet ja niihin syötetyt
   ;; perustiedot ja lisätiedot (oma tietomalli)
   :view {}
   :edit {}

   :view-activities {}
   :edit-activities {}

   ;; "Muita kohteita"
   :create-loi {}
   :view-loi {}
   :edit-loi {}

   ;; "Erillissisältö"
   :view-floorball {}
   :edit-floorball {}

   :analysis-tool {}

   :user-self {}
   :user-management {}})

(def roles
  {;; Kaikkien sisältöjen hallinta oikeudet
   :admin
   {:privileges (set (keys privileges))}

   ;; Kirjautumattomat ja kaikki käyttäjät
   :everyone
   {:privileges #{:view
                  ;; Toistaiseksi uusi ominaisuus piilossa,
                  ;; myöhemmin kaikille.
                  ;; :view-loi
                  :view-floorball}}

   :basic-manager
   {:privileges #{:create
                  :edit
                  :view-activities
                  :analysis-tool}}

   :activities-manager
   {:privileges #{:view-activities
                  :view-loi
                  :edit-activities
                  :create-loi
                  :edit-loi}}

   :floorball-manager
   {:privileges #{:edit-floorball}}})

(def test-org
  {:permissions {:roles []}})

(def test-user
  {:permissions {:roles [;; App wide role
                         {:role :admin}
                         {:city-code 837 :role :basic-information}
                         {:type-code 2001 :activity "outdoor-recreation-areas" :role :activities-information}
                         ;; Possible, might not be needed
                         ;; Combining lipas-id with others doesn't make sense, but possible
                         {:type-code 1 :city-code 1 :role :activities-information}
                         {:lipas-id 1 :role :basic-information}
                         {:lipas-id 1 :role :activities-information}]}})

(defn get-privileges [active-roles]
  (-> (mapcat (fn [role-k]
                (:privileges (get roles role-k)))
              active-roles)
      set))

(defn check-privilege
  "E.g. user-management (admin)"
  [user required-privilege]
  (let [active-roles (->> (:permissions user)
                          (:roles)
                          (filter (fn [role]
                                    (and (nil? (:city-code role))
                                         (nil? (:type-code role))
                                         (nil? (:lipas-id role)))))
                          (map :role))
        privileges (get-privileges active-roles)]
    (contains? privileges required-privilege)))

(defn check-site-privilege
  ([user site required-privilege]
   (check-site-privilege user site nil required-privilege))
  ([user site activity required-privilege]
   (let [;; TODO: Nested somewhere
         {:keys [lipas-id city-code]} site
         type-code (-> site :type :type-code)

         active-roles (->> (:permissions user)
                           (:roles)
                           (filter (fn [role]
                                     (and (or (nil? (:city-code role))
                                              (= city-code (:city-code role)))
                                          (or (nil? (:type-code role))
                                              (= type-code (:type-code role)))
                                          (or (nil? (:activity role))
                                              (= activity (:activity role)))
                                          (or (nil? (:lipas-id role))
                                              (= lipas-id (:lipas-id role))))))
                           (map :role))
         privileges (get-privileges active-roles)]
     (contains? privileges required-privilege))))
