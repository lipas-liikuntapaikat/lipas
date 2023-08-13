(ns lipas.ui.mui
  (:refer-clojure :exclude [list])
  (:require
   ["@material-ui/core/Accordion$default" :as Accordion]
   ["@material-ui/core/AccordionDetails$default" :as AccordionDetails]
   ["@material-ui/core/AccordionSummary$default" :as AccordionSummary]
   ["@material-ui/core/AppBar$default" :as AppBar]
   ["@material-ui/core/Avatar$default" :as Avatar]
   ["@material-ui/core/Button$default" :as Button]
   ["@material-ui/core/Card$default" :as Card]
   ["@material-ui/core/CardActions$default" :as CardActions]
   ["@material-ui/core/CardContent$default" :as CardContent]
   ["@material-ui/core/CardHeader$default" :as CardHeader]
   ["@material-ui/core/Checkbox$default" :as Checkbox]
   ["@material-ui/core/Chip$default" :as Chip]
   ["@material-ui/core/CircularProgress$default" :as CircularProgress]
   ["@material-ui/core/CssBaseline$default" :as CssBaseline]
   ["@material-ui/core/Dialog$default" :as Dialog]
   ["@material-ui/core/DialogActions$default" :as DialogActions]
   ["@material-ui/core/DialogContent$default" :as DialogContent]
   ["@material-ui/core/DialogTitle$default" :as DialogTitle]
   ["@material-ui/core/Divider$default" :as Divider]
   ["@material-ui/core/Drawer$default" :as Drawer]
   ["@material-ui/core/Fab$default" :as Fab]
   ["@material-ui/core/FormControl$default" :as FormControl]
   ["@material-ui/core/FormControlLabel$default" :as FormControlLabel]
   ["@material-ui/core/FormLabel$default" :as FormLabel]
   ["@material-ui/core/FormGroup$default" :as FormGroup]
   ["@material-ui/core/FormHelperText$default" :as FormHelperText]
   ["@material-ui/core/Grid$default" :as Grid]
   ["@material-ui/core/Hidden$default" :as Hidden]
   ["@material-ui/core/Icon$default" :as Icon]
   ["@material-ui/core/IconButton$default" :as IconButton]
   ["@material-ui/core/InputAdornment$default" :as InputAdornment]
   ["@material-ui/core/InputLabel$default" :as InputLabel]
   ["@material-ui/core/Link$default" :as Link]
   ["@material-ui/core/List$default" :as List]
   ["@material-ui/core/ListItem$default" :as ListItem]
   ["@material-ui/core/ListItemIcon$default" :as ListItemIcon]
   ["@material-ui/core/ListItemSecondaryAction$default" :as ListItemSecondaryAction]
   ["@material-ui/core/ListItemText$default" :as ListItemText]
   ["@material-ui/core/Menu$default" :as Menu]
   ["@material-ui/core/MenuItem$default" :as MenuItem]
   ["@material-ui/core/Paper$default" :as Paper]
   ["@material-ui/core/Popper$default" :as Popper]
   ["@material-ui/core/Select$default" :as Select]
   ["@material-ui/core/Slide$default" :as Slide]
   ["@material-ui/core/Slider$default" :as Slider]
   ["@material-ui/core/Snackbar$default" :as Snackbar]
   ["@material-ui/core/SnackbarContent$default" :as SnackbarContent]
   ["@material-ui/core/Step$default" :as Step]
   ["@material-ui/core/StepContent$default" :as StepContent]
   ["@material-ui/core/StepLabel$default" :as StepLabel]
   ["@material-ui/core/Stepper$default" :as Stepper]
   ["@material-ui/core/SvgIcon$default" :as SvgIcon]
   ["@material-ui/core/SwipeableDrawer$default" :as SwipeableDrawer]
   ["@material-ui/core/Switch$default" :as Switch]
   ["@material-ui/core/Tab$default" :as Tab]
   ["@material-ui/core/Table$default" :as Table]
   ["@material-ui/core/TableBody$default" :as TableBody]
   ["@material-ui/core/TableContainer$default" :as TableContainer]
   ["@material-ui/core/TableCell$default" :as TableCell]
   ["@material-ui/core/TableHead$default" :as TableHead]
   ["@material-ui/core/TablePagination$default" :as TablePagination]
   ["@material-ui/core/TableRow$default" :as TableRow]
   ["@material-ui/core/TableSortLabel$default" :as TableSortLabel]
   ["@material-ui/core/Tabs$default" :as Tabs]
   ["@material-ui/core/TextField$default" :as TextField]
   ["@material-ui/core/Toolbar$default" :as Toolbar]
   ["@material-ui/core/Tooltip$default" :as Tooltip]
   ["@material-ui/core/Typography$default" :as Typography]
   ["@material-ui/core/styles" :refer [createTheme ThemeProvider]]
   ["@material-ui/core/withWidth$default" :as withWidth]
   ["@material-ui/lab/Autocomplete$default" :as Autocomplete]
   #_["@material-ui/core/CardMedia$default" :as CardMedia]
   #_["@material-ui/core/AccordionActions$default" :as AccordionActions]
   #_["@material-ui/core/Fade$default" :as Fade]
   #_["@material-ui/core/FormLabel$default" :as FormLabel]
   #_["@material-ui/core/Grow$default" :as Grow]
   #_["@material-ui/core/MenuList$default" :as MenuList]
   #_["@material-ui/core/Radio$default" :as Radio]
   #_["@material-ui/core/RadioGroup$default" :as RadioGroup]
   #_["@material-ui/core/Zoom$default" :as Zoom]
   [camel-snake-kebab.core :refer [convert-case]]
   [camel-snake-kebab.extras :refer [transform-keys]]
   [clojure.string :as s]
   [goog.object :as gobj]
   [lipas.utils :as utils]
   [reagent.core :as r]))

