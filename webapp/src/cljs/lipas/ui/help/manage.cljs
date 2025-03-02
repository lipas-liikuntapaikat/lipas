(ns lipas.ui.help.manage
  (:require
   ["@mui/icons-material/Add$default" :as AddIcon]
   ["@mui/icons-material/ArrowBack$default" :as ArrowBackIcon]
   ["@mui/icons-material/Delete$default" :as DeleteIcon]
   ["@mui/icons-material/Save$default" :as SaveIcon]
   ["@mui/material/AppBar$default" :as AppBar]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/Button$default" :as Button]
   ["@mui/material/Card$default" :as Card]
   ["@mui/material/CardActions$default" :as CardActions]
   ["@mui/material/CardContent$default" :as CardContent]
   ["@mui/material/Container$default" :as Container]
   ["@mui/material/Dialog$default" :as Dialog]
   ["@mui/material/DialogActions$default" :as DialogActions]
   ["@mui/material/DialogContent$default" :as DialogContent]
   ["@mui/material/DialogTitle$default" :as DialogTitle]
   ["@mui/material/Divider$default" :as Divider]
   ["@mui/material/Grid$default" :as Grid]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/material/List$default" :as List]
   ["@mui/material/ListItem$default" :as ListItem]
   ["@mui/material/ListItemText$default" :as ListItemText]
   ["@mui/material/Tab$default" :as Tab]
   ["@mui/material/Tabs$default" :as Tabs]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/Toolbar$default" :as Toolbar]
   ["@mui/material/Typography$default" :as Typography]
   [lipas.ui.help.subs :as subs]
   [lipas.ui.uix.hooks :refer [use-subscribe]]
   [lipas.ui.utils :as utils :refer [==>]]
   [re-frame.core :as rf]
   [uix.core :as uix :refer [$ defui]]))

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
 ::apply-changes
 (fn [db _]
   (assoc-in db [:help :data] (get-in db [:help :edited-data]))))

(rf/reg-sub
 ::edited-help-data
 :<- [::subs/help]
 (fn [help _]
   (:edited-data help)))

