(ns lipas.ui.ptv.review
  "The PTV sync review panel: what differs between the saved site and its
   PTV service location, who changed it since the last sync, and which
   version the edit now holds (lipas.data.ptv/sync-review,
   annotate-review). Choosing a version writes it into the edit, so the
   form shows it at once. Used in the Liikuntapaikat tab, the wizard and on
   the site form's PTV tab; the caller supplies where the choices are
   written and what sends them."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.ui.ptv.diff :as ptv-diff]
            [lipas.ui.ptv.review-data :as review-data]
            [lipas.ui.utils :as utils]
            [reagent.core :as r]))

(def ^:private diff-styles
  ;; Each card marks the words only it has. Neither is struck through:
  ;; either side may be the one that stays.
  {:lipas {:background "#e3f2fd"
           :border-radius "2px"}
   :ptv {:background "#fff3e0"
         :border-radius "2px"}})

(def ^:private body
  "body2, which this theme sets bold, at the normal weight."
  #js {:fontWeight 400})

(defn- empty-value [tr]
  [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :color "text.disabled" :fontStyle "italic"}}
   (tr :ptv.review/empty)])

(defn- diffed-text
  "One side of a word diff of Lipas's and PTV's text: `side`'s own words
   highlighted, the other side's left out."
  [tr ops side]
  (let [skip-op (case side :lipas :added :ptv :removed)
        own-op (case side :lipas :removed :ptv :added)
        style (get diff-styles side)]
    (if (some (fn [[op _]] (#{:equal own-op} op)) ops)
      [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :whiteSpace "pre-wrap" :wordBreak "break-word"}}
       (into [:<>]
             (for [[i [op v]] (map-indexed vector ops)
                   :when (not= op skip-op)]
               (if (= op own-op)
                 ^{:key i} [:span {:style style} v]
                 ^{:key i} [:span v])))]
      (empty-value tr))))

(defn- origin-chip
  [tr {:keys [origin language-added?]}]
  (let [[label color] (cond
                        language-added? [(tr :ptv.review/origin-language-added) "info"]
                        :else (case origin
                                :ptv [(tr :ptv.review/origin-ptv) "warning"]
                                :lipas [(tr :ptv.review/origin-lipas) "info"]
                                :both [(tr :ptv.review/origin-both) "error"]
                                :first-link [(tr :ptv.review/origin-first-link) "default"]
                                [(tr :ptv.review/origin-unknown) "default"]))]
    [:> Chip {:label label :size "small" :color color :variant "outlined"}]))

(defn- side-card
  "One side (Lipas or PTV) of a row; `in-form?` when the edit holds it."
  [{:keys [tr title changed? in-form? content button]}]
  [:> Paper {:variant "outlined"
             :sx #js {:p 1.5
                      :height "100%"
                      :boxSizing "border-box"
                      :display "flex"
                      :flexDirection "column"
                      :gap 1
                      :borderColor (if in-form? "primary.main" "divider")
                      :borderWidth (if in-form? 2 1)}}
   [:> Stack {:direction "row" :spacing 1 :alignItems "center" :sx #js {:minHeight 24}}
    [:> Typography {:variant "subtitle2"} title]
    (when changed?
      [:> Typography {:variant "caption" :color "text.secondary"}
       (tr :ptv.review/changed)])
    (when in-form?
      [:> Chip {:label (tr :ptv.review/in-form)
                :size "small"
                :color "primary"
                :icon (r/as-element [:> Icon {:fontSize "small"} "check"])}])]
   [:div {:style {:flex 1}} content]
   button])

(defn- choice-button
  "`subject` names what the choice is about for a screen reader, which
   would otherwise hear the same label on every row."
  [{:keys [label subject chosen? disabled? on-click]}]
  [:> Button {:variant (if chosen? "contained" "outlined")
              :size "small"
              :disabled disabled?
              :aria-label (str label ": " subject)
              :aria-pressed (boolean chosen?)
              :sx #js {:textTransform "none" :alignSelf "flex-start"}
              :on-click on-click}
   label])

(defn- shown-side
  "The version the edit holds for `row`, once it counts as chosen: decided,
   or a row that needs no decision. nil while undecided or hand-edited."
  [choices row]
  (when (and (not= :edited (:current row))
             (or (ptv-review/review-decided? choices row)
                 (not (ptv-review/review-choice-required? row))))
    (:current row)))

(defn- text-row
  [{:keys [tr row choices on-take names-takeable? stacked? read-only?]}]
  (let [{:keys [field origin lipas ptv current draft]} row
        shown (when-not read-only? (shown-side choices row))
        name? (#{:name :marketing-name} field)
        subject (review-data/field-label tr row)
        takeable? (or (not name?) names-takeable?)
        ops (ptv-diff/coalesce (ptv-diff/diff lipas ptv))]
    [:> Stack {:spacing 1}
     [:> Grid {:container true :spacing 1.5}
      [:> Grid {:item true :xs 12 :md (if stacked? 12 6)}
       [side-card {:tr tr
                   :title (tr :ptv.review/lipas)
                   :changed? (#{:lipas :both} origin)
                   :in-form? (= :lipas shown)
                   :content [diffed-text tr ops :lipas]
                   :button (when-not read-only?
                             [choice-button {:label (tr :ptv.review/keep-lipas)
                                             :subject subject
                                             :chosen? (= :lipas shown)
                                             :on-click #(on-take [row] :lipas)}])}]]
      [:> Grid {:item true :xs 12 :md (if stacked? 12 6)}
       [side-card {:tr tr
                   :title (tr :ptv.review/ptv)
                   :changed? (#{:ptv :both} origin)
                   :in-form? (= :ptv shown)
                   :content [diffed-text tr ops :ptv]
                   :button (cond
                             read-only? nil

                             takeable?
                             [:> Stack {:spacing 0.5}
                              [choice-button {:label (tr :ptv.review/take-ptv)
                                              :subject subject
                                              :chosen? (= :ptv shown)
                                              :on-click #(on-take [row] :ptv)}]
                              (when name?
                                [:> Typography {:variant "caption" :color "text.secondary"}
                                 (tr (if (= :marketing-name field)
                                       :ptv.review/marketing-name-changes-site
                                       :ptv.review/name-changes-site))])]

                             :else
                             [:> Typography {:variant "caption" :color "text.secondary"}
                              (tr :ptv.review/name-needs-edit-right)])}]]]

     (cond
       read-only? nil

       ;; the user's own text, written in the form
       (= :edited current)
       [:> Alert {:severity "info" :variant "outlined" :icon false}
        [:> AlertTitle (tr :ptv.review/edited-title)]
        [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :whiteSpace "pre-wrap"}}
         (or (not-empty draft) (tr :ptv.review/empty))]]

       ;; a conflict can be resolved in the form with a text of both
       (and (= :both origin) (not name?))
       [:> Typography {:variant "caption" :color "text.secondary"}
        (tr :ptv.review/conflict-hint)])]))

(defn- service-list
  "The services one side connects the location to, those only it has
   marked."
  [tr services only-ids side]
  (if (seq services)
    (into [:> Stack {:spacing 0.5}]
          (for [{:keys [id name]} services]
            [:> Typography {:variant "body2" :sx body}
             (if (only-ids id)
               [:span {:style (get diff-styles side)} name]
               name)]))
    (empty-value tr)))

(defn- connection-change
  "What happened to a connection since the last sync."
  [tr {:keys [origin ptv?]}]
  (tr (case [origin (boolean ptv?)]
        [:ptv true] :ptv.review/connection-added-in-ptv
        [:ptv false] :ptv.review/connection-removed-in-ptv
        [:lipas true] :ptv.review/connection-removed-in-lipas
        [:lipas false] :ptv.review/connection-added-in-lipas
        (if ptv?
          :ptv.review/connection-only-in-ptv
          :ptv.review/connection-only-in-lipas))))

(defn- connection-choice
  "One differing connection and the choice to keep or remove it."
  [{:keys [tr row choices on-take read-only?]}]
  (let [{:keys [name ptv? lipas?]} row
        shown (shown-side choices row)
        ;; keeping it takes the side that has it
        keep-side (if ptv? :ptv :lipas)
        remove-side (if ptv? :lipas :ptv)
        connected? (when shown (if (= :ptv shown) ptv? lipas?))]
    [:> Stack {:direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true}
     [:> Typography {:variant "body2" :sx #js {:fontWeight 600}} name]
     [:> Typography {:variant "body2" :color "text.secondary" :sx body}
      (connection-change tr row)]
     (when-not read-only?
       [:<>
        (when (and (ptv-review/review-choice-required? row) (not shown))
          [:> Typography {:variant "caption" :color "warning.dark"}
           (tr :ptv.review/choose-before-sync)])
        [:span {:style {:flex 1}}]
        [choice-button {:label (tr :ptv.review/keep-connection)
                        :subject name
                        :chosen? (true? connected?)
                        :on-click #(on-take [row] keep-side)}]
        [choice-button {:label (tr :ptv.review/remove-connection)
                        :subject name
                        :chosen? (false? connected?)
                        :on-click #(on-take [row] remove-side)}]])]))

(defn- connections-block
  "The location's connections to services: everything each side has, with
   a choice for each difference."
  [{:keys [tr rows services choices on-take stacked? read-only?]}]
  (let [only-lipas (into #{} (comp (filter :lipas?) (map :id)) rows)
        only-ptv (into #{} (comp (filter :ptv?) (map :id)) rows)
        origins (distinct (map :origin rows))]
    [:> Paper {:variant "outlined" :sx #js {:p 1.5}}
     [:> Stack {:spacing 1.5}
      [:> Stack {:direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true}
       [:> Typography {:variant "subtitle1" :sx #js {:fontWeight 600}}
        (tr :ptv.review/field-services)]
       (for [origin origins]
         ^{:key origin} [origin-chip tr {:origin origin}])]
      [:> Grid {:container true :spacing 1.5}
       [:> Grid {:item true :xs 12 :md (if stacked? 12 6)}
        [side-card {:tr tr
                    :title (tr :ptv.review/lipas)
                    :changed? (some #(#{:lipas :both} (:origin %)) rows)
                    :content [service-list tr (:lipas services) only-lipas :lipas]}]]
       [:> Grid {:item true :xs 12 :md (if stacked? 12 6)}
        [side-card {:tr tr
                    :title (tr :ptv.review/ptv)
                    :changed? (some #(#{:ptv :both} (:origin %)) rows)
                    :content [service-list tr (:ptv services) only-ptv :ptv]}]]]
      (into [:> Stack {:spacing 1}]
            (for [row rows]
              [connection-choice {:tr tr :row row :choices choices :on-take on-take
                                  :read-only? read-only?}]))]]))

(defn- review-row
  [{:keys [tr row choices read-only?] :as props}]
  (let [open? (and (not read-only?)
                   (ptv-review/review-choice-required? row)
                   (not (ptv-review/review-decided? choices row)))]
    [:> Paper {:variant "outlined"
               :sx #js {:p 1.5
                        :bgcolor (when (= :both (:origin row)) "rgba(211, 47, 47, 0.04)")}}
     [:> Stack {:spacing 1}
      [:> Stack {:direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true}
       [:> Typography {:variant "subtitle1" :sx #js {:fontWeight 600}}
        (review-data/field-label tr row)]
       [origin-chip tr row]
       (when open?
         [:> Typography {:variant "caption" :color "warning.dark"}
          (tr :ptv.review/choose-before-sync)])]
      [text-row props]]]))

(defn- labels
  [tr rows]
  (str/join ", " (map #(review-data/field-label tr %) rows)))

(defn- language-added-lines
  "One line per language Lipas adds: its texts need no choice."
  [tr rows]
  (for [[language rows] (group-by :language rows)]
    ^{:key language}
    [:> Typography {:variant "body2" :sx body}
     (tr :ptv.review/language-added
         (str/upper-case language)
         (str/join ", " (map (fn [{:keys [field]}]
                               (str/lower-case
                                 (tr (case field
                                       :name :ptv.review/field-name
                                       :marketing-name :ptv.review/field-marketing-name
                                       :summary :ptv.review/field-summary
                                       :description :ptv.review/field-description))))
                             rows)))]))

(defn- up-to-date-line
  [tr matching]
  (when (seq matching)
    [:> Typography {:variant "body2" :color "text.secondary" :sx body}
     (tr :ptv.review/up-to-date
         (str/join ", " (for [{:keys [field] :as m} matching]
                          (if (= :services field)
                            (tr :ptv.review/services-match)
                            (review-data/field-label tr m)))))]))

(defn- connection-outcome
  "What a sync does with the connection `row` when the edit holds
   `shown`'s side: adds it to the side without it, or removes it from the
   side with it."
  [shown {:keys [ptv? lipas?]}]
  (let [connected? (if (= :ptv shown) ptv? lipas?)]
    (case [(boolean connected?) (boolean ptv?)]
      [true true] :ptv.review/connection-to-lipas
      [true false] :ptv.review/connection-to-ptv
      [false true] :ptv.review/connection-removed-from-ptv
      [false false] :ptv.review/connection-removed-from-lipas)))

(defn- outcome-lines
  "Where each difference ends up with what the edit holds now."
  [tr rows choices]
  (let [side (fn [row]
               (if (= :edited (:current row))
                 :edited
                 (shown-side choices row)))
        by (group-by (fn [row]
                       (let [s (side row)]
                         ;; a connection is added or removed, not copied
                         (if (and s (= :services (:field row))) :connection s)))
                     rows)
        connections (group-by #(connection-outcome (side %) %) (get by :connection))]
    [:> Stack {:spacing 0.5}
     (when-let [rows (seq (get by :lipas))]
       [:> Typography {:variant "body2" :sx body}
        (tr :ptv.review/to-ptv (labels tr rows))])
     (when-let [rows (seq (get by :ptv))]
       [:> Typography {:variant "body2" :sx body}
        (tr :ptv.review/to-lipas (labels tr rows))])
     (when-let [rows (seq (get by :edited))]
       [:> Typography {:variant "body2" :sx body}
        (tr :ptv.review/edited-in-form (labels tr rows))])
     (for [k [:ptv.review/connection-to-lipas
              :ptv.review/connection-to-ptv
              :ptv.review/connection-removed-from-ptv
              :ptv.review/connection-removed-from-lipas]
           :let [rows (get connections k)]
           :when rows]
       ^{:key k}
       [:> Typography {:variant "body2" :sx body}
        (tr k (str/join ", " (map :name rows)))])
     (when-let [rows (seq (get by nil))]
       [:> Typography {:variant "body2" :color "warning.dark"}
        (tr :ptv.review/still-open (labels tr rows))])]))

(defn- toggle-button
  [tr open? on-toggle controls]
  [:> Button {:size "small"
              :color "inherit"
              :aria-expanded (boolean open?)
              ;; the details are unmounted while closed
              :aria-controls (when open? controls)
              :sx #js {:textTransform "none" :whiteSpace "nowrap" :ml -0.5}
              :endIcon (r/as-element [:> Icon (if open? "expand_less" "expand_more")])
              :on-click on-toggle}
   (tr (if open? :ptv.review/hide :ptv.review/show))])

(defn review-panel
  "The review of one site, collapsed to a bar until opened.

   Props:
   - :id           DOM id (review-data/panel-dom-id)
   - :review       ptv-review/sync-review of the saved site, annotated with
                   the edit (ptv-review/annotate-review)
   - :choices      the decisions to keep Lipas's version (choices-path)
   - :open?        whether it is expanded; :on-toggle flips it
   - :on-take      (fn [rows side]) makes the edit hold `side`'s version
                   (:lipas or :ptv) of each of `rows`
   - :on-reset     (fn [rows]) back to the saved versions, nothing decided
   - :names-takeable? whether the user may take PTV's names (renaming the
                   site needs the right to edit it)
   - :last-sync    shown in the intro
   - :footer       what the choices go into: a note (string) or an element
   - :stacked?     Lipas's and PTV's cards one above the other, for a
                   narrow column
   - :read-only?   only what differs, without choices (the site's view
                   mode: the choices go into an edit); :footer says how
                   to make them"
  [{:keys [id tr review choices open? on-toggle on-take on-reset
           names-takeable? last-sync footer stacked? read-only?]}]
  (let [{:keys [rows matching services base? first-link?]} review]
    (when (seq rows)
      (let [{lang-added true others false} (group-by #(boolean (and (= :lipas (:origin %))
                                                                    (:language-added? %)))
                                                     rows)
            {connections true texts false} (group-by #(= :services (:field %)) others)
            counts (frequencies (map :origin rows))
            required (filter ptv-review/review-choice-required? rows)
            severity (cond
                       (pos? (get counts :both 0)) "error"
                       (seq required) "warning"
                       :else "info")
            takeable (remove #(and (#{:name :marketing-name} (:field %))
                                   (not names-takeable?))
                             required)
            ;; a choice made against a PTV value that has since changed no
            ;; longer counts, so there's nothing left to undo
            touched? (some #(or (ptv-review/review-choice choices %)
                                (not= :lipas (:current %)))
                           rows)
            details-id (some-> id (str "-details"))]
        [:> Stack {:id id :spacing 1}
         [:> Alert {:severity severity :variant "outlined"}
          [:> AlertTitle {:sx #js {:mb 0.5}}
           (if (= 1 (count rows))
             (tr :ptv.review/bar-one)
             (tr :ptv.review/bar (count rows)))]
          ;; who changed what is told row by row once opened; the toggle
          ;; under the count, where the eye lands first
          [toggle-button tr open? on-toggle details-id]]

         [:> Collapse {:in open? :unmountOnExit true :id details-id}
          [:> Stack {:spacing 1.5 :sx #js {:pt 0.5}}
           [:> Typography {:variant "body2" :sx body}
            (cond
              base? (tr :ptv.review/intro-synced (utils/->human-date-time-at-user-tz last-sync))
              first-link? (tr :ptv.review/intro-first-link)
              :else (tr :ptv.review/intro-unknown))]

           (when (seq lang-added)
             (into [:> Stack {:spacing 0.5}] (language-added-lines tr lang-added)))

           (for [row texts]
             ^{:key (str (:key row))}
             [review-row {:tr tr
                          :row row
                          :choices choices
                          :on-take on-take
                          :names-takeable? names-takeable?
                          :stacked? stacked?
                          :read-only? read-only?}])

           (when (seq connections)
             [connections-block {:tr tr
                                 :rows connections
                                 :services services
                                 :choices choices
                                 :on-take on-take
                                 :stacked? stacked?
                                 :read-only? read-only?}])

           [up-to-date-line tr matching]

           ;; after the differences: decided once they've been read
           ;; bulk only where it saves clicks: more than one difference
           (when (and (not read-only?) (or (next takeable) (next required) touched?))
             [:> Stack {:direction "row" :spacing 1 :flexWrap "wrap" :useFlexGap true}
              (when (next takeable)
                [:> Button {:size "small" :variant "outlined"
                            :sx #js {:textTransform "none"}
                            :on-click #(on-take takeable :ptv)}
                 (tr :ptv.review/take-all-ptv)])
              (when (next required)
                [:> Button {:size "small" :variant "outlined"
                            :sx #js {:textTransform "none"}
                            :on-click #(on-take required :lipas)}
                 (tr :ptv.review/keep-all-lipas)])
              (when touched?
                [:> Button {:size "small" :variant "text"
                            :sx #js {:textTransform "none"}
                            :on-click #(on-reset rows)}
                 (tr :ptv.review/clear-choices)])])

           [:> Paper {:variant "outlined" :sx #js {:p 1.5 :bgcolor "action.hover"}}
            [:> Stack {:spacing 1}
             (when-not read-only?
               [outcome-lines tr rows choices])
             (if (string? footer)
               [:> Typography {:variant "body2" :color "text.secondary" :sx body} footer]
               footer)]]]]]))))
