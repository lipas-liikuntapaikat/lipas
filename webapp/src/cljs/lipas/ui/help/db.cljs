(ns lipas.ui.help.db)

(def default-db
  {:dialog
   {:open? false
    :selected-section-idx 0 ; Index of selected section in vector
    :selected-section-slug nil
    :selected-page-idx nil ; Index of selected page in section's pages vector
    :selected-page-slug nil
    :mode :read}
   ;; Help data is loaded from the server
   :data nil})
