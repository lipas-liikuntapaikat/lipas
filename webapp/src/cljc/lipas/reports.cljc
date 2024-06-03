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
  {"lipas-id"
   {:fi "Lipas-id" :en "Lipas-id" :se "Lipas-id"}
   "name"
   {:fi "Nimi suomeksi" :en "Finnish name" :se "Namn på finska"}
   "name-localized.se"
   {:fi "Nimi ruotsiksi" :en "Swedish name" :se "Namn på svenska"}
   "marketing-name"
   {:fi "Markkinointinimi" :en "Marketing name" :se "Varumärkesnamn"}
   "event-date"
   {:fi "Muokattu viimeksi" :en "Last modified" :se "Senaste redigerad"}
   "owner"
   {:fi "Omistaja" :en "Owner" :se "Ägare"}
   "admin"
   {:fi "Ylläpitäjä" :en "Administrator" :se "Administratör"}
   "construction-year"
   {:fi "Rakennusvuosi" :en "Construction year" :se "Byggår"}
   "renovation-years"
   {:fi "Peruskorjausvuodet" :en "Renovation years" :se "Renoveringsår"}
   "phone-number"
   {:fi "Puhelinnumero" :en "Phone number" :se "Telefonnummer"}
   "email"
   {:fi "Sähköposti" :en "Email" :se "Epost"}
   "www"
   {:fi "WWW" :en "WWW" :se "WWW"}
   "comment"
   {:fi "Lisätieto" :en "Additional information" :se "Ytterligare information"}
   "type.type-code"
   {:fi "Tyyppikoodi" :en "Type code" :se "Typkod"}
   "type.type-name"
   {:fi "Liikuntapaikkatyyppi" :en "Type" :se "Typ"}
   "location.city.city-code"
   {:fi "Kuntanumero" :en "City code" :se "Kommunkod"}
   "location.city.city-name"
   {:fi "Kunta" :en "City" :se "Stat"}
   "location.city.neighborhood"
   {:fi "Kuntaosa" :en "Neighborhood" :se "Kommundel"}
   "location.address"
   {:fi "Katuosoite" :en "Address" :se "Adress"}
   "location.postal-code"
   {:fi "Postinumero" :en "Postal code" :se "Postnummer"}
   "location.postal-office"
   {:fi "Postitoimipaikka" :en "Postal office" :se "Postkontor"}})

(def prop-fields
  (reduce (fn [res [k v]]
            (assoc res (str "properties." (name k)) (:name v)))
          {}
          prop-types/all))

(def meta-fields
  {"search-meta.location.wgs84-point"
   {:fi "Koordinaatit (WGS84)" :se "Koordinater (WGS84)" :en "Coordinates (WGS84)"}

   "search-meta.location.avi-area.name.fi"
   {:fi "AVI-alue" :en "AVI-area" :se "AVI"}

   "search-meta.location.province.name.fi"
   {:fi "Maakunta" :en "Province" :se "Landskap"}

   "search-meta.type.main-category.name.fi"
   {:fi "Liikuntapaikkatyypin pääryhmä" :en "Type main-category" :se "Typ huvud kategori"}

   "search-meta.type.sub-category.name.fi"
   {:fi "Liikuntapaikkatyypin alaryhmä" :en "Type sub-category" :se "Typ under kategori"}

   "search-meta.audits.latest-audit-date"
   {:fi "Viimeisin katselmus" :se "Senaste revision" :en "Latest audit"}})

(def area-fields
  (select-keys prop-fields ["properties.area-m2"
                            "properties.area-km2"
                            "properties.pool-water-area-m2"]))

(def surface-material-fields
  (select-keys prop-fields ["properties.surface-material"
                            "properties.surface-material-info"
                            "properties.running-track-surface-material"
                            "properties.training-spot-surface-material"
                            "properties.inruns-material"
                            "properties.skijump-hill-material"]))

(def length-fields
  (select-keys prop-fields ["properties.field-length-m"
                            "properties.hall-length-m"
                            "properties.inner-lane-length-m"
                            "properties.route-length-km"
                            "properties.lit-route-length-km"
                            "properties.pool-length-m"
                            "properties.sprint-track-length-m"
                            "properties.track-length-m"
                            "properties.beach-length-m"
                            "properties.longest-slope-m"
                            "properties.shortest-slope-m"]))

