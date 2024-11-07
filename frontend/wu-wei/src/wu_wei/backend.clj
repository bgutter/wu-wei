(ns wu-wei.backend
  (:require
   [compojure.core :refer [defroutes GET]]
   [compojure.route :as route]
   [clojure.data.json :as json]))

(defroutes handler

  (GET "/test" []
       {:status 200
        :body "Ring has been tested"})

  (GET "/task/by-id/:id" [id]
        {:headers {"Content-type" "application/json"}
         :status  200
         :body    (json/write-str {:data [1 2 3 4]})})

  (route/not-found
   {:status 404
    :body "<h1>Page not found</h1>"}))
