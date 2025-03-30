(ns lipas.ui.help.db)

(def default-db
  {:dialog
   {:open? false
    :selected-section :general
    :selected-page nil
    :mode :read}
   ;; Help data is loaded from the server
   :data nil})
