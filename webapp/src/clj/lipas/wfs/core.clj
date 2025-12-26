(ns lipas.wfs.core
  "Legacy WFS Compatibility Layer

  This namespace maintains backward compatibility with the LIPAS WFS service
  originally implemented in 2012. It preserves the original layer structure
  and naming conventions to ensure continued functionality for existing
  client applications and integrations.

  The primary motivation for this implementation is to facilitate the
  retirement of the legacy server and database infrastructure while
  maintaining service continuity for dependent systems.

  This compatibility layer is maintained for legacy support purposes.
  New implementations should follow contemporary API and naming
  standards."
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.set :as set]
            [clojure.string :as str]
            [honey.sql :as sql]
            [honey.sql.pg-ops]
            [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.system :as system]
            [lipas.data.types :as types]
            [lipas.wfs.mappings :as mappings]
            [lipas.utils :as utils]
            [next.jdbc :as jdbc]
            [taoensso.timbre :as log]))

(defn ->wfs-row [sports-site idx feature]
  (let [type-code (-> sports-site :type :type-code)
        #_#_geom-type (-> type-code types/all :geometry-type)
        fields    (set/union mappings/common-fields
                             mappings/common-coll-view-fields
                             (get mappings/type-code->legacy-fields type-code))]
    [(:status sports-site)
     (->>
      (for [field fields]
        (let [resolver-fn (mappings/legacy-field->resolve-fn field)]
          [field (resolver-fn {:site sports-site :feature feature :idx idx})]))
      (into {}))]))

(defn ->wfs-rows [sports-site]
  (->> sports-site
       :location
       :geometries
       :features
       (map-indexed (fn [idx feature]
                      (->wfs-row sports-site idx feature)))))

;;; Creating the tables ;;;

(def type-layer-mat-views
  (->>
   (for [[type-code view-names] mappings/type-code->view-names
         view-name view-names]
     [view-name
      {:create-materialized-view [(keyword (str "wfs." view-name)) :if-not-exists]
       :select (into
                ;; Include fid (feature ID) for unique index (enables stable paging in GeoServer)
                [[:id :fid]
                 [(if (str/ends-with? view-name "_3d")
                    [:cast [:st_force_3d :the_geom] (mappings/resolve-geom-field-type type-code :z)]
                    [:cast [:st_force_2d :the_geom] (mappings/resolve-geom-field-type type-code nil)]) :the_geom]]
                (for [field (sort-by mappings/field-sorter utils/reverse-cmp
                                     (set/union
                                      mappings/common-fields
                                      (mappings/type-code->legacy-fields type-code)))
                      :when (not= :the_geom field)]
                  (let [field-type (mappings/resolve-field-type field)]
                    [[:cast [:->> :doc [:inline (name field)]] field-type] field])))
       :from [:wfs.master]
       :where [:and
               [:= :type_code [:inline type-code]]
               [:= :status [:inline "active"]]]}])
   (into {})))