(def width-fields
  (select-keys prop-fields ["properties.field-width-m"
                            "properties.climbing-wall-width-m"
                            "properties.hall-width-m"
                            "properties.pool-width-m"
                            "properties.route-width-m"
                            "properties.track-width-m"]))

(def height-fields
  (select-keys prop-fields ["properties.height-m"
                            "properties.climbing-wall-height-m"]))

(def other-measures
  (select-keys prop-fields ["properties.p-point"
                            "properties.k-point"
                            "properties.altitude-difference"]))

(def measure-fields
  (merge
   area-fields
   length-fields
   width-fields
   height-fields
   other-measures))

(def service-fields
  (select-keys prop-fields ["properties.equipment-rental?"
                            "properties.ski-service?"
                            "properties.kiosk?"
                            "properties.shower?"
                            "properties.parking-place?"
                            "properties.playground?"
                            "properties.pier?"
                            "properties.rest-places-count"
                            "properties.toilet?"
                            "properties.changing-rooms?"
                            "properties.sauna?"]))

(def activity-fields
  (select-keys prop-fields ["properties.rifle-shooting?"
                            "properties.shotgun-shooting?"
                            "properties.pistol-shooting?"
                            "properties.free-rifle-shooting?"
                            "properties.air-gun-shooting?"
                            "properties.shooting-positions-count"
                            "properties.tatamis-count"
                            "properties.badminton-courts-count"
                            "properties.hammer-throw-places-count"
                            "properties.landing-places-count"
                            "properties.weight-lifting-spots-count"
                            "properties.exercise-machines-count"
                            "properties.ice-rinks-count"
                            "properties.futsal-fields-count"
                            "properties.training-wall?"
                            "properties.winter-swimming?"
                            "properties.ski-track-traditional?"
                            "properties.gymnastics-space?"
                            "properties.shotput-count"
                            "properties.fencing-bases-count"
                            "properties.basketball-fields-count"
                            "properties.freestyle-slope?"
                            "properties.throwing-sports-spots-count"
                            "properties.range?"
                            "properties.green?"
                            "properties.longjump-places-count"
                            "properties.holes-count"
                            "properties.boat-places-count"
                            "properties.outdoor-exercise-machines?"
                            "properties.cosmic-bowling?"
                            "properties.spinning-hall?"
                            "properties.climbing-routes-count"
                            "properties.handball-fields-count"
                            "properties.javelin-throw-places-count"
                            "properties.lit-slopes-count"
                            "properties.fields-count"
                            "properties.table-tennis-count"
                            "properties.volleyball-fields-count"
                            "properties.gymnastic-routines-count"
                            "properties.boxing-rings-count"
                            "properties.football-fields-count"
                            "properties.polevault-places-count"
                            "properties.climbing-wall?"
                            "properties.archery?"
                            "properties.jumps-count"
                            "properties.discus-throw-places"
                            "properties.wrestling-mats-count"
                            "properties.show-jumping?"
                            "properties.curling-lanes-count"
                            "properties.bowling-lanes-count"
                            "properties.floorball-fields-count"
                            "properties.highjump-places-count"
                            "properties.other-platforms?"
                            "properties.toboggan-run?"
                            "properties.halfpipe-count"
                            "properties.tennis-courts-count"
                            "properties.slopes-count"
                            "properties.snowpark-or-street?"
                            "properties.circular-lanes-count"
                            "properties.boat-launching-spot?"
                            "properties.plastic-outrun?"
                            "properties.ice-climbing?"
                            "properties.squash-courts-count"
                            "properties.group-exercise-rooms-count"
                            "properties.sprint-lanes-count"]))

