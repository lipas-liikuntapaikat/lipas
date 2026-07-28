(ns lipas.ui.help.manage
  "Help content editor (v2): each locale (fi/se/en) is an independent
   tree edited, drafted and published separately. The toolbar's locale
   tabs switch which tree is being edited; Save draft / Publish /
   History all target the active locale only."
  (:require ["@mui/icons-material/Add$default" :as AddIcon]
            ["@mui/icons-material/ArrowDownward$default" :as ArrowDownIcon]
            ["@mui/icons-material/ArrowUpward$default" :as ArrowUpIcon]
            ["@mui/icons-material/AutoFixHigh$default" :as AutoFixIcon]
            ["@mui/icons-material/CategoryOutlined$default" :as CategoryIcon]
            ["@mui/icons-material/Delete$default" :as DeleteIcon]
            ["@mui/icons-material/Download$default" :as DownloadIcon]
            ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
            ["@mui/icons-material/History$default" :as HistoryIcon]
            ["@mui/icons-material/Image$default" :as ImageIcon]
            ["@mui/icons-material/PictureAsPdf$default" :as PdfIcon]
            ["@mui/icons-material/Preview$default" :as PreviewIcon]
            ["@mui/icons-material/Save$default" :as SaveIcon]
            ["@mui/icons-material/TextFields$default" :as TextIcon]
            ["@mui/icons-material/VideoLibrary$default" :as VideoIcon]
            ;; clj-kondo false positive: the bare symbol `Box` resolves
            ;; against a builtin during analysis, so the alias is reported
            ;; unused even though `[:> Box ...]` is used below. See
            ;; lipas.ui.assistant.views for the same pattern.
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogContentText$default" :as DialogContentText]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/InputAdornment$default" :as InputAdornment]
            ["@mui/material/InputLabel$default" :as InputLabel]
            ["@mui/material/List$default" :as MuiList]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [ajax.core :as ajax]
            [clojure.string :as str]
            [lipas.ui.help.events :as events]
            [lipas.ui.help.subs :as subs]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

;;; ——— Helpers ————————————————————————————————————————————————————————

(defn- editor-locale [db]
  (get-in db [:help :editor :locale] :fi))

(defn- epath
  "Path into the active locale's edited tree."
  [db & ks]
  (into [:help :edited-data (editor-locale db)] ks))

(defn- unique-slug
  "Slug from title, made unique among taken (a set of strings)."
  [title fallback taken]
  (let [base (let [s (cutils/->slug title)]
               (if (str/blank? s) fallback s))]
    (if-not (contains? taken base)
      base
      (loop [n 2]
        (let [s (str base "-" n)]
          (if (contains? taken s) (recur (inc n)) s))))))

(defn- vec-remove [v idx]
  (vec (concat (subvec v 0 idx) (subvec v (inc idx)))))

(defn- vec-swap [v i j]
  (-> v (assoc i (v j)) (assoc j (v i))))

;;; ——— Editor state events ————————————————————————————————————————————

(rf/reg-event-db ::initialize-editor
  (fn [db _]
    (-> db
        (assoc-in [:help :edited-data] (get-in db [:help :data]))
        (assoc-in [:help :editor :section-idx]
                  (when (seq (get-in db [:help :data (editor-locale db)])) 0))
        (assoc-in [:help :editor :page-idx] nil))))

(rf/reg-event-db ::set-editor-locale
  (fn [db [_ locale]]
    (-> db
        (assoc-in [:help :editor :locale] locale)
        (assoc-in [:help :editor :section-idx]
                  (when (seq (get-in db [:help :edited-data locale])) 0))
        (assoc-in [:help :editor :page-idx] nil))))

(rf/reg-event-db ::select-section
  (fn [db [_ idx]]
    (-> db
        (assoc-in [:help :editor :section-idx] idx)
        (assoc-in [:help :editor :page-idx] nil))))

(rf/reg-event-db ::select-page
  (fn [db [_ idx]]
    (assoc-in db [:help :editor :page-idx] idx)))

;;; ——— Section/page field edits ———————————————————————————————————————

(rf/reg-event-db ::update-section-field
  (fn [db [_ section-idx field value]]
    (assoc-in db (epath db section-idx field) value)))

(rf/reg-event-db ::update-page-field
  (fn [db [_ section-idx page-idx field value]]
    (assoc-in db (epath db section-idx :pages page-idx field) value)))

(rf/reg-event-db ::generate-section-slug
  ;; Regenerating a published slug is safe-ish: the old slug should be
  ;; added to :aliases. We do that automatically here.
  (fn [db [_ section-idx]]
    (let [sections (get-in db (epath db))
          {:keys [title slug aliases]} (get sections section-idx)
          taken (into #{} (map :slug) (vec-remove sections section-idx))
          new-slug (unique-slug title slug taken)]
      (if (= new-slug slug)
        db
        (-> db
            (assoc-in (epath db section-idx :slug) new-slug)
            (assoc-in (epath db section-idx :aliases)
                      (vec (distinct (concat aliases [slug])))))))))

(rf/reg-event-db ::generate-page-slug
  (fn [db [_ section-idx page-idx]]
    (let [pages (get-in db (epath db section-idx :pages))
          {:keys [title slug aliases]} (get pages page-idx)
          taken (into #{} (map :slug) (vec-remove pages page-idx))
          new-slug (unique-slug title slug taken)]
      (if (= new-slug slug)
        db
        (-> db
            (assoc-in (epath db section-idx :pages page-idx :slug) new-slug)
            (assoc-in (epath db section-idx :pages page-idx :aliases)
                      (vec (distinct (concat aliases [slug])))))))))

;;; ——— Block events ———————————————————————————————————————————————————

(rf/reg-event-db ::update-block-field
  (fn [db [_ section-idx page-idx block-idx field value]]
    (assoc-in db (epath db section-idx :pages page-idx :blocks block-idx field)
              value)))

(rf/reg-event-db ::add-block
  (fn [db [_ section-idx page-idx block-type]]
    (let [base-block {:block-id (str (random-uuid)) :type block-type}
          new-block (case block-type
                      :text (assoc base-block :content "")
                      :video (assoc base-block
                                    :provider :youtube
                                    :video-id ""
                                    :title "")
                      :image (assoc base-block :url "" :alt "" :caption "")
                      :pdf (assoc base-block :url "" :title "" :caption "")
                      :type-code-explorer base-block
                      :data-model-excel-download base-block)]
      (update-in db (epath db section-idx :pages page-idx :blocks)
                 (fnil conj []) new-block))))

(rf/reg-event-db ::delete-block
  (fn [db [_ section-idx page-idx block-idx]]
    (update-in db (epath db section-idx :pages page-idx :blocks)
               vec-remove block-idx)))

(rf/reg-event-db ::move-block-up
  (fn [db [_ section-idx page-idx block-idx]]
    (if (zero? block-idx)
      db
      (update-in db (epath db section-idx :pages page-idx :blocks)
                 vec-swap block-idx (dec block-idx)))))

(rf/reg-event-db ::move-block-down
  (fn [db [_ section-idx page-idx block-idx]]
    (let [blocks (get-in db (epath db section-idx :pages page-idx :blocks))]
      (if (= block-idx (dec (count blocks)))
        db
        (update-in db (epath db section-idx :pages page-idx :blocks)
                   vec-swap block-idx (inc block-idx))))))

;;; ——— Section/page add/delete/reorder ————————————————————————————————

(rf/reg-event-db ::add-section
  (fn [db _]
    (let [sections (or (get-in db (epath db)) [])
          slug (unique-slug "" "uusi-osio" (into #{} (map :slug) sections))
          new-section {:id (str (random-uuid))
                       :slug slug
                       :title ""
                       :pages []}]
      (-> db
          (assoc-in (epath db) (conj sections new-section))
          (assoc-in [:help :editor :section-idx] (count sections))
          (assoc-in [:help :editor :page-idx] nil)))))

(rf/reg-event-db ::delete-section
  (fn [db [_ section-idx]]
    (let [sections (vec-remove (get-in db (epath db)) section-idx)]
      (-> db
          (assoc-in (epath db) sections)
          (assoc-in [:help :editor :section-idx] (when (seq sections) 0))
          (assoc-in [:help :editor :page-idx] nil)))))

(rf/reg-event-db ::move-section-up
  (fn [db [_ section-idx]]
    (if (zero? section-idx)
      db
      (-> db
          (update-in (epath db) vec-swap section-idx (dec section-idx))
          (assoc-in [:help :editor :section-idx] (dec section-idx))))))

(rf/reg-event-db ::move-section-down
  (fn [db [_ section-idx]]
    (let [sections (get-in db (epath db))]
      (if (= section-idx (dec (count sections)))
        db
        (-> db
            (update-in (epath db) vec-swap section-idx (inc section-idx))
            (assoc-in [:help :editor :section-idx] (inc section-idx)))))))

(rf/reg-event-db ::add-page
  (fn [db [_ section-idx]]
    (let [pages (or (get-in db (epath db section-idx :pages)) [])
          slug (unique-slug "" "uusi-sivu" (into #{} (map :slug) pages))
          new-page {:id (str (random-uuid))
                    :slug slug
                    :title ""
                    :blocks []}]
      (-> db
          (assoc-in (epath db section-idx :pages) (conj pages new-page))
          (assoc-in [:help :editor :page-idx] (count pages))))))

(rf/reg-event-db ::delete-page
  (fn [db [_ section-idx page-idx]]
    (-> db
        (update-in (epath db section-idx :pages) vec-remove page-idx)
        (assoc-in [:help :editor :page-idx] nil))))

(rf/reg-event-db ::move-page-up
  (fn [db [_ section-idx page-idx]]
    (if (zero? page-idx)
      db
      (-> db
          (update-in (epath db section-idx :pages) vec-swap page-idx (dec page-idx))
          (assoc-in [:help :editor :page-idx] (dec page-idx))))))

(rf/reg-event-db ::move-page-down
  (fn [db [_ section-idx page-idx]]
    (let [pages (get-in db (epath db section-idx :pages))]
      (if (= page-idx (dec (count pages)))
        db
        (-> db
            (update-in (epath db section-idx :pages) vec-swap page-idx (inc page-idx))
            (assoc-in [:help :editor :page-idx] (inc page-idx)))))))

;;; ——— Preview / save / publish (per locale) ——————————————————————————

(rf/reg-event-db ::apply-changes
  ;; Preview: copy the active locale's edited tree into the read-mode
  ;; data without saving.
  (fn [db _]
    (let [locale (editor-locale db)]
      (-> db
          (assoc-in [:help :data locale] (get-in db [:help :edited-data locale]))
          (assoc-in [:help :dialog :mode] :read)))))

(rf/reg-event-fx ::save-changes
  (fn [{:keys [db]} _]
    (let [token (-> db :user :login :token)
          locale (editor-locale db)]
      {:db (assoc-in db [:help :save-in-progress] true)
       :fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/save-help-data")
              :params          {:locale locale
                                :data (get-in db [:help :edited-data locale])}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::save-success]
              :on-failure      [::save-failure]}]]})))

(rf/reg-event-fx ::save-success
  (fn [{:keys [db]} _]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-success)
                        :success? true}]
      {:db (assoc-in db [:help :save-in-progress] false)
       :fx [[:dispatch [::apply-changes]]
            [:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::save-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:help :save-in-progress] false)
               (assoc-in [:help :errors :save] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::save-draft
  (fn [{:keys [db]} _]
    (let [token (-> db :user :login :token)
          locale (editor-locale db)]
      {:db (assoc-in db [:help :save-in-progress] true)
       :fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/save-help-draft")
              :params          {:locale locale
                                :data (get-in db [:help :edited-data locale])}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::save-draft-success]
              :on-failure      [::save-failure]}]]})))

