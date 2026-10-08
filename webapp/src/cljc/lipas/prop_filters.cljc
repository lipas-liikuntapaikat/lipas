(ns lipas.prop-filters
  "Elasticsearch clauses for the map search's property filters.

   Lives in cljc so the field paths can be checked against
   `lipas.backend.search/mappings` in clj tests. The paths must follow the
   explicit mapping (`prop-type->es-mapping`): enum and enum-coll props are
   plain keyword fields, only string props carry a `.keyword` sub-field.
   Querying a path the mapping lacks matches nothing without an error — the
   enum filters did exactly that from the switch to the explicit mapping
   (Dec 2025) until they were moved here.")

(defn- prop-path [prop-key]
  (keyword (str "properties." (name prop-key))))

(defn ->es-clause
  "Returns the ES filter clause for one property filter, or nil when the
   filter does not restrict anything (empty values, \"All\")."
  [prop-key prop-filter]
  (let [path (prop-path prop-key)]
    (case (:type prop-filter)
      :range
      (let [{:keys [min max]} prop-filter]
        (when (or min max)
          {:range {path (cond-> {}
                          min (assoc :gte min)
                          max (assoc :lte max))}}))

      :boolean
      (case (:value prop-filter)
        ;; nil = "All" - no filter applied
        nil nil
        ;; true = show sites where property IS true
        true {:term {path true}}
        ;; false = show sites where property is NOT true (false OR absent)
        false {:bool {:should [{:term {path false}}
                               {:bool {:must_not {:exists {:field (name path)}}}}]}})

      :string
      (when-let [text (not-empty (:text prop-filter))]
        {:wildcard {(keyword (str (name path) ".keyword"))
                    {:value (str "*" text "*")
                     :case_insensitive true}}})

      :enum
      (when-let [values (not-empty (:values prop-filter))]
        {:terms {path values}})

      nil)))
