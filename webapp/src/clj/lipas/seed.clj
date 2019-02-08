(ns lipas.seed
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [environ.core :refer [env]]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.system :as backend]
   [lipas.schema.core]
   [taoensso.timbre :as log]))

(def jh-demo
  {:email    "jh@lipas.fi"
   :username "jhdemo"
   :password "jaahalli"
   :permissions
   {:sports-sites [89839]}
   :user-data
   {:firstname           "Jää"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Jyväskylän kilpajäähallin tietoja."}})

(def uh-demo
  {:email    "uh@lipas.fi"
   :username "uhdemo"
   :password "uimahalli"
   :permissions
   {:sports-sites [506032]}
   :user-data
   {:firstname           "Uima"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Äänekosken Vesivelhon tietoja."}})

;; Admin is a person who can login and act as a 'human'
(def admin
  {:email    "admin@lipas.fi"
   :username "admin"
   :password (:admin-password env)
   :permissions
   {:admin? true}
   :user-data
   {:firstname "Lipas"
    :lastname  "Admin"}})

;; Import user should be used for batch jobs such as data
;; migrations and not supposed to login ever.
(def import-user
  {:email    "import@lipas.fi"
   :username "import"
   :password (str (java.util.UUID/randomUUID)) ; no-one should know
   :permissions
   {:admin? true}
   :user-data {}})

(def city-data
  [{:city-code 972,
    :stats
    {:2017
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
     :2016
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
    {:2017
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
     :2016
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
  (log/info "Seeding done!"))

(defn seed-demo-users! [db]
  (log/info "Seeding demo users 'jhdemo' and 'uhdemo'")
  (core/add-user! db jh-demo)
  (core/add-user! db uh-demo)
  (log/info "Seeding done!"))

(defn seed-sports-sites! [db user spec n]
  (log/info "Seeding" n "generated" spec)
  (doseq [_ (range n)]
    (core/upsert-sports-site!* db user (gen/generate (s/gen spec))))
  (log/info "Seeding done!"))

(defn seed-city-data! [db]
  (log/info "Seeding city data for cities " (map :city-code city-data))
  (doseq [city city-data]
    (db/add-city! db city))
  (log/info "Seeding done!"))

(defn -main [& args]
  (let [config (select-keys config/default-config [:db])
        system (backend/start-system! config)
        db     (:db system)]
    (try
      (seed-default-users! db)
      (seed-demo-users! db)
      (seed-city-data! db)
      (let [user (core/get-user db (:email admin))]
        (seed-sports-sites! db user :lipas/sports-site 10))
      (finally (backend/stop-system! system)))))
