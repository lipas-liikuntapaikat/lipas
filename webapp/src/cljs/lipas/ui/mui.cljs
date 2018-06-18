(ns lipas.ui.mui
  (:refer-clojure :exclude [list])
  (:require [material-ui]
            [goog.object :as gobj]
            [clojure.string :as s]
            [reagent.core :as r]
            [camel-snake-kebab.core :refer [convert-case]]
            [camel-snake-kebab.extras :refer [transform-keys]]))

(comment (= (keyword->PasCamelCase :kissa-metso) :kissaMetso))
(comment (= (keyword->PasCamelCase :Kissa-metso) :KissaMetso))
(defn keyword->PasCamelCase
  "Converts keywords to PascalCase or camelCase
  respecting case of the first character."
  [kw & rest]
  (keyword (convert-case identity s/capitalize "" (name kw) rest)))

(keys (js->clj js/MaterialUI))

(def create-mui-theme (gobj/get js/MaterialUI "createMuiTheme"))

(defn ->mui-theme [opts]
  (->> opts
       (transform-keys keyword->PasCamelCase)
       clj->js
       create-mui-theme))

(comment (get-color "blue"))
(comment (get-color :blue "300"))
(defn get-color
  "Args can be strings or keywords. Returns all colors if no args are given.

  (get-color)
  (get-color \"blue\")
  (get-color :blue \"300\")"
  [& args]
  (->> args
       (mapv name)
       (apply (partial gobj/getValueByKeys js/MaterialUI "colors"))))

(def primary "#002957")
(def secondary "#f1563f")

(def jyu-styles-dark {:typography
                      {:font-family "Aleo, serif"}
                      :palette
                      {:type "dark"
                       :primary {:main primary}
                       :secondary {:main secondary}}
                      :overrides
                      {:Mui-card-header
                       {:title {:color secondary}}}})

(def jyu-styles-light (assoc-in jyu-styles-dark [:palette :type] "light"))

(def jyu-theme-dark (->mui-theme jyu-styles-dark))
(def jyu-theme-light (->mui-theme jyu-styles-light))

(defn mui->reagent [mui-name]
  (r/adapt-react-class (gobj/get js/MaterialUI mui-name)))

(def css-baseline (mui->reagent "CssBaseline"))
(def mui-theme-provider (mui->reagent "MuiThemeProvider"))
(def app-bar (mui->reagent "AppBar"))
(def tool-bar (mui->reagent "Toolbar"))
(def typography (mui->reagent "Typography"))
(def icon (mui->reagent "Icon"))
(def svg-icon (mui->reagent "SvgIcon"))
(def icon-button (mui->reagent "IconButton"))
(def text-field (mui->reagent "TextField"))
(def grid (mui->reagent "Grid"))
(def paper (mui->reagent "Paper"))
(def card (mui->reagent "Card"))
(def card-content (mui->reagent "CardContent"))
(def card-header (mui->reagent "CardHeader"))
(def card-actions (mui->reagent "CardActions"))
(def menu (mui->reagent "Menu"))
(def menu-item (mui->reagent "MenuItem"))
(def menu-list (mui->reagent "MenuList"))
(def list (mui->reagent "List"))
(def list-item (mui->reagent "ListItem"))
(def list-item-icon (mui->reagent "ListItemIcon"))
(def list-item-text (mui->reagent "ListItemText"))
(def drawer (mui->reagent "Drawer"))
(def divider (mui->reagent "Divider"))
(def swipeable-drawer (mui->reagent "SwipeableDrawer"))
(def tabs (mui->reagent "Tabs"))
(def tab (mui->reagent "Tab"))
(def input-adornment (mui->reagent "InputAdornment"))
(def form-control (mui->reagent "FormControl"))
(def form-control-label (mui->reagent "FormControlLabel"))
(def form-label (mui->reagent "FormLabel"))
(def form-group (mui->reagent "FormGroup"))
(def form-helper-text (mui->reagent "FormHelperText"))
(def button (mui->reagent "Button"))
(def hidden (mui->reagent "Hidden"))
(def tooltip (mui->reagent "Tooltip"))
(def avatar (mui->reagent "Avatar"))
(def checkbox (mui->reagent "Checkbox"))
(def table (mui->reagent "Table"))
(def table-head (mui->reagent "TableHead"))
(def table-body (mui->reagent "TableBody"))
(def table-row (mui->reagent "TableRow"))
(def table-cell (mui->reagent "TableCell"))
(def dialog (mui->reagent "Dialog"))
(def dialog-title (mui->reagent "DialogTitle"))
(def dialog-content (mui->reagent "DialogContent"))
(def dialog-actions (mui->reagent "DialogActions"))
(def snackbar (mui->reagent "Snackbar"))
(def snackbar-content (mui->reagent "SnackbarContent"))
(def expansion-panel (mui->reagent "ExpansionPanel"))
(def expansion-panel-actions (mui->reagent "ExpansionPanelActions"))
(def expansion-panel-details (mui->reagent "ExpansionPanelDetails"))
(def expansion-panel-summary (mui->reagent "ExpansionPanelSummary"))
(def input-label (mui->reagent "InputLabel"))

(def with-styles* (gobj/get js/MaterialUI "withStyles"))

(defn with-styles [styles]
  (->> styles
       (transform-keys keyword->PasCamelCase)
       clj->js
       with-styles*))

;; (def red-bg {:root {:background-color "red"}})
;; (def red-btn (->styled red-bg "Button"))
(defn ->styled [styles component-name]
  (let [style-fn (with-styles styles)]
    (-> (gobj/get js/MaterialUI component-name)
        style-fn
        r/adapt-react-class)))
