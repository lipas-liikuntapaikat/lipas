(ns lipas.reports
  (:require
   [lipas.utils :as utils]
   [lipas.data.prop-types :as prop-types]))

(defn- all-energy-data-exists? [{:keys [energy-consumption]}]
  (let [{:keys [electricity-mwh heat-mwh water-m3]} energy-consumption]
    (and
     (some? electricity-mwh)
     (some? heat-mwh)
     (some? water-m3))))

(defn- get-values [sites field-kw]
  (->> sites
       (map (comp field-kw :energy-consumption))
       (remove nil?)))

(defn- ->data-point [{:keys [lipas-id name energy-consumption]}]
  (-> energy-consumption
      (assoc :energy-mwh (+ (:heat-mwh energy-consumption 0)
                            (:electricity-mwh energy-consumption 0)))
      (assoc :name name)
      (assoc :lipas-id lipas-id)))

(defn energy-report [sites]
  {:total-count     (count sites)
   :electricity-mwh (utils/simple-stats (get-values sites :electricity-mwh))
   :heat-mwh        (utils/simple-stats (get-values sites :heat-mwh))
   :water-m3        (utils/simple-stats (get-values sites :water-m3))
   :data-points     (->> sites
                         (filter all-energy-data-exists?)
                         (map ->data-point))
   :hall-of-fame    (->> sites
                         (filter all-energy-data-exists?)
                         (map #(select-keys % [:lipas-id :name])))})

(defn ->row [fields m]
  (reduce
   (fn [res f]
     (let [v (utils/get-in-path m f)]
       (conj res (if (coll? v) (utils/join v) v))))
   []
   fields))

(def basic-fields
  {"lipas-id"                   {:fi "ID"}
   "name"                       {:fi "Nimi"}
   "marketing-name"             {:fi "Markkinointinimi"}
   "owner"                      {:fi "Omistaja"}
   "admin"                      {:fi "Ylläpitäjä"}
   "construction-year"          {:fi "Rakennusvuosi"}
   "renovation-years"           {:fi "Peruskorjausvuodet"}
   "phone-number"               {:fi "Puhelinnumero"}
   "email"                      {:fi "Sähköposti"}
   "www"                        {:fi "WWW"}
   "comment"                    {:fi "Kommentti"}
   "type.type-code"             {:fi "Tyyppikoodi"}
   "type.type-name"             {:fi "Liikuntapaikkatyyppi"}
   "location.city.city-code"    {:fi "kuntanumero"}
   "location.city.city-name"    {:fi "Kunta"}
   "location.city.neighborhood" {:fi "Kuntaosa"}
   "location.address"           {:fi "Katuosoite"}
   "location.postal-code"       {:fi "Postinumero"}
   "location.postal-office"     {:fi "Postitoimipaikka"}})

(def prop-fields
  (reduce (fn [res [k v]]
            (assoc res (str "properties." (name k)) (:name v)))
          {}
          prop-types/all))

(def meta-fields
  {"search-meta.location.wgs84-point" {:fi "Koordinaatit (WGS84)"}})

(def area-fields
  (select-keys prop-fields ["properties.area-m2"
                            "properties.area-km2"
                            "properties.field-1-area-m2"
                            "properties.field-2-area-m2"
                            "properties.field-3-area-m2"
                            "properties.pool-water-area-m2"]))

(def surface-material-fields
  (select-keys prop-fields ["properties.surface-material"
                            "properties.surface-material-info"
                            "properties.running-track-surface-material"
                            "properties.training-spot-surface-material"]))

(def length-fields
  (select-keys prop-fields ["properties.length-m"
                            "properties.field-length-m"
                            "properties.field-1-length-m"
                            "properties.field-2-length-m"
                            "properties.field-3-length-m"
                            "properties.hall-length-m"
                            "properties.inner-lane-length-m"
                            "properties.route-length-km"
                            "properties.lit-route-length-km"
                            "properties.pool-length-m"
                            "properties.sprint-track-length-m"
                            "properties.track-length-m"
                            "properties.beach-length-m"]))

(def width-fields
  (select-keys prop-fields ["properties.width-m"
                            "properties.field-width-m"
                            "properties.field-1-width-m"
                            "properties.field-2-width-m"
                            "properties.field-3-width-m"
                            "properties.climbing-wall-width-m"
                            "properties.hall-width-m"
                            "properties.pool-width-m"
                            "properties.route-width-m"
                            "properties.track-width-m"]))

(def height-fields
  (select-keys prop-fields ["properties.height-m"
                            "properties.climbing-wall-height-m"]))

(def common-measure-fields
  (select-keys prop-fields ["properties.field-length-m"
                            "properties.field-width-m"
                            "properties.height-m"
                            "properties.area-m2"
                            "properties.area-km2"
                            "properties.route-length-km"]))

(def fields
  (merge
   basic-fields
   meta-fields
   prop-fields))

(def default-fields
  (select-keys fields ["lipas-id"
                       "name"
                       ;;"marketing-name"
                       "type.type-name"
                       "location.city.city-name"
                       "properties.surface-material"
                       "properties.area-m2"]))
