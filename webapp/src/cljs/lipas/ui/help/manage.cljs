(ns lipas.ui.help.manage
  (:require
   ["@mui/icons-material/Add$default" :as AddIcon]
   ["@mui/icons-material/ArrowDownward$default" :as ArrowDownIcon]
   ["@mui/icons-material/ArrowUpward$default" :as ArrowUpIcon]
   ["@mui/icons-material/Delete$default" :as DeleteIcon]
   ["@mui/icons-material/Edit$default" :as EditIcon]
   ["@mui/icons-material/Image$default" :as ImageIcon]
   ["@mui/icons-material/Save$default" :as SaveIcon]
   ["@mui/icons-material/TextFields$default" :as TextIcon]
   ["@mui/icons-material/VideoLibrary$default" :as VideoIcon]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/Button$default" :as Button]
   ["@mui/material/Card$default" :as Card]
   ["@mui/material/CardActions$default" :as CardActions]
   ["@mui/material/CardContent$default" :as CardContent]
   ["@mui/material/Divider$default" :as Divider]
   ["@mui/material/FormControl$default" :as FormControl]
   ["@mui/material/FormLabel$default" :as FormLabel]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/material/InputLabel$default" :as InputLabel]
   ["@mui/material/MenuItem$default" :as MenuItem]
   ["@mui/material/Paper$default" :as Paper]
   ["@mui/material/Select$default" :as Select]
   ["@mui/material/Tab$default" :as Tab]
   ["@mui/material/Tabs$default" :as Tabs]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/Toolbar$default" :as Toolbar]
   ["@mui/material/Typography$default" :as Typography]
   [clojure.string :as str]
   [lipas.ui.components :as components]
   [lipas.ui.help.events :as events]
   [lipas.ui.help.subs :as subs]
   [lipas.ui.uix.hooks :refer [use-subscribe]]
   [lipas.ui.utils :as utils :refer [==>]]
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
  (let [[lang set-lang!] (use-state :fi)]
    ($ Box {:sx {:p 2}}
       ($ Paper {:sx {:p 2 :mb 2}}
          ($ Typography {:variant "h6" :gutterBottom true}
             "Section Settings")

          ($ language-tabs {:current-lang lang :on-change set-lang!})

          ($ localized-text-field
             {:label "Section Title"
              :value (:title section)
              :lang lang
              :on-change #(rf/dispatch [::update-section-title section-key %1 %2])})))))

(defui page-editor [{:keys [section-key page-key page]}]
  (let [[lang set-lang!] (use-state :fi)]
    ($ Box {:sx {:p 2}}
       ($ Paper {:sx {:p 2 :mb 2}}
          ($ Typography {:variant "h6" :gutterBottom true}
             "Page Settings")

          ($ language-tabs {:current-lang lang :on-change set-lang!})

          ($ localized-text-field
             {:label "Page Title"
              :value (:title page)
              :lang lang
              :on-change #(rf/dispatch [::update-page-title section-key page-key %1 %2])})))))

(defui text-block-editor [{:keys [section-key page-key block-idx blocks-count block]}]
  (let [[lang set-lang!] (use-state :fi)]
    ($ Card {:variant "outlined" :sx {:mb 2}}
       ($ CardContent {}
          ($ Box {:sx {:display "flex" :justifyContent "space-between" :mb 1}}
             ($ Typography {:variant "subtitle1" :color "primary"}
                "Text Block")
             ($ Box {:sx {:display "flex" :gap 0.5}}
                ;; Move up button - disabled if first block
                ($ IconButton {:color "primary" 
                              :size "small"
                              :disabled (zero? block-idx)
                              :onClick #(rf/dispatch [::move-block-up section-key page-key block-idx])}
                   ($ ArrowUpIcon {:fontSize "small"}))
                   
                ;; Move down button - disabled if last block
                ($ IconButton {:color "primary" 
                              :size "small"
                              :disabled (= block-idx (dec blocks-count))
                              :onClick #(rf/dispatch [::move-block-down section-key page-key block-idx])}
                   ($ ArrowDownIcon {:fontSize "small"}))
                   
                ;; Delete button
                ($ IconButton {:color "error" 
                              :size "small"
                              :onClick #(rf/dispatch [::delete-block section-key page-key block-idx])}
                   ($ DeleteIcon {:fontSize "small"}))))

          ($ language-tabs {:current-lang lang :on-change set-lang!})

          ($ localized-text-field
             {:label "Content"
              :value (:content block)
              :multiline true
              :rows 6
              :lang lang
              :on-change #(rf/dispatch [::update-block-content section-key page-key block-idx :content %1 %2])})))))

(defui video-block-editor [{:keys [section-key page-key block-idx blocks-count block]}]
  (let [[lang set-lang!] (use-state :fi)]
    ($ Card {:variant "outlined" :sx {:mb 2}}
       ($ CardContent {}
          ($ Box {:sx {:display "flex" :justifyContent "space-between" :mb 1}}
             ($ Typography {:variant "subtitle1" :color "primary"}
                "Video Block")
             ($ Box {:sx {:display "flex" :gap 0.5}}
                ;; Move up button - disabled if first block
                ($ IconButton {:color "primary" 
                              :size "small"
                              :disabled (zero? block-idx)
                              :onClick #(rf/dispatch [::move-block-up section-key page-key block-idx])}
                   ($ ArrowUpIcon {:fontSize "small"}))
                   
                ;; Move down button - disabled if last block
                ($ IconButton {:color "primary" 
                              :size "small"
                              :disabled (= block-idx (dec blocks-count))
                              :onClick #(rf/dispatch [::move-block-down section-key page-key block-idx])}
                   ($ ArrowDownIcon {:fontSize "small"}))
                   
                ;; Delete button
                ($ IconButton {:color "error" 
                              :size "small"
                              :onClick #(rf/dispatch [::delete-block section-key page-key block-idx])}
                   ($ DeleteIcon {:fontSize "small"}))))

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
              :on-change #(rf/dispatch [::update-block-content section-key page-key block-idx :title %1 %2])})))))

(defui image-block-editor [{:keys [section-key page-key block-idx blocks-count block]}]
  (let [[lang set-lang!] (use-state :fi)]
    ($ Card {:variant "outlined" :sx {:mb 2}}
       ($ CardContent {}
          ($ Box {:sx {:display "flex" :justifyContent "space-between" :mb 1}}
             ($ Typography {:variant "subtitle1" :color "primary"}
                "Image Block")
             ($ Box {:sx {:display "flex" :gap 0.5}}
                ;; Move up button - disabled if first block
                ($ IconButton {:color "primary" 
                              :size "small"
                              :disabled (zero? block-idx)
                              :onClick #(rf/dispatch [::move-block-up section-key page-key block-idx])}
                   ($ ArrowUpIcon {:fontSize "small"}))
                   
                ;; Move down button - disabled if last block
                ($ IconButton {:color "primary" 
                              :size "small"
                              :disabled (= block-idx (dec blocks-count))
                              :onClick #(rf/dispatch [::move-block-down section-key page-key block-idx])}
                   ($ ArrowDownIcon {:fontSize "small"}))
                   
                ;; Delete button
                ($ IconButton {:color "error" 
                              :size "small"
                              :onClick #(rf/dispatch [::delete-block section-key page-key block-idx])}
                   ($ DeleteIcon {:fontSize "small"}))))

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
              :on-change #(rf/dispatch [::update-block-content section-key page-key block-idx :caption %1 %2])})))))

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
  ($ Box {:sx {:display "flex" :gap 1 :mt 2}}
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
        "Content Blocks")

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
  ($ FormControl {:fullWidth true :sx {:mb 2}}
     ($ InputLabel {:id "section-select-label"} "Section")
     ($ Select {:labelId "section-select-label"
               :value (or selected-section "")
               :onChange #(on-select (keyword (.. % -target -value)))
               :displayEmpty true}
        (map (fn [[k v]]
               ($ MenuItem {:key (name k) :value (name k)}
                  (get-in v [:title :fi] (name k))))
             sections))))

(defui page-selector [{:keys [pages selected-page on-select]}]
  ($ FormControl {:fullWidth true :sx {:mb 2}}
     ($ InputLabel {:id "page-select-label"} "Page")
     ($ Select {:labelId "page-select-label"
               :value (or (when selected-page (name selected-page)) "")
               :onChange #(on-select (keyword (.. % -target -value)))
               :displayEmpty true}
        (map (fn [[k v]]
               ($ MenuItem {:key (name k) :value (name k)}
                  (get-in v [:title :fi] (name k))))
             pages))))

(defui editor-toolbar []
  ($ Toolbar {:disableGutters true :sx {:mb 2}}
     ($ Typography {:variant "h5" :component "div" :sx {:flexGrow 1}}
        "Help Content Editor")
     ($ Button
        {:variant "contained"
         :color "primary"
         :startIcon ($ SaveIcon {})
         :onClick #(rf/dispatch [::apply-changes])}
        "Save Changes")
     ($ Button
        {:variant "outlined"
         :color "secondary"
         :sx {:ml 1}
         :onClick #(rf/dispatch [::events/close-edit-mode])}
        "Cancel")))

(defui view
  []
  (let [edit-data (use-subscribe [::edited-help-data])
        selected-section-key (use-subscribe [::subs/selected-section])
        selected-page-key (use-subscribe [::subs/selected-page])

        selected-section (get edit-data selected-section-key)
        selected-page (when (and selected-section-key selected-page-key)
                        (get-in edit-data [selected-section-key :pages selected-page-key]))]

    ($ Box {:sx {:p 2}}
       ($ editor-toolbar {})

       ($ section-selector
          {:sections edit-data
           :selected-section selected-section-key
           :on-select #(rf/dispatch [::events/select-section %])})

       (when selected-section-key
         ($ section-editor {:section-key selected-section-key :section selected-section}))

       (when selected-section-key
         ($ page-selector
            {:pages (get-in edit-data [selected-section-key :pages])
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