(defn keyword->PasCamelCase
  "Converts keywords to PascalCase or camelCase
  respecting case of the first character."
  [kw & rest]
  (keyword (convert-case identity s/capitalize "" (name kw) rest)))

(comment
  (= (keyword->PasCamelCase :kissa-metso) :kissaMetso)
  (= (keyword->PasCamelCase :Kissa-metso) :KissaMetso))

(defn ->mui-theme [opts]
  (->> opts
       (transform-keys keyword->PasCamelCase)
       clj->js
       createTheme))

(def primary "#002957")
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
    :text      {:disabled "rgba(255,255,255,0.88)"}}
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
    {:root      {:fill "rgba(0,0,0,0.5)"}
     :active    {:fill secondary}
     :completed {:fill primary}}
    :MuiInputLabel
    {:root   {:color "gray"}
     :shrink {:color "inherit"}}}})


(def jyu-styles-light
  (utils/deep-merge
   jyu-styles-dark
   {:palette
    {:type "light"
     :text {:disabled "rgba(0,0,0,0.88)"}}
    :typography
    {:body1 {:color primary}
     :body2 {:color primary}}
    :overrides
    {:Mui-card-header
     {:title {:color secondary}}}}))

(def jyu-theme-dark (->mui-theme jyu-styles-dark))
(def jyu-theme-light (->mui-theme jyu-styles-light))

(comment
  (-> jyu-theme-dark (js->clj :keywordize-keys true)))

