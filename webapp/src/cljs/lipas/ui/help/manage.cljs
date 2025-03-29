(ns lipas.ui.help.manage
  (:require
   ["@mui/icons-material/Add$default" :as AddIcon]
   ["@mui/icons-material/ArrowDownward$default" :as ArrowDownIcon]
   ["@mui/icons-material/ArrowUpward$default" :as ArrowUpIcon]
   ["@mui/icons-material/Delete$default" :as DeleteIcon]
   ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
   ["@mui/icons-material/Image$default" :as ImageIcon]
   ["@mui/icons-material/Preview$default" :as PreviewIcon]
   ["@mui/icons-material/Save$default" :as SaveIcon]
   ["@mui/icons-material/TextFields$default" :as TextIcon]
   ["@mui/icons-material/VideoLibrary$default" :as VideoIcon]
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
 ::update-help-data
 (fn [db [_ updated-data]]
   (assoc-in db [:help :edited-data] updated-data)))

(rf/reg-event-db
 ::update-section-title
 (fn [db [_ section-key lang value]]
   (assoc-in db [:help :edited-data section-key :title lang] value)))

(rf/reg-event-db
 ::update-page-title
 (fn [db [_ section-key page-key lang value]]
   (assoc-in db [:help :edited-data section-key :pages page-key :title lang] value)))

(rf/reg-event-db
 ::update-block-content
 (fn [db [_ section-key page-key block-idx field lang value]]
   (assoc-in db [:help :edited-data section-key :pages page-key :blocks block-idx field lang] value)))

(rf/reg-event-db
 ::update-block-field
 (fn [db [_ section-key page-key block-idx field value]]
   (assoc-in db [:help :edited-data section-key :pages page-key :blocks block-idx field] value)))

(rf/reg-event-db
 ::add-block
 (fn [db [_ section-key page-key block-type]]
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
                                  :caption {:fi "" :en "" :se ""}))]
     (update-in db [:help :edited-data section-key :pages page-key :blocks] conj new-block))))


(rf/reg-event-db
 ::delete-block
 (fn [db [_ section-key page-key block-idx]]
   (update-in db [:help :edited-data section-key :pages page-key :blocks]
              (fn [blocks]
                (vec (concat
                       (subvec blocks 0 block-idx)
                       (subvec blocks (inc block-idx))))))))

(rf/reg-event-db
 ::move-block-up
 (fn [db [_ section-key page-key block-idx]]
   (if (zero? block-idx)
     db ; Already at the top, no change
     (update-in db [:help :edited-data section-key :pages page-key :blocks]
                (fn [blocks]
                  (let [block (get blocks block-idx)
                        prev-block (get blocks (dec block-idx))]
                    (-> blocks
                        (assoc (dec block-idx) block)
                        (assoc block-idx prev-block))))))))

(rf/reg-event-db
 ::move-block-down
 (fn [db [_ section-key page-key block-idx]]
   (let [blocks (get-in db [:help :edited-data section-key :pages page-key :blocks])
         last-idx (dec (count blocks))]
     (if (= block-idx last-idx)
       db ; Already at the bottom, no change
       (update-in db [:help :edited-data section-key :pages page-key :blocks]
                  (fn [blocks]
                    (let [block (get blocks block-idx)
                          next-block (get blocks (inc block-idx))]
                      (-> blocks
                          (assoc block-idx next-block)
                          (assoc (inc block-idx) block)))))))))

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
 (fn [db [_ section-key]]
   (let [default-title "New Page"
         ;; Create a basic slug from the title
         base-slug (create-slug default-title)
         ;; Add timestamp to ensure uniqueness
         timestamp (.now js/Date)
         new-page-key (keyword base-slug)
         ;; Create a basic page structure
         new-page {:title {:fi default-title :en default-title :se "Ny sida"}
                   :blocks []}
         ;; Ensure key is unique within the section
         existing-pages (get-in db [:help :edited-data section-key :pages])
         final-page-key (if (contains? existing-pages new-page-key)
                          (keyword (str base-slug "-" timestamp))
                          new-page-key)]
     ;; Add the new page to the appropriate section
     (-> db
         (assoc-in [:help :edited-data section-key :pages final-page-key] new-page)
         ;; Select the new page
         (assoc-in [:help :dialog :selected-page] final-page-key)))))

