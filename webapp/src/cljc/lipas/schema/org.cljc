(ns lipas.schema.org
  (:require [lipas.roles :as roles]
            [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sites]
            [malli.core :as m]
            [malli.util :as mu]))

;; TODO: How to ensure values are set when needed?
(def ptv-data
  "Schema for PTV integration configuration"
  (m/schema
    [:map
     [:org-id {:optional true
               :description "PTV organization UUID"}
      [:maybe common/uuid]]
     [:prod-org-id {:optional true
                    :description "Special UUID used in production for apiUserOrganisation"}
      [:maybe common/uuid]]
     [:test-credentials {:optional true
                         :description "API credentials for test environment"}
      [:maybe [:map
               [:username [:string {:min 1}]]
               [:password [:string {:min 1}]]]]]
     [:city-codes {:optional true
                   :description "Municipality codes for filtering eligible sites"}
      [:vector [:int {:min 1 :max 999}]]]
     [:owners {:optional true
               :description "Ownership types for filtering"}
      [:vector [:enum "city" "city-main-owner" "municipal-consortium"
                "state" "private" "organization" "other"]]]
     [:supported-languages {:optional true
                            :description "Languages supported by the org in PTV"}
      [:vector [:enum "fi" "se" "en"]]]
     [:sync-enabled {:optional true
                     :description "Global flag to enable/disable PTV sync for the organization"}
      [:maybe :boolean]]]))

(def org-id (m/schema :uuid))

;; Standalone schema for org name form validation
(def org-name (m/schema [:string {:min 1 :max 128}]))

(def org-type
  [:enum "city" "municipal-consortium" "state" "private" "sports-federation" "other"])

(def catalog-role-names
  "Role names a catalog template may reference: the catalog-assignable subset of
  the role vocabulary (`:org-editor`, `:ptv-manager`, `:activities-manager`, …).
  Stored as strings, since catalog specs carry stringified role names. This is
  the vocabulary the LIPAS admin builds the per-org ceiling from."
  (->> roles/roles
       (filter (comp :catalog-assignable val))
       (mapv (comp name key))
       sort
       vec))

(def role-spec
  "One role-spec inside a catalog template. `:role` MUST name a real,
  catalog-assignable role — this is what stops a malformed/unknown role from
  being stored (and then silently dropped, or worse, throwing at projection).
  Context keys are optional and `:sequential` (the transit FE may send seqs;
  jsonb normalizes them to arrays). `:org-id` is injected at projection, not
  stored, but tolerated here for round-trip safety."
  [:map
   [:role (into [:enum] catalog-role-names)]
   [:city-code {:optional true} [:sequential [:int {:min 1 :max 999}]]]
   [:type-code {:optional true} [:sequential :int]]
   [:activity {:optional true} [:sequential :string]]
   [:lipas-id {:optional true} [:sequential :int]]
   [:org-id {:optional true} [:sequential [:or :uuid :string]]]])

(def role-templates
  "The per-org role-template catalog (the ceiling). Each entry maps a name to a
  label + a vector of role-specs drawn from the catalog-assignable vocabulary.
  Strict: every spec must name a known role (see `role-spec`)."
  [:map-of :keyword
   [:map
    [:label {:optional true} :string]
    [:roles [:vector role-spec]]]])

(def ownership
  "The take-over claim rule: up to four optional, AND-combined matching axes
  (require ≥1 to match anything). Generalizes beyond the municipality case.
  Axes are `:sequential` (not `:vector`) because the transit FE may send seqs
  as well as vectors — same reason `ptv-config-update` does; jsonb normalizes
  them to arrays on store."
  [:map {:optional true}
   [:city-codes {:optional true} [:sequential [:int {:min 1 :max 999}]]]
   [:owners {:optional true} [:sequential :string]]
   [:type-codes {:optional true} [:sequential :int]]
   [:activities {:optional true} [:sequential :string]]])

(def members
  "Each member carries a single `:roles` list drawn from `#{\"admin\"}` ∪ the keys
  of the org's `:role-templates` catalog. `\"admin\"` is the reserved engine role
  (org management); membership itself confers the `:org/member` baseline, so a
  member with `:roles []` is a plain member. Validated ⊆ catalog at the
  assignment layer (`org/validate-assignment!`); the structural ceiling lives in
  projection (`org/member->roles`)."
  [:vector
   [:map
    [:user-id [:or :uuid :string]]
    [:roles {:optional true} [:vector :string]]]])

(def instructions
  "Free-text guidance an org-admin writes for their members, localized. Each
  locale is optional; the whole map is optional (absent for legacy orgs)."
  [:map
   [:fi {:optional true} [:maybe [:string {:max 10000}]]]
   [:se {:optional true} [:maybe [:string {:max 10000}]]]
   [:en {:optional true} [:maybe [:string {:max 10000}]]]])

(def org
  (m/schema
    [:map
     [:id org-id]
     [:name [:string {:min 1 :max 128}]]
     [:data {:optional true}
      [:map
       [:primary-contact {:optional true}
        [:map
         [:phone {:optional true} [:maybe sites/phone-number]]
         [:email {:optional true} [:maybe sites/email]]
         [:website {:optional true} [:maybe sites/www]]
         [:reservations-link {:optional true} [:maybe sites/reservations-link]]]]]]
     [:ptv-data {:optional true} [:maybe ptv-data]]
     ;; --- org-management (opt-in; absent for legacy orgs) ---
     [:type {:optional true} org-type]
     [:instructions {:optional true} [:maybe instructions]]
     [:role-templates {:optional true} role-templates]
     [:ownership {:optional true} ownership]
     [:members {:optional true} members]]))

(def new-org
  (m/schema (mu/dissoc org :id)))

(def user-updates
  (m/schema
    [:map
     [:changes
      [:vector
       [:or
       ;; Case 1: Has user-id but not email (existing admin workflow)
        [:map {:closed true}
         [:user-id :uuid]
         [:change [:enum "add" "remove"]]
         [:role [:enum "org-admin" "org-user"]]]
       ;; Case 2: Has email but not user-id (new email-based workflow)
        [:map {:closed true}
         [:email :string]
         [:change [:enum "add" "remove"]]
         [:role [:enum "org-admin" "org-user"]]]]]]]))

;; Schema for form validation (includes ID)
(def org-form-validation
  (m/schema org))

;; Schema for API updates (no ID required since it's in URL)
(def org-update
  (m/schema (mu/dissoc org :id)))

 ;; Schema for PTV config updates (stricter validation for API endpoint)
(def ptv-config-update
  (m/schema
    [:map
     [:org-id common/uuid]
     [:prod-org-id {:optional true} common/uuid]
     [:test-credentials {:optional true}
      [:map
       [:username [:string {:min 1}]]
       [:password [:string {:min 1}]]]]
     [:city-codes [:sequential [:int {:min 1 :max 999}]]]
     [:owners [:sequential [:enum "city" "city-main-owner" "municipal-consortium"
                            "state" "private" "organization" "other"]]]
     [:supported-languages [:sequential [:enum "fi" "se" "en"]]]
     [:sync-enabled :boolean]]))
