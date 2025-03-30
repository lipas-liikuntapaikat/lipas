(ns lipas.schema.help
  (:require [lipas.schema.common :as common]
            [malli.core :as m]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reusable Primitive Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def NonEmptyString
  [:string {:min 1}])

(def LocalizedString common/localized-string)

(def BlockId common/uuid)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Content Block Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def TextBlock
  [:map {:closed true} ; Prevent extra keys
   [:block-id BlockId]
   [:type {:decode/string keyword} [:enum :text]]
   [:content LocalizedString]])

(def ImageBlock
  [:map {:closed true}
   [:block-id BlockId]
   [:type {:decode/string keyword} [:enum :image]]
   [:url NonEmptyString] ; Assuming URL is a non-empty string
   [:alt LocalizedString] ; Alt text is mandatory for accessibility
   [:caption {:optional true} LocalizedString]]) ; Caption is optional

(def VideoBlock
  [:map {:closed true}
   [:block-id BlockId]
   [:type {:decode/string keyword} [:enum :video]]
   [:provider [:enum :youtube :vimeo]] ; Allow youtube or vimeo? Or just :youtube?
   [:video-id NonEmptyString] ; The unique ID from the provider
   [:title {:optional true} LocalizedString]]) ; Optional title for the video

(def ContentBlock
  [:multi {:dispatch :type}
   [:text TextBlock]
   [:image ImageBlock]
   [:video VideoBlock]
   ;; Add other block types here in the future
   ;; [:heading HeadingBlock]
   ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page and Section Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Page
  [:map {:closed true}
   [:title LocalizedString]
   [:blocks [:vector ContentBlock]]]) ; A page contains a vector of content blocks

(def Section
  [:map {:closed true}
   [:title LocalizedString]
   [:pages [:map-of :keyword Page]]]) ; Pages identified by keyword within a section

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Top-Level Schema
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def HelpData
  [:map-of :keyword Section]) ; Top level is map of section-keyword -> section data

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Registry (Optional but good practice)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_(def registry
  {::NonEmptyString NonEmptyString
   ::LanguageKeyword LanguageKeyword
   ::LocalizedString LocalizedString
   ::BlockId BlockId
   ::TextBlock TextBlock
   ::ImageBlock ImageBlock
   ::VideoBlock VideoBlock
   ::ContentBlock ContentBlock
   ::Page Page
   ::Section Section
   ::HelpData HelpDataSchema})

;; Set the default registry
#_(mr/set-default-registry! (mr/composite-registry m/default-registry registry))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Example Usage (Validation)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (require '[malli.error :as me])

  ; Example data snippet (replace with your actual data map)
  (def example-data
    {:general
     {:title {:fi "Yleiset" :se "Allmänt" :en "General"}
      :pages
      {:what-is-lipas
       {:title {:fi "Mikä Lipas on?" :en "What is Lipas?" :se "Vad är Lipas?"}
        :blocks [{:block-id "uuid-abc-123"
                  :type :text
                  :content {:fi "Lipas on suomalainen..."
                            :en "Lipas is a Finnish..."
                            :se "Lipas är en finsk..."}}

                 {:block-id "uuid-def-456"
                  :type :image
                  :url "/path/to/your/image/lipas-overview.jpg"
                  :alt {:fi "Yleiskuva Lipas-palvelusta"
                        :en "Overview of the Lipas service"
                        :se "Översikt över Lipas-tjänsten"}
                  :caption {:fi "Lipaksen pääkarttanäkymä."
                            :en "The main map view of Lipas."
                            :se "Huvudkartvyn i Lipas."}}

                 {:block-id "uuid-ghi-789"
                  :type :video
                  :provider :youtube
                  :video-id "dqT-UlYlg1s"
                  :title {:fi "Esittelyvideo"
                          :en "Introduction Video"
                          :se "Introduktionsvideo"}}
                 ]}}}})


  ; Validate the example data against the schema
  (m/validate HelpDataSchema example-data)

  ; Explain validation errors (if any)
  (-> (m/explain HelpDataSchema example-data)
      (me/humanize))

)
