(ns lipas.ui.ptv.sync-summary
  "What a sync changes in PTV, as the user sees it. The review panel lists
   what Lipas changes, the user's unsaved edits too, and shows a text's
   change as a word diff behind a toggle (text-change). Before the sync,
   what it removes from PTV is named and confirmed (confirmation, and
   bulk-removals for many sites at once): a user who erased a text wasn't
   told that the sync removes it from PTV."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.ui.ptv.diff :as ptv-diff]
            [lipas.ui.ptv.review-data :as review-data]
            [reagent.core :as r]))

(def ^:private body
  "body2, which this theme sets bold, at the normal weight."
  #js {:fontWeight 400})

(def ^:private list-style
  {:margin 0 :paddingLeft "1.25em"})

;; What goes is struck through and what comes underlined, besides the
;; colour: a cue that doesn't rely on colour alone.
(def ^:private gone-style
  {:background "#ffebee" :color "inherit" :textDecoration "line-through"})

(def ^:private new-style
  {:background "#e8f5e9" :color "inherit" :textDecoration "underline"})

(defn- join-and
  "\"nimi, tiivistelmä ja kuvaus\"."
  [tr xs]
  (if (next xs)
    (str (str/join ", " (butlast xs)) " " (tr :ptv.review/and) " " (last xs))
    (first xs)))

(defn- text-field-name
  [tr field]
  (str/lower-case (tr (case field
                        :name :ptv.review/field-name
                        :summary :ptv.review/field-summary
                        :description :ptv.review/field-description))))

(defn- language-label
  "\"Englanninkielinen kieliversio\" for a language version row."
  [tr {:keys [language]}]
  (tr :ptv.review/removals-language (review-data/language-adjective tr language true)))

(defn- text-diff
  "PTV's text `from` turned into what the sync sends, `to`, word by word."
  [tr from to]
  [:> Stack {:spacing 0.5 :sx #js {:mt 0.5}}
   [:> Typography {:variant "body2" :component "div"
                   :sx #js {:fontWeight 400 :whiteSpace "pre-wrap" :wordBreak "break-word"}}
    (into [:<>]
          (for [[op v] (ptv-diff/coalesce (ptv-diff/diff from to))]
            (case op
              :removed [:del {:style gone-style} v]
              :added [:ins {:style new-style} v]
              [:span v])))]
   (when (and (not (str/blank? from)) (not (str/blank? to)))
     [:> Typography {:variant "caption" :color "text.secondary"}
      (tr :ptv.review/change-legend)])])

(defn text-change
  "A long text that a sync changes in PTV, `from` PTV's to `to`: its
   `label`, and the word diff behind a toggle. A text that comes or goes
   whole shows as the text."
  [{:keys [tr label from to]}]
  (r/with-let [shown? (r/atom false)]
    (let [whole? (or (str/blank? from) (str/blank? to))]
      [:<>
       [:> Stack {:direction "row" :alignItems "center" :spacing 0.5 :flexWrap "wrap" :useFlexGap true}
        [:> Typography {:variant "body2" :component "span" :sx body} label]
        [:> Button {:size "small" :variant "text"
                    :aria-expanded (boolean @shown?)
                    :sx #js {:textTransform "none" :py 0 :minWidth 0}
                    :endIcon (r/as-element [:> Icon (if @shown? "expand_less" "expand_more")])
                    :on-click #(swap! shown? not)}
         (tr (cond
               @shown? (if whole? :ptv.review/hide-text :ptv.review/hide-change)
               whole? :ptv.review/show-text
               :else :ptv.review/show-change))]]
       [:> Collapse {:in @shown? :unmountOnExit true}
        [text-diff tr from to]]])))

(defn- struck-list
  "`texts` struck through, one after another."
  [texts]
  (interpose ", " (for [t texts] [:del {:style gone-style} t])))

(defn- postal-address-text
  "A postal address of PTV's (GET shape) on one line: \"PL 1, 91601\"."
  [{:keys [streetAddress postOfficeBoxAddress]}]
  (let [line (or (some-> streetAddress :street first :value
                         (str (some->> (:streetNumber streetAddress) (str " "))))
                 (some-> postOfficeBoxAddress :postOfficeBox first :value))]
    (str/join ", " (remove str/blank? [line (:postalCode (or streetAddress postOfficeBoxAddress))]))))

(defn- dropped-value-texts
  "The values of a one-slot `row` PTV loses, as the review shows them."
  [row]
  (map #(review-data/value-text (:field row) #{%}) (ptv-review/dropped-values row)))

(defn- removal-item
  "One thing a sync removes from PTV (a sync-removals row)."
  [{:keys [tr row]}]
  (let [{:keys [field kind ptv texts]} row
        label (review-data/field-label tr row)]
    [:li {:style {:marginBottom 4}}
     (case kind
       ;; a language version: its texts go with it, which a user who
       ;; erased one of them may not expect
       :languages
       (let [fields (filter (set (keys texts)) [:name :summary :description])]
         [:<>
          [:> Typography {:variant "body2" :sx body}
           (str (language-label tr row)
                (when (seq fields)
                  (str " (" (join-and tr (map #(text-field-name tr %) fields)) ")")))]
          ;; why, unless the user marked it for removal
          (when-not (:marked? row)
            [:> Typography {:variant "caption" :component "div"}
             (tr :ptv.review/removals-language-why)])])

       :text
       [text-change {:tr tr :label label :from ptv :to nil}]

       ;; the services the location is disconnected from
       :set
       (into [:> Typography {:variant "body2" :component "div" :sx body} label ": "]
             (struck-list (review-data/service-names
                            row (ptv-review/disconnected-services row))))

       ;; the values PTV has besides the one Lipas keeps
       :one-slot
       (into [:> Typography {:variant "body2" :component "div" :sx body} label ": "]
             (struck-list (dropped-value-texts row)))

       ;; one PTV's API rejects, so the sync can't send it back
       :postal-address
       [:<>
        (into [:> Typography {:variant "body2" :component "div" :sx body}
               (tr :ptv.review/removals-postal-address) ": "]
              (struck-list (map postal-address-text ptv)))
        [:> Typography {:variant "caption" :component "div"}
         (tr :ptv.review/removals-postal-address-why)]]

       ;; :short :one-slot :extra :address
       [:> Typography {:variant "body2" :component "div" :sx body}
        label ": " [:del {:style gone-style} (review-data/value-text field ptv)]])]))

(defn removals
  "What a sync removes from PTV, `rows` (sync-removals), headed by `intro`."
  [{:keys [tr rows intro]}]
  [:div
   [:p {:style {:margin-top 0}} intro]
   (into [:ul {:style list-style}]
         (for [row rows]
           [removal-item {:tr tr :row row}]))])

(defn confirmation
  "Options for :lipas.ui.events/confirm-with before \"Synkronoi nyt\" sends
   a sync that removes `rows` (sync-removals) from PTV: `on-sync` sends
   it, `on-review` shows the changes in the review panel instead."
  [tr {:keys [rows on-sync on-review]}]
  {:title (tr :ptv.review/removals-title)
   :message [removals {:tr tr :rows rows :intro (tr :ptv.review/removals-intro)}]
   :confirm-label (tr :ptv.actions/sync-now)
   :confirm-primary? true
   :on-confirm on-sync
   :decline-label (tr :ptv.review/show)
   :on-decline on-review})

(defn- removal-label
  [tr row]
  (case (:kind row)
    :languages (language-label tr row)
    :set (str (review-data/field-label tr row) " ("
              (str/join ", " (review-data/service-names
                               row (ptv-review/disconnected-services row)))
              ")")
    :one-slot (str (review-data/field-label tr row) " ("
                   (str/join ", " (dropped-value-texts row)) ")")
    :postal-address (tr :ptv.review/removals-postal-address)
    (review-data/field-label tr row)))

(defn bulk-removals
  "For the confirmation of syncing `sites` (the PTV tool's rows) at once:
   what each removes from PTV (:sync-removals), under a warning. nil when
   none does."
  [tr sites]
  (let [removing (filter (comp seq :sync-removals) sites)]
    (when (seq removing)
      [:> Alert {:severity "warning" :sx #js {:my 1}}
       [:> AlertTitle (tr :ptv.review/bulk-removals-title)]
       (into [:ul {:style list-style}]
             (for [site removing]
               [:li (str (:name site) ": "
                         (str/join ", " (map #(removal-label tr %) (:sync-removals site))))]))])))
