(ns lipas.schema.sports-sites.images
  (:require [lipas.schema.common :as common]
            [malli.core :as m]))

(def image-url
  (m/schema
   [:re {:description "URL of the image hosted externally (e.g. city image bank). Must start with http:// or https://."
         :min 1
         :max 2048}
    #"^https?://.+"]))

(def image
  (m/schema
   [:map {:description
          "Image link. LIPAS stores only metadata (URL + descriptions); the
           image file itself is hosted externally by the site owner. Metadata
           is licensed CC BY 4.0; image files are not covered by this license."}
    [:url #'image-url]
    [:alt-text {:optional true
                :description "Screen-reader alternative text describing the image."}
     #'common/localized-string]
    [:copyright {:optional true
                 :description "Photographer, source, license, date."}
     #'common/localized-string]
    [:description {:optional true
                   :description "Visible caption shown with the image."}
     #'common/localized-string]]))

(def images
  (m/schema
   [:sequential {:description
                 "Image links for the sports facility. Editable only by users
                  with :site/edit-images privilege (e.g. :images-manager role)."}
    #'image]))
