(ns lipas.test-utils
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]))

(defn gen-sports-site
  []
  (try
    (gen/generate (s/gen :lipas/sports-site))
    (catch Throwable _t (gen-sports-site))))
