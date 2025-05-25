(ns lipas.ui.help.manage
  (:require
   ["@mui/icons-material/Add$default" :as AddIcon]
   ["@mui/icons-material/ArrowDownward$default" :as ArrowDownIcon]
   ["@mui/icons-material/ArrowUpward$default" :as ArrowUpIcon]
   ["@mui/icons-material/Delete$default" :as DeleteIcon]
   ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
   ["@mui/icons-material/CategoryOutlined$default" :as CategoryIcon]
   ["@mui/icons-material/Image$default" :as ImageIcon]
   ["@mui/icons-material/Preview$default" :as PreviewIcon]
   ["@mui/icons-material/Save$default" :as SaveIcon]
   ["@mui/icons-material/TextFields$default" :as TextIcon]
   ["@mui/icons-material/VideoLibrary$default" :as VideoIcon]
   ["@mui/icons-material/PictureAsPdf$default" :as PdfIcon]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/Button$default" :as Button]
   ["@mui/material/Card$default" :as Card]
   ["@mui/material/CardContent$default" :as CardContent]
   ["@mui/material/CardHeader$default" :as CardHeader]
   ["@mui/material/Collapse$default" :as Collapse]
   ["@mui/material/Dialog$default" :as Dialog]
   ["@mui/material/DialogActions$default" :as DialogActions]
   ["@mui/material/DialogContent$default" :as DialogContent]
   ["@mui/material/DialogContentText$default" :as DialogContentText]
   ["@mui/material/DialogTitle$default" :as DialogTitle]
   ["@mui/material/FormControl$default" :as FormControl]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/material/InputLabel$default" :as InputLabel]
   ["@mui/material/MenuItem$default" :as MenuItem]
   ["@mui/material/Paper$default" :as Paper]
   ["@mui/material/Select$default" :as Select]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/Tab$default" :as Tab]
   ["@mui/material/Tabs$default" :as Tabs]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/Toolbar$default" :as Toolbar]
   ["@mui/material/Typography$default" :as Typography]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [lipas.ui.help.events :as events]
   [lipas.ui.help.subs :as subs]
   [lipas.ui.uix.hooks :refer [use-subscribe]]
   [re-frame.core :as rf]
   [uix.core :as uix :refer [$ defui use-state]]))

;; Events for managing help content
(rf/reg-event-db
 ::initialize-editor
 (fn [db _]
   (update db :help assoc :edited-data (get-in db [:help :data]))))


(rf/reg-event-db
 ::update-section-title
 (fn [db [_ section-idx lang value]]
   (assoc-in db [:help :edited-data section-idx :title lang] value)))

(rf/reg-event-db
 ::update-page-title
 (fn [db [_ section-idx page-idx lang value]]
   (assoc-in db [:help :edited-data section-idx :pages page-idx :title lang] value)))

(rf/reg-event-db
 ::update-block-content
 (fn [db [_ section-idx page-idx block-idx field lang value]]
   (assoc-in db [:help :edited-data section-idx :pages page-idx :blocks block-idx field lang] value)))

(rf/reg-event-db
 ::update-block-field
 (fn [db [_ section-idx page-idx block-idx field value]]
   (assoc-in db [:help :edited-data section-idx :pages page-idx :blocks block-idx field] value)))

(rf/reg-event-db
 ::add-block
 (fn [db [_ section-idx page-idx block-type]]
   (let [block-id (str (random-uuid))
         base-block {:block-id block-id :type block-type}
         new-block (case block-type
                     :text (assoc base-block :content {:fi "" :en "" :se ""})
                     :video (assoc base-block
                                  :provider :youtube
                                  :video-id ""
                                  :title {:fi "" :en "" :se ""})
                     :image (assoc base-block
                                  :url ""
                                  :alt {:fi "" :en "" :se ""}
                                  :caption {:fi "" :en "" :se ""})
                     :pdf (assoc base-block
                                :url ""
                                :caption {:fi "" :en "" :se ""}
                                :title {:fi "" :en "" :se ""})
                     :type-code-explorer base-block ;; No additional props needed for type explorer
                     )]
     (update-in db [:help :edited-data section-idx :pages page-idx :blocks] conj new-block))))


(rf/reg-event-db
 ::delete-block
 (fn [db [_ section-idx page-idx block-idx]]
   (update-in db [:help :edited-data section-idx :pages page-idx :blocks]
              (fn [blocks]
                (vec (concat
                       (subvec blocks 0 block-idx)
                       (subvec blocks (inc block-idx))))))))

(rf/reg-event-db
 ::move-block-up
 (fn [db [_ section-idx page-idx block-idx]]
   (if (zero? block-idx)
     db ; Already at the top, no change
     (update-in db [:help :edited-data section-idx :pages page-idx :blocks]
                (fn [blocks]
                  (let [block (get blocks block-idx)
                        prev-block (get blocks (dec block-idx))]
                    (-> blocks
                        (assoc (dec block-idx) block)
                        (assoc block-idx prev-block))))))))

