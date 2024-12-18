(ns wu-wei.components.task-list-macros
  )

(defmacro on-enter-key
  [event & body]
  `(when (= (.-key ~event) "Enter")
     ~@body
     (-> ~event .preventDefault)))

(defmacro after-delay-ms
  [ms-delay & body]
  `(js/setTimeout (fn [] ~@body) ~ms-delay))

