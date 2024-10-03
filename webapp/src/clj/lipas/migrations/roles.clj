(ns lipas.migrations.roles
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.db.db :refer [get-users]]))

(defn permissions->roles [permissions]
  (let [{:keys [admin? sports-sites cities types
                all-cities? all-types?
                activities]} permissions

        roles (for [type-code (if all-types? [nil] types)
                    city-code (if all-cities? [nil] cities)]
                (cond-> {:role :basic-manager}
                  type-code (assoc :type-code type-code)
                  city-code (assoc :city-code city-code)))]
    (cond-> (vec roles)
      admin? (conj {:role :admin})
      sports-sites (into (map (fn [id]
                                {:role :basic-manager
                                 :lipas-id id})
                              sports-sites))
      activities (into (map (fn [activity]
                          {:role :activities-manager
                           :activity activity})
                        activities))
      )))

(deftest permissions->roles-test
  (is (= [{:role :admin}]
         (permissions->roles {:admin? true})))

  (testing "access-to-sports-site"
    (is (= [{:role :basic-manager
             :lipas-id 1}
            {:role :basic-manager
             :lipas-id 2}]
           (permissions->roles {:sports-sites [1 2]}))))

  (testing "publish/modify-sports-site implicitly case"
    (is (= [{:role :basic-manager
             :type-code 1}
            {:role :basic-manager
             :type-code 2}]
           (permissions->roles {:types [1 2]
                                :all-cities? true})))

    (is (= [{:role :basic-manager
             :city-code 1}
            {:role :basic-manager
             :city-code 2}]
           (permissions->roles {:cities [1 2]
                                :all-types? true})))

    (testing "both cities and types listed"
      (is (= [{:role :basic-manager
               :city-code 1
               :type-code 100}
              {:role :basic-manager
               :city-code 2
               :type-code 100}
              {:role :basic-manager
               :city-code 1
               :type-code 200}
              {:role :basic-manager
               :city-code 2
               :type-code 200}]
             (permissions->roles {:cities [1 2]
                                  :types [100 200]})))))

  ;; ???
  ;; {:types [3210 3120 3110 3130], :cities [853], :draft? true, :all-types? true}

  (testing "existing user with access-to-activity"
    (is (= [{:role :activities-manager
             :activity "outdoor-recreation-facilities"}
            {:role :activities-manager
             :activity "fishing"}]
           (permissions->roles {:activities ["outdoor-recreation-facilities"
                                             "fishing"]}))))
  )

(defn migrate-up [{:keys [db] :as config}]
  (let [users (get-users db)]
    (doseq [user users]
      (println (dissoc user :history :user-data))
      (println (permissions->roles (:permissions user)))
      (println))))

(defn migrate-down [config]
  )

(comment
  (migrate-up {:db (user/db)}))