(def other-fields
  (select-keys prop-fields ["properties.accessibility-info"
                            "properties.basketball-field-type"
                            "properties.summer-usage?"
                            "properties.winter-usage?"
                            "properties.ice-reduction?"
                            "properties.ligthing?"
                            "properties.lifts-count"
                            "properties.school-use?"
                            "properties.skijump-hill-type"
                            "properties.track-type"
                            "properties.covered-stand-person-count"
                            "properties.stand-capacity-person"
                            "properties.eu-beach?"
                            "properties.may-be-shown-in-excursion-map-fi?"
                            "properties.ski-track-freestyle?"
                            "properties.free-use?"
                            "properties.heating?"]))

(def competition-fields
  (select-keys prop-fields ["properties.match-clock?"
                            "properties.automated-timing?"
                            "properties.automated-scoring?"
                            "properties.scoreboard?"
                            "properties.loudspeakers?"
                            "properties.finish-line-camera?"]))


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

(def stats-metrics
  {"investments"          {:fi "Investoinnit"
                           :se "Investeringar"
                           :en "Investments"}
   "operating-expenses"   {:fi "Käyttökustannukset"
                           :se "Driftskostnader"
                           :en "Operating expenses"}
   "operating-incomes"    {:fi "Käyttötuotot"
                           :se "Driftsintäkter"
                           :en "Operating incomes"}
   "subsidies"            {:fi "Kunnan myöntämät avustukset"
                           :se "Understöd och bidrag från kommunen"
                           :en "Subsidies"}
   "net-costs"            {:fi "Nettokustannukset"
                           :se "Nettokostnader"
                           :en "Net costs"}
   "operational-expenses" {:fi "Toimintakulut"
                           :se "Driftskostnader"
                           :en "Operational expenses"}
   "operational-income"   {:fi "Toimintatuotot"
                           :se "Driftsintäkter"
                           :en "Operational income"}
   "surplus"              {:fi "Tilikauden ylijäämä"
                           :se "Surplus"
                           :en "Surplus"}
   "deficit"              {:fi "Tilikauden alijäämä"
                           :se "Deficit"
                           :en "Deficit"}})

(def city-services
  {"sports-services" {:fi "Liikuntatoimi"
                      :se "Idrottsväsende"
                      :en "Sports services"}
   "youth-services"  {:fi "Nuorisotoimi"
                      :se "Ungdomsväsende"
                      :en "Youth services"}})

(def stats-units
  {"1000-euros"       {:fi "Tuhatta €"
                       :se "1000 €"
                       :en "€1000"}
   "euros-per-capita" {:fi "€ / Asukas"
                       :se "€ / Invånare"
                       :en "€ / Capita"}})

(def age-structure-groupings
  {"owner" {:fi "Omistaja"
            :se "Ägare"
            :en "Owner"}
   "admin" {:fi "Ylläpitäjä"
            :se "Administratör"
            :en "Administrator"}})

(def sports-stats-groupings
  {"location.city.city-code" {:fi "Kunta"
                              :se "Kommun"
                              :en "City"}
   "type.type-code"          {:fi "Tyyppi"
                              :se "Typ"
                              :en "Type"}})

(def sports-stats-metrics
  {"sites-count"        {:fi "Liikuntapaikkojen lkm"
                         :se "Antal av platser"
                         :en "Sports facility count"}
   "sites-count-p1000c" {:fi "Liikuntapaikkojen lkm/1000 asukasta"
                         :se "Antal av platser/1000 invånare"
                         :en "Sports facility count/1000 person"}
   "area-m2-sum"        {:fi "Liikuntapinta-ala m²"
                         :se "Idrottsareal m²"
                         :en "Surface area m²"}
   "area-m2-pc"         {:fi "Liikuntapinta-ala m²/asukas"
                         :se "Idrottsareal m²/invånare"
                         :en "Surface area m²/capita"}
   "length-km-sum"      {:fi "Reittien pituus km"
                         :se "Idrottsrutters totalt längd km"
                         :en "Routes total length km"}
   "length-km-pc"       {:fi "Reittien pituus km/asukas"
                         :se "Idrottsrutters totalt längd km/invånare"
                         :en "Routes total length km/capita"}})

(def finance-stats-groupings
  {"avi"      {:fi "AVI-alue"
               :se "AVI"
               :en "AVI-area"}
   "province" {:fi "Maakunta"
               :se "Landskap"
               :en "Province"}
   "city"     {:fi "Kunta"
               :se "Kommun"
               :en "City"}})

