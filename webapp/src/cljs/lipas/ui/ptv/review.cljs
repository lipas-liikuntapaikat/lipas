(ns lipas.ui.ptv.review
  "The PTV sync review panel: what a sync of the saved site would change in
   its PTV service location, who changed what since the last sync, and
   which version the edit now holds (lipas.data.ptv-review/sync-review,
   annotate-review). Choosing a version writes it into the edit, so the
   form shows it at once. One panel for the Liikuntapaikat tab, the wizard
   and the site form's PTV tab; the caller supplies where the choices are
   written, what the user may take, the state notices and what sends the
   result.

   From top to bottom: the bar (one sentence and one count, collapsed by
   default), ① the location's state, ② the decisions grouped by subject,
   ③ what the sync updates from Lipas, ④ what is up to date and what PTV
   keeps, the bulk buttons and ⑤ where every difference ends up."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.ui.ptv.diff :as ptv-diff]
            [lipas.ui.ptv.review-data :as review-data]
            [lipas.ui.utils :as utils :refer [==>]]
            [reagent.core :as r]))

(def ^:private diff-styles
  ;; Each card marks the words only it has, with a dotted underline besides
  ;; the colour (a cue that doesn't rely on colour). Neither is struck
  ;; through: either side may be the one that stays.
  {:lipas {:background "#e3f2fd" :color "inherit" :borderRadius "2px"
           :textDecoration "underline dotted"}
   :ptv {:background "#fff3e0" :color "inherit" :borderRadius "2px"
         :textDecoration "underline dotted"}})

(def ^:private body
  "body2, which this theme sets bold, at the normal weight."
  #js {:fontWeight 400})

(defn- empty-value [tr]
  [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :color "text.disabled" :fontStyle "italic"}}
   (tr :ptv.review/empty)])

(defn- value-line
  [tr v]
  (if (str/blank? v)
    (empty-value tr)
    [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :wordBreak "break-word"}} v]))

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
                 ^{:key i} [:mark {:style style} v]
                 ^{:key i} [:span v])))]
      (empty-value tr))))

