(ns lipas-ui.mui
  (:refer-clojure :exclude [list])
  (:require [cljsjs.material-ui]
            [goog.object :as gobj]
            [reagent.core :as r]
            [camel-snake-kebab.core :refer [->camelCase]]
            [camel-snake-kebab.extras :refer [transform-keys]]))

(defn ->mui-theme [opts]
  (->> opts
       (transform-keys ->camelCase)
       clj->js
       js/MaterialUIStyles.createMuiTheme))

(comment (with-styles {:button {:background "red"}}))
(defn with-styles [styles]
  (->> styles
       (transform-keys ->camelCase)
       clj->js
       js/MaterialUIStyles.withStyles))

(comment (get-color "blue"))
(comment (get-color :blue "300"))
(defn get-color
  "Args can be strings or keywords.

  (get-color \"blue\")
  (get-color :blue \"300\")"
  [& args]
  (->> args
       (mapv name)
       (apply (partial gobj/getValueByKeys js/MaterialUI "colors"))))

(def jyu-styles {:typography
                 {:font-family "Aleo, serif"}
                 :palette
                 {:type "dark"
                  :primary {:main "#002957"}
                  :secondary {:main "#f1563f"}}})

(def jyu-theme (->mui-theme jyu-styles))

(defn mui->reagent [mui-name]
  (r/adapt-react-class (gobj/get js/MaterialUI mui-name)))

(def native-btn (gobj/get js/MaterialUI "Button"))

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
(def form-label (mui->reagent "FormLabel"))
(def form-group (mui->reagent "FormGroup"))
(def button (mui->reagent "Button"))
(def hidden (mui->reagent "Hidden"))
