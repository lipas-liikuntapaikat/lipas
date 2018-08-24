(ns lipas.i18n.core
  (:require [tongue.core :as tongue]
            [clojure.string :as s]
            [lipas.data.swimming-pools :as pools]
            [lipas.data.materials :as materials]
            [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]
            [lipas.i18n.fi :as fi]
            [lipas.i18n.en :as en]
            [lipas.i18n.se :as se]))

(defn- ->translations [locale m]
  (reduce-kv (fn [res k v]
               (assoc res k (-> v locale))) {} m))

(defn- append-data! [locale m]
  (->>
   {:admin                 (->translations locale admins/all)
    :owner                 (->translations locale owners/all)
    :pool-types            (->translations locale pools/pool-types)
    :sauna-types           (->translations locale pools/sauna-types)
    :heat-sources          (->translations locale pools/heat-sources)
    :filtering-methods     (->translations locale pools/filtering-methods)
    :pool-structures       (->translations locale materials/pool-structures)
    :slide-structures      (->translations locale materials/slide-structures)
    :building-materials    (->translations locale materials/building-materials)
    :supporting-structures (->translations locale materials/supporting-structures)
    :ceiling-structures    (->translations locale materials/ceiling-structures)}
   (merge m)))

(def dicts
  {:fi (append-data! :fi fi/translations)
   :se (append-data! :se se/translations)
   :en (append-data! :en en/translations)
   :tongue/fallback :fi})

(comment (translate :fi :front-page/lipas-headline))
(comment (translate :fi :menu/sports-panel))
(comment (translate :fi :menu/sports-panel :lower))
(def translate (tongue/build-translate dicts))

(def formatters
  {:lower-case s/lower-case
   :upper-case s/upper-case
   :capitalize s/capitalize})

(defn fmt
  "Supported formatter options:

  :lower-case
  :upper-case
  :capitalize"
  [s args]
  (case (first args)
    :lower-case (s/lower-case s)
    :upper-case (s/upper-case s)
    :capitalize (s/capitalize s)
    s))

(comment ((->tr-fn :fi) :menu/sports-panel))
(comment ((->tr-fn :fi) :menu/sports-panel :lower))
(defn ->tr-fn
  "Creates translator fn with support for optional formatter. See
  `lipas.ui.i18n/fmt`

  Translator fn Returns current locale (:fi :sv :en) when called with
  no args.

  Function usage: ((->tr-fn :fi) :menu/sports-panel :lower)
  => \"liikuntapaikat\""
  [locale]
  (fn
    ([]
     locale)
    ([kw & args]
     (-> (apply translate (into [locale kw] (filter (complement keyword?) args)))
         (fmt args)))))
