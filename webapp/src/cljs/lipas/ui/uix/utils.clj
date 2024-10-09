(ns lipas.ui.uix.utils
  (:require [clojure.string :as str]
            [uix.compiler.attributes :as attr]
            [uix.compiler.js :as js]))

(defmacro spread-obj
  "Spread multiple JS objects into one JS object using JS spread {...} syntax.

  Creates a shallow copy."
  [& props]
  (list* 'js*
         (str "({"
              (str/join ", " (repeat (count props) "...~{}"))
              "})")
         props))

;; Could also just emit Object.assign, a bit simpler and doesn't need js*.
;; Spread operator might be a bit faster in some cases, but depends on
;; the browser and many variables probably.
;; https://clojurians.slack.com/archives/CNMR41NKB/p1701179422380989
;; We might not want to keep this optimized version in the repo and
;; just use simpler code, this isn't needed probably that often anyway.
;; Right now this is here for testing and investigation.
(defmacro spread-props
  "Combines some Cljs and JS properties into one JS object.
  This works for element properties.

  NOTE: Uses shallow transform."
  [& props]
  ;; One optimization idea: if the first attr is static cljs map, emit
  ;; the converted JS object and spread rest into that (cljs.compiler/emit-js-object)
  `(spread-obj
    ~@(for [attrs props]
        ;; Similar to uix.compiler.aot/compile-attrs :element
        ;; If we have a static value, we can convert it into JS form in macro-compilation,
        ;; but check if the style attr is dynamic and convert that runtime if needed.
        (if (or (map? attrs) (nil? attrs))
          (cond-> attrs
            (and (some? (:style attrs))
                 (not (map? (:style attrs))))
            (assoc :style `(uix.compiler.attributes/convert-prop-value-shallow ~(:style attrs)))
            :always (attr/compile-attrs)
            :always (js/to-js))
          `(uix.compiler.attributes/convert-prop-value-shallow ~attrs)))))

#_
(binding [*out* *err*]
  (println (clojure.walk/macroexpand-all '(let [z (js-object :b 2)]
                                            (spread (js-object :a 1) z))))
  (println (clojure.walk/macroexpand-all '(let [z {:b 2}]
                                            (spread-props {:style (merge {:font "123"} {:margin 5})} z))))
  )