(defn- origin-chip
  [tr {:keys [origin language-added? kind]}]
  (let [[label color] (cond
                        language-added? [(tr :ptv.review/origin-language-added) "info"]
                        (= :languages kind) [(tr :ptv.review/origin-language-removed) "warning"]
                        (contains? ptv-review/info-kinds kind) [(tr :ptv.review/origin-info) "default"]
                        :else (case origin
                                :ptv [(tr :ptv.review/origin-ptv) "warning"]
                                :lipas [(tr :ptv.review/origin-lipas) "info"]
                                ;; marked on the row, by a tint too, not by red
                                :both [(tr :ptv.review/origin-both) "warning"]
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
  [{:keys [id label subject chosen? disabled? on-click]}]
  [:> Button {:id id
              :variant (if chosen? "contained" "outlined")
              :size "small"
              :disabled disabled?
              :aria-label (str label ": " subject)
              :aria-pressed (boolean chosen?)
              :sx #js {:textTransform "none" :alignSelf "flex-start"}
              :on-click on-click}
   label])

(defn- caption [text]
  [:> Typography {:variant "caption" :color "text.secondary"} text])

(defn- shown-side
  "The version the edit holds for `row`, once it counts as chosen: decided,
   or a row that needs no decision. nil while undecided or hand-edited."
  [choices row]
  (when (and (not= :edited (:current row))
             (or (ptv-review/review-decided? choices row)
                 (not (ptv-review/review-choice-required? row))))
    (:current row)))

(defn- changes-site-caption
  "What taking PTV's version of `row` does to Lipas, naming where the field
   is edited in the site form (`site-tab`)."
  [tr row site-tab]
  (let [k (case (when-not (:service? row) (:field row))
            :name :ptv.review/name-changes-site
            :marketing-name :ptv.review/marketing-name-changes-site
            :phone :ptv.review/phone-changes-site
            :email :ptv.review/email-changes-site
            (:www :other-web-pages) :ptv.review/www-changes-site
            :reservations-link :ptv.review/reservations-link-changes-site
            :address :ptv.review/address-changes-site
            nil)]
    (when k
      (str (tr k) (when site-tab (str " (" site-tab ")"))))))

(defn- not-takeable-caption
  [tr row]
  (tr (if (= :ptv-manager (:takeable-by row))
        :ptv.review/needs-ptv-right
        :ptv.review/needs-edit-right)))

(defn- two-cards
  [{:keys [stacked?]} lipas-card ptv-card]
  [:> Grid {:container true :spacing 1.5}
   [:> Grid {:item true :xs 12 :md (if stacked? 12 6)} lipas-card]
   [:> Grid {:item true :xs 12 :md (if stacked? 12 6)} ptv-card]])

(defn- text-row
  "A name, marketing name, summary or description."
  [{:keys [tr row choices on-take can-take? stacked? read-only? site-tab first-open-id]}]
  (let [{:keys [kind origin lipas ptv current draft]} row
        shown (when-not read-only? (shown-side choices row))
        subject (review-data/field-label tr row)
        long? (= :text kind)
        ops (when long? (ptv-diff/coalesce (ptv-diff/diff lipas ptv)))]
    [:> Stack {:spacing 1}
     [two-cards {:stacked? stacked?}
      [side-card {:tr tr
                  :title (tr :ptv.review/lipas)
                  :changed? (#{:lipas :both} origin)
                  :in-form? (= :lipas shown)
                  :content (if long? [diffed-text tr ops :lipas] [value-line tr lipas])
                  :button (when-not read-only?
                            [choice-button {:id first-open-id
                                            :label (tr :ptv.review/keep-lipas)
                                            :subject subject
                                            :chosen? (= :lipas shown)
                                            :on-click #(on-take [row] :lipas)}])}]
      [side-card {:tr tr
                  :title (tr :ptv.review/ptv)
                  :changed? (#{:ptv :both} origin)
                  :in-form? (= :ptv shown)
                  :content (if long? [diffed-text tr ops :ptv] [value-line tr ptv])
                  :button (cond
                            read-only? nil

                            (can-take? row)
                            [:> Stack {:spacing 0.5}
                             [choice-button {:label (tr :ptv.review/take-ptv)
                                             :subject subject
                                             :chosen? (= :ptv shown)
                                             :on-click #(on-take [row] :ptv)}]
                             (some->> (changes-site-caption tr row site-tab) caption)]

                            :else (caption (not-takeable-caption tr row)))}]]

     (when long?
       [:> Typography {:variant "caption" :color "text.secondary"}
        (tr :ptv.review/diff-legend)])

     (cond
       read-only? nil

       ;; the user's own text, written in the form
       (= :edited current)
       [:> Alert {:severity "info" :variant "outlined" :icon false}
        [:> AlertTitle (tr :ptv.review/edited-title)]
        [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :whiteSpace "pre-wrap"}}
         (or (not-empty draft) (tr :ptv.review/empty))]]

       ;; a conflict can be resolved in the form with a text of both
       (and (= :both origin) long?)
       (caption (tr :ptv.review/conflict-hint)))]))

(defn- same-slot-value?
  "Whether two values of a one-slot field are the same: an email or a URL
   regardless of case, a phone as a whole."
  [a b]
  (if (and (string? a) (string? b))
    (= (str/lower-case a) (str/lower-case b))
    (= a b)))

(defn- one-slot-row
  "A phone, email or web page: Lipas holds one value, PTV may hold several,
   each of which can be taken (D5)."
  [{:keys [tr row choices on-take on-take-value can-take? stacked? read-only? site-tab first-open-id]}]
  (let [{:keys [field origin lipas ptv current]} row
        shown (when-not read-only? (shown-side choices row))
        subject (review-data/field-label tr row)
        values (ptv-review/ptv-values row)
        several? (next values)
        held (when (= :ptv current) (first (:draft row)))]
    [:> Stack {:spacing 1}
     [two-cards {:stacked? stacked?}
      [side-card {:tr tr
                  :title (tr :ptv.review/lipas)
                  :changed? (#{:lipas :both} origin)
                  :in-form? (= :lipas shown)
                  :content [value-line tr (review-data/value-text field lipas)]
                  :button (when-not read-only?
                            [:> Stack {:spacing 0.5}
                             [choice-button {:id first-open-id
                                             :label (tr :ptv.review/keep-lipas)
                                             :subject subject
                                             :chosen? (= :lipas shown)
                                             :on-click #(on-take [row] :lipas)}]
                             (when (or several? (and (seq values) (empty? lipas)))
                               (caption (tr :ptv.review/others-removed)))])}]
      [side-card {:tr tr
                  :title (tr :ptv.review/ptv)
                  :changed? (#{:ptv :both} origin)
                  :in-form? (= :ptv shown)
                  :content (if (and several? (not read-only?) (can-take? row))
                             ;; each value with its own button
                             (into [:> Stack {:spacing 1}]
                                   (for [v values
                                         :let [text (review-data/value-text field #{v})]]
                                     [:> Stack {:direction "row" :spacing 1 :alignItems "center"
                                                :flexWrap "wrap" :useFlexGap true}
                                      [:> Typography {:variant "body2" :sx body} text]
                                      [choice-button {:label (tr :ptv.review/take-this)
                                                      :subject (str subject ": " text)
                                                      :chosen? (and (some? held) (same-slot-value? held v))
                                                      :on-click #(on-take-value row v)}]]))
                             [value-line tr (review-data/value-text field ptv)])
                  :button (cond
                            read-only? nil

                            (not (can-take? row)) (caption (not-takeable-caption tr row))

                            several? (some->> (changes-site-caption tr row site-tab) caption)

                            :else
                            [:> Stack {:spacing 0.5}
                             [choice-button {:label (tr :ptv.review/take-ptv)
                                             :subject subject
                                             :chosen? (= :ptv shown)
                                             :on-click #(on-take [row] :ptv)}]
                             (some->> (changes-site-caption tr row site-tab) caption)])}]]
     (caption (tr :ptv.review/one-slot-note (str/lower-case subject)))]))

(defn- other-pages-row
  "Web pages PTV has that Lipas has no slot for: each can be taken as the
   site's web page or booking link; keeping Lipas's removes them."
  [{:keys [tr row choices on-take on-take-value can-take? stacked? read-only? site-tab first-open-id]}]
  (let [{:keys [origin current]} row
        shown (when-not read-only? (shown-side choices row))
        subject (review-data/field-label tr row)]
    [two-cards {:stacked? stacked?}
     [side-card {:tr tr
                 :title (tr :ptv.review/lipas)
                 :changed? false
                 :in-form? (= :lipas shown)
                 :content (empty-value tr)
                 :button (when-not read-only?
                           [:> Stack {:spacing 0.5}
                            [choice-button {:id first-open-id
                                            :label (tr :ptv.review/keep-lipas)
                                            :subject subject
                                            :chosen? (= :lipas shown)
                                            :on-click #(on-take [row] :lipas)}]
                            (caption (tr :ptv.review/other-pages-removed))])}]
     [side-card {:tr tr
                 :title (tr :ptv.review/ptv)
                 :changed? (#{:ptv :both} origin)
                 :in-form? (= :ptv current)
                 :content (into [:> Stack {:spacing 1}]
                                (for [url (ptv-review/ptv-values row)]
                                  [:> Stack {:spacing 0.5}
                                   [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :wordBreak "break-all"}} url]
                                   (when (and (not read-only?) (can-take? row))
                                     [:> Stack {:direction "row" :spacing 1 :flexWrap "wrap" :useFlexGap true}
                                      [choice-button {:label (tr :ptv.review/take-as-www)
                                                      :subject url
                                                      :on-click #(on-take-value row {:slot :www :url url})}]
                                      [choice-button {:label (tr :ptv.review/take-as-reservations-link)
                                                      :subject url
                                                      :on-click #(on-take-value row {:slot :reservations-link :url url})}]])]))
                 :button (cond
                           read-only? nil
                           (not (can-take? row)) (caption (not-takeable-caption tr row))
                           :else (some->> (changes-site-caption tr row site-tab) caption))}]]))

(defn- address-row
  [{:keys [tr row choices on-take can-take? stacked? read-only? site-tab first-open-id]}]
  (let [{:keys [origin lipas ptv]} row
        shown (when-not read-only? (shown-side choices row))
        subject (review-data/field-label tr row)
        postal-office (get-in row [:saved [:location :postal-office]])
        replaced (cond-> []
                   (:other-streets ptv) (conj (tr :ptv.review/address-other-streets
                                                  (str/join ", " (for [[lang v] (sort (:other-streets ptv))]
                                                                   (str v " (" (str/upper-case lang) ")")))))
                   (:additional-info ptv) (conj (tr :ptv.review/address-additional-info)))]
    [:> Stack {:spacing 1}
     [two-cards {:stacked? stacked?}
      [side-card {:tr tr
                  :title (tr :ptv.review/lipas)
                  :changed? (#{:lipas :both} origin)
                  :in-form? (= :lipas shown)
                  :content [value-line tr (review-data/value-text :address lipas postal-office)]
                  :button (when-not read-only?
                            [:> Stack {:spacing 0.5}
                             [choice-button {:id first-open-id
                                             :label (tr :ptv.review/keep-lipas)
                                             :subject subject
                                             :chosen? (= :lipas shown)
                                             :on-click #(on-take [row] :lipas)}]
                             (when (seq replaced)
                               (caption (str/join " " replaced)))])}]
      [side-card {:tr tr
                  :title (tr :ptv.review/ptv)
                  :changed? (#{:ptv :both} origin)
                  :in-form? (= :ptv shown)
                  :content [value-line tr (review-data/value-text :address ptv)]
                  :button (cond
                            read-only? nil
                            (not (can-take? row)) (caption (not-takeable-caption tr row))
                            :else
                            [:> Stack {:spacing 0.5}
                             [choice-button {:label (tr :ptv.review/take-ptv)
                                             :subject subject
                                             :chosen? (= :ptv shown)
                                             :on-click #(on-take [row] :ptv)}]
                             (some->> (changes-site-caption tr row site-tab) caption)])}]]
     (caption (tr :ptv.review/one-slot-note (str/lower-case subject)))]))

(defn- info-row
  "Municipality or point: information, never a choice (D4)."
  [{:keys [tr row]}]
  (let [{:keys [field lipas ptv distance-m]} row]
    [:> Stack {:spacing 0.5}
     (case field
       :municipality
       [:> Typography {:variant "body2" :sx body}
        (tr :ptv.review/municipality-replaced
            (review-data/value-text :municipality ptv)
            (review-data/value-text :municipality lipas))]

       :point
       [:<>
        [:> Typography {:variant "body2" :sx body}
         (tr :ptv.review/point-replaced distance-m)]
        (caption (tr :ptv.review/point-not-takeable))])]))

(defn- copy-button
  [tr text]
  [:> Tooltip {:title (tr :ptv.review/copy)}
   [:> IconButton {:size "small"
                   :aria-label (tr :ptv.review/copy)
                   :on-click #(==> [:lipas.ui.events/copy-to-clipboard! text])}
    [:> Icon {:fontSize "small"} "content_copy"]]])

(defn- language-row
  "A language version PTV has that the sync removes (D6): what goes, with
   each text copyable, and, when the org has the language, taking PTV's
   texts into Lipas so it stays."
  [{:keys [tr row on-take can-take? org-langs read-only?]}]
  (r/with-let [texts-open? (r/atom false)]
    (let [{:keys [language locale texts current]} row
          enabled? (and locale (contains? (set org-langs) (name locale)))
          lang-name (review-data/language-name tr language)]
      [:> Stack {:spacing 1}
       [:> Typography {:variant "body2" :sx body}
        (tr :ptv.review/language-removed (review-data/language-adjective tr language false))]
       (when (seq texts)
         [:> Stack {:spacing 0.5}
          [:> Button {:size "small" :variant "text" :color "inherit"
                      :aria-expanded @texts-open?
                      :sx #js {:textTransform "none" :alignSelf "flex-start" :ml -0.5}
                      :startIcon (r/as-element [:> Icon (if @texts-open? "expand_less" "chevron_right")])
                      :on-click #(swap! texts-open? not)}
           (tr :ptv.review/show-removed-texts)]
          [:> Collapse {:in @texts-open? :unmountOnExit true}
           (into [:> Stack {:spacing 1 :sx #js {:pl 1}}]
                 (for [f [:name :summary :description]
                       :let [v (get texts f)]
                       :when v]
                   [:> Stack {:direction "row" :spacing 1 :alignItems "flex-start"}
                    [:> Stack {:sx #js {:flex 1 :minWidth 0}}
                     (caption (tr (keyword "ptv.review" (str "field-" (name f)))))
                     [:> Typography {:variant "body2" :sx #js {:fontWeight 400 :whiteSpace "pre-wrap"}} v]]
                    [copy-button tr v]]))]])
       (cond
         read-only? nil

         (and enabled? (can-take? row))
         [:> Stack {:spacing 0.5}
          [choice-button {:label (tr :ptv.review/take-ptv-texts)
                          :subject lang-name
                          :chosen? (= :ptv current)
                          :on-click #(on-take [row] (if (= :ptv current) :lipas :ptv))}]
          (caption (tr :ptv.review/take-ptv-texts-hint lang-name))]

         :else
         (caption (tr :ptv.review/language-enable-hint lang-name)))])))

(defn- service-list
  "The services one side connects the location to, those only it has
   marked."
  [tr services only-ids side]
  (if (seq services)
    (into [:> Stack {:spacing 0.5}]
          (for [{:keys [id name]} services]
            [:> Typography {:variant "body2" :sx body}
             (if (only-ids id)
               [:mark {:style (get diff-styles side)} name]
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
  [{:keys [tr row choices on-take can-take? read-only? first-open-id]}]
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
        (if (can-take? row)
          [:<>
           [choice-button {:id first-open-id
                           :label (tr :ptv.review/keep-connection)
                           :subject name
                           :chosen? (true? connected?)
                           :on-click #(on-take [row] keep-side)}]
           [choice-button {:label (tr :ptv.review/remove-connection)
                           :subject name
                           :chosen? (false? connected?)
                           :on-click #(on-take [row] remove-side)}]]
          (caption (not-takeable-caption tr row)))])]))

(defn- connections-block
  "The location's connections to services: everything each side has, with
   a choice for each difference."
  [{:keys [tr rows services stacked?] :as props}]
  (let [only-lipas (into #{} (comp (filter :lipas?) (map :id)) rows)
        only-ptv (into #{} (comp (filter :ptv?) (map :id)) rows)]
    [:> Stack {:spacing 1.5}
     [two-cards {:stacked? stacked?}
      [side-card {:tr tr
                  :title (tr :ptv.review/lipas)
                  :changed? (some #(#{:lipas :both} (:origin %)) rows)
                  :content [service-list tr (:lipas services) only-lipas :lipas]}]
      [side-card {:tr tr
                  :title (tr :ptv.review/ptv)
                  :changed? (some #(#{:ptv :both} (:origin %)) rows)
                  :content [service-list tr (:ptv services) only-ptv :ptv]}]]
     (into [:> Stack {:spacing 1}]
           (for [[i row] (map-indexed vector rows)]
             [connection-choice (cond-> (assoc props :row row)
                                  (pos? i) (dissoc :first-open-id))]))]))

(defn- decided-text
  "One line for a decided row: which version stays."
  [tr row choices]
  (let [label (review-data/field-label tr row)]
    (case (if (= :edited (:current row)) :edited (shown-side choices row))
      :ptv (tr :ptv.review/decided-ptv label)
      :edited (tr :ptv.review/decided-edited label)
      (tr :ptv.review/decided-lipas label))))

(defn- row-body
  [{:keys [row] :as props}]
  (case (:kind row)
    (:short :text) [text-row props]
    :one-slot [one-slot-row props]
    :extra [other-pages-row props]
    :address [address-row props]
    (:info :distance) [info-row props]
    :languages [language-row props]
    nil))

(defn- review-row
  "A row of ②: its field, who changed it and its versions. A decided row
   collapses to one line, which [Muuta] opens again."
  [{:keys [tr row choices read-only? expanded on-expand] :as props}]
  (let [required? (ptv-review/review-choice-required? row)
        decided? (ptv-review/review-decided? choices row)
        open? (and (not read-only?) required? (not decided?))]
    (if (and required? decided? (not read-only?) (not (contains? expanded (:key row))))
      [:> Stack {:direction "row" :spacing 1 :alignItems "center" :sx #js {:py 0.5}}
       [:> Icon {:fontSize "small" :color "success"} "check"]
       [:> Typography {:variant "body2" :sx (clj->js {:fontWeight 400 :flex 1})}
        (decided-text tr row choices)]
       [:> Button {:size "small" :variant "text"
                   :sx #js {:textTransform "none"}
                   :aria-label (str (tr :ptv.review/change) ": " (review-data/field-label tr row))
                   :on-click #(on-expand (:key row))}
        (tr :ptv.review/change)]]
      [:> Paper {:variant "outlined"
                 :sx #js {:p 1.5
                          :bgcolor (when (= :both (:origin row)) "rgba(237, 108, 2, 0.06)")}}
       [:> Stack {:spacing 1}
        [:> Stack {:direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true}
         [:> Typography {:variant "subtitle1" :sx #js {:fontWeight 600}}
          (review-data/field-label tr row)]
         [origin-chip tr row]
         (when open?
           [:> Typography {:variant "caption" :color "warning.dark"}
            (tr :ptv.review/choose-before-sync)])]
        [row-body props]]])))

(defn- group-title
  [tr group]
  [:> Typography {:variant "overline" :color "text.secondary" :sx #js {:lineHeight 1.5}}
   (tr (keyword "ptv.review" (str "group-" (name group))))])

(defn- short-row-text?
  [row]
  (contains? #{:short :one-slot :address} (:kind row)))

(defn- lipas-change-line
  "A line of ③: what the sync updates in PTV from Lipas."
  [tr row]
  (let [{:keys [field kind lipas ptv]} row]
    (cond
      (= :services field)
      (tr (if (:lipas? row) :ptv.review/connection-to-ptv :ptv.review/connection-removed-from-ptv)
          (:name row))

      (short-row-text? row)
      (tr :ptv.review/lipas-change-value (review-data/field-label tr row)
          (or (review-data/value-text field ptv) (tr :ptv.review/empty))
          (or (review-data/value-text field lipas) (tr :ptv.review/empty)))

      (= :text kind)
      (review-data/field-label tr row)

      :else (review-data/field-label tr row))))

(defn- language-added-lines
  "One line per language Lipas adds: its texts need no choice."
  [tr rows]
  (for [[language rows] (group-by :language rows)]
    ^{:key language}
    [:li
     (tr :ptv.review/language-added
         (review-data/language-adjective tr language false)
         (str/join ", " (map (fn [{:keys [field]}]
                               (str/lower-case
                                 (tr (case field
                                       :name :ptv.review/field-name
                                       :marketing-name :ptv.review/field-marketing-name
                                       :summary :ptv.review/field-summary
                                       :description :ptv.review/field-description))))
                             rows)))]))

(defn- lipas-changes
  "③ What the sync updates in PTV from Lipas: Lipas's own changes, and on
   a first link the connections the location keeps. Collapsed while
   decisions are open."
  [{:keys [tr rows open-count]}]
  (r/with-let [open? (r/atom nil)]
    (let [{added true others false} (group-by #(boolean (:language-added? %)) rows)
          expanded? (if (nil? @open?) (zero? open-count) @open?)]
      [:> Stack {:spacing 0.5}
       [:> Button {:size "small" :variant "text" :color "inherit"
                   :aria-expanded (boolean expanded?)
                   :sx #js {:textTransform "none" :alignSelf "flex-start" :ml -0.5}
                   :startIcon (r/as-element [:> Icon (if expanded? "expand_less" "chevron_right")])
                   :on-click #(reset! open? (not expanded?))}
        (tr :ptv.review/lipas-changes (count rows))]
       [:> Collapse {:in expanded? :unmountOnExit true}
        [:ul {:style {:margin 0 :paddingLeft "1.5em"}}
         (language-added-lines tr added)
         (for [row others]
           ^{:key (str (:key row))}
           [:li [:> Typography {:variant "body2" :component "span" :sx body}
                 (if (and (= :services (:field row)) (= :first-link (:origin row)))
                   (tr (if (:ptv? row) :ptv.review/connection-kept :ptv.review/connection-to-ptv) (:name row))
                   (lipas-change-line tr row))]])]]])))

(defn- up-to-date-line
  [tr matching]
  (when (seq matching)
    [:> Typography {:variant "body2" :color "text.secondary" :sx body}
     (tr :ptv.review/up-to-date
         (str/join ", " (for [{:keys [field] :as m} matching]
                          (if (= :services field)
                            (tr :ptv.review/services-match)
                            (str/lower-case (review-data/field-label tr m))))))]))

(defn- kept-in-ptv-line
  "What PTV keeps that Lipas has no field for (D8), a postal address the
   sync can't send back, and on a service, PTV's changes the edit keeps."
  [tr {:keys [service-hours? postal-addresses ptv-changes]}]
  (let [kept (cond-> []
               service-hours? (conj (tr :ptv.review/kept-service-hours))
               (seq (:kept postal-addresses)) (conj (tr :ptv.review/kept-postal-address)))]
    [:<>
     (when (seq kept)
       [:> Typography {:variant "body2" :color "text.secondary" :sx body}
        (tr :ptv.review/kept-in-ptv (str/join ", " kept))])
     (when (seq (:dropped postal-addresses))
       [:> Typography {:variant "body2" :color "warning.dark" :sx body}
        (tr :ptv.review/postal-address-dropped)])
     (when (seq ptv-changes)
       [:> Typography {:variant "body2" :color "text.secondary" :sx body}
        (tr :ptv.review/ptv-changes-kept
            (str/join ", " (map #(str/lower-case (review-data/field-label tr %)) ptv-changes)))])]))

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

(defn- labels
  [tr rows]
  (str/join ", " (map #(review-data/field-label tr %) rows)))

(defn- outcome-lines
  "Where each decision ends up with what the edit holds now."
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

(defn- removals-text
  "\"ruotsinkielisen kieliversion\" for the language versions a sync
   removes; nil when none."
  [tr languages]
  (when (seq languages)
    (let [names (map #(review-data/language-adjective tr (:language %) false) languages)]
      (tr (if (next names) :ptv.review/removes-versions :ptv.review/removes-version)
          (str/join (str " " (tr :ptv.review/and) " ") names)))))

(defn- ptv-date
  "PTV's :modified (UTC, no zone) as the user's local date and time."
  [s]
  (when-not (str/blank? s)
    (utils/->human-date-time-at-user-tz (cond-> s (not (re-find #"Z$" s)) (str "Z")))))

(defn- bar
  "One sentence that says what to do, one count, and the dates that tell
   who changed what and when (D7)."
  [{:keys [tr review rows required open languages open? on-toggle details-id]}]
  (let [{:keys [base? first-link? evidence? last-sync ptv-modified]} review
        changes (count (remove #(= :ptv (:current %)) rows))
        removes (removals-text tr languages)
        [severity text] (cond
                          (seq open)
                          ["warning" (tr (cond
                                           first-link? :ptv.review/bar-first-link
                                           (not base?) :ptv.review/bar-no-base
                                           :else :ptv.review/bar-open)
                                         (count required))]

                          (seq required)
                          ["success" (if (pos? changes)
                                       (tr :ptv.review/bar-decided changes)
                                       (tr :ptv.review/bar-decided-none))]

                          :else
                          ["info" (tr :ptv.review/bar-lipas-only changes)])
        dates (remove nil? [(when last-sync
                              (tr :ptv.review/synced-at (utils/->human-date-time-at-user-tz last-sync)))
                            (when (and evidence? base? ptv-modified)
                              (tr :ptv.review/modified-in-ptv-at (ptv-date ptv-modified)))])]
    [:> Alert {:severity severity :variant "outlined"}
     [:> AlertTitle {:sx #js {:mb 0.5}}
      (if removes (str text " " removes) text)]
     (when (seq dates)
       [:> Typography {:variant "body2" :color "text.secondary" :sx body}
        (str/join " · " dates)])
     ;; the toggle under the sentence, where the eye lands first
     [toggle-button tr open? on-toggle details-id]]))

(defn- notice
  "A ① notice: the state of the link, with its actions."
  [{:keys [severity title text actions]}]
  [:> Alert {:severity (or severity "warning") :variant "outlined"}
   (when title [:> AlertTitle title])
   (when text [:> Typography {:variant "body2" :sx body} text])
   (when (seq actions)
     (into [:> Stack {:direction "row" :spacing 1 :flexWrap "wrap" :useFlexGap true :sx #js {:mt 1}}]
           (for [{:keys [label on-click href primary? disabled?]} actions]
             [:> Button (cond-> {:size "small"
                                 :variant (if primary? "contained" "outlined")
                                 :color "warning"
                                 :disabled disabled?
                                 :sx #js {:textTransform "none"}}
                          on-click (assoc :on-click on-click)
                          href (assoc :href href :target "_blank"))
              label])))])

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
   - :on-take-value (fn [row value]) makes the edit hold one of PTV's
                   values of a one-slot row, or for other web pages
                   {:slot field :url url}
   - :on-reset     (fn [rows]) back to the saved versions, nothing decided
   - :rights       {:site-editor? :ptv-manager?}: who may take what
                   (review-data/takeable?)
   - :org-langs    the org's PTV languages (LIPAS codes): a language
                   version PTV has can be kept only in one of them
   - :notices      ① the state of the link: [{:key :severity :title :text
                   :actions [{:label :on-click :href :primary?}]}]
   - :site-tab     where the site's own data is edited in the site form,
                   for captions (\"Perustiedot\"); nil outside it
   - :first-open-id DOM id for the first undecided row's first button
   - :footer       what the choices go into: a note (string) or an element
   - :stacked?     Lipas's and PTV's cards one above the other, for a
                   narrow column
   - :read-only?   only what differs, without choices (the site's view
                   mode: the choices go into an edit); :footer says how
                   to make them
   Shows nothing while there is nothing to review and no notice."
  [{:keys [id tr review choices open? on-toggle on-take on-take-value on-reset
           rights org-langs notices site-tab first-open-id footer stacked? read-only?]}]
  (r/with-let [expanded (r/atom #{})]
    (let [{:keys [rows matching services]} review]
      (when (or (seq rows) (seq notices))
        (let [can-take? #(review-data/takeable? rights %)
              required (filter ptv-review/review-choice-required? rows)
              open (ptv-review/open-review-rows rows choices)
              languages (review-data/language-removals rows)
              in-decisions (filter #(or (ptv-review/review-choice-required? %)
                                        (contains? ptv-review/info-kinds (:kind %)))
                                   rows)
              from-lipas (remove (set in-decisions) rows)
              first-open (first open)
              decided-count (- (count required) (count open))
              takeable (filter #(and (can-take? %) (ptv-review/takeable-in-bulk? %)) required)
              ;; a choice made against a PTV value that has since changed no
              ;; longer counts, so there's nothing left to undo
              touched? (some #(or (ptv-review/review-choice choices %)
                                  (not= :lipas (:current %)))
                             (remove #(contains? ptv-review/info-kinds (:kind %)) rows))
              details-id (some-> id (str "-details"))
              props (fn [row]
                      {:tr tr :row row :choices choices :on-take on-take
                       :on-take-value on-take-value :can-take? can-take?
                       :org-langs org-langs :stacked? stacked? :read-only? read-only?
                       :site-tab site-tab
                       :first-open-id (when (= row first-open) first-open-id)
                       :expanded @expanded
                       :on-expand #(swap! expanded conj %)})]
          [:> Stack {:id id :spacing 1}
           (when (seq rows)
             [bar {:tr tr :review review :rows rows :required required :open open
                   :languages languages :open? open? :on-toggle on-toggle
                   :details-id details-id}])

           ;; ① always in sight: it may block the sync
           (for [n notices]
             ^{:key (:key n)} [notice n])

           (when (seq rows)
             [:> Collapse {:in open? :unmountOnExit true :id details-id}
              [:> Stack {:spacing 1.5 :sx #js {:pt 0.5}}
               ;; how it works, for whoever meets this for the first time
               [:> Button {:size "small" :variant "text" :color "inherit"
                           :sx #js {:textTransform "none" :alignSelf "flex-start" :ml -0.5}
                           :startIcon (r/as-element [:> Icon "help_outline"])
                           :on-click #(==> [:lipas.ui.help.events/navigate-to
                                            "ptv-integraatio" "ptv-muutosten-lapikaynti"])}
                (tr :ptv.review/help-link)]

               ;; ②
               (when (seq in-decisions)
                 [:> Stack {:spacing 1}
                  [:> Stack {:direction "row" :alignItems "baseline" :spacing 1}
                   [:> Typography {:variant "subtitle2" :sx #js {:flex 1}}
                    (tr (if read-only? :ptv.review/decisions-read-only :ptv.review/decisions))]
                   (when (and (seq required) (not read-only?))
                     [:> Typography {:variant "body2" :color "text.secondary" :sx body}
                      (tr :ptv.review/decided-count decided-count (count required))])]
                  (for [group ptv-review/review-groups
                        :let [group-rows (filter #(= group (:group %)) in-decisions)]
                        :when (seq group-rows)]
                    ^{:key group}
                    [:> Stack {:spacing 1}
                     [group-title tr group]
                     (if (= :services group)
                       [connections-block (assoc (props (first group-rows))
                                                 :rows group-rows :services services
                                                 :first-open-id (when (some #{first-open} group-rows)
                                                                  first-open-id))]
                       (for [row group-rows]
                         ^{:key (str (:key row))}
                         [review-row (props row)]))])])

               ;; ③
               (when (seq from-lipas)
                 [lipas-changes {:tr tr :rows from-lipas :open-count (count open)}])

               ;; ④
               [up-to-date-line tr matching]
               [kept-in-ptv-line tr review]

               ;; bulk, after the rows: decided once they've been read, and
               ;; only where it saves clicks
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
                                :on-click #(do (reset! expanded #{})
                                               (on-reset rows))}
                     (tr :ptv.review/clear-choices)])])

               ;; ⑤
               [:> Paper {:variant "outlined" :sx #js {:p 1.5 :bgcolor "action.hover"}}
                [:> Stack {:spacing 1}
                 (when-not read-only?
                   [outcome-lines tr (remove #(contains? ptv-review/info-kinds (:kind %)) rows) choices])
                 (when (and (not read-only?) (seq languages))
                   [:> Typography {:variant "body2" :sx body}
                    (tr :ptv.review/outcome-removes
                        (str/join ", " (map #(str/lower-case (review-data/language-name tr (:language %)))
                                            languages)))])
                 (when-not read-only? [:> Divider])
                 (if (string? footer)
                   [:> Typography {:variant "body2" :color "text.secondary" :sx body} footer]
                   footer)]]]])])))))
