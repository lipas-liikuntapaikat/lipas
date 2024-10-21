(ns lipas.ui.mui
  (:refer-clojure :exclude [list])
  (:require ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/Autocomplete$default" :as Autocomplete]
            ["@mui/material/Avatar$default" :as Avatar]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardActions$default" :as CardActions]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/CssBaseline$default" :as CssBaseline]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/Drawer$default" :as Drawer]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/FormHelperText$default" :as FormHelperText]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/InputAdornment$default" :as InputAdornment]
            ["@mui/material/InputLabel$default" :as InputLabel]
            ["@mui/material/LinearProgress$default" :as LinearProgress]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemIcon$default" :as ListItemIcon]
            ["@mui/material/ListItemSecondaryAction$default" :as ListItemSecondaryAction]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Menu$default" :as Menu]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Popper$default" :as Popper]
            ["@mui/material/Radio$default" :as Radio]
            ["@mui/material/RadioGroup$default" :as RadioGroup]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Slide$default" :as Slide]
            ["@mui/material/Slider$default" :as Slider]
            ["@mui/material/Snackbar$default" :as Snackbar]
            ["@mui/material/SnackbarContent$default" :as SnackbarContent]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Step$default" :as Step]
            ["@mui/material/StepContent$default" :as StepContent]
            ["@mui/material/StepLabel$default" :as StepLabel]
            ["@mui/material/Stepper$default" :as Stepper]
            ["@mui/material/SvgIcon$default" :as SvgIcon]
            ["@mui/material/SwipeableDrawer$default" :as SwipeableDrawer]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableContainer$default" :as TableContainer]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TablePagination$default" :as TablePagination]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/TableSortLabel$default" :as TableSortLabel]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            ["@mui/material/Unstable_Grid2$default" :as Grid2]
            ["@mui/material/styles" :refer [createTheme ThemeProvider useTheme]]
            ["@mui/material/useMediaQuery$default" :as useMediaQuery]
            ["@mui/material/AppBar$default" :as AppBar] ; ["@mui/material/withWidth$default" :as withWidth]
            [camel-snake-kebab.core :refer [convert-case]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.string :as s]
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
   {:mode      "dark"
    :primary   {:main primary}
    :secondary {:main secondary}
    :gray1     {:main gray1}
    :text      {:disabled "rgba(255,255,255,0.88)"}}
   :components
   {:MuiCardHeader {:styleOverrides {:title {:fontSize "2rem"}
                                     :action {:mt 0}}}
    :MuiTooltip {:styleOverrides {:tooltip {:fontSize "0.8rem"}}}
    :MuiStepIcon {:styleOverrides {:root {:fill "rgba(0,0,0,0.5)"}}}
    :MuiInputLabel {:styleOverrides {:root   {:color "gray"}
                                     :shrink {:color "inherit"}}}
    ;; MUI v4 used body2 font-size for <body>
    :MuiCssBaseline {:styleOverrides {:body {:fontSize "0.875rem"
                                             :lineHeight 1.43
                                             :letterSpacing "0.01071rem"
                                             ;; Use light mode background color for <body>
                                             :backgroundColor "#fafafa"}}}
    :MuiAppBar {:styleOverrides {:root {;; Disable gradient
                                        :backgroundImage "none"}}}
    :MuiLink {:defaultProps {:underline "hover"}}
    :MuiIconButton {:defaultProps {:size "large"}}}})

(def jyu-styles-light
  (utils/deep-merge
    jyu-styles-dark
    {:palette
     {:mode "light"
      :text {:disabled "rgba(0,0,0,0.88)"}}
     :typography
     {:body1 {:color primary}
      :body2 {:color primary}}
     :components
     {:MuiCardHeader {:styleOverrides {:title {:color secondary}}}}}))

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
(def linear-progress (r/adapt-react-class LinearProgress))
(def collapse (r/adapt-react-class Collapse))
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
;; In v5 regular Grid item only has top and left padding,
;; the new v2 Grid has padding all around.
(def grid (r/adapt-react-class Grid))
(def grid2 (r/adapt-react-class Grid2))
#_(def grow (r/adapt-react-class Grow))
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
(def radio (r/adapt-react-class Radio))
(def radio-group (r/adapt-react-class RadioGroup))
(def select (r/adapt-react-class Select))
(def slide (r/adapt-react-class Slide))
(def slider (r/adapt-react-class Slider))
(def snackbar (r/adapt-react-class Snackbar))
(def snackbar-content (r/adapt-react-class SnackbarContent))
(def stack (r/adapt-react-class Stack))
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

(defn use-width []
  (let [theme (useTheme)
        breakpoints (.-breakpoints theme)
        sm (useMediaQuery (.up breakpoints "sm"))
        md (useMediaQuery (.up breakpoints "md"))
        lg (useMediaQuery (.up breakpoints "lg"))
        xl (useMediaQuery (.up breakpoints "xl"))]
    (cond
      xl "xl"
      lg "lg"
      md "md"
      sm "sm"
      :else "xs")))
