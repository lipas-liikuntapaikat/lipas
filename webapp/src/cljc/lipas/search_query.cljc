(ns lipas.search-query
  "Builds the Elasticsearch request body for the map view's sports-site
   search (lipas.ui.search.events) from the search state in app-db.

   Pure and cljc so clj tests can check the generated queries against
   `lipas.backend.search/mappings`: ES treats a query or sort on a path the
   mapping lacks as matching nothing, without an error, so a drifted path
   only shows up as missing results."
  (:require [clojure.string :as str]
            [lipas.prop-filters :as prop-filters]
            [lipas.roles :as roles]))

;; Zoom level where we start fetching full geoms
(def full-geoms-threshold 9)

(defn- add-filter [m filter]
  (update-in m [:query :function_score :query :bool :filter] conj filter))

(defn ->sort-key [k locale]
  ;; Text columns never sort on the field itself, always on an
  ;; icu_collation_keyword built from it, which orders mixed-case and accented
  ;; values in the locale's collation order instead of raw Unicode code-point
  ;; order (where every lowercase value sorts after the entire uppercase
  ;; alphabet). Name and category fields get it as a `.sort` sub-field
  ;; (lipas.backend.search/text-with-sort); the free-text fields below get it
  ;; as a separate search-meta key so placeholder values can be excluded.
  (case k
    (:lipas-id) :lipas-id
    (:name) :search-meta.name.sort
    (:name-localized.se
      :name-localized.en) (-> k name (str ".sort") keyword)
    ;; Free-text user-entered fields sort on a derived search-meta key, which
    ;; is absent for values with nothing sortable in them — blanks, and the "-"
    ;; users type into a mandatory field. See lipas.utils/->sortable-text.
    (:marketing-name
      :www
      :email
      :phone-number) (keyword (str "search-meta.sort." (name k)))
    (:location.address) :search-meta.sort.address
    (:location.postal-office) :search-meta.sort.postal-office
    (:location.city.name
      :type.name
      :admin.name
      :owner.name) (-> k name
                       (->> (str "search-meta."))
                       (str "." (name locale) ".sort")
                       keyword)
    ;; Localized names live under search-meta.type.*.name.<locale>
    (:type.main-category
      :type.sub-category) (keyword (str "search-meta." (name k) ".name."
                                        (name locale) ".sort"))
    ;; keyword-typed field, no sub-fields
    (:location.postal-code) k
    (:event-date
      :construction-year
      :renovation-years) k

    (keyword (str (name k) ".keyword"))))

(defn resolve-sort [{:keys [sort-fn asc?]} locale decay? center]
  (if-not decay?

    {:sort
     {:_geo_distance
      {:search-meta.location.wgs84-point
       {:lon (:lon center)
        :lat (:lat center)}
       :order "asc"
       :unit "m"
       :mode "min"
       :distance_type "arc"
       :ignore_unmapped true}}}

    {:sort
     (filterv some?
              [(cond
                 (= sort-fn :score) :_score
                 sort-fn {(->sort-key sort-fn locale)
                          (cond-> {:order (if asc? "asc" "desc")
                                   ;; Sites with no value for the column go to
                                   ;; the bottom in both directions. This is
                                   ;; also ES's default, but pin it so the
                                   ;; behaviour is stated rather than inherited.
                                   :missing "_last"}
                            ;; Int array: ES sorts arrays by min asc and max
                            ;; desc by default, so [1990 2010] would sort on
                            ;; 1990 one way and 2010 the other.
                            (= sort-fn :renovation-years) (assoc :mode "min"))}
                 :else nil)
               ;; Deterministic tiebreaker. Without it, docs sharing a sort key
               ;; -- which is *every* site missing the sorted column, up to 97%
               ;; of them for e.g. marketing-name -- come back in Lucene doc-id
               ;; order, which changes whenever segments merge. Paging through
               ;; that block then shows some sites twice and skips others.
               (when sort-fn {:lipas-id {:order "asc"}})])}))

(defn resolve-pagination [{:keys [page page-size]} decay?]
  (if decay?

    {:from (* page page-size)
     :size page-size}

    {:from 0
     :size 5000}))

