(ns lipas.schema.sports-sites.ptv
  "Schema definitions for PTV (Palvelutietovaranto) integration in sports sites."
  (:require [lipas.data.ptv :as ptv-data]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [malli.core :as m]))

(defn localized-string-schema
  "Creates schema for localized strings with optional constraints."
  [string-props]
  [:map
   {:closed true}
   [:fi {:optional true} [:string string-props]]
   [:se {:optional true} [:string string-props]]
   [:en {:optional true} [:string string-props]]])

(defn- max-length-props
  "String props for a text of at most `max` characters whose too-long error
   reads `message` (a localization key). Only a string over the limit gets
   it: a plain :error/message would label every failure of the schema,
   e.g. a cleared field's nil, as too long. Other failures keep malli's
   default message, which the UI doesn't surface (see
   lipas.ui.ptv.subs/service-location-schema-errors)."
  [max message]
  {:max max
   :error/fn (fn [{:keys [value]} _]
               (when (and (string? value) (> (count value) max))
                 message))})

(def integration-enum
  "Enum for PTV integration management types."
  (m/schema [:enum "lipas-managed" "manual"]))

(def audit-status-enum
  "Enum for PTV audit status values."
  (m/schema [:enum "approved" "changes-requested"]))

(def audit-field
  "Schema for individual audit field feedback. :audited-content snapshots
   the localized text the verdict was given on — verdicts are per revision,
   and comparing the snapshot against current content detects edits made
   after the audit (see lipas.data.ptv/audit-field-state)."
  (m/schema
    [:map
     {:closed true}
     [:status audit-status-enum]
     [:feedback [:string {:min 0 :max 1000}]]
     [:audited-content {:optional true} (localized-string-schema nil)]]))

(def ptv-audit
  "Schema for PTV audit information including timestamp, auditor, and field-specific feedback.
   :user-instruction (Toimintaohje) is audited for PTV Services only —
   ServiceLocations don't carry a user instruction."
  (m/schema
    [:map
     {:closed true}
     [:timestamp [:string {:min 24 :max 30}]] ;; ISO-8601 format timestamps
     [:auditor-id :string]
     [:summary {:optional true} audit-field]
     [:description {:optional true} audit-field]
     [:user-instruction {:optional true} audit-field]]))

(def audit-field-submission
  "audit-field as the auditor submits it: a change request must carry
   feedback text (see lipas.data.ptv/audit-feedback-missing?). Stored
   audits (ptv-audit) are not held to this — rows saved before the rule
   may lack it."
  (m/schema
    [:and
     audit-field
     [:fn {:error/message "a change request needs feedback text"}
      (complement ptv-data/audit-feedback-missing?)]]))

(def audit-data
  "Schema for audit data sent from frontend (before backend adds timestamp/auditor-id)."
  (m/schema
    [:map
     {:closed true}
     [:summary {:optional true} audit-field-submission]
     [:description {:optional true} audit-field-submission]
     [:user-instruction {:optional true} audit-field-submission]]))

(def ptv-meta
  "Schema for PTV metadata associated with sports sites."
  (m/schema
    [:map
     {:closed true}
     [:org-id :string]
     [:sync-enabled :boolean]
     [:delete-existing {:optional true} :boolean]

    ;; These options aren't used now:
    ;; TODO: Remove
     [:service-channel-integration
      {:optional true}
      integration-enum]
     [:service-integration
      {:optional true}
      integration-enum]

     [:service-channel-ids [:vector :string]]
     [:service-ids [:vector :string]]
     [:languages {:optional true} [:vector :string]]

    ;; Per-language character limits enforced by PTV (see lipas.data.ptv).
    ;; The :error/message values are localization keys resolved via `tr`
    ;; in the UI (e.g. the sync-button why-disabled tooltip).
     [:summary (localized-string-schema (max-length-props ptv-data/max-summary-length
                                                          :ptv/error-summary-too-long))]
     [:description (localized-string-schema (max-length-props ptv-data/max-description-length
                                                              :ptv/error-description-too-long))]
     [:user-instruction {:optional true} (localized-string-schema
                                           (max-length-props ptv-data/max-user-instruction-length
                                                             :ptv/error-user-instruction-too-long))]

     [:audit {:optional true} ptv-audit]]))

(def site-patch
  "The site's own data a PTV sync writes first, as the sync review took it
   from PTV (lipas.data.ptv-review/site-patch-paths, which this must
   match)."
  (m/schema
    [:map {:closed true}
     [:name {:optional true} #'sports-sites-schema/name]
     [:name-localized {:optional true}
      [:map {:closed true}
       [:se {:optional true} [:maybe [:string {:min 2 :max 100}]]]
       [:en {:optional true} [:maybe [:string {:min 2 :max 100}]]]]]
     [:marketing-name {:optional true} [:maybe #'sports-sites-schema/marketing-name]]
     [:phone-number {:optional true} [:maybe #'sports-sites-schema/phone-number]]
     [:email {:optional true} [:maybe #'sports-sites-schema/email]]
     [:www {:optional true} [:maybe #'sports-sites-schema/www]]
     [:reservations-link {:optional true} [:maybe #'sports-sites-schema/reservations-link]]
     [:location {:optional true}
      [:map {:closed true}
       [:address {:optional true} #'location-schema/address]
       [:postal-code {:optional true} #'location-schema/postal-code]
       [:postal-office {:optional true} [:maybe #'location-schema/postal-office]]]]]))

(def create-ptv-service-location
  "Schema for creating PTV service locations.
   `:archive?` requests an explicit archive (publishingStatus \"Deleted\")
   instead of a publish/update — used by the explicit \"Archive in PTV\"
   actions in both the wizard and the sports-site PTV tab."
  (m/schema
    [:map
     {:closed true}
     [:org-id :string]
     [:lipas-id #'sports-sites-schema/lipas-id]
     [:archive? {:optional true} :boolean]
     [:ptv ptv-meta]
     ;; The site's own data as the sync review took it from PTV
     ;; (lipas.data.ptv-review/review-site-patch): the fields a site editor
     ;; may take, validated as a site save validates them; nil removes an
     ;; optional value. Needs the right to edit the site.
     [:site-patch {:optional true} site-patch]
     ;; What the user's sync review was made against: PTV's :modified and
     ;; connections of the location. A location changed since is refused
     ;; (409 :ptv-changed-since-review). Absent: no check.
     [:reviewed {:optional true}
      [:map {:closed true}
       [:modified {:optional true} [:maybe :string]]
       [:service-ids {:optional true} [:vector :string]]]]]))
