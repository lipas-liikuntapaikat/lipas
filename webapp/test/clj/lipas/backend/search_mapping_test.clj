(ns lipas.backend.search-mapping-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [lipas.backend.search :as search]
   [lipas.data.prop-types :as prop-types]))

(deftest generate-explicit-mapping-test
  (testing "Mapping generation produces correct structure"
    (let [mapping (:sports-site search/mappings)]

      (testing "Has strict dynamic mode"
        (is (= "strict" (:dynamic (:mappings mapping)))))

      (testing "Has field limit of 350"
        (is (= 350 (get-in mapping [:settings :index :mapping :total_fields :limit]))))

      (testing "Has max_result_window of 60000"
        (is (= 60000 (get-in mapping [:settings :max_result_window]))))

      (testing "Has properties map"
        (is (map? (get-in mapping [:mappings :properties]))))))

  (testing "All property fields from prop-types are mapped"
    (let [mapping (:sports-site search/mappings)
          properties (get-in mapping [:mappings :properties])
          prop-type-keys (set (keys prop-types/all))]

      (testing "Total property count matches"
        (is (= 181 (count prop-type-keys))))

      (testing "Each prop-type field has a mapping"
        (doseq [[prop-key prop-def] prop-types/all]
          (is (contains? properties (keyword (str "properties." (name prop-key))))
              (str "Missing mapping for property: " prop-key))))))

  (testing "Property types are correctly mapped to ES types"
    (let [mapping (:sports-site search/mappings)
          properties (get-in mapping [:mappings :properties])]

      (testing "Numeric properties map to double"
        (let [numeric-props (filter #(= "numeric" (get-in % [1 :data-type])) prop-types/all)
              sample-key (keyword (str "properties." (name (ffirst numeric-props))))]
          (is (= 97 (count numeric-props)))
          (is (= "double" (:type (get properties sample-key))))))

      (testing "Boolean properties map to boolean"
        (let [boolean-props (filter #(= "boolean" (get-in % [1 :data-type])) prop-types/all)
              sample-key (keyword (str "properties." (name (ffirst boolean-props))))]
          (is (= 67 (count boolean-props)))
          (is (= "boolean" (:type (get properties sample-key))))))

      (testing "String properties map to text with keyword field"
        (let [string-props (filter #(= "string" (get-in % [1 :data-type])) prop-types/all)
              sample-key (keyword (str "properties." (name (ffirst string-props))))]
          (is (= 11 (count string-props)))
          (is (= "text" (:type (get properties sample-key))))
          (is (= "keyword" (get-in properties [sample-key :fields :keyword :type])))))

      (testing "Enum properties map to keyword"
        (let [enum-props (filter #(= "enum" (get-in % [1 :data-type])) prop-types/all)
              sample-key (keyword (str "properties." (name (ffirst enum-props))))]
          (is (= 3 (count enum-props)))
          (is (= "keyword" (:type (get properties sample-key))))))

      (testing "Enum-coll properties map to keyword"
        (let [enum-coll-props (filter #(= "enum-coll" (get-in % [1 :data-type])) prop-types/all)
              sample-key (keyword (str "properties." (name (ffirst enum-coll-props))))]
          (is (= 3 (count enum-coll-props)))
          (is (= "keyword" (:type (get properties sample-key))))))))

  (testing "Geographic fields have correct types"
    (let [mapping (:sports-site search/mappings)
          properties (get-in mapping [:mappings :properties])]

      (is (= "geo_point" (:type (get properties :search-meta.location.wgs84-point))))
      (is (= "geo_point" (:type (get properties :search-meta.location.wgs84-center))))
      (is (= "geo_point" (:type (get properties :search-meta.location.wgs84-end))))
      (is (= "geo_shape" (:type (get properties :search-meta.location.geometries))))))

  (testing "Activities field restructuring"
    (let [mapping (:sports-site search/mappings)
          properties (get-in mapping [:mappings :properties])]

      (testing "Activities field is disabled for indexing"
        (is (false? (:enabled (get properties :activities)))))

      (testing "search-meta.activities is keyword array"
        (is (= "keyword" (:type (get properties :search-meta.activities)))))))

  (testing "Core fields are mapped"
    (let [mapping (:sports-site search/mappings)
          properties (get-in mapping [:mappings :properties])]

      (is (= "integer" (:type (get properties :lipas-id))))
      (is (= "keyword" (:type (get properties :status))))
      (is (= "date" (:type (get properties :event-date))))
      (is (= "integer" (:type (get properties :type.type-code))))
      (is (= "integer" (:type (get properties :location.city.city-code))))
      (is (= "integer" (:type (get properties :construction-year))))))

  (testing "Disabled fields prevent index bloat"
    (let [mapping (:sports-site search/mappings)
          properties (get-in mapping [:mappings :properties])]

      (is (false? (:enabled (get properties :location.geometries))))
      (is (false? (:enabled (get properties :search-meta.location.simple-geoms)))))))
