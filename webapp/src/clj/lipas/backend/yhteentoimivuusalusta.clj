(ns lipas.backend.yhteentoimivuusalusta
  "Export LIPAS type-code metadata to Finland's Yhteentoimivuusalusta (Interoperability Platform) codes registry.
  
  This namespace provides functionality to generate Excel files compatible with the
  Yhteentoimivuusalusta Koodistot-työkalu (Codes Tool) import format.
  
  ## Background
  
  LIPAS maintains a hierarchical classification system for sports facilities:
  - Main categories (pääluokat): Top-level categories like 'Outdoor fields and sports parks'
  - Sub-categories (alaluokat): Mid-level categories like 'Ice sports areas'
  - Type codes (tyyppikoodit): Specific facility types like 'Ice rink'
  
  This classification needs to be registered in Finland's official codes registry
  to enable interoperability with other Finnish public services.
  
  ## Excel Format Requirements
  
  The import Excel must have two sheets:
  
  1. **CodeSchemes** - Metadata about the LIPAS type-code classification system
  2. **Codes** - All individual codes in hierarchical structure
  
  ### CodeSchemes Sheet (Koodiston metatiedot)
  
  Required fields:
  - CODEVALUE: Unique identifier (e.g., 'lipas-type-codes')
  - PREFLABEL_FI/SV/EN: Name in Finnish/Swedish/English (at least one required)
  - INFORMATIONDOMAIN: Classification domain code (P27 = Sports and Recreation)
  - STATUS: DRAFT, VALID, etc.
  
  Optional fields:
  - ORGANIZATION: UUID of managing organization(s), semicolon-separated
  - DESCRIPTION_FI/SV/EN: Description in each language
  - LANGUAGECODE: Supported languages (e.g., 'fi;sv;en')
  - SOURCE, LEGALBASE, GOVERNANCEPOLICY: Additional metadata
  
  ### Codes Sheet (Koodit ja metatiedot)
  
  Required fields:
  - CODEVALUE: The code value (e.g., '1000', '1530')
  - STATUS: Code status (DRAFT, VALID, etc.)
  
  Key field for hierarchy:
  - BROADER: Parent code value for hierarchical codes (empty for top-level)
  
  Optional fields:
  - PREFLABEL_FI/SV/EN: Code label in each language
  - DESCRIPTION_FI/SV/EN: Code description in each language
  - ORDER: Sort order number
  
  ## Hierarchical Structure
  
  The BROADER field creates the hierarchy:
  - Main categories: BROADER is empty (top level)
  - Sub-categories: BROADER points to main category code
  - Type codes: BROADER points to sub-category code
  
  Example:
  ```
  CODEVALUE  BROADER  PREFLABEL_FI
  1000       ''       Ulkokentät ja liikuntapuistot  (main category)
  1500       1000     Jääurheilualueet              (sub-category)
  1530       1500     Kaukalo                        (type code)
  ```
  
  ## Usage
  
  From the REPL after `(user/reset)`:
  
  ```clojure
  (require '[lipas.backend.yhteentoimivuusalusta :as yhteentoimivuus])
  
  ;; Generate Excel file
  (yhteentoimivuus/export-to-registry-excel
    \"/path/to/output/lipas-types-registry-export.xlsx\")
  
  ;; Or get the workbook for further manipulation
  (def wb (yhteentoimivuus/create-registry-workbook))
  ```
  
  ## Notes for Future Developers
  
  1. **Organization UUID**: The ORGANIZATION field is currently nil. If you need to
     specify the managing organization, add the UUID from PTV (Palvelutietovaranto).
     Multiple organizations can be specified with semicolon separator.
  
  2. **Information Domain**: We use 'P27' which corresponds to 'Liikunta ja urheilu'
     (Sports and Recreation) in the Tietoalueiden luokitus classification.
  
  3. **Status Management**: Codes start as DRAFT. Change to VALID once verified.
     The system supports: INCOMPLETE, DRAFT, SUGGESTED, SUBMITTED, VALID, 
     SUPERSEDED, RETIRED, INVALID.
  
  4. **Code Values**: We use the existing LIPAS type-codes as CODEVALUE. These are
     numeric (0, 1000, 1530, etc.) and must remain stable for API compatibility.
  
  5. **Updates**: When updating the classification, modify the source data in
     `lipas.data.types` namespace. This export is derived from that canonical source.
  
  See also:
  - Yhteentoimivuusalusta documentation: https://kehittajille.suomi.fi/
  - LIPAS data model: lipas.data.types namespace
  - Import template: /yhteentoimivuus/import-template.xlsx
  - Import example: /yhteentoimivuus/import-example.xlsx"
  (:require
   [dk.ative.docjure.spreadsheet :as excel]
   [lipas.data.types :as lipas-types]))

