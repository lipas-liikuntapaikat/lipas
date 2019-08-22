(ns lipas.ui.mui
  (:refer-clojure :exclude [list])
  (:require
   [goog.object :as gobj]
   [clojure.string :as s]
   [reagent.core :as r]
   [camel-snake-kebab.core :refer [convert-case]]
   [camel-snake-kebab.extras :refer [transform-keys]]
   [lipas.utils :as utils]))

(comment (= (keyword->PasCamelCase :kissa-metso) :kissaMetso))
(comment (= (keyword->PasCamelCase :Kissa-metso) :KissaMetso))
(defn keyword->PasCamelCase
  "Converts keywords to PascalCase or camelCase
  respecting case of the first character."
  [kw & rest]
  (keyword (convert-case identity s/capitalize "" (name kw) rest)))

(def create-mui-theme (gobj/get js/mui "createMuiTheme"))

(defn ->mui-theme [opts]
  (->> opts
       (transform-keys keyword->PasCamelCase)
       clj->js
       create-mui-theme))

(def primary "#002957 ")
(def primary2 "rgb(0, 41, 87, 0.5)")
(def primary3 "rgb(0, 41, 87, 0.3)")
(def secondary "#f1563f")
(def secondary2 "rgba(241, 86, 63, 0.9)")
(def secondary3 "rgba(241, 86, 63, 0.5)")
(def gold "#C29A5B")
(def gray1 "rgba(199, 201, 200, 1.0)")
(def gray2 "rgba(199, 201, 200, 0.5)")
(def gray3 "rgba(199, 201, 200, 0.3)")

(def headline-aleo
  {:font-family    "Aleo, serif"
   :font-weight    700
   :letter-spacing "+0.025em"
   :text-transform "uppercase"})

(def headline-common
  {:font-family    "Lato, serif"
   :font-weight    700
   :text-transform "uppercase"})

(def jyu-styles-dark
  {:typography
   {:use-next-variants true
    :font-family       "Lato, sans-serif"

    :h1 headline-common
    :h2 headline-common
    :h3 headline-common
    :h4 headline-common
    :h5 headline-common
    :h6 headline-common

    :body1
    {:font-weight    400
     :line-height    1.4
     :letter-spacing "-0,025em"}
    :body2
    {:font-weight    700
     :line-height    1.4
     :letter-spacing "-0,025em"}
    :button
    {:font-weight 700}}
   :palette
   {:type      "dark"
    :primary   {:main primary}
    :secondary {:main secondary}
    :text      {:disabled "rgba(255, 255, 255, 0.88)"}}
   :overrides
   {:Mui-card-header
    {:title
     {:font-size "2rem"}
     :action
     {:margin-top 0}}
    :Mui-tooltip
    {:tooltip
     {:font-size "0.8rem"}}
    :Mui-step-icon
    {:root      {:fill "rgba(0, 0, 0, 0.5)"}
     :active    {:fill primary}
     :completed {:fill primary}
     :text
     {:fill "white"}}}})

(def jyu-styles-light
  (utils/deep-merge
   jyu-styles-dark
   {:palette
    {:type "light"
     :text {:disabled "rgba(0, 0, 0, 0.88)"}}
    :typography
    {:body1 {:color primary}
     :body2 {:color primary}}
    :overrides
    {:Mui-card-header
     {:title {:color secondary}}}}))

(def jyu-theme-dark (->mui-theme jyu-styles-dark))
(def jyu-theme-light (->mui-theme jyu-styles-light))

(defn mui->reagent [mui-name]
  (r/adapt-react-class (gobj/get js/mui mui-name)))