(rf/reg-event-db
 ::move-block-down
 (fn [db [_ section-idx page-idx block-idx]]
   (let [blocks (get-in db [:help :edited-data section-idx :pages page-idx :blocks])
         last-idx (dec (count blocks))]
     (if (= block-idx last-idx)
       db ; Already at the bottom, no change
       (update-in db [:help :edited-data section-idx :pages page-idx :blocks]
                  (fn [blocks]
                    (let [block (get blocks block-idx)
                          next-block (get blocks (inc block-idx))]
                      (-> blocks
                          (assoc block-idx next-block)
                          (assoc (inc block-idx) block)))))))))

;; Section reordering events
(rf/reg-event-db
 ::move-section-up
 (fn [db [_ section-idx]]
   (if (zero? section-idx)
     db ; Already at the top, no change
     (update-in db [:help :edited-data]
                (fn [sections]
                  (let [section (get sections section-idx)
                        prev-section (get sections (dec section-idx))]
                    (-> sections
                        (assoc (dec section-idx) section)
                        (assoc section-idx prev-section))))))))

(rf/reg-event-db
 ::move-section-down
 (fn [db [_ section-idx]]
   (let [sections (get-in db [:help :edited-data])
         last-idx (dec (count sections))]
     (if (= section-idx last-idx)
       db ; Already at the bottom, no change
       (update-in db [:help :edited-data]
                  (fn [sections]
                    (let [section (get sections section-idx)
                          next-section (get sections (inc section-idx))]
                      (-> sections
                          (assoc section-idx next-section)
                          (assoc (inc section-idx) section)))))))))

;; Page reordering events
(rf/reg-event-db
 ::move-page-up
 (fn [db [_ section-idx page-idx]]
   (if (zero? page-idx)
     db ; Already at the top, no change
     (update-in db [:help :edited-data section-idx :pages]
                (fn [pages]
                  (let [page (get pages page-idx)
                        prev-page (get pages (dec page-idx))]
                    (-> pages
                        (assoc (dec page-idx) page)
                        (assoc page-idx prev-page))))))))

(rf/reg-event-db
 ::move-page-down
 (fn [db [_ section-idx page-idx]]
   (let [pages (get-in db [:help :edited-data section-idx :pages])
         last-idx (dec (count pages))]
     (if (= page-idx last-idx)
       db ; Already at the bottom, no change
       (update-in db [:help :edited-data section-idx :pages]
                  (fn [pages]
                    (let [page (get pages page-idx)
                          next-page (get pages (inc page-idx))]
                      (-> pages
                          (assoc page-idx next-page)
                          (assoc (inc page-idx) page)))))))))