(rf/reg-event-db
 ::delete-page
 (fn [db [_ section-key page-key]]
   (let [pages (get-in db [:help :edited-data section-key :pages])
         selected-page (get-in db [:help :dialog :selected-page])
         ;; If we're deleting the currently selected page, select the first available page
         new-selected-page (if (= selected-page page-key)
                             (when (> (count pages) 1)
                               (first (keys (dissoc pages page-key))))
                             selected-page)]
     (-> db
         ;; Remove the page from the section
         (update-in [:help :edited-data section-key :pages] dissoc page-key)
         ;; Update the selected page if needed
         (assoc-in [:help :dialog :selected-page] new-selected-page)))))

(rf/reg-event-db
 ::add-section
 (fn [db _]
   (let [default-title "New Section"
         ;; Create a basic slug from the title
         base-slug (create-slug default-title)
         ;; Add timestamp to ensure uniqueness
         timestamp (.now js/Date)
         new-section-key (keyword base-slug)
         ;; Create a basic section structure with one default page
         new-page-key (keyword "welcome")
         new-section {:title {:fi default-title :en default-title :se "Ny sektion"}
                      :pages {new-page-key {:title {:fi "Welcome" :en "Welcome" :se "Välkommen"}
                                           :blocks []}}}
         ;; Ensure key is unique
         existing-sections (get-in db [:help :edited-data])
         final-section-key (if (contains? existing-sections new-section-key)
                             (keyword (str base-slug "-" timestamp))
                             new-section-key)]
     ;; Add the new section
     (-> db
         (assoc-in [:help :edited-data final-section-key] new-section)
         ;; Select the new section and its first page
         (assoc-in [:help :dialog :selected-section] final-section-key)
         (assoc-in [:help :dialog :selected-page] new-page-key)))))

(rf/reg-event-db
 ::delete-section
 (fn [db [_ section-key]]
   (let [sections (get-in db [:help :edited-data])
         selected-section (get-in db [:help :dialog :selected-section])
         ;; If we're deleting the currently selected section, select the first available section
         new-selected-section (if (= selected-section section-key)
                                (when (> (count sections) 1)
                                  (first (keys (dissoc sections section-key))))
                                selected-section)]
     (-> db
         ;; Remove the section
         (update-in [:help :edited-data] dissoc section-key)
         ;; Update the selected section if needed
         (assoc-in [:help :dialog :selected-section] new-selected-section)
         ;; Clear page selection if we deleted the selected section
         ((fn [updated-db]
            (if (= selected-section section-key)
              (assoc-in updated-db [:help :dialog :selected-page] nil)
              updated-db)))))))

(rf/reg-event-db
 ::apply-changes
 (fn [db _]
   (-> db
       (assoc-in [:help :data] (get-in db [:help :edited-data]))
       (assoc-in [:help :dialog :mode] :read))))

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
                        :delete-section [::delete-section (:section-key params)]
                        :delete-page [::delete-page (:section-key params) (:page-key params)]
                        :delete-block [::delete-block (:section-key params) (:page-key params) (:block-idx params)])]]})))

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

