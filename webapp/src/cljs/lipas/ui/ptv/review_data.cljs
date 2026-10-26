(ns lipas.ui.ptv.review-data
  "PTV sync review state shared by the PTV tool (:ptv module) and the site
   form (:map module): where it lives in app-db, the site form's review, and
   how rows are named. Free of UI and of the :ptv module so either can load
   it."
  (:require [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]))

(defn choices-path
  "The choices made in a site's review: row :key -> {:choice :lipas :seen}
   (see ptv-review/review-choice)."
  [lipas-id]
  [:ptv :review :choices lipas-id])

(defn open-path
  "Whether a site's review panel is expanded."
  [lipas-id]
  [:ptv :review :open lipas-id])

(defn panel-dom-id
  "The DOM id of a site's review panel, for scrolling it into view."
  [lipas-id]
  (str "ptv-review-" lipas-id))

(defn site-path
  "What the site form fetched to review a site (fetch-ptv-site-review)."
  [lipas-id]
  [:ptv :review :site lipas-id])

(defn site-form-review
  "The sync review of the site form's site: the `saved` revision against
   PTV, annotated with what the edit `draft` holds (the decisions), from
   `fetched`, what the site form fetched for it (at site-path). nil until
   fetched, for a location PTV no longer has, and for a site whose save
   won't sync."
  [fetched saved draft]
  (let [{:keys [base channel services org-langs]} fetched]
    (when (and channel saved (-> draft :ptv :sync-enabled))
      (-> (ptv-review/sync-review saved base channel services
                                  (ptv-data/effective-languages saved org-langs))
          (ptv-review/annotate-review draft)))))

(defn field-label
  "\"Tiivistelmä (SV)\", \"Markkinointinimi\", \"Liitetty palvelu Uimahallit\"."
  [tr {:keys [field language name]}]
  (case field
    :services (tr :ptv.review/service name)
    :marketing-name (tr :ptv.review/field-marketing-name)
    (str (tr (case field
               :name :ptv.review/field-name
               :summary :ptv.review/field-summary
               :description :ptv.review/field-description))
         " (" (str/upper-case language) ")")))

(defn overwritten-line
  "What a sync that keeps Lipas's side of `row` does to PTV, for the
   confirmation listing the PTV changes a sync would overwrite."
  [tr {:keys [field origin ptv? name] :as row}]
  (cond
    (and (= :services field) ptv?) (tr :ptv.review/overwritten-connection-removed name)
    (= :services field) (tr :ptv.review/overwritten-connection-restored name)
    (= :both origin) (tr :ptv.review/overwritten-both (field-label tr row))
    :else (tr :ptv.review/overwritten-text (field-label tr row))))

(defn overwrite-confirmation
  "Options for :lipas.ui.events/confirm-with asking before a write (`intro`
   says which) overwrites the changes made in PTV that `open` (rows without
   a choice) lists. `on-review` opens the review instead."
  [tr {:keys [title intro open confirm-label on-confirm on-review]}]
  {:title title
   :message [:div
             [:p {:style {:margin-top 0}} intro]
             (into [:ul]
                   (for [row open]
                     [:li (overwritten-line tr row)]))
             [:p {:style {:margin-bottom 0}} (tr :ptv.review/confirm-hint)]]
   :confirm-label confirm-label
   :decline-label (tr :ptv.review/review)
   :on-confirm on-confirm
   :on-decline on-review})
