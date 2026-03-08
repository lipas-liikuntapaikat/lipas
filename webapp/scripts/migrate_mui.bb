#!/usr/bin/env bb
;; scripts/migrate_mui.bb — Migrate mui/xxx component aliases to direct MUI imports
;;
;; Usage:
;;   bb scripts/migrate_mui.bb [--dry-run] file1.cljs [file2.cljs ...]
;;
;; Transforms:
;;   [mui/button {:on-click f} "Click"]  →  [:> Button {:on-click f} "Click"]
;;   (r/create-element mui/text-field p)  →  (r/create-element TextField p)
;;
;; And adds the corresponding ["@mui/material/Button$default" :as Button] imports.
;; Run cljfmt afterwards to fix import ordering.

(require '[clojure.string :as str])

;; ===========================================================================
;; Component alias → [npm-path, default-alias]
;; ===========================================================================

(def component-map
  {"alert"                       ["@mui/material/Alert$default" "Alert"]
   "app-bar"                     ["@mui/material/AppBar$default" "AppBar"]
   "autocomplete"                ["@mui/material/Autocomplete$default" "Autocomplete"]
   "avatar"                      ["@mui/material/Avatar$default" "Avatar"]
   "box"                         ["@mui/material/Box$default" "Box"]
   "button"                      ["@mui/material/Button$default" "Button"]
   "card"                        ["@mui/material/Card$default" "Card"]
   "card-actions"                ["@mui/material/CardActions$default" "CardActions"]
   "card-content"                ["@mui/material/CardContent$default" "CardContent"]
   "card-header"                 ["@mui/material/CardHeader$default" "CardHeader"]
   "checkbox"                    ["@mui/material/Checkbox$default" "Checkbox"]
   "chip"                        ["@mui/material/Chip$default" "Chip"]
   "circular-progress"           ["@mui/material/CircularProgress$default" "CircularProgress"]
   "collapse"                    ["@mui/material/Collapse$default" "Collapse"]
   "css-baseline"                ["@mui/material/CssBaseline$default" "CssBaseline"]
   "dialog"                      ["@mui/material/Dialog$default" "Dialog"]
   "dialog-actions"              ["@mui/material/DialogActions$default" "DialogActions"]
   "dialog-content"              ["@mui/material/DialogContent$default" "DialogContent"]
   "dialog-title"                ["@mui/material/DialogTitle$default" "DialogTitle"]
   "divider"                     ["@mui/material/Divider$default" "Divider"]
   "drawer"                      ["@mui/material/Drawer$default" "Drawer"]
   "expansion-panel"             ["@mui/material/Accordion$default" "Accordion"]
   "expansion-panel-details"     ["@mui/material/AccordionDetails$default" "AccordionDetails"]
   "expansion-panel-summary"     ["@mui/material/AccordionSummary$default" "AccordionSummary"]
   "fab"                         ["@mui/material/Fab$default" "Fab"]
   "form-control"                ["@mui/material/FormControl$default" "FormControl"]
   "form-control-label"          ["@mui/material/FormControlLabel$default" "FormControlLabel"]
   "form-group"                  ["@mui/material/FormGroup$default" "FormGroup"]
   "form-helper-text"            ["@mui/material/FormHelperText$default" "FormHelperText"]
   "form-label"                  ["@mui/material/FormLabel$default" "FormLabel"]
   "grid"                        ["@mui/material/GridLegacy$default" "Grid"]
   "grid2"                       ["@mui/material/Grid$default" "Grid2"]
   "icon"                        ["@mui/material/Icon$default" "Icon"]
   "icon-button"                 ["@mui/material/IconButton$default" "IconButton"]
   "input-adornment"             ["@mui/material/InputAdornment$default" "InputAdornment"]
   "input-label"                 ["@mui/material/InputLabel$default" "InputLabel"]
   "linear-progress"             ["@mui/material/LinearProgress$default" "LinearProgress"]
   "link"                        ["@mui/material/Link$default" "Link"]
   "list"                        ["@mui/material/List$default" "List"]
   "list-item"                   ["@mui/material/ListItem$default" "ListItem"]
   "list-item-button"            ["@mui/material/ListItemButton$default" "ListItemButton"]
   "list-item-icon"              ["@mui/material/ListItemIcon$default" "ListItemIcon"]
   "list-item-secondary-action"  ["@mui/material/ListItemSecondaryAction$default" "ListItemSecondaryAction"]
   "list-item-text"              ["@mui/material/ListItemText$default" "ListItemText"]
   "menu"                        ["@mui/material/Menu$default" "Menu"]
   "menu-item"                   ["@mui/material/MenuItem$default" "MenuItem"]
   "paper"                       ["@mui/material/Paper$default" "Paper"]
   "popper"                      ["@mui/material/Popper$default" "Popper"]
   "radio"                       ["@mui/material/Radio$default" "Radio"]
   "radio-group"                 ["@mui/material/RadioGroup$default" "RadioGroup"]
   "select"                      ["@mui/material/Select$default" "Select"]
   "slide"                       ["@mui/material/Slide$default" "Slide"]
   "slider"                      ["@mui/material/Slider$default" "Slider"]
   "snackbar"                    ["@mui/material/Snackbar$default" "Snackbar"]
   "snackbar-content"            ["@mui/material/SnackbarContent$default" "SnackbarContent"]
   "stack"                       ["@mui/material/Stack$default" "Stack"]
   "step"                        ["@mui/material/Step$default" "Step"]
   "step-content"                ["@mui/material/StepContent$default" "StepContent"]
   "step-label"                  ["@mui/material/StepLabel$default" "StepLabel"]
   "stepper"                     ["@mui/material/Stepper$default" "Stepper"]
   "svg-icon"                    ["@mui/material/SvgIcon$default" "SvgIcon"]
   "swipeable-drawer"            ["@mui/material/SwipeableDrawer$default" "SwipeableDrawer"]
   "switch"                      ["@mui/material/Switch$default" "Switch"]
   "tab"                         ["@mui/material/Tab$default" "Tab"]
   "table"                       ["@mui/material/Table$default" "Table"]
   "table-body"                  ["@mui/material/TableBody$default" "TableBody"]
   "table-cell"                  ["@mui/material/TableCell$default" "TableCell"]
   "table-container"             ["@mui/material/TableContainer$default" "TableContainer"]
   "table-head"                  ["@mui/material/TableHead$default" "TableHead"]
   "table-pagination"            ["@mui/material/TablePagination$default" "TablePagination"]
   "table-row"                   ["@mui/material/TableRow$default" "TableRow"]
   "table-sort-label"            ["@mui/material/TableSortLabel$default" "TableSortLabel"]
   "tabs"                        ["@mui/material/Tabs$default" "Tabs"]
   "text-field"                  ["@mui/material/TextField$default" "TextField"]
   "toggle-button"               ["@mui/material/ToggleButton$default" "ToggleButton"]
   "toggle-button-group"         ["@mui/material/ToggleButtonGroup$default" "ToggleButtonGroup"]
   "tool-bar"                    ["@mui/material/Toolbar$default" "Toolbar"]
   "tooltip"                     ["@mui/material/Tooltip$default" "Tooltip"]
   "typography"                  ["@mui/material/Typography$default" "Typography"]})

;; Non-component symbols that must remain as mui/xxx references
(def non-component-syms
  #{"primary" "primary2" "primary3"
    "secondary" "secondary2" "secondary3"
    "gold" "gray1" "gray2" "gray3"
    "headline-aleo" "headline-common"
    "jyu-styles-dark" "jyu-styles-light"
    "jyu-theme-dark" "jyu-theme-light"
    "->mui-theme" "keyword->PasCamelCase"
    "use-width" "mui-theme-provider"})

