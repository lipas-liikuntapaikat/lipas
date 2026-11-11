(ns lipas.ui.ptv.review-data
  "PTV sync review state shared by the PTV tool (:ptv module) and the site
   form (:map module): where it lives in app-db, the site form's review, how
   rows are named and shown, and the confirmation before a write that would
   overwrite changes made in PTV. Free of UI and of the :ptv module so
   either can load it."
  (:require [clojure.string :as str]
            [lipas.data.cities :as cities]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.roles :as roles]))

(defn choices-path
  "The choices made in a site's review: row :key -> {:choice :lipas :seen}
   (see ptv-review/review-choice)."
  [lipas-id]
  [:ptv :review :choices lipas-id])

(defn undo-path
  "What the choices made in a review (a site's, or a service's by its edit
   key) wrote into the edit, for \"Kumoa valinnat\" to take back: path ->
   {:before :written} (ptv-review/record-review-writes). Spent with the
   choices."
  [id]
  [:ptv :review :undo id])

(defn open-path
  "Whether a site's review panel is expanded."
  [lipas-id]
  [:ptv :review :open lipas-id])

(defn panel-dom-id
  "The DOM id of a site's review panel, for scrolling it into view."
  [lipas-id]
  (str "ptv-review-" lipas-id))

(defn first-open-dom-id
  "The DOM id of the first choice button of a site's first undecided row,
   where \"Käy muutokset läpi\" moves the focus."
  [lipas-id]
  (str (panel-dom-id lipas-id) "-first-open"))

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
          (ptv-review/annotate-review draft (ptv-data/effective-languages draft org-langs))))))

(defn takeable?
  "Whether the user may take PTV's version of `row` into Lipas: `rights`
   {:site-editor? :ptv-manager?} against the row's :takeable-by."
  [rights row]
  (case (:takeable-by row)
    :site-editor (boolean (:site-editor? rights))
    :ptv-manager (boolean (:ptv-manager? rights))
    false))

(defn language-name
  "\"Ruotsi\" for \"sv\" (a PTV language code); the code itself for a
   language Lipas has no name for."
  [tr lang]
  (case lang
    "fi" (tr :ptv.review/lang-fi)
    "sv" (tr :ptv.review/lang-sv)
    "en" (tr :ptv.review/lang-en)
    (str/upper-case (str lang))))