;; Helper function to create a slug from a string
(defn- create-slug [s]
  (-> (or s "")
      str/lower-case
      (str/replace #"[^\w\s-]" "") ; Remove special chars except spaces and hyphens
      (str/replace #"\s+" "-")     ; Replace spaces with hyphens
      (str/replace #"-+" "-")      ; Replace multiple hyphens with single
      (str/replace #"^-|-$" "")))  ; Remove leading/trailing hyphens

(rf/reg-event-db
 ::add-page
 (fn [db [_ section-idx]]
   (let [default-title "New Page"
         ;; Create a basic slug from the title
         base-slug (create-slug default-title)
         ;; Add timestamp to ensure uniqueness
         timestamp (.now js/Date)
         ;; Create a basic page structure
         new-page {:slug (keyword (str base-slug "-" timestamp))
                  :title {:fi default-title :en default-title :se "Ny sida"}
                  :blocks []}
         ;; Get the current pages vector
         pages (get-in db [:help :edited-data section-idx :pages])
         new-page-idx (count pages)]
     ;; Add the new page to the appropriate section
     (-> db
         (update-in [:help :edited-data section-idx :pages] conj new-page)
         ;; Select the new page
         (assoc-in [:help :dialog :selected-page-idx] new-page-idx)
         (assoc-in [:help :dialog :selected-page-slug] (:slug new-page))))))

(rf/reg-event-db
 ::delete-page
 (fn [db [_ section-idx page-idx]]
   (let [pages (get-in db [:help :edited-data section-idx :pages])
         selected-page-idx (get-in db [:help :dialog :selected-page-idx])
         ;; If we're deleting the currently selected page, select the first available page
         new-selected-idx (if (= selected-page-idx page-idx)
                             (if (= page-idx 0)
                               (if (> (count pages) 1) 0 nil) ; Select first page if still pages, else nil
                               (dec page-idx)) ; Select previous page
                             (if (and selected-page-idx (> selected-page-idx page-idx))
                                (dec selected-page-idx) ; Adjust selected index if it's after the deleted one
                                selected-page-idx))
         new-selected-slug (when (and (some? new-selected-idx) (< new-selected-idx (count (filterv #(not= % (nth pages page-idx)) pages))))
                             (:slug (nth (filterv #(not= % (nth pages page-idx)) pages) new-selected-idx)))]
     (-> db
         ;; Remove the page from the section
         (update-in [:help :edited-data section-idx :pages]
                   (fn [pages] (vec (concat (subvec pages 0 page-idx) (subvec pages (inc page-idx))))))
         ;; Update the selected page if needed
         (assoc-in [:help :dialog :selected-page-idx] new-selected-idx)
         (assoc-in [:help :dialog :selected-page-slug] new-selected-slug)))))

(rf/reg-event-db
 ::add-section
 (fn [db _]
   (let [default-title "New Section"
         ;; Create a basic slug from the title
         base-slug (create-slug default-title)
         ;; Add timestamp to ensure uniqueness
         timestamp (.now js/Date)
         ;; Create a basic section structure with one default page
         section-slug (keyword (str base-slug "-" timestamp))
         welcome-page {:slug (keyword "welcome")
                      :title {:fi "Welcome" :en "Welcome" :se "Välkommen"}
                      :blocks []}
         new-section {:slug section-slug
                     :title {:fi default-title :en default-title :se "Ny sektion"}
                     :pages [welcome-page]}
         ;; Get the current sections
         sections (or (get-in db [:help :edited-data]) [])
         new-section-idx (count sections)]
     ;; Add the new section
     (-> db
         (update-in [:help :edited-data] (fn [sections] (conj (or sections []) new-section)))
         ;; Select the new section and its first page
         (assoc-in [:help :dialog :selected-section-idx] new-section-idx)
         (assoc-in [:help :dialog :selected-section-slug] section-slug)
         (assoc-in [:help :dialog :selected-page-idx] 0)
         (assoc-in [:help :dialog :selected-page-slug] (:slug welcome-page))))))

(rf/reg-event-db
 ::delete-section
 (fn [db [_ section-idx]]
   (let [sections (get-in db [:help :edited-data])
         selected-section-idx (get-in db [:help :dialog :selected-section-idx])
         ;; If we're deleting the currently selected section, select the first available section
         new-selected-idx (if (= selected-section-idx section-idx)
                            (if (= section-idx 0)
                              (if (> (count sections) 1) 0 nil) ; Select first section if still sections, else nil
                              (dec section-idx)) ; Select previous section
                            (if (and selected-section-idx (> selected-section-idx section-idx))
                              (dec selected-section-idx) ; Adjust selected index if it's after the deleted one
                              selected-section-idx))
         new-selected-slug (when (and (some? new-selected-idx) (< new-selected-idx (count (filterv #(not= % (nth sections section-idx)) sections))))
                             (:slug (nth (filterv #(not= % (nth sections section-idx)) sections) new-selected-idx)))]
     (-> db
         ;; Remove the section
         (update-in [:help :edited-data]
                   (fn [sections] (vec (concat (subvec sections 0 section-idx) (subvec sections (inc section-idx))))))
         ;; Update the selected section if needed
         (assoc-in [:help :dialog :selected-section-idx] new-selected-idx)
         (assoc-in [:help :dialog :selected-section-slug] new-selected-slug)
         ;; Clear page selection if we deleted the selected section
         ((fn [updated-db]
            (if (= selected-section-idx section-idx)
              (-> updated-db
                 (assoc-in [:help :dialog :selected-page-idx] nil)
                 (assoc-in [:help :dialog :selected-page-slug] nil))
              updated-db)))))))

(rf/reg-event-db
 ::apply-changes
 (fn [db _]
   (-> db
       (assoc-in [:help :data] (get-in db [:help :edited-data]))
       (assoc-in [:help :dialog :mode] :read))))

(rf/reg-event-fx ::save-changes
  (fn [{:keys [db]} _]
    (let [token  (-> db :user :login :token)]
      {:db (assoc-in db [:help :save-in-progress] true)
       :fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/save-help-data")
              :params          (get-in db [:help :edited-data])
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::save-success]
              :on-failure      [::save-failure]}]]})))

(rf/reg-event-fx ::save-success
  (fn [{:keys [db]} _]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-success)
                        :success? true}]
      {:db (-> db (assoc-in [:ptv :save-in-progress] false))
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


(rf/reg-sub
 ::edited-help-data
 :<- [::subs/help]
 (fn [help _]
   (:edited-data help)))

;; Confirmation dialog related events and subscriptions
(rf/reg-event-db
 ::show-confirm-dialog
 (fn [db [_ dialog-type params]]
   (assoc-in db [:help :confirm-dialog] {:open? true
                                        :type dialog-type
                                        :params params})))

(rf/reg-event-db
 ::hide-confirm-dialog
 (fn [db _]
   (assoc-in db [:help :confirm-dialog :open?] false)))

(rf/reg-event-fx
 ::confirm-action
 (fn [{:keys [db]} _]
   (let [{:keys [type params]} (get-in db [:help :confirm-dialog])]
     {:db (assoc-in db [:help :confirm-dialog :open?] false)
      :fx [[:dispatch (case type
                        :delete-section [::delete-section (:section-idx params)]
                        :delete-page [::delete-page (:section-idx params) (:page-idx params)]
                        :delete-block [::delete-block (:section-idx params) (:page-idx params) (:block-idx params)])]]})))

(rf/reg-sub
 ::confirm-dialog
 :<- [::subs/help]
 (fn [help _]
   (get help :confirm-dialog {:open? false})))

;; Helper UI Components
(defui language-tabs [{:keys [current-lang on-change]}]
  ($ Tabs
     {:value current-lang
      :onChange #(on-change %2)
      :centered true}
     ($ Tab {:value :fi :label "Suomi"})
     ($ Tab {:value :en :label "English"})
     ($ Tab {:value :se :label "Svenska"})))

(defui localized-text-field
  [{:keys [value label on-change multiline rows lang]}]
  ($ TextField
     {:fullWidth true
      :label label
      :value (get value lang "")
      :onChange #(on-change lang (.. % -target -value))
      :variant "outlined"
      :margin "normal"
      :multiline (boolean multiline)
      :rows (or rows 4)}))

(defui section-editor [{:keys [section-idx section]}]
  (let [[lang set-lang!] (use-state :fi)
        [expanded set-expanded!] (use-state false)]
    ($ Box {:sx #js{:mt 2}}
       ($ Paper {:sx #js{:p 2 :mb 2
                        :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                        :transition "box-shadow 0.3s ease"}}
          ($ Box {:sx #js{:display "flex" :justifyContent "space-between" :alignItems "center"}}
             ($ Typography {:variant "body2" :gutterBottom false}
                "SECTION SETTINGS")
             ($ IconButton {:onClick #(set-expanded! (not expanded))
                          :size "small"
                          :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                :transition "transform 0.3s"}}
                ($ ExpandMoreIcon {:fontSize "small"})))

          ($ Collapse {:in expanded :timeout "auto"}
             ($ Box {:sx #js{:mt 2}}
                ($ language-tabs {:current-lang lang :on-change set-lang!})

                ($ localized-text-field
                   {:label "Section Title"
                    :value (:title section)
                    :lang lang
                    :on-change #(rf/dispatch [::update-section-title section-idx %1 %2])})))))))

(defui page-editor [{:keys [section-idx page-idx page]}]
  (let [[lang set-lang!] (use-state :fi)
        [expanded set-expanded!] (use-state false)]
    ($ Box {:sx #js{:mt 2}}
       ($ Paper {:sx #js{:p 2 :mb 2
                        :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                        :transition "box-shadow 0.3s ease"}}
          ($ Box {:sx #js{:display "flex" :justifyContent "space-between" :alignItems "center"}}
             ($ Typography {:variant "body2" :gutterBottom false}
                "PAGE SETTINGS")
             ($ IconButton {:onClick #(set-expanded! (not expanded))
                          :size "small"
                          :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                :transition "transform 0.3s"}}
                ($ ExpandMoreIcon {:fontSize "small"})))

          ($ Collapse {:in expanded :timeout "auto"}
             ($ Box {:sx #js{:mt 2}}
                ($ language-tabs {:current-lang lang :on-change set-lang!})

                ($ localized-text-field
                   {:label "Page Title"
                    :value (:title page)
                    :lang lang
                    :on-change #(rf/dispatch [::update-page-title section-idx page-idx %1 %2])})))))))

(defui text-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  (let [[lang set-lang!] (use-state :fi)
        [expanded set-expanded!] (use-state false)
        content-preview (or
                         (when-let [content (get-in block [:content :fi])]
                           (when (not (str/blank? content))
                             (if (> (count content) 50)
                               (str (subs content 0 50) "...")
                               content)))
                         "Empty text block")]
    ($ Card {:variant "elevation"
            :elevation 3
            :sx #js{:mb 2
                   :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                   :transition "box-shadow 0.3s ease"}}
       ($ CardHeader
          {:title ($ Typography {:variant "subtitle1" :component "div"}
                    ($ Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
                       ($ TextIcon {:fontSize "small" :color "action" :sx #js{:mr 1}})
                       "Text Block"
                       ($ Typography {:variant "body2" :color "text.secondary" :component "span" :sx #js{:ml 2}}
                          content-preview)))
           :action ($ Box {:sx #js{:display "flex" :gap 0.5}}
                      ;; Expand/collapse button
                      ($ IconButton {:onClick #(set-expanded! (not expanded))
                                    :size "small"
                                    :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                         :transition "transform 0.3s"}}
                         ($ ExpandMoreIcon {:fontSize "small"}))

                      ;; Move up button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (zero? block-idx)
                                    :onClick #(rf/dispatch [::move-block-up section-idx page-idx block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-idx page-idx block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-idx section-idx :page-idx page-idx :block-idx block-idx}])}
                         ($ DeleteIcon {:fontSize "small"})))})

       ($ Collapse {:in expanded :timeout "auto" :unmountOnExit true}
          ($ CardContent {}
             ($ language-tabs {:current-lang lang :on-change set-lang!})

             ($ localized-text-field
                {:label "Content"
                 :value (:content block)
                 :multiline true
                 :rows 6
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-idx page-idx block-idx :content %1 %2])}))))))

(defui video-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  (let [[lang set-lang!] (use-state :fi)
        [expanded set-expanded!] (use-state false)
        video-id (or (:video-id block) "")
        provider (name (or (:provider block) :youtube))
        title (get-in block [:title :fi] "")]
    ($ Card {:variant "elevation"
            :elevation 3
            :sx #js{:mb 2
                   :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                   :transition "box-shadow 0.3s ease"}}
       ($ CardHeader
          {:title ($ Typography {:variant "subtitle1" :component "div"}
                    ($ Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
                       ($ VideoIcon {:fontSize "small" :color "action" :sx #js {:mr 1}})
                       "Video Block"
                       ($ Typography {:variant "body2" :color "text.secondary" :component "span" :sx #js{:ml 2}}
                          (if (str/blank? video-id)
                            "No video set"
                            (str provider ": " video-id (when-not (str/blank? title) (str " - " title)))))))
           :action ($ Box {:sx #js{:display "flex" :gap 0.5}}
                      ;; Expand/collapse button
                      ($ IconButton {:onClick #(set-expanded! (not expanded))
                                    :size "small"
                                    :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                         :transition "transform 0.3s"}}
                         ($ ExpandMoreIcon {:fontSize "small"}))

                      ;; Move up button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (zero? block-idx)
                                    :onClick #(rf/dispatch [::move-block-up section-idx page-idx block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-idx page-idx block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-idx section-idx :page-idx page-idx :block-idx block-idx}])}
                         ($ DeleteIcon {:fontSize "small"})))})

       ($ Collapse {:in expanded :timeout "auto" :unmountOnExit true}
          ($ CardContent {}
             ($ FormControl {:fullWidth true :margin "normal"}
                ($ InputLabel {:id "video-provider-label"} "Provider")
                ($ Select {:labelId "video-provider-label"
                          :value (or (:provider block) :youtube)
                          :onChange #(rf/dispatch [::update-block-field
                                                 section-idx page-idx block-idx
                                                 :provider
                                                 (keyword (.. % -target -value))])}
                   ($ MenuItem {:value "youtube"} "YouTube")
                   ($ MenuItem {:value "vimeo"} "Vimeo")))

             ($ TextField {:fullWidth true
                          :label "Video ID"
                          :value (or (:video-id block) "")
                          :onChange #(rf/dispatch [::update-block-field
                                                 section-idx page-idx block-idx
                                                 :video-id
                                                 (.. % -target -value)])
                          :variant "outlined"
                          :margin "normal"
                          :helperText "For YouTube: the part after v= in URL"})

             ($ language-tabs {:current-lang lang :on-change set-lang!})

             ($ localized-text-field
                {:label "Title"
                 :value (:title block)
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-idx page-idx block-idx :title %1 %2])}))))))

(defui image-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  (let [[lang set-lang!] (use-state :fi)
        [expanded set-expanded!] (use-state false)
        url (or (:url block) "")
        alt-text (get-in block [:alt :fi] "")
        image-name (when-not (str/blank? url)
                     (let [parts (str/split url #"/")]
                       (if (seq parts)
                         (last parts)
                         url)))]
    ($ Card {:variant "elevation"
            :elevation 3
            :sx #js{:mb 2
                   :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                   :transition "box-shadow 0.3s ease"}}
       ($ CardHeader
          {:title ($ Typography {:variant "subtitle1" :component "div"}
                    ($ Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
                       ($ ImageIcon {:fontSize "small" :color "action" :sx #js{:mr 1}})
                       "Image Block"
                       ($ Typography {:variant "body2" :color "text.secondary" :component "span" :sx #js{:ml 2}}
                          (if (str/blank? url)
                            "No image set"
                            (if (str/blank? alt-text)
                              image-name
                              alt-text)))))
           :action ($ Box {:sx #js{:display "flex" :gap 0.5}}
                      ;; Expand/collapse button
                      ($ IconButton {:onClick #(set-expanded! (not expanded))
                                    :size "small"
                                    :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                         :transition "transform 0.3s"}}
                         ($ ExpandMoreIcon {:fontSize "small"}))

                      ;; Move up button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (zero? block-idx)
                                    :onClick #(rf/dispatch [::move-block-up section-idx page-idx block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-idx page-idx block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-idx section-idx :page-idx page-idx :block-idx block-idx}])}
                         ($ DeleteIcon {:fontSize "small"})))})

       ($ Collapse {:in expanded :timeout "auto" :unmountOnExit true}
          ($ CardContent {}
             ($ TextField {:fullWidth true
                          :label "Image URL"
                          :value (or (:url block) "")
                          :onChange #(rf/dispatch [::update-block-field
                                                 section-idx page-idx block-idx
                                                 :url
                                                 (.. % -target -value)])
                          :variant "outlined"
                          :margin "normal"})

             ($ language-tabs {:current-lang lang :on-change set-lang!})

             ($ localized-text-field
                {:label "Alt Text"
                 :value (:alt block)
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-idx page-idx block-idx :alt %1 %2])})

             ($ localized-text-field
                {:label "Caption"
                 :value (:caption block)
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-idx page-idx block-idx :caption %1 %2])}))))))

(defui pdf-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  (let [[lang set-lang!] (use-state :fi)
        [expanded set-expanded!] (use-state false)
        url (or (:url block) "")
        title (get-in block [:title :fi] "")
        pdf-name (when-not (str/blank? url)
                   (let [parts (str/split url #"/")]
                     (if (seq parts)
                       (last parts)
                       url)))]
    ($ Card {:variant "elevation"
            :elevation 3
            :sx #js{:mb 2
                   :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                   :transition "box-shadow 0.3s ease"}}
       ($ CardHeader
          {:title ($ Typography {:variant "subtitle1" :component "div"}
                    ($ Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
                       ($ PdfIcon {:fontSize "small" :color "action" :sx #js{:mr 1}})
                       "PDF Block"
                       ($ Typography {:variant "body2" :color "text.secondary" :component "span" :sx #js{:ml 2}}
                          (if (str/blank? url)
                            "No PDF set"
                            (if (str/blank? title)
                              pdf-name
                              title)))))
           :action ($ Box {:sx #js{:display "flex" :gap 0.5}}
                      ;; Expand/collapse button
                      ($ IconButton {:onClick #(set-expanded! (not expanded))
                                    :size "small"
                                    :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                         :transition "transform 0.3s"}}
                         ($ ExpandMoreIcon {:fontSize "small"}))

                      ;; Move up button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (zero? block-idx)
                                    :onClick #(rf/dispatch [::move-block-up section-idx page-idx block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-idx page-idx block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-idx section-idx :page-idx page-idx :block-idx block-idx}])}
                         ($ DeleteIcon {:fontSize "small"})))})

       ($ Collapse {:in expanded :timeout "auto" :unmountOnExit true}
          ($ CardContent {}
             ($ TextField {:fullWidth true
                          :label "PDF URL"
                          :value (or (:url block) "")
                          :onChange #(rf/dispatch [::update-block-field
                                                 section-idx page-idx block-idx
                                                 :url
                                                 (.. % -target -value)])
                          :variant "outlined"
                          :margin "normal"
                           :helperText "URL path to the PDF file"})

             (when (and (str/starts-with? url "https://drive.google.com/file")
                        (str/ends-with? url "/view?usp=sharing"))
               (let [gid (second (re-find #"/file/d/([^/]+)" url))
                     gurl (str "https://docs.google.com/viewer?srcid="
                               gid
                               "&pid=explorer&efh=false&a=v&chrome=false&embedded=true")]
                 ($ Button {:onClick #(rf/dispatch [::update-block-field
                                                    section-idx page-idx block-idx
                                                    :url gurl])}

                    "Fix Google Drive Link")))

             ($ language-tabs {:current-lang lang :on-change set-lang!})

             ($ localized-text-field
                {:label "Title"
                 :value (:title block)
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-idx page-idx block-idx :title %1 %2])})

             ($ localized-text-field
                {:label "Caption"
                 :value (:caption block)
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-idx page-idx block-idx :caption %1 %2])}))))))

(defui type-code-explorer-block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  (let [[expanded set-expanded!] (use-state false)]
    ($ Card {:variant "elevation"
            :elevation 3
            :sx #js{:mb 2
                   :boxShadow (if expanded "0px 6px 10px rgba(0, 0, 0, 0.15)" "")
                   :transition "box-shadow 0.3s ease"}}
       ($ CardHeader
          {:title ($ Typography {:variant "subtitle1" :component "div"}
                    ($ Box {:sx #js{:display "flex" :alignItems "center" :gap 1}}
                       ($ CategoryIcon {:fontSize "small" :color "action" :sx #js{:mr 1}})
                       "Type Code Explorer"))
           :action ($ Box {:sx #js{:display "flex" :gap 0.5}}
                      ;; Expand/collapse button
                      ($ IconButton {:onClick #(set-expanded! (not expanded))
                                    :size "small"
                                    :sx #js{:transform (if expanded "rotate(180deg)" "rotate(0deg)")
                                         :transition "transform 0.3s"}}
                         ($ ExpandMoreIcon {:fontSize "small"}))

                      ;; Move up button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (zero? block-idx)
                                    :onClick #(rf/dispatch [::move-block-up section-idx page-idx block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-idx page-idx block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-idx section-idx :page-idx page-idx :block-idx block-idx}])}
                         ($ DeleteIcon {:fontSize "small"})))})

       ($ Collapse {:in expanded :timeout "auto" :unmountOnExit true}
          ($ CardContent {}
             ;; Type explorer has no editable properties - it just displays the sports facility types.
             ($ Typography {:variant "body2" :color "text.secondary"}
                "This block will display a hierarchical browser for sports facility types. Users can explore main categories, subcategories, and individual facility types."))))))

(defui block-editor [{:keys [section-idx page-idx block-idx blocks-count block]}]
  (case (:type block)
    :text ($ text-block-editor {:section-idx section-idx
                              :page-idx page-idx
                              :block-idx block-idx
                              :blocks-count blocks-count
                              :block block})
    :video ($ video-block-editor {:section-idx section-idx
                                :page-idx page-idx
                                :block-idx block-idx
                                :blocks-count blocks-count
                                :block block})
    :image ($ image-block-editor {:section-idx section-idx
                                :page-idx page-idx
                                :block-idx block-idx
                                :blocks-count blocks-count
                                :block block})
    :pdf ($ pdf-block-editor {:section-idx section-idx
                             :page-idx page-idx
                             :block-idx block-idx
                             :blocks-count blocks-count
                             :block block})
    :type-code-explorer ($ type-code-explorer-block-editor {:section-idx section-idx
                                                          :page-idx page-idx
                                                          :block-idx block-idx
                                                          :blocks-count blocks-count
                                                          :block block})
    ($ Typography {:color "error"} (str "Unknown block type: " (:type block)))))

(defui add-block-controls [{:keys [section-idx page-idx]}]
  ($ Box {:sx #js{:display "flex" :gap 1 :mt 2 :flexWrap "wrap"}}
     ($ Button
        {:variant "outlined"
         :size "small"
         :startIcon ($ TextIcon {})
         :onClick #(rf/dispatch [::add-block section-idx page-idx :text])}
        "Add Text")
     ($ Button
        {:variant "outlined"
         :size "small"
         :startIcon ($ VideoIcon {})
         :onClick #(rf/dispatch [::add-block section-idx page-idx :video])}
        "Add Video")
     ($ Button
        {:variant "outlined"
         :size "small"
         :startIcon ($ ImageIcon {})
         :onClick #(rf/dispatch [::add-block section-idx page-idx :image])}
        "Add Image")
     ($ Button
        {:variant "outlined"
         :size "small"
         :startIcon ($ PdfIcon {})
         :onClick #(rf/dispatch [::add-block section-idx page-idx :pdf])}
        "Add PDF")
     ($ Button
        {:variant "outlined"
         :size "small"
         :color "secondary"
         :startIcon ($ CategoryIcon {})
         :onClick #(rf/dispatch [::add-block section-idx page-idx :type-code-explorer])}
        "Add Type Explorer")))

(defui blocks-editor [{:keys [section-idx page-idx blocks]}]
  ($ Box {}
     ($ Typography {:variant "h6" :gutterBottom true :mt 2}
        "Page Content Blocks")

     (map-indexed
      (fn [idx block]
        ($ block-editor
           {:key (str (:block-id block))
            :section-idx section-idx
            :page-idx page-idx
            :block-idx idx
            :blocks-count (count blocks)
            :block block}))
      blocks)

     ($ add-block-controls {:section-idx section-idx :page-idx page-idx})))

(defui section-selector [{:keys [sections selected-section-idx on-select]}]
  ($ Stack {:spacing 1}

     ($ Typography {:variant "h6"} "Select Section")

     ($ FormControl {:fullWidth true}
        #_($ InputLabel {:id "section-select-label"} "Select Section")
        ($ Select {:labelId "section-select-label"
                  :value (or selected-section-idx "")
                  :onChange #(on-select (js/parseInt (.. % -target -value)))
                  :displayEmpty true}
           (map-indexed
            (fn [idx section]
              ($ MenuItem {:key idx :value idx}
                 (get-in section [:title :fi] (str "Section " idx))))
            sections)))

     ($ Stack {:direction "row" :spacing 1 :flexWrap "wrap"}
        ;; Add Section button
        ($ Button
           {:variant "contained"
            :color "primary"
            :size "small"
            :startIcon ($ AddIcon {})
            :onClick #(rf/dispatch [::add-section])
            :sx #js{:mt 0}}
           "Add Section")

        ;; Delete Section button (disabled if no section is selected)
        ($ Button
           {:variant "outlined"
            :color "error"
            :size "small"
            :disabled (nil? selected-section-idx)
            :startIcon ($ DeleteIcon {})
            :onClick #(rf/dispatch [::show-confirm-dialog :delete-section {:section-idx selected-section-idx}])
            :sx #js{:mt 0}}
           "Delete Section")

        ;; Move Section Up button
        ($ Button
           {:variant "outlined"
            :color "primary"
            :size "small"
            :disabled (or (nil? selected-section-idx) (zero? selected-section-idx))
            :startIcon ($ ArrowUpIcon {})
            :onClick #(rf/dispatch [::move-section-up selected-section-idx])
            :sx #js{:mt 0}}
           "Move Up")

        ;; Move Section Down button
        ($ Button
           {:variant "outlined"
            :color "primary"
            :size "small"
            :disabled (or (nil? selected-section-idx) 
                          (= selected-section-idx (dec (count sections))))
            :startIcon ($ ArrowDownIcon {})
            :onClick #(rf/dispatch [::move-section-down selected-section-idx])
            :sx #js{:mt 0}}
           "Move Down"))))

(defui page-selector [{:keys [section-idx pages selected-page-idx on-select]}]
  ($ Stack {:spacing 2}

     ($ Typography {:variant "h6"} "Select Page")

     ($ FormControl {:fullWidth true}
        #_($ InputLabel {:id "page-select-label"} "Select Page")
        ($ Select {:labelId "page-select-label"
                   :value (or selected-page-idx "")
                   :onChange #(on-select (js/parseInt (.. % -target -value)))
                   :displayEmpty true}
           (map-indexed
            (fn [idx page]
              ($ MenuItem {:key idx :value idx}
                 (get-in page [:title :fi] (str "Page " idx))))
            pages)))

     ($ Stack {:direction "row" :spacing 1 :flexWrap "wrap"}

        ;; Add Page button
        ($ Button
           {:variant "contained"
            :color "primary"
            :size "small"
            :startIcon ($ AddIcon {})
            :onClick #(rf/dispatch [::add-page section-idx])
            :sx #js{:mt 0}}
           "Add Page")

        ;; Delete Page button (disabled if no page is selected)
        ($ Button
           {:variant "outlined"
            :color "error"
            :size "small"
            :disabled (nil? selected-page-idx)
            :startIcon ($ DeleteIcon {})
            :onClick #(rf/dispatch [::show-confirm-dialog :delete-page {:section-idx section-idx
                                                                        :page-idx selected-page-idx}])
            :sx #js{:mt 0}}
           "Delete Page")

        ;; Move Page Up button
        ($ Button
           {:variant "outlined"
            :color "primary"
            :size "small"
            :disabled (or (nil? selected-page-idx) (zero? selected-page-idx))
            :startIcon ($ ArrowUpIcon {})
            :onClick #(rf/dispatch [::move-page-up section-idx selected-page-idx])
            :sx #js{:mt 0}}
           "Move Up")

        ;; Move Page Down button
        ($ Button
           {:variant "outlined"
            :color "primary"
            :size "small"
            :disabled (or (nil? selected-page-idx)
                          (= selected-page-idx (dec (count pages))))
            :startIcon ($ ArrowDownIcon {})
            :onClick #(rf/dispatch [::move-page-down section-idx selected-page-idx])
            :sx #js{:mt 0}}
           "Move Down"))))

(defui editor-toolbar []
  ($ Toolbar {:disableGutters true :sx #js{:mb 2}}
     ($ Typography {:variant "h5" :component "div" :sx #js{:flexGrow 1}}
        "Help Content Editor")

     ($ Stack {:direction "row" :spacing 1}
        ($ Button
           {:variant "contained"
            :color "primary"
            :startIcon ($ PreviewIcon {})
            :onClick #(rf/dispatch [::apply-changes])}
           "Preview")
        ($ Button
           {:variant "contained"
            :color "secondary"
            :startIcon ($ SaveIcon {})
            :onClick #(rf/dispatch [::save-changes])}
           "Save")
        ($ Button
           {:variant "outlined"
            :color "secondary"
            :sx #js{:ml 1}
            :onClick #(rf/dispatch [::events/close-edit-mode])}
           "Cancel"))))

(defui confirmation-dialog []
  (let [dialog (use-subscribe [::confirm-dialog])
        dialog-type (:type dialog)
        params (:params dialog)
        section-idx (:section-idx params)
        page-idx (:page-idx params)
        block-idx (:block-idx params)

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

    ($ Dialog
       {:open (:open? dialog)
        :onClose #(rf/dispatch [::hide-confirm-dialog])
        :aria-labelledby "confirm-dialog-title"}

       ($ DialogTitle
          {:id "confirm-dialog-title"}
          (get-title))

       ($ DialogContent {}
          ($ DialogContentText {}
             (get-message)))

       ($ DialogActions {}
          ($ Button
             {:onClick #(rf/dispatch [::hide-confirm-dialog])
              :color "primary"}
             "Cancel")
          ($ Button
             {:onClick #(rf/dispatch [::confirm-action])
              :color "error"
              :variant "contained"
              :autoFocus true}
             "Delete")))))

(defui view
  []
  (let [edit-data (use-subscribe [::edited-help-data])
        selected-section-idx (use-subscribe [::subs/selected-section-idx])
        selected-page-idx (use-subscribe [::subs/selected-page-idx])

        selected-section (when (and edit-data (number? selected-section-idx)
                                  (< selected-section-idx (count edit-data)))
                           (nth edit-data selected-section-idx))
        selected-pages (when selected-section
                         (:pages selected-section))
        selected-page (when (and selected-pages (number? selected-page-idx)
                                (< selected-page-idx (count selected-pages)))
                        (nth selected-pages selected-page-idx))]

    ($ Box {:sx #js{:p 2}}
       ;; Confirmation dialog always rendered but only shown when needed
       ($ confirmation-dialog {})
       ($ editor-toolbar {})

       ($ section-selector
          {:sections edit-data
           :selected-section-idx selected-section-idx
           :on-select #(rf/dispatch [::events/select-section % (get-in (nth edit-data %) [:slug])])})

       (when selected-section
         ($ section-editor {:section-idx selected-section-idx :section selected-section}))

       (when selected-section
         ($ page-selector
            {:section-idx selected-section-idx
             :pages selected-pages
             :selected-page-idx selected-page-idx
             :on-select #(rf/dispatch [::events/select-page % (get-in (nth selected-pages %) [:slug])])}))

       (when (and selected-section selected-page)
         ($ page-editor
            {:section-idx selected-section-idx
             :page-idx selected-page-idx
             :page selected-page}))

       (when (and selected-section selected-page)
         ($ blocks-editor
            {:section-idx selected-section-idx
             :page-idx selected-page-idx
             :blocks (:blocks selected-page)})))))