(rf/reg-event-fx ::save-draft-success
  (fn [{:keys [db]} _]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-success)
                        :success? true}]
      {:db (assoc-in db [:help :save-in-progress] false)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

;;; ——— Version history (per locale) ———————————————————————————————————

(rf/reg-event-fx ::open-version-history
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:help :versions :dialog-open?] true)
     :fx [[:dispatch [::get-versions]]]}))

(rf/reg-event-db ::close-version-history
  (fn [db _]
    (assoc-in db [:help :versions :dialog-open?] false)))

(rf/reg-event-fx ::get-versions
  (fn [{:keys [db]} _]
    (let [token (-> db :user :login :token)]
      {:fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/get-help-versions")
              :params          {:locale (editor-locale db)}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::get-versions-success]
              :on-failure      [::save-failure]}]]})))

(rf/reg-event-db ::get-versions-success
  (fn [db [_ versions]]
    (assoc-in db [:help :versions :items] versions)))

(rf/reg-event-fx ::load-version
  (fn [{:keys [db]} [_ id]]
    (let [token (-> db :user :login :token)]
      {:fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/get-help-version")
              :params          {:id (str id)}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::load-version-success]
              :on-failure      [::save-failure]}]]})))

(rf/reg-event-fx ::load-version-success
  ;; Loads the version into the editor only — publishing it
  ;; (= rollback) is an explicit separate step.
  (fn [{:keys [db]} [_ version]]
    {:db (-> db
             (assoc-in [:help :edited-data (editor-locale db)] (:body version))
             (assoc-in [:help :versions :dialog-open?] false))
     :fx [[:dispatch [:lipas.ui.events/set-active-notification
                      {:message  "Version loaded into editor. Publish to make it live."
                       :success? true}]]]}))

