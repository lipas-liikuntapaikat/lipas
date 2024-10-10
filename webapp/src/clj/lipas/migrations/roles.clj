(ns lipas.migrations.roles
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [lipas.backend.db.db :refer [get-users]]
            [lipas.backend.db.user :as user]
            [lipas.schema.core]
            [taoensso.timbre :as log]))

(defn floorball-user?
  [email]
  (and email
       (or (str/ends-with? email "@salibandy.fi")
           (str/ends-with? email "@lipas.fi"))))

(defn permissions->roles [permissions email]
  (let [{:keys [admin? sports-sites cities types
                all-cities? all-types?
                activities]} permissions

        roles (cond
                admin?
                [{:role :admin}]

                ;; No permissions
                (= {:draft? true} permissions)
                []

                (and all-cities? all-types?)
                (do
                  (log/errorf "Surprising user permissions, ignored and given no role: %s" email)
                  [])

                (and (seq cities) all-types?)
                [{:role :city-manager
                  :city-code (set cities)}]

                (and (seq types) all-cities?)
                [{:role :type-manager
                  :type-code (set types)}]

                (and (seq types) (seq cities))
                [{:role (if (< (count types) (count cities))
                          :type-manager
                          :city-manager)
                  :type-code (set types)
                  :city-code (set cities)}]

                :else
                (do
                  (log/errorf "How did we get here? %s" email)
                  []))

        result (cond-> (vec roles)
                 (and (floorball-user? email) (not admin?)
                      (or all-types? (seq types)))
                 ;; FIXME: If no types, add the 4 salibandy types?
                 ;; #{2240 2150 2210 2220}
                 (conj (if all-types?
                         {:role :floorball-manager}
                         {:role :floorball-manager
                          :type-code (set types)}))

                 (seq sports-sites)
                 (conj {:role :site-manager
                        :lipas-id (set sports-sites)})

                 (seq activities)
                 (conj {:role :activities-manager
                        :activity (set activities)}))]
    result))

(deftest permissions->roles-test
  (is (= [{:role :admin}]
         (permissions->roles {:admin? true} nil)))

  (testing "access-to-sports-site"
    (is (= [{:role :site-manager
             :lipas-id #{1 2}}]
           (permissions->roles {:sports-sites [1 2]} nil))))

  (testing "salibandy"
    (is (= [{:role :type-manager
             :type-code #{101 102}}
            {:role :floorball-manager
             :type-code #{101 102}}]
           (permissions->roles {:all-cities? true
                                :types [101 102]}
                               "foo@salibandy.fi"))))

  (testing "publish/modify-sports-site implicitly case"
    (is (= [{:role :type-manager
             :type-code #{101 102}}]
           (permissions->roles {:types [101 202]
                                :all-cities? true}
                               nil)))

    (is (= [{:role :city-manager
             :city-code #{4 5}}]
           (permissions->roles {:cities [9 10]
                                :all-types? true}
                               nil)))

    (testing "both cities and types listed"
      (is (= [{:role :type-manager
               :city-code #{9 10}
               :type-code #{101}}]
             (permissions->roles {:cities [9 10]
                                  :types [101]}
                                 nil)))

      (is (= [{:role :city-manager
               :city-code #{9}
               :type-code #{101 102}}]
             (permissions->roles {:cities [9]
                                  :types [101 102]}
                                 nil)))))

  (testing "ignore types if all-types given"
    (is (= [{:role :basic-manager
             :city-code 853}]
           (permissions->roles {:types [3210 3120 3110 3130], :cities [853], :draft? true, :all-types? true}
                               nil))))

  (testing "existing user with access-to-activity"
    (is (= [{:role :activities-manager
             :activity "outdoor-recreation-facilities"}
            {:role :activities-manager
             :activity "fishing"}]
           (permissions->roles {:activities ["outdoor-recreation-facilities"
                                             "fishing"]}
                               nil)))))

(defn migrate-up [{:keys [db] :as _config}]
  (let [users (get-users db)]
    (doseq [user users
            :let [roles (permissions->roles (:permissions user) (:email user))]]
      (println (dissoc user :history :user-data))
      (println roles)
      (println)

      (when-let [err (s/explain-data :lipas.user.permissions/roles roles)]
        (throw (ex-info "Bad roles" {:roles roles
                                     :err err})))

      (user/update-user-permissions! db {:id (:id user)
                                    :permissions (assoc (:permissions user) :roles roles)}))))

(defn migrate-down [{:keys [db] :as _config}]
  (let [users (get-users db)]
    (doseq [user users]
      (user/update-user-permissions! db {:id (:id user)
                                         :permissions (dissoc (:permissions user) :roles)}))))

(comment
  (require 'user)
  (migrate-up {:db (user/db)}))
