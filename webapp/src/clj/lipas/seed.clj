(ns lipas.seed
  (:require [lipas.backend.system :as backend]
            [lipas.backend.core :as core]
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
    :permissions-request "Haluan oikeudet päivittää Jyväskylän
             kilpajäähallin tietoja."}})

(def uh-demo
  {:email    "uh@lipas.fi"
   :username "uhdemo"
   :password "uimahalli"
   :permissions
   {:sports-sites [506032]}
   :user-data
   {:firstname           "Uima"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Äänekosken
    Vesivelhon tietoja."}})

(defn -main [& args]
  (let [config (select-keys backend/default-config [:db])
        system (backend/start-system! config)]
    (try
      (log/info "Seeding demo users 'jhdemo' and 'uhdemo'")
      (core/add-user! (:db system) jh-demo)
      (core/add-user! (:db system) uh-demo)
      (log/info "Seeding done!")
      (finally (backend/stop-system! system)))))
