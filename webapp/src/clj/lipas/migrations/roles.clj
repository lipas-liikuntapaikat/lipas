(ns lipas.migrations.roles
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.db.db :refer [get-users]]
            [lipas.backend.db.user :as user]))

(defn permissions->roles [permissions]
  (let [{:keys [admin? sports-sites cities types
                all-cities? all-types?
                activities]} permissions

        ;; TODO: Add floorball-manager role usinbg email?
        ;; check lipas.ui.sports-sites.floorball.subs/floorball-user?
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

  (testing "ignore types if all-types given"
    (is (= [{:role :basic-manager
             :city-code 853}]
           (permissions->roles {:types [3210 3120 3110 3130], :cities [853], :draft? true, :all-types? true}))))

  (testing "existing user with access-to-activity"
    (is (= [{:role :activities-manager
             :activity "outdoor-recreation-facilities"}
            {:role :activities-manager
             :activity "fishing"}]
           (permissions->roles {:activities ["outdoor-recreation-facilities"
                                             "fishing"]}))))
  )

(defn migrate-up [{:keys [db] :as _config}]
  (let [users (get-users db)]
    (doseq [user users
            :let [roles (permissions->roles (:permissions user))]]
      (user/update-user-permissions! db {:id (:id user)
                                    :permissions (assoc (:permissions user) :roles roles)})
      (println (dissoc user :history :user-data))
      (println roles)
      (println))))

(defn migrate-down [{:keys [db] :as _config}]
  (let [users (get-users db)]
    (doseq [user users]
      (user/update-user-permissions! db {:id (:id user)
                                         :permissions (dissoc (:permissions user))}))))

(comment
  (migrate-up {:db (user/db)}))