(def subsidies-groupings
  (merge
   finance-stats-groupings
   {"type" {:fi "Tyyppi"
            :se "Typ"
            :en "Type"}}))

(def subsidies-issuers
  {"AVI" {:fi "AVI"
          :se "AVI"
          :en "AVI"}
   "OKM" {:fi "OKM"
          :se "OKM"
          :en "OKM"}})

(defn- service-avgs [service year cities]
  (let [ms (map (comp #(get % service) :services #(get % year) :stats) cities)
        ks (-> stats-metrics keys (->> (map keyword)))]
    (reduce
     (fn [res k]
       (assoc res k (->> ms
                         (map k)
                         (remove nil?)
                         utils/simple-stats)))
     {}
     ks)))

(defn calc-avgs [year cities]
  {:population (->> cities
                    (map (comp :population #(get % year) :stats))
                    (remove nil?)
                    utils/simple-stats)
   :services
   {:youth-services     (service-avgs :youth-services year cities)
    :sports-services    (service-avgs :sports-services year cities)
    :youth-services-pc  (service-avgs :youth-services-pc year cities)
    :sports-services-pc (service-avgs :sports-services-pc year cities)}})

(def calc-avgs-memo (memoize calc-avgs))

(defn calc-stats [years cities]
  (reduce
   (fn [res year]
     (assoc res year (calc-avgs-memo year cities)))
   {}
   years))

(defn calc-per-capita [population m]
  (reduce
   (fn [m [k v]]
     (assoc m k (/ (* 1000 v) population)))
   {}
   m))

(defn finance-report [city-codes all-cities]
  (let [cities (utils/index-by :city-code all-cities)
        years  (into #{} (mapcat (comp keys :stats)) all-cities)]
    {:country-averages (calc-stats years all-cities)
     :data-points      (select-keys cities city-codes)}))

(defn calculate-stats-by-city [aggs-data pop-data]
  (reduce
   (fn [res m]
     (let [city-code  (:key m)
           population (pop-data city-code)

           m2-sum          (-> m :area_m2_stats :sum)
           km-sum          (-> m :length_km_stats :sum)
           area-m2-stats   (-> m
                               :area_m2_stats
                               (assoc :pc (when (and population m2-sum)
                                            (double (/ m2-sum population))))
                               (utils/->prefix-map "area-m2-"))
           length-km-stats (-> m
                               :length_km_stats
                               (assoc :pc (when (and population km-sum)
                                            (double (/ km-sum population))))
                               (utils/->prefix-map "length-km-"))
           sites-count     (:doc_count m)
           entry           (merge
                            area-m2-stats
                            length-km-stats
                            {:population         population
                             :sites-count        sites-count
                             :sites-count-p1000c (when (and population sites-count)
                                                   (double
                                                    (/ sites-count
                                                       (/ population 1000))))})]
       (assoc res city-code entry)))
   {}
   aggs-data))

(defn calculate-stats-by-type [aggs-data pop-data city-codes]
  (reduce
   (fn [res m]
     (let [type-code       (:key m)
           populations     (if (empty? city-codes)
                             pop-data ;; all
                             (select-keys pop-data city-codes))
           population      (->> populations vals (reduce +))
           m2-sum          (-> m :area_m2_stats :sum)
           km-sum          (-> m :length_km_stats :sum)
           area-m2-stats   (-> m
                               :area_m2_stats
                               (assoc :pc (when (and population m2-sum)
                                            (double (/ m2-sum population))))
                               (utils/->prefix-map "area-m2-"))
           length-km-stats (-> m
                               :length_km_stats
                               (assoc :pc (when (and population km-sum)
                                            (double (/ km-sum population))))
                               (utils/->prefix-map "length-km-"))

           sites-count (:doc_count m)
           entry       (merge
                        area-m2-stats
                        length-km-stats
                        {:population         population
                         :sites-count        sites-count
                         :sites-count-p1000c (when (and population sites-count)
                                               (double
                                                (/ sites-count
                                                   (/ population 1000))))})]
       (assoc res type-code entry)))
   {}
   aggs-data))
