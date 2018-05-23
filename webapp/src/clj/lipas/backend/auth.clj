(ns lipas.backend.auth
  (:require [buddy.auth.backends :refer [jws]]
            [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.hashers :as hashers]
            [clojure.edn :as edn]
            [lipas.backend.core :as core]
            [environ.core :refer [env]]))

(defn basic-auth
  [db request {:keys [username password]}]
  (let [user (core/get-user db username)]
    (if (and user (hashers/check password (:password user)))
      (dissoc user :password)
      false)))

(defn basic-auth-backend
  [db]
  (http-basic-backend {:authfn (partial basic-auth db)}))

(def token-backend
  (jws {:secret (env :auth-key) :options {:alg :hs512}}))
