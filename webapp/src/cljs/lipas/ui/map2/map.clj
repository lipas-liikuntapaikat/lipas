(ns lipas.ui.map2.map)

(defmacro use-object
  "Executes to body to initialize the ref
  if the ref value is nil.
  Returns [ref ref-value]"
  [& body]
  `(let [object-ref# (uix/use-ref)]
     (when-not (.-current object-ref#)
       (set! (.-current object-ref#) (do ~@body)))
     [object-ref# (.-current object-ref#)]))