(defn create-registry-workbook
  "Create an Excel workbook compatible with Yhteentoimivuusalusta codes registry import.
  
  Returns an Apache POI XSSFWorkbook object that can be saved to a file using
  `excel/save-workbook!`.
  
  The workbook contains:
  - CodeSchemes sheet: Metadata about the LIPAS type-code classification
  - Codes sheet: All codes (main categories, sub-categories, type codes) in hierarchical order
  
  Example:
    (def wb (create-registry-workbook))
    (excel/save-workbook! \"/path/to/file.xlsx\" wb)"
  []

  ;; CodeSchemes sheet - metadata about the LIPAS type-code classification system
  ;; See PDF documentation for field descriptions
  (let [code-scheme-data
        [;; Headers
         ["CODEVALUE" "PREFLABEL_FI" "PREFLABEL_SV" "PREFLABEL_EN"
          "ORGANIZATION" "INFORMATIONDOMAIN" "STATUS" "LANGUAGECODE"
          "DESCRIPTION_FI" "DESCRIPTION_SV" "DESCRIPTION_EN"
          "STARTDATE" "ENDDATE" "URI" "VERSION" "SOURCE"
          "LEGALBASE" "GOVERNANCEPOLICY" "CONCEPTURI" "DEFAULTCODE"
          "DEFINITION_FI" "DEFINITION_SV" "DEFINITION_EN"
          "CHANGENOTE_FI" "CHANGENOTE_SV" "CHANGENOTE_EN"
          "HREF" "CODESSHEET" "LINKSSHEET"
          "FEEDBACK_CHANNEL_FI" "FEEDBACK_CHANNEL_EN" "FEEDBACK_CHANNEL_SV"]

         ;; Data row - single code scheme describing the entire LIPAS type classification
         ["lipas-type-codes" ; CODEVALUE - unique identifier for this code scheme
          "LIPAS Liikuntapaikkatyypit" ; PREFLABEL_FI - Finnish name (required)
          "LIPAS Idrottsplatstyper" ; PREFLABEL_SV - Swedish name
          "LIPAS Sports Facility Types" ; PREFLABEL_EN - English name

          ;; ORGANIZATION - Managing organization UUID in Koodistot system
          ;; This is the Koodistot organization ID (not PTV organization ID)
          ;; Jyväskylän yliopisto in Koodistot: 4a7795bb-c165-486e-96ff-0e3258527d71
          "4a7795bb-c165-486e-96ff-0e3258527d71"

          "P27" ; INFORMATIONDOMAIN - P27 = Liikunta ja urheilu (Sports and Recreation)
          "DRAFT" ; STATUS - Start as DRAFT, change to VALID after verification
          "fi;sv;en" ; LANGUAGECODE - Supported languages

          ;; Descriptions
          "LIPAS-järjestelmän liikuntapaikkojen tyyppiluokitus. Luokitus koostuu pääluokista, alaluokista ja tyypeistä."
          "Typsklassificering av idrottsplatser i LIPAS-systemet. Klassificeringen består av huvudklasser, underklasser och typer."
          "Sports facility type classification in the LIPAS system. The classification consists of main categories, sub-categories, and types."

          nil ; STARTDATE - Will be set automatically on import
          nil ; ENDDATE - Leave empty unless you know the end date
          nil ; URI - Will be generated automatically by the registry
          nil ; VERSION - Will be set to 1 automatically for new code schemes

          "LIPAS - lipas.fi" ; SOURCE
          nil ; LEGALBASE - No specific legal basis
          "Tilastoluokitus" ; GOVERNANCEPOLICY - Statistical classification
          nil ; CONCEPTURI - No link to terminology/ontology tool (Sanastot)
          nil ; DEFAULTCODE - Not used

          ;; DEFINITION fields - Only used when referencing concepts in Sanastot tool
          ;; Since LIPAS doesn't have definitions in Sanastot yet, these are left empty
          nil ; DEFINITION_FI
          nil ; DEFINITION_SV
          nil ; DEFINITION_EN

          nil ; CHANGENOTE_FI - Version change notes
          nil ; CHANGENOTE_SV
          nil ; CHANGENOTE_EN
          nil ; HREF - External links

          "Codes" ; CODESSHEET - Name of the sheet containing the codes
          nil ; LINKSSHEET - Name of the links sheet (not used)

          ;; Feedback channels - Check lipas.fi for latest contact information
          "Katso ajantasaiset yhteystiedot osoitteesta lipas.fi"
          "For latest contact information, visit lipas.fi"
          "För aktuell kontaktinformation, besök lipas.fi"]]

        ;; Codes sheet - all the actual codes in hierarchical structure
        codes-headers
        ["CODEVALUE" "BROADER" "STATUS"
         "PREFLABEL_FI" "PREFLABEL_SV" "PREFLABEL_EN"
         "DESCRIPTION_FI" "DESCRIPTION_SV" "DESCRIPTION_EN"
         "ORDER" "STARTDATE" "ENDDATE" "HREF"]

        ;; Main categories - top level of hierarchy (BROADER is empty)
        main-category-rows
        (map-indexed
         (fn [idx [type-code {:keys [name]}]]
           [(str type-code) ; CODEVALUE
            "" ; BROADER - empty for top-level codes
            "DRAFT" ; STATUS - match the code scheme status
            (:fi name) ; PREFLABEL_FI
            (:se name) ; PREFLABEL_SV  
            (:en name) ; PREFLABEL_EN
            "" "" "" ; DESCRIPTION fields - not needed for categories
            (str (inc idx)) ; ORDER - sort order (1-based)
            nil nil ""]) ; STARTDATE, ENDDATE, HREF
         (sort-by first lipas-types/main-categories))

        ;; Sub-categories - second level (BROADER points to main category)
        sub-category-rows
        (map-indexed
         (fn [idx [type-code {:keys [name main-category]}]]
           [(str type-code) ; CODEVALUE
            (str main-category) ; BROADER - points to parent main category
            "DRAFT" ; STATUS
            (:fi name) ; PREFLABEL_FI
            (:se name) ; PREFLABEL_SV
            (:en name) ; PREFLABEL_EN
            "" "" "" ; DESCRIPTION fields
            (str (inc idx)) ; ORDER
            nil nil ""])
         (sort-by first lipas-types/sub-categories))

        ;; Type codes - leaf level (BROADER points to sub-category)
        type-rows
        (map-indexed
         (fn [idx [type-code {:keys [name description sub-category]}]]
           [(str type-code) ; CODEVALUE
            (str sub-category) ; BROADER - points to parent sub-category
            "DRAFT" ; STATUS
            (:fi name) ; PREFLABEL_FI
            (:se name) ; PREFLABEL_SV
            (:en name) ; PREFLABEL_EN
            ;; Include descriptions for leaf nodes (actual facility types)
            (or (:fi description) "")
            (or (:se description) "")
            (or (:en description) "")
            (str (inc idx)) ; ORDER
            nil nil ""])
         (sort-by first lipas-types/active))

        ;; Combine all codes: header + main categories + sub categories + type codes
        ;; Order matters for clarity but the hierarchy is determined by BROADER field
        all-codes (concat [codes-headers]
                          main-category-rows
                          sub-category-rows
                          type-rows)]

    ;; Create workbook with two sheets
    ;; Sheet names must be exactly "CodeSchemes" and "Codes" (case-sensitive)
    (excel/create-workbook "CodeSchemes" code-scheme-data
                           "Codes" all-codes)))

(defn export-to-registry-excel
  "Generate and save LIPAS type-codes Excel file for Yhteentoimivuusalusta import.
  
  Parameters:
    output-path - Absolute path where to save the Excel file (e.g., '/path/to/lipas-types.xlsx')
  
  Returns:
    nil (file is saved to disk)
  
  Example:
    (export-to-registry-excel \"/Users/myuser/Desktop/lipas-types-registry.xlsx\")
  
  The generated file can be imported to Yhteentoimivuusalusta by:
  1. Logging in to https://koodistot.suomi.fi/
  2. Click 'Lisää koodisto'
  3. Click 'Tuo koodisto tiedostosta'
  4. Select registry (e.g., 'JHS')
  5. Choose Excel format
  6. Upload the generated file
  7. Click 'Tuo' to import"
  [output-path]
  (let [wb (create-registry-workbook)]
    (excel/save-workbook! output-path wb)
    (println "✓ Excel file generated successfully!")
    (println "  Location:" output-path)
    (println "  Main categories:" (count lipas-types/main-categories))
    (println "  Sub categories:" (count lipas-types/sub-categories))
    (println "  Type codes:" (count lipas-types/active))
    (println "  Total codes:" (+ (count lipas-types/main-categories)
                                 (count lipas-types/sub-categories)
                                 (count lipas-types/active)))
    (println)
    (println "Next steps:")
    (println "  1. Review the file to ensure data is correct")
    (println "  2. Consider updating ORGANIZATION field if needed")
    (println "  3. Import to https://koodistot.suomi.fi/")
    (println "  4. After verification, change STATUS from DRAFT to VALID")))

(comment
  ;; Quick export
  (export-to-registry-excel "/Users/tipo/lipas/lipas/webapp/yhteentoimivuus/lipas-types-registry-export.xlsx")

  ;; Get workbook for inspection
  (def wb (create-registry-workbook))

  ;; Check structure
  (require '[dk.ative.docjure.spreadsheet :as excel])
  (mapv #(.getSheetName %) (excel/sheet-seq wb))

  ;; Inspect first few rows of Codes sheet
  (let [codes-sheet (second (excel/sheet-seq wb))]
    (->> codes-sheet
         excel/row-seq
         (map excel/cell-seq)
         (map (fn [row] (map excel/read-cell row)))
         (take 15)
         (map (fn [row] (take 7 row))) ; First 7 columns
         vec)))