(def autocomplete (r/adapt-react-class Autocomplete))
(def app-bar (r/adapt-react-class AppBar))
(def avatar (r/adapt-react-class Avatar))
(def button (r/adapt-react-class Button))
(def card (r/adapt-react-class Card))
(def card-actions (r/adapt-react-class CardActions))
(def card-content (r/adapt-react-class CardContent))
(def card-header (r/adapt-react-class CardHeader))
#_(def card-media (r/adapt-react-class CardMedia))
(def checkbox (r/adapt-react-class Checkbox))
(def chip (r/adapt-react-class Chip))
(def circular-progress (r/adapt-react-class CircularProgress))
(def css-baseline (r/adapt-react-class CssBaseline))
(def dialog (r/adapt-react-class Dialog))
(def dialog-actions (r/adapt-react-class DialogActions))
(def dialog-content (r/adapt-react-class DialogContent))
(def dialog-title (r/adapt-react-class DialogTitle))
(def divider (r/adapt-react-class Divider))
(def drawer (r/adapt-react-class Drawer))
(def expansion-panel (r/adapt-react-class Accordion))
#_(def expansion-panel-actions (r/adapt-react-class AccordionActions))
(def expansion-panel-details (r/adapt-react-class AccordionDetails))
(def expansion-panel-summary (r/adapt-react-class AccordionSummary))
(def fab (r/adapt-react-class Fab))
#_(def fade (r/adapt-react-class Fade))
(def form-control (r/adapt-react-class FormControl))
(def form-control-label (r/adapt-react-class FormControlLabel))
(def form-label (r/adapt-react-class FormLabel))
(def form-group (r/adapt-react-class FormGroup))
(def form-helper-text (r/adapt-react-class FormHelperText))
#_(def form-label (r/adapt-react-class FormLabel))
(def grid (r/adapt-react-class Grid))
#_(def grow (r/adapt-react-class Grow))
(def hidden (r/adapt-react-class Hidden))
(def icon (r/adapt-react-class Icon))
(def icon-button (r/adapt-react-class IconButton))
(def input-adornment (r/adapt-react-class InputAdornment))
(def input-label (r/adapt-react-class InputLabel))
(def link (r/adapt-react-class Link))
(def list (r/adapt-react-class List))
(def list-item (r/adapt-react-class ListItem))
(def list-item-icon (r/adapt-react-class ListItemIcon))
(def list-item-secondary-action (r/adapt-react-class ListItemSecondaryAction))
(def list-item-text (r/adapt-react-class ListItemText))
(def menu (r/adapt-react-class Menu))
(def menu-item (r/adapt-react-class MenuItem))
#_(def menu-list (r/adapt-react-class MenuList))
(def mui-theme-provider (r/adapt-react-class ThemeProvider))
(def paper (r/adapt-react-class Paper))
(def popper (r/adapt-react-class Popper))
#_(def radio (r/adapt-react-class Radio))
#_(def radio-group (r/adapt-react-class RadioGroup))
(def select (r/adapt-react-class Select))
(def slide (r/adapt-react-class Slide))
(def slider (r/adapt-react-class Slider))
(def snackbar (r/adapt-react-class Snackbar))
(def snackbar-content (r/adapt-react-class SnackbarContent))
(def step (r/adapt-react-class Step))
(def step-content (r/adapt-react-class StepContent))
(def step-label (r/adapt-react-class StepLabel))
(def stepper (r/adapt-react-class Stepper))
(def svg-icon (r/adapt-react-class SvgIcon))
(def swipeable-drawer (r/adapt-react-class SwipeableDrawer))
(def switch (r/adapt-react-class Switch))
(def tab (r/adapt-react-class Tab))
(def table (r/adapt-react-class Table))
(def table-body (r/adapt-react-class TableBody))
(def table-cell (r/adapt-react-class TableCell))
(def table-container (r/adapt-react-class TableContainer))
(def table-head (r/adapt-react-class TableHead))
(def table-pagination (r/adapt-react-class TablePagination))
(def table-row (r/adapt-react-class TableRow))
(def table-sort-label (r/adapt-react-class TableSortLabel))
(def tabs (r/adapt-react-class Tabs))
(def text-field (r/adapt-react-class TextField))
(def tool-bar (r/adapt-react-class Toolbar))
(def tooltip (r/adapt-react-class Tooltip))
(def typography (r/adapt-react-class Typography))
#_(def zoom (r/adapt-react-class Zoom))

(def with-width* withWidth)
