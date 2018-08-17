(ns lipas.seed
  (:require [lipas.backend.system :as backend]
            [lipas.backend.core :as core]
            [environ.core :refer [env]]
            [lipas.schema.core]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
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
    (core/upsert-sports-site! db user (gen/generate (s/gen spec))))
  (log/info "Seeding done!"))

(defn -main [& args]
  (let [config (select-keys backend/default-config [:db])
        system (backend/start-system! config)
        db     (:db system)]
    (try
      (seed-default-users! db)
      (seed-demo-users! db)
      (finally (backend/stop-system! system)))))
