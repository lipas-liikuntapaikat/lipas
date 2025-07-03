(ns legacy-api.sports-places
  (:require
   [legacy-api.util :refer [parse-path parse-year select-paths]]
   [lipas.data.admins :as admins]
   [lipas.data.owners :as owners]
   [lipas.data.types-old :as types-old]))

(def df-in (java.time.format.DateTimeFormatter/ofPattern
            "yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]"))

(def df-out (java.time.format.DateTimeFormatter/ofPattern
             "yyyy-MM-dd HH:mm:ss.SSS"))

(defn parse-date [x]
  (try
    (-> x
        (java.time.LocalDateTime/parse df-in)
        (.format df-out))
    (catch Exception e)))

(defn convert-iso8601-to-legacy [iso-timestamp]
  "Converts ISO8601 timestamp (2021-09-16T08:49:04.675Z) to legacy format (2021-09-16 08:49:04.675)"
  (try
    (when iso-timestamp
      (-> iso-timestamp
          (clojure.string/replace #"T" " ")
          (clojure.string/replace #"Z$" "")
          ;; Ensure we have 3 decimal places for milliseconds
          (as-> s (if (re-find #"\.\d{1,3}$" s)
                    s
                    (str s ".000")))))
    (catch Exception e
      nil)))

(comment
  (parse-date "2014-10-02 12:50:37.123")
  (parse-date "KEKKONEN")
  (parse-date "2014-10-02 12:50:37.12")
  (parse-date "2014-10-02 12:50:37.1")
  (parse-date "2014-10-02 12:50:37"))

(defn format-sports-place
  [sports-place locale location-format-fn]
  {:sportsPlaceId (:id sports-place)
   :name (:name sports-place)
   :marketingName (:marketingName sports-place)
   :type {:typeCode (-> sports-place :type :typeCode)
          :name (-> (types-old/all
                     (-> sports-place :type :typeCode))
                    :name)}
   :schoolUse (:schoolUse sports-place)
   :freeUse (:freeUse sports-place)
   :constructionYear (parse-year (:constructionYear sports-place))
   :renovationYears (:renovationYears sports-place)
   :lastModified (-> sports-place :lastModified parse-date)
   :owner (owners/all (-> sports-place :owner))
   :admin (admins/all (-> sports-place :admin))
   :phoneNumber (:phoneNumber sports-place)
   :reservationsLink (:reservationsLink sports-place)
   :www (:www sports-place)
   :email (:email sports-place)
   :location (when-let [location (:location sports-place)]
               (apply location-format-fn [location locale (:sportsPlaceId sports-place)]))
   :properties (:properties sports-place)})

(defn update-with-locale
  [sp locale fallback-locale path]
  (let [value (or (get-in sp (conj path locale))
                  (get-in sp (conj path fallback-locale)))]
    (assoc-in sp path value)))

(defn format-sports-place-es
  [sports-place locale]
  (-> sports-place
      (update :location dissoc :geom-coll)
      (update-with-locale locale :fi [:name])
      (update-with-locale locale :fi [:type :name])
      (update-with-locale locale :fi [:location :city :name])
      (update-with-locale locale :fi [:location :neighborhood])
      ;; Handle owner and admin fields - convert enum keys to localized strings
      (update :owner #(or (get-in % [locale]) ; Preserve if already localized
                          (get-in owners/all [% locale]) ; Convert enum key to localized string
                          (get-in owners/all [% :en]) ; Fallback to English
                          %)) ; Fallback to original value
      (update :admin #(or (get-in % [locale]) ; Preserve if already localized
                          (get-in admins/all [% locale]) ; Convert enum key to localized string
                          (get-in admins/all [% :en]) ; Fallback to English
                          %)) ; Fallback to original value
      ;; Handle lastModified field conversion
      (update :lastModified #(or % (convert-iso8601-to-legacy (:event-date sports-place))))
      ;; Extract schoolUse and freeUse from properties for legacy API compatibility
      ;; Only if they don't already exist at the top level
      (update :schoolUse #(or % (get-in sports-place [:properties :school-use?])))
      (update :freeUse #(or % (get-in sports-place [:properties :free-use?])))))

(defn filter-and-format
  [locale fields sp]
  (let [formatted (format-sports-place-es sp locale)]
    (if (empty? fields)
      formatted ; Return all formatted data if no specific fields requested
      (let [paths (map parse-path fields)]
        (apply select-paths (cons formatted paths))))))