;;; ——— Subs ———————————————————————————————————————————————————————————

(rf/reg-sub ::versions
  (fn [db _]
    (get-in db [:help :versions])))

(rf/reg-sub ::editor-locale*
  (fn [db _]
    (editor-locale db)))

(rf/reg-sub ::edited-tree
  ;; The active locale's edited tree
  (fn [db _]
    (get-in db [:help :edited-data (editor-locale db)])))

(rf/reg-sub ::editor-section-idx
  (fn [db _]
    (get-in db [:help :editor :section-idx])))

(rf/reg-sub ::editor-page-idx
  (fn [db _]
    (get-in db [:help :editor :page-idx])))

;;; ——— Confirmation dialog ————————————————————————————————————————————

(rf/reg-event-db ::show-confirm-dialog
  (fn [db [_ dialog-type params]]
    (assoc-in db [:help :confirm-dialog] {:open? true
                                          :type dialog-type
                                          :params params})))

(rf/reg-event-db ::hide-confirm-dialog
  (fn [db _]
    (assoc-in db [:help :confirm-dialog :open?] false)))

(rf/reg-event-fx ::confirm-action
  (fn [{:keys [db]} _]
    (let [{:keys [type params]} (get-in db [:help :confirm-dialog])]
      {:db (assoc-in db [:help :confirm-dialog :open?] false)
       :fx [[:dispatch (case type
                         :delete-section [::delete-section (:section-idx params)]
                         :delete-page [::delete-page (:section-idx params) (:page-idx params)]
                         :delete-block [::delete-block (:section-idx params) (:page-idx params) (:block-idx params)])]]})))

(rf/reg-sub ::confirm-dialog
  :<- [::subs/help]
  (fn [help _]
    (get help :confirm-dialog {:open? false})))