(def app-bar (mui->reagent "AppBar"))
(def avatar (mui->reagent "Avatar"))
(def button (mui->reagent "Button"))
(def card (mui->reagent "Card"))
(def card-actions (mui->reagent "CardActions"))
(def card-content (mui->reagent "CardContent"))
(def card-header (mui->reagent "CardHeader"))
(def card-media (mui->reagent "CardMedia"))
(def checkbox (mui->reagent "Checkbox"))
(def chip (mui->reagent "Chip"))
(def circular-progress (mui->reagent "CircularProgress"))
(def css-baseline (mui->reagent "CssBaseline"))
(def dialog (mui->reagent "Dialog"))
(def dialog-actions (mui->reagent "DialogActions"))
(def dialog-content (mui->reagent "DialogContent"))
(def dialog-title (mui->reagent "DialogTitle"))
(def divider (mui->reagent "Divider"))
(def drawer (mui->reagent "Drawer"))
(def expansion-panel (mui->reagent "ExpansionPanel"))
(def expansion-panel-actions (mui->reagent "ExpansionPanelActions"))
(def expansion-panel-details (mui->reagent "ExpansionPanelDetails"))
(def expansion-panel-summary (mui->reagent "ExpansionPanelSummary"))
(def fab (mui->reagent "Fab"))
(def fade (mui->reagent "Fade"))
(def form-control (mui->reagent "FormControl"))
(def form-control-label (mui->reagent "FormControlLabel"))
(def form-group (mui->reagent "FormGroup"))
(def form-helper-text (mui->reagent "FormHelperText"))
(def form-label (mui->reagent "FormLabel"))
(def grid (mui->reagent "Grid"))
(def grow (mui->reagent "Grow"))
(def hidden (mui->reagent "Hidden"))
(def icon (mui->reagent "Icon"))
(def icon-button (mui->reagent "IconButton"))
(def input-adornment (mui->reagent "InputAdornment"))
(def input-label (mui->reagent "InputLabel"))
(def link (mui->reagent "Link"))
(def list (mui->reagent "List"))
(def list-item (mui->reagent "ListItem"))
(def list-item-icon (mui->reagent "ListItemIcon"))
(def list-item-secondary-action (mui->reagent "ListItemSecondaryAction"))
(def list-item-text (mui->reagent "ListItemText"))
(def menu (mui->reagent "Menu"))
(def menu-item (mui->reagent "MenuItem"))
(def menu-list (mui->reagent "MenuList"))
(def mui-theme-provider (mui->reagent "MuiThemeProvider"))
(def paper (mui->reagent "Paper"))
(def popper (mui->reagent "Popper"))
(def radio (mui->reagent "Radio"))
(def radio-group (mui->reagent "RadioGroup"))
(def select (mui->reagent "Select"))
(def slide (mui->reagent "Slide"))
(def snackbar (mui->reagent "Snackbar"))
(def snackbar-content (mui->reagent "SnackbarContent"))
(def speed-dial (mui->reagent "SpeedDial"))
(def speed-dial-action (mui->reagent "SpeedDialAction"))
(def speed-dial-icon (mui->reagent "SpeedDialIcon"))
(def step (mui->reagent "Step"))
(def step-content (mui->reagent "StepContent"))
(def step-label (mui->reagent "StepLabel"))
(def stepper (mui->reagent "Stepper"))
(def svg-icon (mui->reagent "SvgIcon"))
(def swipeable-drawer (mui->reagent "SwipeableDrawer"))
(def tab (mui->reagent "Tab"))
(def table (mui->reagent "Table"))
(def table-body (mui->reagent "TableBody"))
(def table-cell (mui->reagent "TableCell"))
(def table-head (mui->reagent "TableHead"))
(def table-pagination (mui->reagent "TablePagination"))
(def table-row (mui->reagent "TableRow"))
(def table-sort-label (mui->reagent "TableSortLabel"))
(def tabs (mui->reagent "Tabs"))
(def text-field (mui->reagent "TextField"))
(def tool-bar (mui->reagent "Toolbar"))
(def tooltip (mui->reagent "Tooltip"))
(def typography (mui->reagent "Typography"))
(def zoom (mui->reagent "Zoom"))

(def with-width* (.withWidth js/mui))
