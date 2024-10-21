(ns lipas.backend.db.city
  (:require [clojure.set :as cset]
            [hugsql.core :as hugsql]
            [lipas.utils :as utils]))

(defn marshall [city]
  {:city_code (:city-code city)
   :stats     (:stats city)})

(defn- maybe-fix-kw
  "Fixes keywords that begin with a number and thus are not valid. If
  keyword contains only nombers it's coerced using
  `read-string`. Otherwise keyword `name` is returned."
  [k]
  (if (and (keyword? k) (-> k name (.charAt 0) Character/isDigit))
      (or (utils/->number (name k))
          (name k))
      k))

(defn- fix-keys [m]
  (let [ks    (keys m)
        fixed (into {} (map (juxt identity maybe-fix-kw) ks))]
    (cset/rename-keys m fixed)))

(defn unmarshall [city]
  {:city-code (:city_code city)
   :stats     (-> city :stats fix-keys)})

(hugsql/def-db-fns "sql/city.sql")