;; ===========================================================================
;; Helpers
;; ===========================================================================

(defn find-mui-refs
  "Find all unique mui/xxx symbol references in content."
  [content]
  (->> (re-seq #"mui/([^\s()\[\]{}\",;@/]+)" content)
       (map second)
       (remove #{"material" "icons-material"})
       set))

(defn find-existing-imports
  "Find existing [\"@mui/material/...\" :as Alias] imports.
  Returns {npm-path alias-name}."
  [lines]
  (->> lines
       (map #(re-find #"\[\"(@mui/material/[^\"]+)\"\s+:as\s+(\S+)\]" %))
       (remove nil?)
       (map (fn [[_ path alias]] [path alias]))
       (into {})))

(defn find-mui-require-idx
  "Find the line index of [lipas.ui.mui :as mui] in the require block."
  [lines]
  (first (keep-indexed
           (fn [i line]
             (when (re-find #"\[lipas\.ui\.mui\s+:as\s+mui\]" line) i))
           lines)))

;; ===========================================================================
;; Core transformation
;; ===========================================================================

(defn replace-component-usages
  "Replace [mui/xxx with [:> Alias and bare mui/xxx with Alias.
  alias-map: {component-name alias-string}"
  [content alias-map]
  (let [;; Sort by name length descending to prevent substring matches
        ;; e.g., replace 'list-item-button' before 'list-item' before 'list'
        sorted-names (sort-by #(- (count %)) (keys alias-map))]
    (reduce
      (fn [s comp-name]
        (let [alias (get alias-map comp-name)
              quoted (java.util.regex.Pattern/quote comp-name)
             ;; Pass 1: [mui/xxx → [:> Alias (hiccup form)
              s (str/replace s
                             (re-pattern (str "\\[mui/" quoted "(?![\\w-])"))
                             (str "[:> " alias))
             ;; Pass 2: bare mui/xxx → Alias (e.g., r/create-element)
              s (str/replace s
                             (re-pattern (str "(?<![:\\w])mui/" quoted "(?![\\w-])"))
                             alias)]
          s))
      content
      sorted-names)))

(defn update-ns-lines
  "Update the ns :require block: add new imports, optionally remove [lipas.ui.mui :as mui].
  Returns updated lines vector."
  [lines new-imports keep-mui-require?]
  (let [mui-idx (find-mui-require-idx lines)]
    (when-not mui-idx
      (println "  ERROR: Could not find [lipas.ui.mui :as mui] in ns form")
      (System/exit 1))
    (let [mui-line (nth lines mui-idx)
          ;; Extract prefix before [lipas.ui.mui (e.g., "  (:require " or "            ")
          prefix (second (re-find #"^(.*?)\[lipas\.ui\.mui" mui-line))
          ;; Extract suffix after :as mui] (e.g., "))" or "")
          suffix (second (re-find #":as\s+mui\](.*)" mui-line))
          ;; Indentation for continuation lines (align with first [)
          cont-indent (apply str (repeat (count prefix) \space))
          existing (find-existing-imports lines)
          imports-to-add (->> new-imports
                              (remove #(contains? existing (first %)))
                              (sort-by first))
          ;; Build import lines: first gets prefix, rest get cont-indent
          import-lines (if (seq imports-to-add)
                         (into [(str prefix "[\"" (first (first imports-to-add))
                                     "\" :as " (second (first imports-to-add)) "]")]
                               (map (fn [[path alias]]
                                      (str cont-indent "[\"" path "\" :as " alias "]"))
                                    (rest imports-to-add)))
                         [])
          ;; If keeping mui require, add it after the new imports
          mui-keep-line (when keep-mui-require?
                          (if (seq import-lines)
                            (str cont-indent "[lipas.ui.mui :as mui]")
                            ;; No new imports, keep original line as-is
                            mui-line))
          ;; Collect all replacement lines
          all-new-lines (vec (concat import-lines
                                     (when mui-keep-line [mui-keep-line])))
          ;; Append suffix (closing parens) to the last replacement line
          all-new-lines (if (and (seq suffix) (seq all-new-lines))
                          (update all-new-lines (dec (count all-new-lines)) str suffix)
                          all-new-lines)
          before (subvec lines 0 mui-idx)
          after (subvec lines (inc mui-idx))]
      (vec (concat before all-new-lines after)))))

;; ===========================================================================
;; Main migration function
;; ===========================================================================

(defn migrate-file!
  "Migrate a single file. Returns a result map or nil if nothing to do."
  [path {:keys [dry-run]}]
  (let [content (slurp path)
        trailing-newline? (str/ends-with? content "\n")
        all-refs (find-mui-refs content)
        comp-refs (filterv #(contains? component-map %) all-refs)
        non-comp-refs (filterv #(contains? non-component-syms %) all-refs)
        unknown-refs (remove #(or (contains? component-map %)
                                  (contains? non-component-syms %))
                             all-refs)]

    (when (seq unknown-refs)
      (println (str "  WARN: Unknown mui/ refs: " (str/join ", " (sort unknown-refs)))))

    (if (empty? comp-refs)
      (do (println "  SKIP (no component refs)")
          nil)
      (let [lines (vec (str/split-lines content))
            existing (find-existing-imports lines)
            path->alias (into {} existing)

            alias-map (into {}
                            (for [comp-name comp-refs
                                  :let [[npm-path default-alias] (component-map comp-name)
                                        alias (or (get path->alias npm-path) default-alias)]]
                              [comp-name alias]))

            new-imports (distinct
                          (for [comp-name comp-refs
                                :let [[npm-path _] (component-map comp-name)
                                      alias (get alias-map comp-name)]]
                            [npm-path alias]))

            new-content (replace-component-usages content alias-map)

            new-lines (update-ns-lines
                        (vec (str/split-lines new-content))
                        new-imports
                        (boolean (seq non-comp-refs)))

            final-content (cond-> (str/join "\n" new-lines)
                            trailing-newline? (str "\n"))]

        (if dry-run
          (do
            (println (str "  Would migrate " (count comp-refs) " components: "
                          (str/join ", " (sort comp-refs))))
            (when (seq non-comp-refs)
              (println (str "  Keeping [lipas.ui.mui :as mui] for: "
                            (str/join ", " (sort non-comp-refs))))))
          (do
            (spit path final-content)
            (println (str "  Migrated " (count comp-refs) " components"))
            (when (seq non-comp-refs)
              (println (str "  Kept [lipas.ui.mui :as mui] for: "
                            (str/join ", " (sort non-comp-refs)))))))

        {:path path
         :components (count comp-refs)
         :kept-mui? (boolean (seq non-comp-refs))}))))

;; ===========================================================================
;; CLI
;; ===========================================================================

(let [args *command-line-args*
      dry-run? (some #{"--dry-run"} args)
      files (remove #(str/starts-with? % "--") args)]
  (when (empty? files)
    (println "Usage: bb scripts/migrate_mui.bb [--dry-run] file1.cljs [file2.cljs ...]")
    (System/exit 1))
  (doseq [f files]
    (println (str "Processing " f "..."))
    (when-not (.exists (java.io.File. f))
      (println (str "  ERROR: File not found: " f))
      (System/exit 1))
    (migrate-file! f {:dry-run dry-run?}))
  (println "Done."))
