(ns lipas.seed
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [environ.core :refer [env]]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.system :as backend]
   [lipas.maintenance :as maintenance]
   [lipas.schema.core]
   [taoensso.timbre :as log]))

(def jh-demo
  {:email    "jh@lipas.fi"
   :status "active"
   :username "jhdemo"
   :password "jaahalli"
   :permissions {:roles [{:role :site-manager
                          :lipas-id #{89839}}]}
   :user-data
   {:firstname           "Jää"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Jyväskylän kilpajäähallin tietoja."}})

(def sb-demo
  {:email    "sb@lipas.fi"
   :status   "active"
   :username "sbdemo"
   :password "atk-on-ihanaa"
   :permissions {:roles [{:role :type-manager
                          :type-code #{2240}}]}
   :user-data
   {:firstname           "Testi"
    :lastname            "Testinen"
    :permissions-request "Kaikki salibandyhallit"}})

(def uh-demo
  {:email    "uh@lipas.fi"
   :status   "active"
   :username "uhdemo"
   :password "uimahalli"
   :permissions {:roles [{:role :site-manager
                          :lipas-id #{506032}}]}
   :user-data
   {:firstname           "Uima"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Äänekosken Vesivelhon tietoja."}})

;; Admin is a person who can login and act as a 'human'
(def admin
  {:email    "admin@lipas.fi"
   :status   "active"
   :username "admin"
   :password (:admin-password env)
   :permissions {:roles [{:role :admin}]}
   :user-data
   {:firstname "Lipas"
    :lastname  "Admin"}})

;; Import user should be used for batch jobs such as data
;; migrations and not supposed to login ever.
(def import-user
  {:email     "import@lipas.fi"
   :status    "active"
   :username  "import"
   :password  (str (java.util.UUID/randomUUID)) ; no-one should know
   :permissions {:roles [{:role :admin}]}
   :user-data {}})

;; Robot user is similar to import user but meant for predictable
;; automated tasks instead of 'manual' import type of jobs.
(def robot-user
  {:email     "robot@lipas.fi"
   :status    "active"
   :username  "robot"
   :password  (str (java.util.UUID/randomUUID)) ; no-one should know
   :permissions {:roles [{:role :admin}]}
   :user-data {}})

(def city-data
  [{:city-code 972,
    :stats
    {:2019
     {:services
      {:youth-services
       {:net-costs 61.0,
        :subsidies nil,
        :operating-incomes 0.0,
        :operating-expenses 60.0},
       :sports-services
       {:net-costs 222.0,
        :subsidies nil,
        :operating-incomes 43.0,
        :operating-expenses 260.0}},
      :population 2100}
     :2018
     {:services
      {:youth-services
       {:net-costs 60.0,
        :subsidies nil,
        :operating-incomes 0.0,
        :operating-expenses 60.0},
       :sports-services
       {:net-costs 217.0,
        :subsidies nil,
        :operating-incomes 43.0,
        :operating-expenses 260.0}},
      :population 2229},
     :2017
     {:services
      {:youth-services
       {:net-costs 60.0,
        :subsidies nil,
        :operating-incomes 0.0,
        :operating-expenses 60.0},
       :sports-services
       {:net-costs 221.0,
        :subsidies nil,
        :operating-incomes 46.0,
        :operating-expenses 267.0}},
      :population 2192}}}
   {:city-code 275,
    :stats
    {:2019
     {:services
      {:youth-services
       {:net-costs 100.0,
        :subsidies 16.0,
        :operating-incomes 6.0,
        :operating-expenses 112.0},
       :sports-services
       {:net-costs 97.0,
        :subsidies 7.0,
        :operating-incomes 1.0,
        :operating-expenses 98.0}},
      :population 3000}
     :2018
     {:services
      {:youth-services
       {:net-costs 106.0,
        :subsidies 15.0,
        :operating-incomes 6.0,
        :operating-expenses 112.0},
       :sports-services
       {:net-costs 97.0,
        :subsidies 7.0,
        :operating-incomes 1.0,
        :operating-expenses 98.0}},
      :population 3027},
     :2017
     {:services
      {:youth-services
       {:net-costs 116.0,
        :subsidies 3.0,
        :operating-incomes 90.0,
        :operating-expenses 206.0},
       :sports-services
       {:net-costs 82.0,
        :subsidies 1.0,
        :operating-incomes 0.0,
        :operating-expenses 82.0}},
      :population 2978}}}])

(defn seed-default-users! [db]
  (log/info "Seeding default users 'admin' and 'import'")
  (core/add-user! db admin)
  (core/add-user! db import-user)
  (core/add-user! db robot-user)
  (log/info "Seeding done!"))

(defn seed-demo-users! [db]
  (log/info "Seeding demo users 'jhdemo' and 'uhdemo'")
  (core/add-user! db jh-demo)
  (core/add-user! db uh-demo)
  (log/info "Seeding done!"))

(defn gen-sports-site
  []
  (try
    (gen/generate (s/gen :lipas/sports-site))
    (catch Throwable _t (gen-sports-site))))

(defn gen-loi []
  (try
    (->
     (gen/generate (s/gen :lipas.loi/document))
     (assoc :id (java.util.UUID/randomUUID)))
    (catch Throwable _ (gen-loi))))

(defn seed-lois! [db search user spec n]
  (log/info "Seeding " n "generated " spec)
  (doseq [x (range n)]

    (let [loi (gen-loi)]
      (core/upsert-loi! db search user loi)
      (log/info loi))
    (log/info "Generated " x " of " n))
  (log/info "Seeding done!"))


(defn seed-sports-sites! [db user spec n]
  (log/info "Seeding" n "generated" spec)
  (doseq [_ (range n)]
    (core/upsert-sports-site!* db user (gen-sports-site)))
  (log/info "Seeding done!"))

(defn seed-city-data! [db search]
  (log/info "Seeding city data for cities " (map :city-code city-data))
  (doseq [city city-data]
    (db/add-city! db city))
  (maintenance/index-city-finance-data! {:db db :search search})
  (log/info "Seeding done!"))

(defn -main [& _args]
  (let [config (select-keys config/system-config [:lipas/db :lipas/search])
        system (backend/start-system! config)
        db     (:lipas/db system)
        search (:lipas/search system)]
    (try
      (seed-default-users! db)
      (seed-demo-users! db)
      (seed-city-data! db search)
      (let [user (core/get-user db (:email admin))]
        (seed-sports-sites! db user :lipas/sports-site 10)
        (seed-lois! db search user :lipas.loi/document 10))
      (finally (backend/stop-system! system)))))
