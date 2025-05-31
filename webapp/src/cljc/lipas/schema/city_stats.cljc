(ns lipas.schema.city-stats
  "Malli schemas for city statistics data structure.")

;; Financial data schema used for both absolute and per-capita values
(def financial-data-schema
  "Schema for financial service data (youth-services, sports-services, etc.)"
  [:map
   [:net-costs
    {:description "Net costs for the service"}
    [:maybe number?]]
   [:subsidies
    {:description "Subsidies received for the service"}
    [:maybe number?]]
   [:investments
    {:description "Investments made in the service"}
    [:maybe number?]]
   [:operating-incomes
    {:description "Operating incomes from the service"}
    [:maybe number?]]
   [:operating-expenses
    {:description "Operating expenses for the service"}
    [:maybe number?]]])

;; Services schema covering youth and sports services
(def services-schema
  "Schema for city services data"
  [:map
   [:youth-services
    {:description "Youth services financial data (absolute values)"}
    financial-data-schema]
   [:sports-services
    {:description "Sports services financial data (absolute values)"}
    financial-data-schema]
   [:youth-services-pc
    {:optional true
     :description "Youth services financial data per capita"}
    financial-data-schema]
   [:sports-services-pc
    {:optional true
     :description "Sports services financial data per capita"}
    financial-data-schema]])

;; Schema for a single year's statistics
(def yearly-stats-schema
  "Schema for city statistics for a single year"
  [:map
   [:services
    {:description "Services financial data for the year"}
    services-schema]
   [:population
    {:optional true
     :description "City population for the year"}
    pos-int?]])

;; Schema for the complete city stats (all years)
(def city-stats-schema
  "Schema for complete city statistics across all years"
  [:map-of
   [:and
    keyword?
    [:fn {:description "Year keyword (e.g., :2023)"}
     #(re-matches #"^\d{4}$" (name %))]]
   yearly-stats-schema])

;; Complete city schema including city code and stats
(def city-schema
  "Schema for a complete city entity with code and statistics"
  [:map
   [:city_code
    {:description "Official city code"}
    pos-int?]
   [:stats
    {:description "Historical statistics by year"}
    city-stats-schema]])

;; Schema for city with additional metadata (useful for API responses)
(def city-with-metadata-schema
  "Schema for city with additional metadata fields"
  [:map
   [:city_code pos-int?]
   [:city_name {:optional true} string?]
   [:latest_year {:optional true} pos-int?]
   [:population {:optional true} pos-int?]
   [:sports_investments {:optional true} number?]
   [:stats city-stats-schema]])
