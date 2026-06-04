(ns lipas.schema.users
  (:require [lipas.data.activities :as activities]
            [lipas.data.cities :as cities]
            [lipas.data.types :as types]
            [lipas.roles :as roles]
            [lipas.schema.common :as common]
            [malli.core :as m]
            #?(:clj [clojure.test.check.generators :as gen])))

;; Email validation
(def email-regex #"^[a-zA-Z0-9åÅäÄöÖ._%+-]+@[a-zA-Z0-9åÅäÄöÖ.-]+\.[a-zA-Z]{2,63}$")
(def two-consecutive-dots-regex #"\.{2,}")

(def email-schema
  (m/schema
   [:and
    {:gen/gen #?(:clj (gen/fmap
                       (fn [[user domain tld]]
                         (str user "@" domain "." tld))
                       (gen/tuple
                        (gen/fmap #(apply str %) (gen/vector gen/char-alphanumeric 3 12))
                        (gen/fmap #(apply str %) (gen/vector gen/char-alphanumeric 3 10))
                        (gen/elements ["fi" "com" "org" "net"])))
                 :cljs nil)}
    [:string]
    [:fn {:error/message "Not a valid email address"}
     #(re-matches email-regex %)]
    [:fn {:error/message "Email contains consecutive dots"}
     #(not (re-find two-consecutive-dots-regex %))]]))

;; Common string validations
(defn string-length
  [min max]
  [:string {:min min :max max}])

;; User property schemas
(def username-schema (m/schema (string-length 1 128)))
(def password-schema (m/schema (string-length 6 128)))
(def firstname-schema (m/schema (string-length 1 128)))
(def lastname-schema (m/schema (string-length 1 128)))
(def permissions-request-schema (m/schema (string-length 1 200)))

;; User status
(def user-status (m/schema [:enum "active" "archived"]))

;; User data schemas
(def user-data-schema
  (m/schema
   [:map
    [:firstname firstname-schema]
    [:lastname lastname-schema]
    [:permissions-request {:optional true} permissions-request-schema]]))

;; Main user schema for registration/validation
(def new-user-schema
  (m/schema
   [:map
    [:email email-schema]
    [:username username-schema]
    [:password {:optional true} password-schema]
    [:user-data user-data-schema]
    [:permissions {:optional true} [:map]]]))

;; Complete user schema with system fields
(def user-schema
  (m/schema
   [:map
    [:id common/uuid]
    [:status [:enum "active" "archived"]]
    [:email email-schema]
    [:username username-schema]
    [:user-data user-data-schema]
    [:password {:optional true} password-schema]]))

;;; Role-based permissions ;;;

(def role-keyword
  (m/schema (into [:enum] (keys roles/roles))))

(def city-codes (into #{} (map :city-code) cities/all))
(def type-codes (into #{} (keys types/all)))
(def activity-values (into #{} (->> activities/by-types vals (map :value))))

(def role-schema
  "Multi-dispatch role schema, replaces s/multi-spec role-type."
  (m/schema
   [:multi {:dispatch (fn [x] (some-> x :role keyword))}
    [:admin [:map [:role [:= :admin]]]]
    [:type-manager [:map
                    [:role [:= :type-manager]]
                    [:type-code [:set (into [:enum] type-codes)]]
                    [:city-code {:optional true} [:set (into [:enum] city-codes)]]]]
    [:city-manager [:map
                    [:role [:= :city-manager]]
                    [:city-code [:set (into [:enum] city-codes)]]
                    [:type-code {:optional true} [:set (into [:enum] type-codes)]]]]
    [:site-manager [:map
                    [:role [:= :site-manager]]
                    [:lipas-id [:set [:int {:min 0}]]]]]
    [:activities-manager [:map
                          [:role [:= :activities-manager]]
                          [:activity [:set (into [:enum] activity-values)]]
                          [:city-code {:optional true} [:set (into [:enum] city-codes)]]
                          [:type-code {:optional true} [:set (into [:enum] type-codes)]]]]
    [:itrs-assessor [:map
                     [:role [:= :itrs-assessor]]
                     [:city-code {:optional true} [:set (into [:enum] city-codes)]]
                     [:type-code {:optional true} [:set (into [:enum] type-codes)]]]]
    [:floorball-manager [:map
                         [:role [:= :floorball-manager]]
                         [:type-code {:optional true} [:set (into [:enum] type-codes)]]]]
    [:analysis-user [:map [:role [:= :analysis-user]]]]
    [:analysis-experimental-user [:map [:role [:= :analysis-experimental-user]]]]
    [:ptv-manager [:map
                   [:role [:= :ptv-manager]]
                   [:city-code {:optional true} [:set (into [:enum] city-codes)]]]]
    [:ptv-auditor [:map [:role [:= :ptv-auditor]]]]
    [:org-admin [:map
                 [:role [:= :org-admin]]
                 [:org-id {:optional true} [:set :string]]]]
    [:org-user [:map
                [:role [:= :org-user]]
                [:org-id {:optional true} [:set :string]]]]
    ;; Catch-all for unknown roles - just requires :role keyword
    [::default [:map [:role role-keyword]]]]))

(def roles-schema
  (m/schema [:vector role-schema]))

(def permissions-schema
  "User permissions map, covering both old flat permissions and new role-based."
  (m/schema
   [:map
    ;; Old permissions
    [:admin? {:optional true} :boolean]
    [:draft? {:optional true} :boolean]
    [:sports-sites {:optional true} [:vector [:int {:min 0}]]]
    [:all-cities? {:optional true} :boolean]
    [:all-types? {:optional true} :boolean]
    [:cities {:optional true} [:vector (into [:enum] city-codes)]]
    [:types {:optional true} [:vector (into [:enum] type-codes)]]
    [:activities {:optional true} [:vector (into [:enum] activity-values)]]
    ;; New roles
    [:roles {:optional true} roles-schema]]))
