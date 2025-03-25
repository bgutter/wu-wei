(ns wu-wei.util)

(defn ts-now
  "Get the current time as UTC seconds."
  []
  #?(:clj  (int (/ (.getTime (java.util.Date.)) 1000.))
     :cljs (js/Math.floor (/ (.getTime (js/Date.)) 1000))))

(defn time-from-str
  "Get UTC seconds from a time string"
  [time-string]
  #?(:clj (let [df (java.text.SimpleDateFormat. "yyyy-MM-dd")]
            (quot (.getTime (.parse df time-string)) 1000))
     :cljs (let [df (js/Date. time-string)]
             (quot (.getTime df) 1000))))

(defn tasks-sort-open-first [task-seq]
  (let
      [[done-tasks open-tasks] (partition-by (comp nil? #{:open} :status)
                                             (sort-by :status task-seq))]
    (concat open-tasks done-tasks)))
