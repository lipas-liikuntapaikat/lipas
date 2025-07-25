(ns lipas.schema.help
  (:require [lipas.schema.common :as common]))

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

(def PdfBlock
  [:map {:closed true}
   [:block-id BlockId]
   [:type {:decode/string keyword} [:enum :pdf]]
   [:url NonEmptyString] ; Assuming URL is a non-empty string
   [:caption {:optional true} LocalizedString]
   [:title {:optional true} LocalizedString]])

(def TypeCodeExplorerBlock
  [:map {:closed true}
   [:block-id BlockId]
   [:type {:decode/string keyword} [:enum :type-code-explorer]]])

(def DataModelExcelDownload
  [:map {:closed true}
   [:block-id BlockId]
   [:type {:decode/string keyword} [:enum :data-model-excel-download]]])

(def ContentBlock
  [:multi {:dispatch :type}
   [:text TextBlock]
   [:image ImageBlock]
   [:video VideoBlock]
   [:pdf PdfBlock]
   [:type-code-explorer TypeCodeExplorerBlock]
   [:data-model-excel-download DataModelExcelDownload]
   ;; Add other block types here in the future
   ;; [:heading HeadingBlock]
   ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page and Section Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Page
  [:map {:closed true}
   [:slug :keyword]
   [:title LocalizedString]
   [:blocks [:vector ContentBlock]]])

(def Section
  [:map {:closed true}
   [:slug :keyword]
   [:title LocalizedString]
   [:pages [:vector
           [:map {:closed true}
            [:slug :keyword]
            [:title LocalizedString]
            [:blocks [:vector ContentBlock]]]]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Top-Level Schema
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def HelpData
  [:vector Section]) ; Top level is a vector of sections

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