(defn language-adjective
  "\"ruotsinkielisen\" for \"sv\" (genitive, as in \"… kieliversion\"), or
   with `nominative?` \"Ruotsinkielinen\"; \"SE-kielisen\" for a language
   Lipas has no name for."
  [tr lang nominative?]
  (let [k #(keyword "ptv.review" (str (if nominative? "lang-nom-" "lang-adj-") %))]
    (case lang
      ("fi" "sv" "en") (tr (k lang))
      (tr (k "other") (str/upper-case (str lang))))))

(defn field-label
  "\"Tiivistelmä (SV)\", \"Puhelinnumero\", \"Liitetyt palvelut\",
   \"Ruotsi (SV)\"."
  [tr {:keys [field language]}]
  (case field
    :services (tr :ptv.review/field-services)
    :marketing-name (tr :ptv.review/field-marketing-name)
    :phone (tr :ptv.review/field-phone)
    :email (tr :ptv.review/field-email)
    :www (tr :ptv.review/field-www)
    :reservations-link (tr :ptv.review/field-reservations-link)
    :other-web-pages (tr :ptv.review/field-other-web-pages)
    :address (tr :ptv.review/field-address)
    :municipality (tr :ptv.review/field-municipality)
    :point (tr :ptv.review/field-point)
    :languages (str (language-name tr language) " (" (str/upper-case language) ")")
    (str (tr (case field
               :name :ptv.review/field-name
               :summary :ptv.review/field-summary
               :description :ptv.review/field-description
               :user-instruction :ptv.review/field-user-instruction))
         " (" (str/upper-case language) ")")))

(defn- municipality-text
  [code]
  (let [city (some-> code parse-long cities/by-city-code)]
    (str/trim (str code " " (or (-> city :name :fi) "")))))

(defn value-text
  "One side's value `v` of a `field` row as shown, nil for none. A Lipas
   address shows the site's own postal office (`postal-office`), which
   the sync doesn't send."
  ([field v] (value-text field v nil))
  ([field v postal-office]
   (case field
     :phone (not-empty (str/join ", " (map ptv-review/format-phone (sort-by str v))))
     (:email :www :reservations-link :other-web-pages) (not-empty (str/join ", " (sort v)))
     :address (when v
                (not-empty
                  (str/join ", " (remove str/blank?
                                         [(:street v)
                                          (str/trim (str (:postal-code v) " "
                                                         (or (:post-office v) postal-office)))]))))
     :municipality (some-> v municipality-text)
     v)))

(defn short-row?
  "Whether `row`'s values are short enough to show both in a list."
  [row]
  (contains? #{:short :one-slot :extra :address :info} (:kind row)))

(defn service-names
  "The names of the services `ids` (by the connections `row`'s :names), in
   alphabetical order."
  [row ids]
  (sort-by str/lower-case (map #(get-in row [:names %] %) ids)))

(defn ptv-day
  "PTV's time `s` (UTC, no zone, microseconds) as the user's local date:
   \"16.5.2026\". nil when it can't be read."
  [s]
  (when-not (str/blank? s)
    (let [d (js/Date. (cond-> (str/replace s #"(\.\d{3})\d+" "$1")
                        (not (re-find #"(Z|[+-]\d{2}:?\d{2})$" s)) (str "Z")))]
      (when-not (js/isNaN (.getTime d))
        (str (.getDate d) "." (inc (.getMonth d)) "." (.getFullYear d))))))

(defn gone-service-label
  "How a service PTV no longer has (ptv-review/gone-service) is named where
   the service would be: \"Uimahallit (arkistoitu PTV:ssä)\", or
   \"Tuntematon palvelu (ei löydy PTV:stä)\" for one nobody can name."
  [tr {:keys [name gone]}]
  (cond
    (= :archived gone) (tr :ptv.review/gone-archived-label (or name (tr :ptv.review/gone-unnamed)))
    name (tr :ptv.review/gone-unknown-label name)
    :else (tr :ptv.review/gone-unknown-unnamed)))

(defn gone-notice-line
  "The review's line about a service PTV no longer has: what happened in
   PTV and what the next sync does."
  [tr {:keys [name gone archived-at] :as service}]
  (let [day (ptv-day archived-at)
        n (or name (tr :ptv.review/gone-unnamed))]
    (str (cond
           (and (= :archived gone) day) (tr :ptv.review/gone-archived-on n day)
           (= :archived gone) (tr :ptv.review/gone-archived n)
           :else (str (gone-service-label tr service) "."))
         " "
         (tr :ptv.review/gone-dropped-on-sync))))

(defn gone-labels
  "id -> label (gone-service-label) of each service `services`
   (services-by-id) marks as gone among `ids`, for naming them in place of
   their id."
  [tr services ids]
  (into {} (for [id ids
                 :when (ptv-review/gone-service? services id)]
             [id (gone-service-label tr (ptv-review/gone-service services id))])))

(defn unlisted-labels
  "id -> label of each of `ids` that `known` (services-by-id entries: the
   PTV tool's unlisted services, or the site form's review fetch) has: one
   PTV no longer has as gone-service-label, another organisation's by its
   name. For a services selector's chips (:value-labels)."
  [tr known ids]
  (into {} (keep (fn [id]
                   (if (ptv-review/gone-service? known id)
                     [id (gone-service-label tr (ptv-review/gone-service known id))]
                     (when-let [n (ptv-data/select-service-name (get-in known [id :serviceNames]))]
                       [id n]))))
        ids))

(defn unlisted-link-items
  "Link-field items ({:id :name :url :caption}) for `ids`, services the
   org's own list lacks, by `known` (unlisted-labels): one PTV no longer has
   without a link (PTV has none for it), with its id as the caption when
   nobody can name it. `url-fn` gives a service's link. Ids `known` lacks
   are left out."
  [tr known ids url-fn]
  (let [labels (unlisted-labels tr known ids)]
    (for [id ids
          :let [label (get labels id)]
          :when label]
      (if (ptv-review/gone-service? known id)
        (cond-> {:id id :name label}
          (nil? (:name (ptv-review/gone-service known id))) (assoc :caption id))
        {:id id :name label :url (url-fn id)}))))

(defn dropped-line
  "What a sync did about a service PTV no longer has: dropped its
   connection from Lipas."
  [tr {:keys [id name gone]}]
  (if (= :archived gone)
    (tr :ptv.review/dropped-archived (or name id))
    (tr :ptv.review/dropped-unknown (or name id))))

(defn dropped-notification
  "The notification after a sync that dropped `dropped`, connections to
   services PTV no longer has, from Lipas: the save's success, and what was
   dropped. nil when nothing was."
  [tr dropped]
  (when (seq dropped)
    {:message (str/join " " (cons (str (tr :notifications/save-success) ".")
                                  (map #(dropped-line tr %) dropped)))
     :success? true}))

(defn site-form-dropped
  "The services PTV no longer has that a site-form save dropped: those of
   `gone` (the review's :gone-services when the save was made) that the
   saved site `result` no longer connects, when the save synced: a sync
   dates the revision it writes with its :last-sync. The save response
   carries no such list; the review knew."
  [gone result]
  (when (and (seq gone)
             (some? (:event-date result))
             (= (:event-date result) (-> result :ptv :last-sync))
             (nil? (-> result :ptv :error)))
    (let [kept (set (-> result :ptv :service-ids))]
      (vec (remove #(contains? kept (:id %)) gone)))))

(defn services-change
  "What turning the connections `from` (a set of service ids) into `to`
   does, by `row`'s names: \"lisätään Jäähallit; poistetaan Uimahallit\"."
  [tr row from to]
  (let [names #(str/join ", " (service-names row %))]
    (str/join "; " (remove nil?
                           [(when-let [ids (seq (remove (set from) to))]
                              (tr :ptv.review/services-add (names ids)))
                            (when-let [ids (seq (remove (set to) from))]
                              (tr :ptv.review/services-remove (names ids)))]))))

(defn overwritten-line
  "What a write that sends the edit's side of `row` (Lipas's, or the user's
   own value) does to PTV, for the confirmation listing the PTV values it
   would overwrite: PTV's value is replaced by Lipas's, in so many words."
  [tr {:keys [field kind ptv current draft] :as row}]
  (let [edited? (= :edited current)
        sent (if edited? draft (:lipas row))]
    (cond
      (= :set kind)
      (tr (if edited? :ptv.review/overwritten-services-edited :ptv.review/overwritten-services)
          (services-change tr row ptv sent))

      (= :languages kind)
      (tr :ptv.review/overwritten-language (language-adjective tr (:language row) true))

      (short-row? row)
      (let [label (field-label tr row)
            from (value-text field ptv)
            to (value-text field sent (when-not edited? (get-in row [:saved [:location :postal-office]])))]
        (cond
          (nil? to) (tr :ptv.review/overwritten-value-removed label from)
          (nil? from) (tr :ptv.review/overwritten-value-added label to)
          :else (tr (if edited? :ptv.review/overwritten-value-edited :ptv.review/overwritten-value)
                    label from to)))

      :else (tr :ptv.review/overwritten-text (field-label tr row)))))

(defn overwrite-confirmation
  "Options for :lipas.ui.events/confirm-with before a write overwrites what
   PTV has in the rows `open` (rows without a choice), and the language
   versions in `languages` (rows) it removes. Reviewing them is the main
   action; `on-overwrite` writes anyway, its button naming the write:
   `action` :sync (the default) or :save. On a first link, or without what
   the last sync sent (origin :first-link, :unknown), the rows are
   differences, not changes made in PTV, and the dialog says so."
  [tr {:keys [open languages on-overwrite on-review action]}]
  (let [differences? (boolean (some #(#{:first-link :unknown} (:origin %)) open))
        review-label (tr (if differences? :ptv.review/review-differences :ptv.review/review))]
    {:title (tr (if differences? :ptv.review/gate-title-differences :ptv.review/gate-title))
     :message [:div
               [:p {:style {:margin-top 0}}
                (tr (if differences? :ptv.review/gate-intro-differences :ptv.review/gate-intro))]
               (into [:ul]
                     (for [row (concat open languages)]
                       [:li (overwritten-line tr row)]))
               [:p {:style {:margin-bottom 0}}
                (tr (if differences? :ptv.review/confirm-hint-differences :ptv.review/confirm-hint)
                    review-label)]]
     :confirm-label review-label
     :confirm-primary? true
     :on-confirm on-review
     :decline-label (tr (if (= :save action) :ptv.review/save-and-overwrite :ptv.review/sync-and-overwrite))
     :on-decline on-overwrite}))

(defn language-removals
  "The rows of `rows` (annotated) that are a language version the sync
   removes: not one whose texts were taken from PTV."
  [rows]
  (filterv #(and (= :languages (:kind %)) (not= :ptv (:current %))) rows))

(defn- name-words
  [s]
  (into #{} (filter #(>= (count %) 3))
        (str/split (str/lower-case (str s)) #"[\s,.;:()/\-–]+")))

(defn names-differ-wholesale?
  "Whether a site's name and its PTV location's name share no word (of
   three letters or more): the site may be linked to the wrong location."
  [site-name ptv-name]
  (let [a (name-words site-name)
        b (name-words ptv-name)]
    (boolean (and (seq a) (seq b) (empty? (filter a b))))))

(defn app-user
  "The user as privileges are checked (:lipas.ui.user.subs/user-data), for
   an event handler: with the developer's privilege overrides."
  [db]
  (some-> (get-in db [:user :login])
          (assoc :dev/overrides (:lipas.ui.project-devtools/privilege-override db))))

(defn ptv-manager-for?
  "Whether `user` manages PTV for `site`'s municipality: only their save
   writes to PTV (D2, lipas.backend.core/ptv-sync-by?)."
  [user site]
  (boolean (roles/check-privilege user {:city-code (get-in site [:location :city :city-code])}
                                  :ptv/manage)))

(defn save-syncs?
  "Whether `user`'s save of the edit `site` writes to PTV (the backend's
   lipas.backend.core/ptv-sync-by?)."
  [user site]
  (and (ptv-data/save-syncs? site) (ptv-manager-for? user site)))

(defn reviewed
  "What a review against `channel` (PTV's location, GET shape) was made
   against, for the backend to refuse a write over a change made in PTV
   since: {:modified :service-ids}. nil without a channel or its
   :modified."
  [channel]
  (when (:modified channel)
    {:modified (:modified channel)
     :service-ids (vec (ptv-data/channel-service-ids channel))}))
