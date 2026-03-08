(ns lipas.ui.mui
  (:require ["@mui/material/styles" :refer [createTheme ThemeProvider useTheme]]
            ["@mui/material/useMediaQuery$default" :as useMediaQuery]
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
  {:font-family "Aleo, serif"
   :font-weight 700
   :letter-spacing "+0.025em"
   :text-transform "uppercase"})

(def headline-common
  {:font-family "Lato, serif"
   :font-weight 700
   :text-transform "uppercase"})

(def jyu-styles-dark
  {:typography
   {:font-family "Lato, sans-serif"

    :h1 headline-common
    :h2 headline-common
    :h3 headline-common
    :h4 headline-common
    :h5 headline-common
    :h6 headline-common

    :body1
    {:font-weight 400
     :line-height 1.4
     :letter-spacing "-0,025em"}
    :body2
    {:font-weight 700
     :line-height 1.4
     :letter-spacing "-0,025em"}
    :button
    {:font-weight 700}}
   :palette
   {:mode "dark"
    :primary {:main primary}
    :secondary {:main secondary}
    :gray1 {:main gray1}
    :text {:disabled "rgba(255,255,255,0.88)"}}
   :components
   {:MuiCardHeader {:styleOverrides {:title {:fontSize "2rem"}
                                     :action {:mt 0}}}
    :MuiTooltip {:styleOverrides {:tooltip {:fontSize "0.8rem"}}}
    :MuiStepIcon {:styleOverrides {:root {:fill "rgba(0,0,0,0.5)"}}}
    :MuiInputLabel {:styleOverrides {:root {:color "gray"}
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
    :MuiIconButton {:defaultProps {:size "large"}}
    :MuiStepLabel {:styleOverrides {:label {:fontWeight 500}}}}})

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

;; ThemeProvider uses a named export, not $default — keep as alias
(def mui-theme-provider (r/adapt-react-class ThemeProvider))

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
