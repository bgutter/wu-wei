(ns wu-wei.backend)

(defn handler [req]
  (println "HIT ME BABY")
  {:status 404
   :headers {"Content-Type" "text/html"}
   :body "Yep the server failed to find it."})
