(ns lipas.schema.sports-sites.images
  (:require [lipas.schema.common :as common]
            [malli.core :as m]))

(defn valid-url?
  "True when s is an https URL acceptable as an image link. Plain http is
  rejected on purpose: LIPAS is served over https and browsers block
  mixed-content images, so an http link would never render."
  [s]
  (and (string? s)
       (boolean (re-matches #"^https://[^\s]+$" s))))

(def image-url
  (m/schema
   [:re {:description "URL of the image file hosted externally (e.g. city image bank). Must be https:// — plain http images are blocked as mixed content in browsers."
         :min 1
         :max 2048}
    #"^https://[^\s]+$"]))

(def localized-string-required
  "Like lipas.schema.common/localized-string but at least one non-empty
  translation must be present."
  (m/schema
   [:and
    [:map
     [:fi {:optional true :description "Finnish translation"} [:string {:min 1}]]
     [:se {:optional true :description "Swedish translation"} [:string {:min 1}]]
     [:en {:optional true :description "English translation"} [:string {:min 1}]]]
    [:fn {:error/message "at least one translation (fi/se/en) is required"}
     (fn [m] (boolean (some #(seq (get m %)) [:fi :se :en])))]]))

(def image
  (m/schema
   [:map {:description
          "Image link. LIPAS stores only metadata (URL + descriptions); the
           image file itself is hosted externally by the site owner. Metadata
           is licensed CC BY 4.0; image files are NOT covered by this license —
           their terms of use are set by the source organisation. Consumers
           should display images by embedding (hotlinking) the URL directly
           and must not download, cache or re-host the image files, so that
           removals at the source (GDPR/takedown) propagate immediately."}
    [:url #'image-url]
    [:alt-text {:description "Screen-reader alternative text describing the image. Required for accessibility (EU Web Accessibility Directive)."}
     #'localized-string-required]
    [:copyright {:description "Image source/owner organisation and copyright information, e.g. \"Loimaan kaupunki\". Required."}
     #'localized-string-required]
    [:description {:optional true
                   :description "Visible caption shown with the image."}
     #'common/localized-string]]))

(def images
  (m/schema
   [:sequential {:description
                 "Image links for the sports facility. Editable only by users
                  with :site/edit-images privilege (e.g. :images-manager role).
                  Metadata is CC BY 4.0; the image files at the URLs are not —
                  embed/hotlink them, do not cache or redistribute."}
    #'image]))
