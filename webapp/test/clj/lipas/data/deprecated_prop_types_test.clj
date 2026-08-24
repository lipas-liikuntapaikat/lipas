(ns lipas.data.deprecated-prop-types-test
  "Deprecated prop types are hidden from the UI but must survive everywhere
  else: existing values stay in the database, in the search index and in every
  API response, and the API docs say the field is deprecated.

  Harrastuspassi.fi shut down, so `:may-be-shown-in-harrastuspassi-fi?` is the
  first prop to go through this. The tests are written against
  `prop-types/deprecated` so the next one is covered for free."
  (:require
    [camel-snake-kebab.core :as csk]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [lipas.backend.api.v1.handlers :as v1-handlers]
    [lipas.data.bulk-operations :as bulk-ops]
    [lipas.data.prop-types :as prop-types]
    [lipas.data.types :as types]
    [lipas.reports :as reports]
    [lipas.schema.sports-sites.types :as types-schema]
    [malli.core :as m]
    [malli.util :as mu]))

(def harrastuspassi :may-be-shown-in-harrastuspassi-fi?)

(deftest harrastuspassi-is-deprecated
  (is (contains? prop-types/deprecated harrastuspassi))
  (is (prop-types/deprecated? harrastuspassi)))

(deftest every-prop-type-declares-a-known-status
  (doseq [[k m] prop-types/all]
    (is (contains? prop-types/statuses (:status m))
        (str k " should declare a :status from prop-types/statuses"))))

(deftest deprecated?-fails-safe
  (testing "a prop with no :status, or an unknown key, counts as active"
    ;; `lipas.data.types/active` keeps only an explicit "active", so a type
    ;; with a forgotten :status disappears. Prop types are added far more
    ;; often, so a forgotten :status here must fail towards visible.
    (is (not (prop-types/deprecated? :no-such-prop-key)))
    (is (contains? prop-types/active :height-m))))

(deftest deprecated-props-are-retained-in-the-data-model
  (doseq [k (keys prop-types/deprecated)]
    (testing (str k " is kept in prop-types/all")
      (is (contains? prop-types/all k)))

    (testing (str k " keeps a validation schema, so stored values still coerce")
      (is (some? (get prop-types/schemas k))))

    (testing (str k " is still declared by the types that had it")
      ;; The types keep declaring it: `save-edits` prunes properties down to
      ;; the type's declared props, so dropping it there would silently wipe
      ;; the value from every site the next time it is saved.
      (is (pos? (count (filter #(contains? (:props (val %)) k) types/all)))))))

(deftest deprecated-props-are-excluded-from-the-ui-listings
  (doseq [k (keys prop-types/deprecated)]
    (testing (str k " is not in prop-types/active")
      (is (not (contains? prop-types/active k))))

    (testing (str k " is not offered as a report column")
      (is (not (contains? reports/visible-fields (str "properties." (name k))))))

    (testing (str k " is not offered as a bulk-edit field")
      (doseq [type-code (keys (filter #(contains? (:props (val %)) k) types/all))]
        (is (not (some #{k} (map :field-id (bulk-ops/property-fields [type-code])))))))))

(deftest deprecated-props-are-still-accepted-by-the-apis
  (doseq [k (keys prop-types/deprecated)]
    (testing (str k " remains an exportable report field")
      ;; `reports/fields` backs the API's report-field enum. A client that has
      ;; always exported this column keeps getting it.
      (is (contains? reports/fields (str "properties." (name k)))))

    (testing (str k " remains in the legacy v1 type definitions")
      (let [type-code (->> types/all
                           (filter #(contains? (:props (val %)) k))
                           ffirst)
            legacy-key (-> k name (str/replace #"\?$" "") csk/->camelCaseKeyword)
            prop (-> (v1-handlers/sports-place-by-type-code :fi type-code)
                     :properties
                     legacy-key)]
        (is (some? prop))
        (testing "with the legacy response shape unchanged"
          ;; The v1 endpoint declares its own response schema
          ;; (`legacy-property-type-definition`) and coercion strips anything
          ;; else, so :status must not surface here.
          (is (= #{:name :description :dataType} (set (keys prop)))))))))

(deftest v2-publishes-the-status-to-api-consumers
  ;; Response coercion strips keys the endpoint's schema does not declare, so
  ;; :status only reaches consumers because `types-schema/type` names it.
  (let [prop-entry (-> types-schema/type (mu/get :props) m/children first)]
    (is (some? (mu/get prop-entry :status))
        "types-schema/type must declare :status on prop entries"))

  (testing "a real category payload still validates against that schema"
    (let [type-code (->> types/all
                         (filter #(contains? (:props (val %)) harrastuspassi))
                         ffirst)
          category (types/->type (types/all type-code))]
      ;; `->type` sorts :props into a seq while the schema says :vector, which
      ;; predates this change — vec it so the assertion is about :status.
      (is (m/validate types-schema/type (update category :props vec)))
      (is (= "deprecated"
             (->> category :props
                  (filter #(= harrastuspassi (:key %)))
                  first
                  :status))))))

(deftest deprecated-props-say-so-in-every-locale
  (doseq [k (keys prop-types/deprecated)
          [locale marker] {:fi "POISTUNUT KÄYTÖSTÄ" :se "TAGEN UR BRUK" :en "DEPRECATED"}]
    (testing (str k " " locale " description is marked deprecated")
      ;; These descriptions are what the v1 and v2 OpenAPI specs publish.
      (is (str/starts-with? (get-in prop-types/all [k :description locale]) marker)))))
