(ns lipas.schema.users
  (:require #?(:clj [clojure.test.check.generators :as gen])
            [clojure.string :as str]
            [lipas.data.activities :as activities]
            [lipas.data.cities :as cities]
            [lipas.data.types :as types]
            [lipas.roles :as roles]
            [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [malli.core :as m]))

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

;; Free text typed by a person. Trimmed on the way in (the backend's JSON
;; coercion runs :decode/json; the browser form validates the raw value, which
;; is fine — it only has to be non-blank). Control characters are rejected: they
;; have no business in a name, and a CR/LF in a value that ends up in an email is
;; how header injection starts. `multiline?` keeps \t \n \r for text areas.
(def ^:private control-chars #"[\x00-\x1F\x7F]")
(def ^:private control-chars-except-whitespace #"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]")

(defn- trim-string [x]
  (if (string? x) (str/trim x) x))

(defn human-text
  [min max & {:keys [multiline?]}]
  (let [forbidden (if multiline? control-chars-except-whitespace control-chars)]
    [:and
     [:string {:min min :max max :decode/json trim-string}]
     [:fn {:error/message "Must not be blank"} (complement str/blank?)]
     [:fn {:error/message "Contains control characters"}
      #(not (re-find forbidden %))]]))

;; User property schemas
(def username-schema (m/schema (string-length 1 128)))
(def password-schema (m/schema (string-length 6 128)))
(def firstname-schema (m/schema (human-text 1 128)))
(def lastname-schema (m/schema (human-text 1 128)))
(def permissions-request-schema (m/schema (human-text 1 200 :multiline? true)))

;; Self-chosen usernames are stricter than stored ones (which include legacy
;; values and invite accounts' full email addresses). No whitespace, no ":"
;; (the HTTP Basic user:pass separator, so such a name could never log in) and
;; no "@" (login looks the identifier up as an email FIRST, so a username shaped
;; like someone else's address would be shadowed by — or shadow — that account).
;; Same character set as an email local part, which is what the form pre-fills.
(def registration-username-regex #"^[a-zA-Z0-9åÅäÄöÖ._%+-]{1,128}$")

(def registration-username-schema
  (m/schema
    [:re {:error/message "Letters, digits and . _ % + - only"}
     registration-username-regex]))

;; User status
(def user-status (m/schema [:enum "active" "archived"]))

;; How the email address was proven. See the account migration
;; 20260911120000-account-email-verification for the meaning of each value.
(def email-verified-via (m/schema [:enum "legacy" "registration" "login"]))

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

;; Self-registration, step 1: "send me a link". `:register-url` is validated by
;; the route (lipas.schema.handler/magic-link-login-url) — kept out of here so
;; this namespace doesn't grow a dependency on the handler schemas.
(def registration-lang (m/schema [:enum "fi" "se" "en"]))

;; Self-registration, step 2: the form on the page the link opens. No email —
;; the address comes from the verified token, never from the client.
(def registration-form-schema
  (m/schema
    [:map
     [:username registration-username-schema]
     [:password password-schema]
     [:user-data user-data-schema]]))

(def registration-payload-schema
  (m/schema
    [:map {:closed true}
     [:token [:string {:min 1 :max 2048}]]
     [:username registration-username-schema]
     [:password password-schema]
     [:user-data [:map {:closed true}
                  [:firstname firstname-schema]
                  [:lastname lastname-schema]
                  [:permissions-request {:optional true} permissions-request-schema]]]]))

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
     [:images-manager [:map
                       [:role [:= :images-manager]]
                       [:city-code [:set (into [:enum] city-codes)]]
                       [:type-code {:optional true} [:set (into [:enum] type-codes)]]]]
     [:site-manager [:map
                     [:role [:= :site-manager]]
                     [:lipas-id [:set #'sports-sites-schema/lipas-id]]]]
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
     [:assistant-tester [:map [:role [:= :assistant-tester]]]]
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