;; Helper UI Components
(defui TextFieldMultilingual
  [{:keys [label value onChange]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])]
    ($ Box {:sx #js{:mb 2}}
       ($ Typography {:variant "subtitle2" :gutterBottom true} label)
       ($ Grid {:container true :spacing 2}
          ($ Grid {:item true :xs 12 :md 4}
             ($ TextField {:fullWidth true
                           :label (tr :language/fi)
                           :value (or (:fi value) "")
                           :onChange #(onChange (assoc value :fi (.. % -target -value)))}))
          ($ Grid {:item true :xs 12 :md 4}
             ($ TextField {:fullWidth true
                           :label (tr :language/en)
                           :value (or (:en value) "")
                           :onChange #(onChange (assoc value :en (.. % -target -value)))}))
          ($ Grid {:item true :xs 12 :md 4}
             ($ TextField {:fullWidth true
                           :label (tr :language/se)
                           :value (or (:se value) "")
                           :onChange #(onChange (assoc value :se (.. % -target -value)))}))))))

(defui VideosList
  [{:keys [videos onChange]}]
  (let [[newVideo setNewVideo] (uix/use-state "")]
    ($ Box {:sx #js{:mb 2}}
       ($ Typography {:variant "subtitle2" :gutterBottom true} "Videos")
       ($ List {:sx #js{:width "100%" :bgcolor "background.paper"}}
          (for [[idx url] (map-indexed vector videos)]
            ($ ListItem
               {:key idx
                :secondaryAction
                ($ IconButton
                   {:edge "end"
                    :aria-label "delete"
                    :onClick #(onChange (vec (concat
                                             (subvec videos 0 idx)
                                             (subvec videos (inc idx)))))}
                   ($ DeleteIcon))}
               ($ ListItemText {:primary url}))))

       ($ Box {:sx #js{:display "flex" :mt 2}}
          ($ TextField
             {:fullWidth true
              :value newVideo
              :placeholder "https://www.youtube.com/embed/..."
              :onChange #(setNewVideo (.. % -target -value))})
          ($ Button
             {:variant "contained"
              :color "primary"
              :sx #js{:ml 2}
              :startIcon ($ AddIcon)
              :onClick #(do
                          (when (not-empty newVideo)
                            (onChange (conj (or videos []) newVideo))
                            (setNewVideo "")))}
             "Add")))))

(defui PageEditor
  [{:keys [page onChange onDelete]}]
  (let [{:keys [title text videos]} page]
    ($ Card {:variant "outlined" :sx #js{:mb 3}}
       ($ CardContent
          ($ TextFieldMultilingual {:label "Title" :value title :onChange #(onChange (assoc page :title %))})
          ($ TextFieldMultilingual {:label "Content" :value text :onChange #(onChange (assoc page :text %))})
          ($ VideosList {:videos (or videos []) :onChange #(onChange (assoc page :videos %))}))
       ($ CardActions
          ($ Button
             {:color "error"
              :variant "outlined"
              :startIcon ($ DeleteIcon)
              :onClick onDelete}
             "Delete Page")))))

(defui SectionEditor
  [{:keys [section sectionKey onChange onDeletePage onAddPage]}]
  (let [{:keys [title pages]} section]
    ($ Box
       ($ TextFieldMultilingual
          {:label "Section Title"
           :value title
           :onChange #(onChange sectionKey (assoc section :title %))})

       ($ Typography {:variant "h6" :sx #js{:mt 3 :mb 2}} "Pages")

       (for [[pageKey pageData] pages]
         ($ PageEditor
            {:key pageKey
             :page pageData
             :onChange #(onChange sectionKey (assoc-in section [:pages pageKey] %))
             :onDelete #(onDeletePage sectionKey pageKey)}))

       ($ Button
          {:variant "contained"
           :color "primary"
           :startIcon ($ AddIcon)
           :onClick #(onAddPage sectionKey)}
          "Add Page"))))

(defui AddSectionDialog
  [{:keys [open onClose onAdd]}]
  (let [[newSectionKey setNewSectionKey] (uix/use-state "")
        [newSectionTitle setNewSectionTitle] (uix/use-state {:fi "" :en "" :se ""})]

    ($ Dialog {:open open :onClose onClose}
       ($ DialogTitle "Add New Section")
       ($ DialogContent
          ($ TextField
             {:autoFocus true
              :margin "dense"
              :id "section-key"
              :label "Section ID (keyword)"
              :fullWidth true
              :variant "standard"
              :value newSectionKey
              :onChange #(setNewSectionKey (.. % -target -value))})
          ($ TextFieldMultilingual
             {:label "Section Title"
              :value newSectionTitle
              :onChange setNewSectionTitle}))
       ($ DialogActions
          ($ Button {:onClick onClose} "Cancel")
          ($ Button
             {:onClick #(do
                         (when (and (not-empty newSectionKey)
                                   (not-empty (:fi newSectionTitle)))
                           (onAdd
                            (keyword newSectionKey)
                            {:title newSectionTitle :pages {}})
                           (setNewSectionKey "")
                           (setNewSectionTitle {:fi "" :en "" :se ""})
                           (onClose)))}
             "Add")))))

(defui view
  []
  (let [edited-data (use-subscribe [::edited-help-data])
        [selectedSection setSelectedSection] (uix/use-state (first (keys edited-data)))
        [addSectionOpen setAddSectionOpen] (uix/use-state false)]

    ;; Initialize editor data from app-db
    (uix/use-effect
     (fn []
       (==> [::initialize-editor])
       (fn []))
     [])

    ($ Container {:maxWidth "lg" :sx #js{:mt 3}}
       ($ AppBar {:position "static" :color "default" :elevation 0 :sx #js{:mb 3}}
          ($ Toolbar
             ($ IconButton
                {:edge "start"
                 :color "inherit"
                 :aria-label "back"
                 :sx #js{:mr 2}
                 :on-click #(==> [:lipas.ui.help.events/close-edit-mode])}
                ($ ArrowBackIcon))
             ($ Typography {:variant "h6" :sx #js{:flexGrow 1}}
                "Help Content Manager")
             ($ Button
                {:color "primary"
                 :variant "contained"
                 :startIcon ($ SaveIcon)
                 :onClick #(==> [::apply-changes])}
                "Save Changes")))

       ($ Grid {:container true :spacing 3}
          ($ Grid {:item true :xs 12}
             ($ Box {:sx #js{:display "flex" :justifyContent "space-between" :alignItems "center" :mb 2}}
                ($ Typography {:variant "h5"} "Manage Help Sections")
                ($ Button
                   {:variant "contained"
                    :color "primary"
                    :startIcon ($ AddIcon)
                    :onClick #(setAddSectionOpen true)}
                   "Add Section")))

          ($ Grid {:item true :xs 12}
             ($ Tabs
                {:value (or selectedSection (first (keys edited-data)))
                 :onChange #(setSelectedSection (keyword %2))
                 :variant "scrollable"
                 :scrollButtons "auto"}
                (for [section-key (keys edited-data)]
                  ($ Tab {:key section-key :value section-key :label (get-in edited-data [section-key :title :fi])}))))

          ($ Grid {:item true :xs 12}
             ($ Divider {:sx #js{:mb 3}}))

          ($ Grid {:item true :xs 12}
             (when (and edited-data selectedSection)
               (println selectedSection (type selectedSection))
               ($ SectionEditor
                  {:section (get edited-data selectedSection)
                   :sectionKey selectedSection
                   :onChange (fn [sectionKey updatedSection]
                               (==> [::update-help-data (assoc edited-data sectionKey updatedSection)]))
                   :onDeletePage (fn [sectionKey pageKey]
                                   (==> [::update-help-data
                                         (update-in edited-data [sectionKey :pages] dissoc pageKey)]))
                   :onAddPage (fn [sectionKey]
                                (let [newPageKey (keyword (str "new-page-" (rand-int 1000)))]
                                  (==> [::update-help-data
                                        (assoc-in edited-data
                                                 [sectionKey :pages newPageKey]
                                                 {:title {:fi "New Page" :en "New Page" :se "New Page"}
                                                  :text {:fi "" :en "" :se ""}
                                                  :videos []})])))}))))

       ($ AddSectionDialog
          {:open addSectionOpen
           :onClose #(setAddSectionOpen false)
           :onAdd (fn [sectionKey sectionData]
                    (==> [::update-help-data (assoc edited-data sectionKey sectionData)]))}))))
