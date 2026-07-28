(ns lipas.schema.help
  "Help content v2: each locale (fi/se/en) has its own independent tree
   of sections → pages → blocks, and every leaf string is a plain
   string. This replaced the v1 model where one shared structure
   carried {:fi :se :en} maps in every leaf — untranslated locales
   accumulated placeholder junk (\"New Page\", \"Ny sida\") that leaked
   into the UI and the assistant knowledge base.

   Storage: one versioned_data document per locale (types help-v2-fi /
   help-v2-se / help-v2-en) so each language drafts, publishes and
   rolls back independently.

   Slugs are the ?ohje= deep-link currency. Renames must keep the old
   slug in :aliases — published links and KB citations resolve through
   them."
  (:require [lipas.schema.common :as common]
            [malli.core :as m]))

(def locales [:fi :se :en])

(def Locale
  (m/schema [:enum {:decode/string keyword :decode/json keyword}
             :fi :se :en]))

(def Slug
  "URL-safe identifier, unique among siblings."
  (m/schema [:re {:error/message "expected lowercase letters, numbers and hyphens"}
             #"^[a-z0-9][a-z0-9-]*$"]))

(def BlockId (m/schema common/uuid))

;; Leaf strings allow "" so drafts can be saved mid-edit.

(def TextBlock
  (m/schema
    [:map {:closed true}
     [:block-id BlockId]
     [:type {:decode/string keyword} [:enum :text]]
     [:content :string]])) ; markdown

(def ImageBlock
  (m/schema
    [:map {:closed true}
     [:block-id BlockId]
     [:type {:decode/string keyword} [:enum :image]]
     [:url :string]
     [:alt :string] ; mandatory for accessibility
     [:caption {:optional true} :string]]))

(def VideoBlock
  (m/schema
    [:map {:closed true}
     [:block-id BlockId]
     [:type {:decode/string keyword} [:enum :video]]
     [:provider {:decode/string keyword} [:enum :youtube :vimeo]]
     [:video-id :string]
     [:title {:optional true} :string]]))

(def PdfBlock
  (m/schema
    [:map {:closed true}
     [:block-id BlockId]
     [:type {:decode/string keyword} [:enum :pdf]]
     [:url :string]
     [:title {:optional true} :string]
     [:caption {:optional true} :string]]))

(def TypeCodeExplorerBlock
  (m/schema
    [:map {:closed true}
     [:block-id BlockId]
     [:type {:decode/string keyword} [:enum :type-code-explorer]]]))

(def DataModelExcelDownload
  (m/schema
    [:map {:closed true}
     [:block-id BlockId]
     [:type {:decode/string keyword} [:enum :data-model-excel-download]]]))

(def ContentBlock
  (m/schema
    [:multi {:dispatch :type}
     [:text TextBlock]
     [:image ImageBlock]
     [:video VideoBlock]
     [:pdf PdfBlock]
     [:type-code-explorer TypeCodeExplorerBlock]
     [:data-model-excel-download DataModelExcelDownload]]))

(def Page
  (m/schema
    [:map {:closed true}
     [:id common/uuid] ; stable across slug renames
     [:slug Slug]
     [:title :string]
     [:summary {:optional true} :string] ; 1-2 sentences for landing lists + KB
     [:aliases {:optional true} [:vector :string]] ; old slugs, kept resolvable
     [:translation-of {:optional true} common/uuid] ; counterpart page id in another locale
     [:blocks [:vector ContentBlock]]]))

(def Section
  (m/schema
    [:map {:closed true}
     [:id common/uuid]
     [:slug Slug]
     [:title :string]
     [:summary {:optional true} :string]
     [:aliases {:optional true} [:vector :string]]
     [:pages [:vector Page]]]))

(def LocaleTree
  "One locale's full help content."
  (m/schema [:vector Section]))

(def HelpData
  "The get-help-data response: every locale's published tree. A locale
   with no published content maps to []."
  (m/schema
    [:map {:closed true}
     [:fi LocaleTree]
     [:se LocaleTree]
     [:en LocaleTree]]))

(def SaveHelpDataBody
  (m/schema
    [:map {:closed true}
     [:locale Locale]
     [:data LocaleTree]]))

(def HelpVersionsBody
  (m/schema
    [:map {:closed true}
     [:locale Locale]]))