(defui section-editor [{:keys [section-key section]}]
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
                    :on-change #(rf/dispatch [::update-section-title section-key %1 %2])})))))))

(defui page-editor [{:keys [section-key page-key page]}]
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
                    :on-change #(rf/dispatch [::update-page-title section-key page-key %1 %2])})))))))

(defui text-block-editor [{:keys [section-key page-key block-idx blocks-count block]}]
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
                                    :onClick #(rf/dispatch [::move-block-up section-key page-key block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-key page-key block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-key section-key :page-key page-key :block-idx block-idx}])}
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
                 :on-change #(rf/dispatch [::update-block-content section-key page-key block-idx :content %1 %2])}))))))

(defui video-block-editor [{:keys [section-key page-key block-idx blocks-count block]}]
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
                                    :onClick #(rf/dispatch [::move-block-up section-key page-key block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-key page-key block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-key section-key :page-key page-key :block-idx block-idx}])}
                         ($ DeleteIcon {:fontSize "small"})))})

       ($ Collapse {:in expanded :timeout "auto" :unmountOnExit true}
          ($ CardContent {}
             ($ FormControl {:fullWidth true :margin "normal"}
                ($ InputLabel {:id "video-provider-label"} "Provider")
                ($ Select {:labelId "video-provider-label"
                          :value (or (:provider block) :youtube)
                          :onChange #(rf/dispatch [::update-block-field
                                                 section-key page-key block-idx
                                                 :provider
                                                 (keyword (.. % -target -value))])}
                   ($ MenuItem {:value "youtube"} "YouTube")
                   ($ MenuItem {:value "vimeo"} "Vimeo")))

             ($ TextField {:fullWidth true
                          :label "Video ID"
                          :value (or (:video-id block) "")
                          :onChange #(rf/dispatch [::update-block-field
                                                 section-key page-key block-idx
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
                 :on-change #(rf/dispatch [::update-block-content section-key page-key block-idx :title %1 %2])}))))))

(defui image-block-editor [{:keys [section-key page-key block-idx blocks-count block]}]
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
                                    :onClick #(rf/dispatch [::move-block-up section-key page-key block-idx])}
                         ($ ArrowUpIcon {:fontSize "small"}))

                      ;; Move down button
                      ($ IconButton {:color "primary"
                                    :size "small"
                                    :disabled (= block-idx (dec blocks-count))
                                    :onClick #(rf/dispatch [::move-block-down section-key page-key block-idx])}
                         ($ ArrowDownIcon {:fontSize "small"}))

                      ;; Delete button
                      ($ IconButton {:color "error"
                                    :size "small"
                                    :onClick #(rf/dispatch [::show-confirm-dialog :delete-block {:section-key section-key :page-key page-key :block-idx block-idx}])}
                         ($ DeleteIcon {:fontSize "small"})))})

       ($ Collapse {:in expanded :timeout "auto" :unmountOnExit true}
          ($ CardContent {}
             ($ TextField {:fullWidth true
                          :label "Image URL"
                          :value (or (:url block) "")
                          :onChange #(rf/dispatch [::update-block-field
                                                 section-key page-key block-idx
                                                 :url
                                                 (.. % -target -value)])
                          :variant "outlined"
                          :margin "normal"})

             ($ language-tabs {:current-lang lang :on-change set-lang!})

             ($ localized-text-field
                {:label "Alt Text"
                 :value (:alt block)
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-key page-key block-idx :alt %1 %2])})

             ($ localized-text-field
                {:label "Caption"
                 :value (:caption block)
                 :lang lang
                 :on-change #(rf/dispatch [::update-block-content section-key page-key block-idx :caption %1 %2])}))))))

(defui block-editor [{:keys [section-key page-key block-idx blocks-count block]}]
  (case (:type block)
    :text ($ text-block-editor {:section-key section-key
                              :page-key page-key
                              :block-idx block-idx
                              :blocks-count blocks-count
                              :block block})
    :video ($ video-block-editor {:section-key section-key
                                :page-key page-key
                                :block-idx block-idx
                                :blocks-count blocks-count
                                :block block})
    :image ($ image-block-editor {:section-key section-key
                                :page-key page-key
                                :block-idx block-idx
                                :blocks-count blocks-count
                                :block block})
    ($ Typography {:color "error"} (str "Unknown block type: " (:type block)))))

(defui add-block-controls [{:keys [section-key page-key]}]
  ($ Box {:sx #js{:display "flex" :gap 1 :mt 2}}
     ($ Button
        {:variant "outlined"
         :size "small"
         :startIcon ($ TextIcon {})
         :onClick #(rf/dispatch [::add-block section-key page-key :text])}
        "Add Text")
     ($ Button
        {:variant "outlined"
         :size "small"
         :startIcon ($ VideoIcon {})
         :onClick #(rf/dispatch [::add-block section-key page-key :video])}
        "Add Video")
     ($ Button
        {:variant "outlined"
         :size "small"
         :startIcon ($ ImageIcon {})
         :onClick #(rf/dispatch [::add-block section-key page-key :image])}
        "Add Image")))

(defui blocks-editor [{:keys [section-key page-key blocks]}]
  ($ Box {}
     ($ Typography {:variant "h6" :gutterBottom true :mt 2}
        "Page Content Blocks")

     (map-indexed
      (fn [idx block]
        ($ block-editor
           {:key (str (:block-id block))
            :section-key section-key
            :page-key page-key
            :block-idx idx
            :blocks-count (count blocks)
            :block block}))
      blocks)

     ($ add-block-controls {:section-key section-key :page-key page-key})))

(defui section-selector [{:keys [sections selected-section on-select]}]
  ($ Stack {:spacing 1}

     ($ Typography {:variant "h6"} "Select Section")

     ($ FormControl {:fullWidth true}
        #_($ InputLabel {:id "section-select-label"} "Select Section")
        ($ Select {:labelId "section-select-label"
                  :value (or selected-section "")
                  :onChange #(on-select (keyword (.. % -target -value)))
                  :displayEmpty true}
           (map (fn [[k v]]
                  ($ MenuItem {:key (name k) :value (name k)}
                     (get-in v [:title :fi] (name k))))
                sections)))

     ($ Stack {:direction "row" :spacing 1}
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
            :disabled (nil? selected-section)
            :startIcon ($ DeleteIcon {})
            :onClick #(rf/dispatch [::show-confirm-dialog :delete-section {:section-key selected-section}])
            :sx #js{:mt 0}}
           "Delete Section"))))

(defui page-selector [{:keys [section-key pages selected-page on-select]}]
  ($ Stack {:spacing 2}

     ($ Typography {:variant "h6"} "Select Page")

     ($ FormControl {:fullWidth true}
        #_($ InputLabel {:id "page-select-label"} "Select Page")
        ($ Select {:labelId "page-select-label"
                   :value (or (when selected-page (name selected-page)) "")
                   :onChange #(on-select (keyword (.. % -target -value)))
                   :displayEmpty true}
           (map (fn [[k v]]
                  ($ MenuItem {:key (name k) :value (name k)}
                     (get-in v [:title :fi] (name k))))
                pages)))

     ($ Stack {:direction "row" :spacing 1}

        ;; Add Page button
        ($ Button
           {:variant "contained"
            :color "primary"
            :size "small"
            :startIcon ($ AddIcon {})
            :onClick #(rf/dispatch [::add-page section-key])
            :sx #js{:mt 0}}
           "Add Page")

        ;; Delete Page button (disabled if no page is selected)
        ($ Button
           {:variant "outlined"
            :color "error"
            :size "small"
            :disabled (nil? selected-page)
            :startIcon ($ DeleteIcon {})
            :onClick #(rf/dispatch [::show-confirm-dialog :delete-page {:section-key section-key
                                                                        :page-key selected-page}])
            :sx #js{:mt 0}}
           "Delete Page"))))

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
            :onClick #(rf/dispatch [::apply-changes])}
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
        section-key (:section-key params)
        page-key (:page-key params)
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
        selected-section-key (use-subscribe [::subs/selected-section])
        selected-page-key (use-subscribe [::subs/selected-page])

        selected-section (get edit-data selected-section-key)
        selected-page (when (and selected-section-key selected-page-key)
                        (get-in edit-data [selected-section-key :pages selected-page-key]))]

    ($ Box {:sx #js{:p 2}}
       ;; Confirmation dialog always rendered but only shown when needed
       ($ confirmation-dialog {})
       ($ editor-toolbar {})

       ($ section-selector
          {:sections edit-data
           :selected-section selected-section-key
           :on-select #(rf/dispatch [::events/select-section %])})

       (when selected-section-key
         ($ section-editor {:section-key selected-section-key :section selected-section}))

       (when selected-section-key
         ($ page-selector
            {:section-key selected-section-key
             :pages (get-in edit-data [selected-section-key :pages])
             :selected-page selected-page-key
             :on-select #(rf/dispatch [::events/select-page %])}))

       (when (and selected-section-key selected-page-key)
         ($ page-editor
            {:section-key selected-section-key
             :page-key selected-page-key
             :page selected-page}))

       (when (and selected-section-key selected-page-key)
         ($ blocks-editor
            {:section-key selected-section-key
             :page-key selected-page-key
             :blocks (:blocks selected-page)})))))
