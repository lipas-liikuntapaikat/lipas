(ns lipas.i18n.core
  (:require
   [clojure.string :as s]
   [lipas.data.admins :as admins]
   [lipas.data.ice-stadiums :as ice]
   [lipas.data.materials :as materials]
   [lipas.data.owners :as owners]
   [lipas.data.swimming-pools :as pools]
   [lipas.data.cities :as cities]
   [lipas.data.types :as types]
   [lipas.i18n.en :as en]
   [lipas.i18n.fi :as fi]
   [lipas.i18n.se :as se]
   [lipas.utils :as utils]
   [tongue.core :as tongue]))

(defn- ->translations [locale m]
  (reduce-kv (fn [res k v]
               (assoc res k (-> v locale))) {} m))

(def cities (utils/index-by :city-code cities/all))
(def types types/all)

(defn- append-data! [locale m]
  (->>
   {:admin                     (->translations locale admins/all)
    :owner                     (->translations locale owners/all)
    :pool-types                (->translations locale pools/pool-types)
    :sauna-types               (->translations locale pools/sauna-types)
    :heat-sources              (->translations locale pools/heat-sources)
    :filtering-methods         (->translations locale pools/filtering-methods)
    :pool-structures           (->translations locale materials/pool-structures)
    :slide-structures          (->translations locale materials/slide-structures)
    :building-materials        (->translations locale materials/building-materials)
    :supporting-structures     (->translations locale materials/supporting-structures)
    :ceiling-structures        (->translations locale materials/ceiling-structures)
    :heat-recovery-types       (->translations locale ice/heat-recovery-types)
    :dryer-duty-types          (->translations locale ice/dryer-duty-types)
    :dryer-types               (->translations locale ice/dryer-types)
    :heat-pump-types           (->translations locale ice/heat-pump-types)
    :condensate-energy-targets (->translations locale ice/condensate-energy-targets)
    :ice-resurfacer-fuels      (->translations locale ice/ice-resurfacer-fuels)
    :refrigerant-solutions     (->translations locale ice/refrigerant-solutions)
    :refrigerants              (->translations locale ice/refrigerants)
    :size-categories           (->translations locale ice/size-categories)}
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

(defn localize-pool [locale pool]
  (-> pool
      (update-in [:type] #(locale (get pools/pool-types %)))
      (update-in [:structure] #(locale (get materials/all %)))
      utils/clean))

(defn localize-sauna [locale sauna]
  (-> sauna
      (update-in [:type] #(locale (get pools/sauna-types %)))
      utils/clean))

(def localizations
  [
   ;; Sports site
   {:path         [:admin]
    :translations admins/all}
   {:path         [:owner]
    :translations owners/all}

   ;; Type
   {:path         [:type]
    :translate-fn (fn [locale {:keys [type-code] :as type}]
                    (assoc type :type-name (-> (get types type-code)
                                               :name
                                               locale)))}

   ;; Location
   {:path         [:location :city]
    :translate-fn (fn [locale {:keys [city-code] :as city}]
                    (assoc city :city-name (-> (get cities city-code)
                                               :name
                                               locale)))}

   ;; Proerties->surface-material
   {:path         [:properties :surface-material]
    :translations materials/surface-materials
    :many?        true}

   ;; Ice stadiums
   {:path         [:type :size-category]
    :translations ice/size-categories}
   {:path         [:building :base-floor-structure]
    :translations materials/all}
   {:path         [:ventilation :dryer-type]
    :translations ice/dryer-types}
   {:path         [:ventilation :dryer-duty-type]
    :translations ice/dryer-duty-types}
   {:path         [:ventilation :heat-pump-type]
    :translations ice/heat-pump-types}
   {:path         [:ventilation :heat-recovery-type]
    :translations ice/heat-recovery-types}
   {:path         [:refrigeration :condensate-energy-main-targets]
    :translations ice/condensate-energy-targets
    :many?        true}
   {:path         [:refrigeration :refrigerant]
    :translations ice/refrigerants}
   {:path         [:refrigeration :refrigerant-solution]
    :translations ice/refrigerant-solutions}
   {:path         [:envelope :base-floor-structure]
    :translations materials/all}

   ;; Swimming pools
   {:path         [:building :supporting-structures]
    :translations materials/all
    :many?        true}
   {:path         [:building :main-construction-materials]
    :translations materials/all
    :many?        true}
   {:path         [:building :ceiling-structures]
    :translations materials/all
    :many?        true}
   {:path         [:water-treatment :filtering-methods]
    :translations pools/filtering-methods
    :many?        true}
   {:path         [:pools]
    :translate-fn (fn [locale pools] (->> pools
                                          (map (partial localize-pool locale))
                                          (remove nil?)))}
   {:path         [:saunas]
    :translate-fn (fn [locale saunas] (->> saunas
                                           (map (partial localize-sauna locale))
                                           (remove nil?)))}])

(defn localize [locale sports-site]
  (reduce
   (fn [sports-site {:keys [path translations many? translate-fn]}]
     (if-let [value (get-in sports-site path)]
       (assoc-in sports-site path
                  (cond
                    translations (if many?
                                   (map #(get-in translations [% locale]) value)
                                   (get-in translations [value locale]))
                    translate-fn (apply translate-fn [locale value])
                    :else        (throw
                                  (ex-info "Invalid localization definition."
                                           {:missing-either
                                            [:translations :translate-fn]}))))
       sports-site))
   sports-site
   localizations))

(comment
  (localize :fi {:admin "state"
                 :building {:supporting-structures ["concrete"]}}))
