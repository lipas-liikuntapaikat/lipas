(ns lipas.i18n.core
  (:require
   [clojure.string :as s]
   [lipas.data.sports-sites :as sports-sites]
   [lipas.data.admins :as admins]
   [lipas.data.ice-stadiums :as ice]
   [lipas.data.materials :as materials]
   [lipas.data.owners :as owners]
   [lipas.data.swimming-pools :as pools]
   [lipas.data.cities :as cities]
   [lipas.data.types :as types]
   [lipas.reports :as reports]
   [lipas.i18n.generated :as translations]
   [lipas.utils :as utils]
   [tongue.core :as tongue]))

(defn- ->translations [locale m]
  (reduce-kv (fn [res k v]
               (assoc res k (-> v locale))) {} m))

(def cities (utils/index-by :city-code cities/all))
(def types types/all)

(defn append-data! [locale m]
  (->>
   {:status                    (->translations locale sports-sites/statuses)
    :admin                     (->translations locale admins/all)
    :owner                     (->translations locale owners/all)
    :pool-types                (->translations locale pools/pool-types)
    :sauna-types               (->translations locale pools/sauna-types)
    :accessibility             (->translations locale pools/accessibility)
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
    :size-categories           (->translations locale ice/size-categories)
    ;;:stats-metrics             (->translations locale reports/stats-metrics)
    }
   (merge m)))

(def dicts
  (assoc translations/dicts :tongue/fallback :fi))

(def translate (tongue/build-translate dicts))

(defn ->tr-fn
  "Returns a translator fn for given locale.

  Translator fn Returns current locale (:fi :se :en) when called with
  no args. "
  [locale]
  (fn
    ([]
     locale)
    ([kw & args]
     (apply translate (into [locale kw] args)))))

(defn- localize-accessibility [locale ss]
  (when-not (empty? ss)
    (map (fn [s] (get-in pools/accessibility [s locale])) ss)))

(defn localize-pool [locale pool]
  (-> pool
      (update-in [:type] #(locale (get pools/pool-types %)))
      (update-in [:structure] #(locale (get materials/all %)))
      (update-in [:accessibility] (partial localize-accessibility locale))
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