(defn ->coll-layer
  [view-name fields]
  (let [geom-type (case view-name
                    ("lipas_kaikki_pisteet" "retkikartta_pisteet") "Point"
                    ("lipas_kaikki_reitit" "retkikartta_reitit") "LineString"
                    ("lipas_kaikki_alueet" "retkikartta_alueet") "Polygon")]
    {:create-materialized-view [(keyword (str "wfs." view-name)) :if-not-exists]
     ;; Include fid (feature ID) for unique index (enables stable paging in GeoServer)
     :select (cons [:id :fid]
                   (for [[k data-type] fields]
                     (case k
                       :the_geom [[[:cast [:st_force_2d k]
                                    (case geom-type
                                      "Point" (keyword "geometry(Point,3067)")
                                      "LineString" (keyword "geometry(LineString,3067)")
                                      "Polygon" (keyword "geometry(Polygon,3067)"))]] k]
                       :x [[:st_x [:st_centroid :the_geom]] k]
                       :y [[:st_y [:st_centroid :the_geom]] k]

                       (:osa_alue_json :reittiosa_json :piste_json) [[:st_asgeojson :the_geom] k]

                       ;; Retkikartta specific
                       :category_id [:type_code k]
                       :name_fi [[:cast [:->> :doc [:inline "nimi_fi"]] (keyword data-type)] k]
                       :name_se [[:cast [:->> :doc [:inline "nimi_se"]] (keyword data-type)] k]
                       :name_en [[:cast [:->> :doc [:inline "nimi_en"]] (keyword data-type)] k]
                       :ext_link [[:cast [:->> :doc [:inline "www"]] (keyword data-type)] k]
                       :admin [[:cast [:->> :doc [:inline "yllapitaja"]] (keyword data-type)] k]
                       :geometry [:the_geom k]
                       :info_fi [[:cast [:->> :doc [:inline "lisatieto_fi"]] (keyword data-type)] k]
                       (:alue_id :osa_alue_id) [[:cast [:->> :doc [:inline "alue_id"]] (keyword data-type)] k]

                       (:reitti_id :reittiosa_id) [[:cast [:->> :doc [:inline "reitti_id"]] (keyword data-type)] k]

                       :reitisto_id [:lipas_id k]

                       ;; Default
                       [[:cast [:->> :doc [:inline (name k)]] (keyword data-type)] k])))
     :from [:wfs.master]
     :where [:and
             [:= :geom_type [:inline geom-type]]
             [:and
              [:= :status [:inline "active"]]
              (when (str/starts-with? view-name "retkikartta_")
                [:= true [:cast [:->> :doc [:inline "saa_julkaista_retkikartassa"]] :boolean]])
              (when (= view-name "retkikartta_alueet")
                [:in :type_code [:inline [102, 103, 106, 104, 112]]])
              (when (= view-name "retkikartta_reitit")
                [:in :type_code [:inline [4403, 4402, 4401, 4404, 4405, 4411, 4412, 4451, 4452, 4421, 4422]]])
              (when (= view-name "retkikartta_pisteet")
                [:in :type_code [:inline [207, 302, 202, 301, 206, 304, 204, 205, 203, 1180, 4720, 4460, 3230, 3220, 1550]]])]]}))

(def coll-layer-mat-views
  (->> mappings/coll-layer-mat-view-specs
       (map (fn [[k m]] [(name k) (->coll-layer (name k) m)]))
       (into {})))

(defn drop-legacy-mat-views!
  [db]

  (doseq [view-name (into (keys type-layer-mat-views)
                          (keys coll-layer-mat-views))]
    (log/info "Dropping mat-view" view-name)
    (jdbc/execute! db [(str "DROP MATERIALIZED VIEW IF EXISTS wfs." view-name)]))

  ;; Type layers
  (doseq [[_type-code view-names] mappings/type-code->view-names
          view-name               view-names]
    (log/info "Dropping mat-view" (str "wfs." view-name))
    (jdbc/execute! db [(str "DROP MATERIALIZED VIEW IF EXISTS wfs." view-name)]))

  (log/info "All Legacy mat-views dropped."))

(defn create-legacy-mat-views!
  [db]

  ;; Coll layers AND type layers
  (doseq [[view-name ddl] (merge type-layer-mat-views
                                 coll-layer-mat-views)]
    (log/info "Creating mat-view" (str "wfs." view-name))
    (jdbc/execute! db (sql/format ddl))

    ;; Spatial index for geometry queries
    (let [geom-idx-name (str "idx_" view-name "_the_geom")
          geom-query (str "CREATE INDEX IF NOT EXISTS "
                          geom-idx-name
                          " ON wfs." view-name
                          (if (str/starts-with? view-name "retkikartta_")
                            " USING GIST(geometry)"
                            " USING GIST(the_geom)"))]
      (jdbc/execute! db [geom-query]))

    ;; Unique index on fid for stable paging in GeoServer and REFRESH CONCURRENTLY
    (let [unique-idx-name (str "idx_" view-name "_fid_unique")
          unique-query (str "CREATE UNIQUE INDEX IF NOT EXISTS "
                            unique-idx-name
                            " ON wfs." view-name "(fid)")]
      (jdbc/execute! db [unique-query])))

  (log/info "All Legacy mat-views created."))