;;; ——— UI: shared block chrome ————————————————————————————————————————

(r/defc block-card
  "Common card chrome for block editors: icon + label + preview text in
   the header, expand/reorder/delete actions, editable fields inside."
  [{:keys [icon label preview section-idx page-idx block-idx blocks-count children]}]
  (let [[expanded set-expanded!] (hooks/use-state false)]
    [:> Card {:variant "elevation"
              :elevation 3
              :sx #js{:mb 2
                      :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                      :transition "box-shadow 0.3s ease"}}
     [:> CardHeader
      {:title (r/as-element
                [:> Typography {:variant "subtitle1" :component "div"}
                 [:> Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
                  icon
                  label
                  [:> Typography {:variant "body2" :color "text.secondary" :component "span" :sx #js{:ml 2}}
                   preview]]])
       :action (r/as-element
                 [:> Box {:sx #js{:display "flex" :gap 0.5}}
                  [:> IconButton {:onClick #(set-expanded! (not expanded))
                                  :size "small"
                                  :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                          :transition "transform 0.3s"}}
                   [:> ExpandMoreIcon {:fontSize "small"}]]

                  [:> IconButton {:color "primary"
                                  :size "small"
                                  :disabled (zero? block-idx)
                                  :onClick #(rf/dispatch [::move-block-up section-idx page-idx block-idx])}
                   [:> ArrowUpIcon {:fontSize "small"}]]

                  [:> IconButton {:color "primary"
                                  :size "small"
                                  :disabled (= block-idx (dec blocks-count))
                                  :onClick #(rf/dispatch [::move-block-down section-idx page-idx block-idx])}
                   [:> ArrowDownIcon {:fontSize "small"}]]

                  [:> IconButton {:color "error"
                                  :size "small"
                                  :onClick #(rf/dispatch [::show-confirm-dialog :delete-block
                                                          {:section-idx section-idx
                                                           :page-idx page-idx
                                                           :block-idx block-idx}])}
                   [:> DeleteIcon {:fontSize "small"}]]])}]

     [:> Collapse {:in expanded :timeout "auto" :unmountOnExit true}
      [:> CardContent {}
       children]]]))

(defn- truncate [s n]
  (when-not (str/blank? s)
    (if (> (count s) n) (str (subs s 0 n) "...") s)))

;;; ——— UI: block editors ——————————————————————————————————————————————

(r/defc text-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  [block-card
   {:icon (r/as-element [:> TextIcon {:fontSize "small" :color "action" :sx #js{:mr 1}}])
    :label "Text"
    :preview (or (truncate (:content block) 50) "Empty text block")
    :section-idx section-idx :page-idx page-idx :block-idx block-idx :blocks-count blocks-count
    :children
    (r/as-element
      [:> TextField
       {:fullWidth true
        :label "Content (markdown)"
        :value (or (:content block) "")
        :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                 :content (.. % -target -value)])
        :variant "outlined"
        :margin "normal"
        :multiline true
        :rows 8}])}])

(r/defc video-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  [block-card
   {:icon (r/as-element [:> VideoIcon {:fontSize "small" :color "action" :sx #js{:mr 1}}])
    :label "Video"
    :preview (if (str/blank? (:video-id block))
               "No video set"
               (str (name (or (:provider block) :youtube)) ": " (:video-id block)
                    (when-not (str/blank? (:title block)) (str " - " (:title block)))))
    :section-idx section-idx :page-idx page-idx :block-idx block-idx :blocks-count blocks-count
    :children
    (r/as-element
      [:<>
       [:> FormControl {:fullWidth true :margin "normal"}
        [:> InputLabel {:id "video-provider-label"} "Provider"]
        [:> Select {:labelId "video-provider-label"
                    :value (name (or (:provider block) :youtube))
                    :label "Provider"
                    :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                             :provider (keyword (.. % -target -value))])}
         [:> MenuItem {:value "youtube"} "YouTube"]
         [:> MenuItem {:value "vimeo"} "Vimeo"]]]

       [:> TextField {:fullWidth true
                      :label "Video ID"
                      :value (or (:video-id block) "")
                      :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                               :video-id (.. % -target -value)])
                      :variant "outlined"
                      :margin "normal"
                      :helperText "For YouTube: the part after v= in URL"}]

       [:> TextField {:fullWidth true
                      :label "Title"
                      :value (or (:title block) "")
                      :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                               :title (.. % -target -value)])
                      :variant "outlined"
                      :margin "normal"}]])}])

(r/defc image-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  [block-card
   {:icon (r/as-element [:> ImageIcon {:fontSize "small" :color "action" :sx #js{:mr 1}}])
    :label "Image"
    :preview (cond
               (str/blank? (:url block)) "No image set"
               (not (str/blank? (:alt block))) (:alt block)
               :else (last (str/split (:url block) #"/")))
    :section-idx section-idx :page-idx page-idx :block-idx block-idx :blocks-count blocks-count
    :children
    (r/as-element
      [:<>
       [:> TextField {:fullWidth true
                      :label "Image URL"
                      :value (or (:url block) "")
                      :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                               :url (.. % -target -value)])
                      :variant "outlined"
                      :margin "normal"}]

       [:> TextField {:fullWidth true
                      :label "Alt text"
                      :value (or (:alt block) "")
                      :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                               :alt (.. % -target -value)])
                      :variant "outlined"
                      :margin "normal"
                      :helperText "Mandatory for accessibility"}]

       [:> TextField {:fullWidth true
                      :label "Caption"
                      :value (or (:caption block) "")
                      :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                               :caption (.. % -target -value)])
                      :variant "outlined"
                      :margin "normal"}]])}])

