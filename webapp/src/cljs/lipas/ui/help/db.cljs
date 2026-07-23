(ns lipas.ui.help.db)

(def default-db
  {:dialog
   {:open? false
    ;; Selection is by canonical slug (strings). ?ohje= aliases resolve
    ;; to these on navigation.
    :selected-section-slug nil
    :selected-page-slug nil
    :expanded-sections #{}
    :search-term ""
    :mode :read}
   :editor
   {:locale :fi}
   ;; {:fi [sections] :se [...] :en [...]} loaded from the server
   :data nil})