(defn refresh-legacy-mat-views!
  "Refreshes all legacy WFS materialized views.

  Uses CONCURRENTLY by default (requires unique index on id), which allows
  read operations to continue during refresh. Set concurrent? to false for
  the initial refresh after creating empty views."
  ([db] (refresh-legacy-mat-views! db true))
  ([db concurrent?]
   (doseq [view-names (into (vals mappings/type-code->view-names)
                            (map vector (keys coll-layer-mat-views)))
           view-name view-names]
     (log/info "Refreshing mat-view" (str "wfs." view-name) (when concurrent? "(concurrently)"))
     (jdbc/execute! db [(str "REFRESH MATERIALIZED VIEW "
                             (when concurrent? "CONCURRENTLY ")
                             "wfs." view-name)]))))

(defn refresh-wfs-master-table!
  [db]

  ;; Truncate
  (log/info "Truncating table :wfs.master")
  (jdbc/execute! db (sql/format {:truncate :wfs.master}))

  ;; Populate master table
  (log/info "Populating table :wfs.master")
  (doseq [type-code (keys types/all)]
    (log/info "Populating table :wfs.master with sites" type-code)
    (let [sites (core/get-sports-sites-by-type-code db type-code)]
      (doseq [part (->> sites
                        (mapcat ->wfs-rows)
                        (partition-all 100))]
        (jdbc/execute!
         db
         (sql/format
          {:insert-into [:wfs.master]
           :values (for [[status row] part]
                     {:lipas-id (:id row)
                      :type_code (:tyyppikoodi row)
                      :geom_type (:type (:the_geom row))
                      :doc [:lift row]
                      ;; Geometries are in 3067 coordinate system in Legacy WFS
                      :the_geom [:st_transform
                                 [:st_setsrid
                                  [:st_geomfromgeojson [:lift (:the_geom row)]]
                                  [:cast 4326 :int]]
                                 [:cast 3067 :int]]
                      :status status})}))))))

(defn refresh-all!
  [db]
  (log/info "Starting full legacy wfs refresh")
  (refresh-wfs-master-table! db)
  (refresh-legacy-mat-views! db)
  (log/info "Full legacy wfs refresh DONE!"))

;;; Geoserver Layer management ;;;

(def geoserver-config
  {:root-url #_"https://lipas.fi/geoserver/rest" "http://localhost:8888/geoserver/rest"
   :workspace-name "lipas"
   :datastore-name "lipas-wfs-v2"
   :default-http-opts
   {:basic-auth [(get (System/getenv) "GEOSERVER_ADMIN_USER")
                 (get (System/getenv) "GEOSERVER_ADMIN_PASSWORD")]
    :accept :json
    :as :json}})

(defn get-all-layers
  []
  (->
   (http/get (str (get geoserver-config :root-url) "/layers")
             (get geoserver-config :default-http-opts))
   (get-in [:body :layers :layer])))

(defn get-layer
  [layer-name]
  (->
   (http/get (str (get geoserver-config :root-url) "/layers/" layer-name)
             (get geoserver-config :default-http-opts))
   (get-in [:body])))

(defn list-featuretypes
  "Lists all available featuretypes in lipas-wfs-v2 datastore. "
  []
  (let [url (str (:root-url geoserver-config) "/rest/workspaces/"
                 (:workspace-name geoserver-config)
                 "/datastores/" (:datastore-name geoserver-config)
                 "/featuretypes.json")
        response (http/get url (:default-http-opts geoserver-config))]
    (get-in response [:body :featureTypes :featureType])))

(defn list-styles
  "Lists all styles available in GeoServer."
  []
  (let [url (str (:root-url geoserver-config) "/styles.json")
        response (http/get url (:default-http-opts geoserver-config))]
    (get-in response [:body :styles :style])))