(r/defc pdf-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  (let [url (or (:url block) "")]
    [block-card
     {:icon (r/as-element [:> PdfIcon {:fontSize "small" :color "action" :sx #js{:mr 1}}])
      :label "PDF"
      :preview (cond
                 (str/blank? url) "No PDF set"
                 (not (str/blank? (:title block))) (:title block)
                 :else (last (str/split url #"/")))
      :section-idx section-idx :page-idx page-idx :block-idx block-idx :blocks-count blocks-count
      :children
      (r/as-element
        [:<>
         [:> TextField {:fullWidth true
                        :label "PDF URL"
                        :value url
                        :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                                 :url (.. % -target -value)])
                        :variant "outlined"
                        :margin "normal"
                        :helperText "URL path to the PDF file"}]

         (when (and (str/starts-with? url "https://drive.google.com/file")
                    (str/ends-with? url "/view?usp=sharing"))
           (let [gid (second (re-find #"/file/d/([^/]+)" url))
                 gurl (str "https://docs.google.com/viewer?srcid="
                           gid
                           "&pid=explorer&efh=false&a=v&chrome=false&embedded=true")]
             [:> Button {:onClick #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                                 :url gurl])}
              "Fix Google Drive Link"]))

         [:> TextField {:fullWidth true
                        :label "Title"
                        :value (or (:title block) "")
                        :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                                 :title (.. % -target -value)])
                        :variant "outlined"
                        :margin "normal"}]

         [:> TextField {:fullWidth true
                        :label "Caption"
                        :value (or (:caption block) "")
                        :onChange #(rf/dispatch [::update-block-field section-idx page-idx block-idx
                                                 :caption (.. % -target -value)])
                        :variant "outlined"
                        :margin "normal"}]])}]))

(r/defc type-code-explorer-block-editor [{:keys [section-idx page-idx block-idx blocks-count]}]
  [block-card
   {:icon (r/as-element [:> CategoryIcon {:fontSize "small" :color "action" :sx #js{:mr 1}}])
    :label "Type Code Explorer"
    :preview nil
    :section-idx section-idx :page-idx page-idx :block-idx block-idx :blocks-count blocks-count
    :children
    (r/as-element
      [:> Typography {:variant "body2" :color "text.secondary"}
       "This block displays a hierarchical browser for sports facility types."])}])

(r/defc data-model-excel-download-block-editor [{:keys [section-idx page-idx block-idx blocks-count]}]
  [block-card
   {:icon (r/as-element [:> DownloadIcon {:fontSize "small" :color "action" :sx #js{:mr 1}}])
    :label "Data Model Excel Download"
    :preview nil
    :section-idx section-idx :page-idx page-idx :block-idx block-idx :blocks-count blocks-count
    :children
    (r/as-element
      [:> Typography {:variant "body2" :color "text.secondary"}
       "This block displays a button that downloads a data model Excel file."])}])

(r/defc block-editor [{:keys [block] :as props}]
  (case (:type block)
    :text [text-block-editor props]
    :video [video-block-editor props]
    :image [image-block-editor props]
    :pdf [pdf-block-editor props]
    :type-code-explorer [type-code-explorer-block-editor props]
    :data-model-excel-download [data-model-excel-download-block-editor props]
    [:> Typography {:color "error"} (str "Unknown block type: " (:type block))]))

(r/defc add-block-controls [{:keys [section-idx page-idx]}]
  [:> Box {:sx #js{:display "flex" :gap 1 :mt 2 :flexWrap "wrap"}}
   [:> Button
    {:variant "outlined"
     :size "small"
     :startIcon (r/as-element [:> TextIcon {}])
     :onClick #(rf/dispatch [::add-block section-idx page-idx :text])}
    "Add Text"]
   [:> Button
    {:variant "outlined"
     :size "small"
     :startIcon (r/as-element [:> VideoIcon {}])
     :onClick #(rf/dispatch [::add-block section-idx page-idx :video])}
    "Add Video"]
   [:> Button
    {:variant "outlined"
     :size "small"
     :startIcon (r/as-element [:> ImageIcon {}])
     :onClick #(rf/dispatch [::add-block section-idx page-idx :image])}
    "Add Image"]
   [:> Button
    {:variant "outlined"
     :size "small"
     :startIcon (r/as-element [:> PdfIcon {}])
     :onClick #(rf/dispatch [::add-block section-idx page-idx :pdf])}
    "Add PDF"]
   [:> Button
    {:variant "outlined"
     :size "small"
     :color "secondary"
     :startIcon (r/as-element [:> CategoryIcon {}])
     :onClick #(rf/dispatch [::add-block section-idx page-idx :type-code-explorer])}
    "Add Type Explorer"]
   [:> Button
    {:variant "outlined"
     :size "small"
     :color "secondary"
     :startIcon (r/as-element [:> DownloadIcon {}])
     :onClick #(rf/dispatch [::add-block section-idx page-idx :data-model-excel-download])}
    "Add Data Model Excel Download"]])

(r/defc blocks-editor [{:keys [section-idx page-idx blocks]}]
  [:> Box {}
   [:> Typography {:variant "h6" :gutterBottom true :mt 2}
    "Page Content Blocks"]

   (map-indexed
     (fn [idx block]
       ^{:key (str (:block-id block))}
       [block-editor
        {:section-idx section-idx
         :page-idx page-idx
         :block-idx idx
         :blocks-count (count blocks)
         :block block}])
     blocks)

   [add-block-controls {:section-idx section-idx :page-idx page-idx}]])

;;; ——— UI: section/page settings ——————————————————————————————————————

(r/defc slug-field
  [{:keys [value label on-change on-generate helper]}]
  [:> TextField
   {:fullWidth true
    :label (or label "Slug")
    :value (or value "")
    :onChange #(on-change (.. % -target -value))
    :variant "outlined"
    :margin "normal"
    :helperText (or helper "Used in ?ohje= links. Renames keep the old slug working (alias).")
    :InputProps
    #js{:endAdornment
        (r/as-element
          [:> InputAdornment {:position "end"}
           [:> Tooltip {:title "Generate from title"}
            [:> IconButton {:size "small" :onClick on-generate}
             [:> AutoFixIcon {:fontSize "small"}]]]])}}])

(r/defc section-editor [{:keys [section-idx section]}]
  (let [[expanded set-expanded!] (hooks/use-state false)]
    [:> Box {:sx #js{:mt 2}}
     [:> Paper {:sx #js{:p 2 :mb 2
                        :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                        :transition "box-shadow 0.3s ease"}}
      [:> Box {:sx #js{:display "flex" :justifyContent "space-between" :alignItems "center"}}
       [:> Typography {:variant "body2" :gutterBottom false}
        "SECTION SETTINGS"]
       [:> IconButton {:onClick #(set-expanded! (not expanded))
                       :size "small"
                       :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                               :transition "transform 0.3s"}}
        [:> ExpandMoreIcon {:fontSize "small"}]]]

      [:> Collapse {:in expanded :timeout "auto"}
       [:> Box {:sx #js{:mt 2}}
        [:> TextField
         {:fullWidth true
          :label "Section Title"
          :value (or (:title section) "")
          :onChange #(rf/dispatch [::update-section-field section-idx :title (.. % -target -value)])
          :variant "outlined"
          :margin "normal"}]

        [slug-field
         {:value (:slug section)
          :on-change #(rf/dispatch [::update-section-field section-idx :slug %])
          :on-generate #(rf/dispatch [::generate-section-slug section-idx])}]

        [:> TextField
         {:fullWidth true
          :label "Summary"
          :value (or (:summary section) "")
          :onChange #(rf/dispatch [::update-section-field section-idx :summary (.. % -target -value)])
          :variant "outlined"
          :margin "normal"
          :multiline true
          :rows 2
          :helperText "1-2 sentences shown in listings"}]]]]]))

(r/defc page-editor [{:keys [section-idx page-idx page]}]
  (let [[expanded set-expanded!] (hooks/use-state false)]
    [:> Box {:sx #js{:mt 2}}
     [:> Paper {:sx #js{:p 2 :mb 2
                        :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                        :transition "box-shadow 0.3s ease"}}
      [:> Box {:sx #js{:display "flex" :justifyContent "space-between" :alignItems "center"}}
       [:> Typography {:variant "body2" :gutterBottom false}
        "PAGE SETTINGS"]
       [:> IconButton {:onClick #(set-expanded! (not expanded))
                       :size "small"
                       :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                               :transition "transform 0.3s"}}
        [:> ExpandMoreIcon {:fontSize "small"}]]]

      [:> Collapse {:in expanded :timeout "auto"}
       [:> Box {:sx #js{:mt 2}}
        [:> TextField
         {:fullWidth true
          :label "Page Title"
          :value (or (:title page) "")
          :onChange #(rf/dispatch [::update-page-field section-idx page-idx :title (.. % -target -value)])
          :variant "outlined"
          :margin "normal"}]

        [slug-field
         {:value (:slug page)
          :on-change #(rf/dispatch [::update-page-field section-idx page-idx :slug %])
          :on-generate #(rf/dispatch [::generate-page-slug section-idx page-idx])}]

        [:> TextField
         {:fullWidth true
          :label "Summary"
          :value (or (:summary page) "")
          :onChange #(rf/dispatch [::update-page-field section-idx page-idx :summary (.. % -target -value)])
          :variant "outlined"
          :margin "normal"
          :multiline true
          :rows 2
          :helperText "1-2 sentences shown in listings and used by the AI assistant"}]]]]]))

;;; ——— UI: selectors ——————————————————————————————————————————————————

(defn- node-label [node idx kind]
  (let [title (:title node)]
    (if (str/blank? title)
      (str kind " " (inc idx) " (" (:slug node) ")")
      title)))

(r/defc section-selector [{:keys [sections selected-section-idx]}]
  [:> Stack {:spacing 1}

   [:> Typography {:variant "h6"} "Select Section"]

   [:> FormControl {:fullWidth true}
    [:> Select {:value (if (some? selected-section-idx) selected-section-idx "")
                :onChange #(rf/dispatch [::select-section (js/parseInt (.. % -target -value))])
                :displayEmpty true}
     (map-indexed
       (fn [idx section]
         [:> MenuItem {:key idx :value idx}
          (node-label section idx "Section")])
       sections)]]

   [:> Stack {:direction "row" :spacing 1 :flexWrap "wrap"}
    [:> Button
     {:variant "contained"
      :color "primary"
      :size "small"
      :startIcon (r/as-element [:> AddIcon {}])
      :onClick #(rf/dispatch [::add-section])
      :sx #js{:mt 0}}
     "Add Section"]

    [:> Button
     {:variant "outlined"
      :color "error"
      :size "small"
      :disabled (nil? selected-section-idx)
      :startIcon (r/as-element [:> DeleteIcon {}])
      :onClick #(rf/dispatch [::show-confirm-dialog :delete-section {:section-idx selected-section-idx}])
      :sx #js{:mt 0}}
     "Delete Section"]

    [:> Button
     {:variant "outlined"
      :color "primary"
      :size "small"
      :disabled (or (nil? selected-section-idx) (zero? selected-section-idx))
      :startIcon (r/as-element [:> ArrowUpIcon {}])
      :onClick #(rf/dispatch [::move-section-up selected-section-idx])
      :sx #js{:mt 0}}
     "Move Up"]

    [:> Button
     {:variant "outlined"
      :color "primary"
      :size "small"
      :disabled (or (nil? selected-section-idx)
                    (= selected-section-idx (dec (count sections))))
      :startIcon (r/as-element [:> ArrowDownIcon {}])
      :onClick #(rf/dispatch [::move-section-down selected-section-idx])
      :sx #js{:mt 0}}
     "Move Down"]]])

(r/defc page-selector [{:keys [section-idx pages selected-page-idx]}]
  [:> Stack {:spacing 2}

   [:> Typography {:variant "h6"} "Select Page"]

   [:> FormControl {:fullWidth true}
    [:> Select {:value (if (some? selected-page-idx) selected-page-idx "")
                :onChange #(rf/dispatch [::select-page (js/parseInt (.. % -target -value))])
                :displayEmpty true}
     (map-indexed
       (fn [idx page]
         [:> MenuItem {:key idx :value idx}
          (node-label page idx "Page")])
       pages)]]

   [:> Stack {:direction "row" :spacing 1 :flexWrap "wrap"}

    [:> Button
     {:variant "contained"
      :color "primary"
      :size "small"
      :startIcon (r/as-element [:> AddIcon {}])
      :onClick #(rf/dispatch [::add-page section-idx])
      :sx #js{:mt 0}}
     "Add Page"]

    [:> Button
     {:variant "outlined"
      :color "error"
      :size "small"
      :disabled (nil? selected-page-idx)
      :startIcon (r/as-element [:> DeleteIcon {}])
      :onClick #(rf/dispatch [::show-confirm-dialog :delete-page {:section-idx section-idx
                                                                  :page-idx selected-page-idx}])
      :sx #js{:mt 0}}
     "Delete Page"]

    [:> Button
     {:variant "outlined"
      :color "primary"
      :size "small"
      :disabled (or (nil? selected-page-idx) (zero? selected-page-idx))
      :startIcon (r/as-element [:> ArrowUpIcon {}])
      :onClick #(rf/dispatch [::move-page-up section-idx selected-page-idx])
      :sx #js{:mt 0}}
     "Move Up"]

    [:> Button
     {:variant "outlined"
      :color "primary"
      :size "small"
      :disabled (or (nil? selected-page-idx)
                    (= selected-page-idx (dec (count pages))))
      :startIcon (r/as-element [:> ArrowDownIcon {}])
      :onClick #(rf/dispatch [::move-page-down section-idx selected-page-idx])
      :sx #js{:mt 0}}
     "Move Down"]]])

;;; ——— UI: toolbar + dialogs ——————————————————————————————————————————

(r/defc editor-toolbar []
  (let [locale @(rf/subscribe [::editor-locale*])]
    [:> Toolbar {:disableGutters true :sx #js{:mb 2 :gap 2 :flexWrap "wrap"}}
     [:> Typography {:variant "h5" :component "div"}
      "Help Content Editor"]

     ;; Which language's tree is being edited. Each language is
     ;; drafted/published independently.
     [:> Tabs {:value (name locale)
               :onChange #(rf/dispatch [::set-editor-locale (keyword %2)])
               :sx #js{:flexGrow 1 :minHeight 40}}
      [:> Tab {:value "fi" :label "Suomi"}]
      [:> Tab {:value "se" :label "Svenska"}]
      [:> Tab {:value "en" :label "English"}]]

     [:> Stack {:direction "row" :spacing 1}
      [:> Button
       {:variant "outlined"
        :color "primary"
        :startIcon (r/as-element [:> HistoryIcon {}])
        :onClick #(rf/dispatch [::open-version-history])}
       "History"]
      [:> Button
       {:variant "contained"
        :color "primary"
        :startIcon (r/as-element [:> PreviewIcon {}])
        :onClick #(rf/dispatch [::apply-changes])}
       "Preview"]
      [:> Button
       {:variant "outlined"
        :color "secondary"
        :startIcon (r/as-element [:> SaveIcon {}])
        :onClick #(rf/dispatch [::save-draft])}
       (str "Save draft (" (name locale) ")")]
      [:> Button
       {:variant "contained"
        :color "secondary"
        :startIcon (r/as-element [:> SaveIcon {}])
        :onClick #(rf/dispatch [::save-changes])}
       (str "Publish (" (name locale) ")")]
      [:> Button
       {:variant "outlined"
        :color "secondary"
        :sx #js{:ml 1}
        :onClick #(rf/dispatch [::events/close-edit-mode])}
       "Cancel"]]]))

(r/defc version-history-dialog []
  (let [{:keys [dialog-open? items]} @(rf/subscribe [::versions])
        locale @(rf/subscribe [::editor-locale*])]
    [:> Dialog
     {:open (boolean dialog-open?)
      :onClose #(rf/dispatch [::close-version-history])
      :maxWidth "sm"
      :fullWidth true}

     [:> DialogTitle {} (str "Version history (" (name locale) ")")]

     [:> DialogContent {}
      (if (empty? items)
        [:> DialogContentText {} "No saved versions."]
        [:> MuiList {}
         (for [{:keys [id event-date status]} items]
           ^{:key id}
           [:> ListItem
            {:secondaryAction
             (r/as-element
               [:> Button
                {:size "small"
                 :variant "outlined"
                 :onClick #(rf/dispatch [::load-version id])}
                "Load into editor"])}
            [:> Chip {:size "small"
                      :label status
                      :color (if (= "active" status) "secondary" "default")
                      :sx #js{:mr 2}}]
            [:> ListItemText
             ;; "2026-07-08 14:03:22.123456" → "2026-07-08 14:03"
             {:primary (subs (str event-date) 0 16)}]])])]

     [:> DialogActions {}
      [:> Button
       {:onClick #(rf/dispatch [::close-version-history])
        :color "primary"}
       "Close"]]]))

(r/defc confirmation-dialog []
  (let [dialog @(rf/subscribe [::confirm-dialog])
        dialog-type (:type dialog)

        get-title (fn []
                    (case dialog-type
                      :delete-section "Delete Section"
                      :delete-page "Delete Page"
                      :delete-block "Delete Block"
                      "Confirm Action"))

        get-message (fn []
                      (case dialog-type
                        :delete-section "Are you sure you want to delete this section? This will also delete all pages and content within the section."
                        :delete-page "Are you sure you want to delete this page? This will also delete all content blocks on the page."
                        :delete-block "Are you sure you want to delete this content block?"
                        "Are you sure you want to proceed with this action?"))]

    [:> Dialog
     {:open (boolean (:open? dialog))
      :onClose #(rf/dispatch [::hide-confirm-dialog])
      :aria-labelledby "confirm-dialog-title"}

     [:> DialogTitle
      {:id "confirm-dialog-title"}
      (get-title)]

     [:> DialogContent {}
      [:> DialogContentText {}
       (get-message)]]

     [:> DialogActions {}
      [:> Button
       {:onClick #(rf/dispatch [::hide-confirm-dialog])
        :color "primary"}
       "Cancel"]
      [:> Button
       {:onClick #(rf/dispatch [::confirm-action])
        :color "error"
        :variant "contained"
        :autoFocus true}
       "Delete"]]]))

(r/defc view
  []
  (let [tree @(rf/subscribe [::edited-tree])
        selected-section-idx @(rf/subscribe [::editor-section-idx])
        selected-page-idx @(rf/subscribe [::editor-page-idx])

        selected-section (when (and tree (number? selected-section-idx)
                                    (< selected-section-idx (count tree)))
                           (nth tree selected-section-idx))
        selected-pages (when selected-section
                         (:pages selected-section))
        selected-page (when (and selected-pages (number? selected-page-idx)
                                 (< selected-page-idx (count selected-pages)))
                        (nth selected-pages selected-page-idx))]

    [:> Box {:sx #js{:p 2}}
     ;; Confirmation dialog always rendered but only shown when needed
     [confirmation-dialog {}]
     [version-history-dialog {}]
     [editor-toolbar {}]

     [section-selector
      {:sections tree
       :selected-section-idx selected-section-idx}]

     (when selected-section
       [section-editor {:section-idx selected-section-idx :section selected-section}])

     (when selected-section
       [page-selector
        {:section-idx selected-section-idx
         :pages selected-pages
         :selected-page-idx selected-page-idx}])

     (when (and selected-section selected-page)
       [page-editor
        {:section-idx selected-section-idx
         :page-idx selected-page-idx
         :page selected-page}])

     (when (and selected-section selected-page)
       [blocks-editor
        {:section-idx selected-section-idx
         :page-idx selected-page-idx
         :blocks (:blocks selected-page)}])]))