(defn resolve-query-string
  "`s` is users input to search field. Goal is to transform `s` into ES
  query-string that returns relevant results for the user. Current
  implementation appends '*' wildcard after each word. Nil and Empty
  string generates a match-all query. Dashes '-' are replaced with
  whitespace because ES standard analyzer removes punctuation marks."
  [s]
  (if (empty? s)
    "*"
    (-> s
        (str/replace "-" " ")
        (str/replace "/" "")
        (str/replace #"\s+" " ")
        (str/split #" ")
        (->> (map #(str % "*"))
             (str/join " ")))))

(defn ->geo-intersects-filter
  [{:keys [top-left bottom-right]}]
  {:geo_shape
   {:search-meta.location.geometries
    {:shape
     {:type "envelope"
      :coordinates [top-left bottom-right]}
     :relation "intersects"}}})

;; NOTE: this used to `merge` an `add-distance-fields` map here, which asked ES
;; to run three Painless `script_fields` (arcDistance to the map centre) on
;; every hit. Nothing ever read the resulting `fields`: every consumer of the
;; search response reads only `_source` and `_score`. So it was pure cost, and
;; it was also the only thing any LIPAS client sent that the unauthenticated
;; /actions/search endpoint cannot safely allow. Removed together with the
;; scripting guard (lipas.backend.search-guard).

(defn ->es-search-body
  ([params user]
   (->es-search-body params user false))
  ([{:keys [filters string center distance sort decay?
            locale pagination zoom bbox geom]} user terse?]
   (let [string (resolve-query-string string)
         bbox? (and
                 (> zoom 3)
                 (-> filters :bounding-box?))
         type-codes (-> filters :type-codes not-empty)
         city-codes (-> filters :city-codes not-empty)
         year-min (-> filters :construction-year-min)
         year-max (-> filters :construction-year-max)
         admins (-> filters :admins not-empty)
         owners (-> filters :owners not-empty)
         statuses (-> filters :statuses not-empty)
         edit-permission? (-> filters :edit-permission?)
         properties-filters (-> filters :properties-filters)
         {:keys [lon lat]} center

         params (merge
                  (resolve-sort sort locale decay? center)
                  (resolve-pagination pagination decay?)
                  {:track_total_hits 60000
                   :_source
                   {:includes (if terse?

                               ;; Used in result list view (while browsing map)
                                ["lipas-id"
                                 "status"
                                 "name"
                                 "name-localized"
                                 "type.type-code"
                                 "location.city.city-code"
                                 (if (> full-geoms-threshold zoom)
                                   "search-meta.location.simple-geoms"
                                   "location.geometries")]

                               ;; Used in results table view
                                ["lipas-id"
                                 "status"
                                 "event-date"
                                 "name"
                                 "name-localized"
                                 "marketing-name"
                                 "www"
                                 "phone-number"
                                 "email"
                                 "owner"
                                 "admin"
                                 "type.type-code"
                                 "renovation-years"
                                 "construction-year"
                                 "location.address"
                                 "location.postal-code"
                                 "location.postal-office"
                                 "location.city.city-code"
                                 "search-meta.type.main-category"
                                 "search-meta.type.sub-category"
                                 (if (> full-geoms-threshold zoom)
                                   "search-meta.location.simple-geoms"
                                   "location.geometries")])}
                   :query
                   (when decay?
                     {:function_score
                      {:score_mode "max"
                       :query
                       {:bool
                        {:must
                         [{:simple_query_string
                           {:query string
                            :fields
                            ["name^3"
                             "name-localized.*^3"
                             "marketing-name^3"
                             "lipas-id"
                             "search-meta.location.city.name.*^2"
                             "search-meta.type.name.*^2"
                             "search-meta.type.tags.*^2"
                             "search-meta.type.main-category.name.*"
                             "search-meta.type.sub-category.name.*"
                             "search-meta.location.province.name.*"
                             "search-meta.location.avi-area.name.*"
                             "admin"
                             "owner"
                             "comment"
                             "email"
                             "phone-number"
                             "location.address"
                             "location.postal-office"
                             "location.postal-code"
                             "location.city.neighborhood"
                             "properties.surface-material-info"]
                            :default_operator "AND"
                            :analyze_wildcard true}}]}}
                       :functions
                       (filterv some?
                                (for [kw [:search-meta.location.wgs84-point
                                          :search-meta.location.wgs84-center
                                          :search-meta.location.wgs84-end]]
                                  (when (every? pos? [lon lat distance])
                                    {:exp
                                     {kw {:origin (str lat "," lon)
                                          :offset (str distance "m")
                                          :scale (str distance "m")}}})))}})})]

     (if-not decay?

       (assoc params :query
              {:bool
               {:must
                (if (not-empty type-codes)
                  {:terms
                   {:type.type-code type-codes}}
                  {:match_all {}})
                :filter
                {:geo_shape
                 {:search-meta.location.geometries
                  {:shape (if (= "Point" (-> geom :type))
                            {:type "circle"
                             :coordinates (-> geom :coordinates)
                             :radius (str distance "m")}
                            geom)
                   :relation "intersects"}}}

                #_{:geo_distance
                   {:distance (str distance "m")
                    :search-meta.location.wgs84-point
                    {:lon lon
                     :lat lat}}}}})

       (cond-> params
         bbox? (add-filter (->geo-intersects-filter bbox))
         statuses (add-filter {:terms {:status statuses}})
         type-codes (add-filter {:terms {:type.type-code type-codes}})
         city-codes (add-filter {:terms {:location.city.city-code city-codes}})
         year-min (add-filter {:range {:construction-year {:gte year-min}}})
         year-max (add-filter {:range {:construction-year {:lte year-max}}})
         admins (add-filter {:terms {:admin admins}})
         owners (add-filter {:terms {:owner owners}})

         ;; Apply property filters
         properties-filters
         (as-> params* params*
           (reduce-kv
             (fn [acc prop-key prop-filter]
               (if-let [clause (prop-filters/->es-clause prop-key prop-filter)]
                 (add-filter acc clause)
                 acc))
             params*
             properties-filters))

         ;; Add the condition to search based on site props which affect user roles.
         ;; Keep function_score query at the top level, but add this query around other filters (like name, type etc.)
         edit-permission? (update-in [:query :function_score :query] (fn [x]
                                                                       (roles/wrap-es-query-site-has-privilege x user :site/create-edit))))))))