(defn publish-layer
  "Publishes a new vector layer from an existing datastore.

   Parameters:
   - feature-name: Name of the feature in the datastore (e.g., table name)
   - publish-name: Name to be published
  "
  [feature-name publish-name geom-type]
  (let [url (str (get geoserver-config :root-url) "/workspaces/"
                 (get geoserver-config :workspace-name)
                 "/datastores/" (get geoserver-config :datastore-name)
                 "/featuretypes")

        style {:name (case geom-type
                       "Point" "lipas:tyyli_pisteet"
                       "Polygon" "lipas:tyyli_alueet_2"
                       "LineString" "lipas:tyyli_reitit")}

        settings {:name publish-name
                  :nativeName feature-name
                  :title publish-name
                  :srs "EPSG:3067"
                  :nativeCRS "EPSG:3067"
                  :enabled true
                  :advertised true
                  :queryable true
                  :nativeBoundingBox {:minx 50000.0
                                      :maxx 760000.0
                                      :miny 6600000.0
                                      :maxy 7800000.0
                                      :crs "EPSG:3067"}
                  :latLonBoundingBox {:minx 19.08
                                      :maxx 31.59
                                      :miny 59.45
                                      :maxy 70.09
                                      :crs "EPSG:4326"}

                  :projectionPolicy "NONE"
                  :defaultStyle style}]

    (log/info "Publishing layer" publish-name)
    (http/post url
               (merge (:default-http-opts geoserver-config)
                      {:body (json/generate-string {:featureType settings})
                       :content-type "application/json"}))

    ;; Style is not setting correctly during POST se we PUT it after publishing
    (let [layer-url (str (get geoserver-config :root-url) "/layers/"
                         (get geoserver-config :workspace-name) ":" publish-name)]

      (http/put layer-url
                (merge (:default-http-opts geoserver-config)
                       {:body (json/generate-string
                               {:layer {:defaultStyle style}})
                        :content-type "application/json"})))))

(defn delete-layer
  "Deletes a published layer from GeoServer.

   Parameters:
   - publish-name: Name of the published layer to delete
   - recurse: (Optional, default true) If true, recursively deletes associated resources
  "
  ([publish-name]
   (delete-layer publish-name true))
  ([publish-name recurse]
   (let [url (str (get geoserver-config :root-url) "/layers/"
                  (get geoserver-config :workspace-name) ":" publish-name)

         ;; Add recurse parameter to query string if true
         url-with-params (if recurse
                           (str url "?recurse=true")
                           url)]

     (log/info (str "Deleting layer: " publish-name))

     (try
       (http/delete url-with-params
                    (merge (:default-http-opts geoserver-config)
                           {:content-type "application/json"}))
       (catch Exception ex (if (= 404 (:status (ex-data ex)))
                             (log/info "Ignoring Not Found error during delete")
                             (throw ex)))))))

(defn rebuild-all-legacy-layers
  []
  (log/info "Rebuilding all layers")

  (log/info "Rebuilding type layers")
  (doseq [[type-code layers] mappings/type-code->view-names
          layer-name layers]
    (let [geom-type (get-in types/all [type-code :geometry-type])]
      (delete-layer layer-name)
      (publish-layer layer-name layer-name geom-type)))

  (log/info "Rebuilding collection layers")

  (delete-layer "lipas_kaikki_pisteet")
  (publish-layer "lipas_kaikki_pisteet" "lipas_kaikki_pisteet" "Point")

  (delete-layer "retkikartta_pisteet")
  (publish-layer "retkikartta_pisteet" "retkikartta_pisteet" "Point")

  (delete-layer "lipas_kaikki_reitit")
  (publish-layer "lipas_kaikki_reitit" "lipas_kaikki_reitit" "LineString")

  (delete-layer "retkikartta_reitit")
  (publish-layer "retkikartta_reitit" "retkikartta_reitit" "LineString")

  (delete-layer "lipas_kaikki_alueet")
  (publish-layer "lipas_kaikki_alueet" "lipas_kaikki_alueet" "Polygon")

  (delete-layer "retkikartta_alueet")
  (publish-layer "retkikartta_alueet" "retkikartta_alueet" "Polygon")

  (log/info "All rebuilt!"))

;;; Layer groups ;;;

(def main-category-layer-grpups
  (->> mappings/main-category-layer-groups
       (map (fn [[t gname]]
              [gname (-> (parse-long t)
                         (types/by-main-category)
                         (->> (map :type-code)
                              (select-keys mappings/type-code->view-names)
                              vals
                              (mapcat identity)))]))
       (into {})))

(def sub-category-layer-grpups
  (->> mappings/sub-category-layer-groups
       (map (fn [[t gname]]
              [gname (-> (parse-long t)
                         (types/by-sub-category)
                         (->> (map :type-code)
                              (select-keys mappings/type-code->view-names)
                              vals
                              (mapcat identity)))]))
       (into {})))

(def all-sites-layer-group
  {"lipas_kaikki_kohteet"
   ["lipas_kaikki_pisteet"
    "lipas_kaikki_reitit"
    "lipas_kaikki_alueet"]})

(defn rebuild-all-legacy-layer-groups
  []
  (log/info "Rebuilding all layer groups...")

  (log/info "Rebuilding layer groups for main- and sub-categories...")
  (doseq [[group-name layers] (merge main-category-layer-grpups
                                     sub-category-layer-grpups
                                     all-sites-layer-group)]

    (log/info "Deleting layer group" group-name)
    (try
      (http/delete (str (:root-url geoserver-config)
                        "/workspaces/"
                        (:workspace-name geoserver-config)
                        "/layergroups/"
                        group-name)
                   (:default-http-opts geoserver-config))
      ;; Geoserver doesn't return 404 if not found, but instead
      ;; explodes with a 500 and stack trace...
      (catch Exception _ex (log/info "Ignoring error during delete")))

    (log/info "Creating layer group" group-name)
    (http/post (str (:root-url geoserver-config)
                    "/workspaces/"
                    (:workspace-name geoserver-config)
                    "/layergroups")
               (merge (:default-http-opts geoserver-config)
                      {:content-type "application/json"
                       :as :raw
                       :body (json/generate-string
                              {:layerGroup
                               {:name group-name
                                :mode "SINGLE"
                                :workspace {:name (:workspace-name geoserver-config)}
                                :bounds {:minx 50000.0
                                         :maxx 760000.0
                                         :miny 6600000.0
                                         :maxy 7800000.0
                                         :crs "EPSG:3067"}
                                :publishables
                                {:published (for [layer layers]
                                              {"@type" "layer"
                                               :name (str "lipas:" layer)})}}})})))

  (log/info "All layergroups rebuilt!"))

(defn migrate-fron-legacy-db
  [db]
  (drop-legacy-mat-views! db)
  (create-legacy-mat-views! db)
  (rebuild-all-legacy-layers)
  (rebuild-all-legacy-layer-groups))

;;; Main entrypoint ;;;

(defn -main [& _]
  (let [system (system/start-system! (select-keys config/system-config [:lipas/db]))
        db (:lipas/db system)]
    (refresh-all! db)
    (system/stop-system! system)
    (shutdown-agents)
    (System/exit 0)))

(comment

  (migrate-fron-legacy-db (user/db))

  (get-layer "lipas_1170_pyorailurata")
  (delete-layer "lipas_1170_pyorailurata_v2")
  (ex-data *e)
  (publish-layer "lipas_1170_pyorailurata" "lipas_1170_pyorailurata_v2" "Point")
  *1

  (http/delete (str (:root-url geoserver-config)
                    "/workspaces/"
                    (:workspace-name geoserver-config)
                    "/layergroups/"
                    "lipas_4800_ampumaurheilupaikat")
               (:default-http-opts geoserver-config))

  (rebuild-all-legacy-layer-groups)

  (println (json/generate-string
            {:layerGroup {:name "lipas_4800_ampumaurheilupaikat", :mode "SINGLE", :workspace "lipas", :bounds {:minx 50000.0, :maxx 760000.0, :miny 6600000.0, :maxy 7800000.0, :crs "EPSG:3067"}, :publishables {:published '({:type "layer", :name "lipas:lipas_4830_jousiammuntarata"} {:type "layer", :name "lipas:lipas_4840_jousiammuntamaastorata"} {:type "layer", :name "lipas:lipas_4820_ampumaurheilukeskus"} {:type "layer", :name "lipas:lipas_4810_ampumarata"})}}}))

  (list-featuretypes)

  (require '[lipas.backend.config :as config])
  (require '[cheshire.core :as json])
  (require '[clj-http.client :as http])
  (do
    (drop-legacy-mat-views! (user/db))
    (create-legacy-mat-views! (user/db)))

  (refresh-all! (user/db))

  (get-layer "lipas:lipas_4401_kuntorata_3d")

  (refresh-legacy-mat-views! (user/db))

  (require '[lipas.backend.core :as core])

  ;; Sisäampumarata (point)
  (->wfs-rows (core/get-sports-site (user/db) 510812))

  ;; Latu (linestring)
  (->wfs-rows (core/get-sports-site (user/db) 523760))
  (->wfs-rows (core/get-sports-site (user/db) 94714))
  (->wfs-rows (core/get-sports-site (user/db) 515522))

  ;; Ulkoilualue (polygon)
  (->wfs-rows (core/get-sports-site (user/db) 72648))

  (require '[clojure.data.csv :as csv])
  (->> (slurp "/Users/tipo/lipas/wfs-tasot-revamp/ominaisuustyypit.csv")
       csv/read-csv
       (drop 1)
       (map (fn [[nimi-fi handle]]
              [(keyword handle) (keyword nimi-fi)]))
       (into {}))

  (def legacy-fields
    (->> (slurp "/Users/tipo/lipas/wfs-tasot-revamp/mat_view_fields.csv")
         csv/read-csv
         (drop 1)
         (map (fn [[schema type-code view-name column-name data-type]]
                {:type-code (parse-long type-code)
                 :view-name view-name
                 :data-type data-type
                 :legacy-field (keyword column-name)}))))

  (->> legacy-fields
       (map (fn [{:keys [view-name type-code]}] [type-code view-name]))
       distinct
       (into {}))

  (->> legacy-fields
       (map #(select-keys % [:legacy-field :data-type]))
       distinct
       (filter (fn [m]
                 (and (not= "text" (:data-type m))
                      (not (str/starts-with? (:data-type m) "character varying"))))))

  (update-vals (group-by :type-code legacy-fields)
               (fn [coll] (set/difference
                           (->> coll (map :legacy-field) set)
                           common-fields)))

  (require '[clojure.set :as set])

  (set/map-invert legacy-handle->legacy-prop)

  (def all-sites (atom []))
  (doseq [type-code (keys types/all)
          site (core/get-sports-sites-by-type-code (user/db) type-code)]
    (swap! all-sites conj site))

  (count @all-sites)

  (def as-rows
    (mapcat ->wfs-rows @all-sites))

  (take 3 as-rows)

  (defn create-postgis-datastore
    [base-url username password workspace-name datastore-name connection-params]
    (let [url (str base-url "/rest/workspaces/" workspace-name "/datastores")
          auth {:basic-auth [username password]}
          default-params {:host "localhost"
                          :port 5432
                          :database "postgres"
                          :schema "public"
                          :user "postgres"
                          :passwd ""
                          :dbtype "postgis"}
          params (merge default-params connection-params)
          json-body (json/generate-string
                     {:dataStore
                      {:name datastore-name
                       :type "PostGIS"
                       :enabled true
                       :connectionParameters
                       {:entry (map (fn [[k v]] {"@key" (name k) "$" (str v)}) params)}}})]
      (http/put url (merge auth
                           {:body json-body
                            :content-type "application/json"
                            :accept "application/json"}))))

  (publish-layer "lipas_4402_latu_3d" "lipas_4402_latu_3d_test" "LineString")

  (let [url (str (:root-url geoserver-config) "/workspaces/lipas/styles.json")
        response (http/get url (merge (:default-http-opts geoserver-config)
                                      {:basic-auth ["GEOSERVER_ADMIN_USER" ""]}))]
    (get-in response [:body :styles :style]))

  (get-layer "lipas_1170_pyorailurata"))
