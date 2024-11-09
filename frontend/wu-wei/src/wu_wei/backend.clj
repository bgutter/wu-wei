(ns wu-wei.backend
  (:require
   [compojure.core :refer [defroutes GET]]
   [compojure.coercions :refer [as-int]]
   [compojure.route :as route]
   [clojure.data.json :as json]))

(def list-table
  #{{:id 1 :name "Work" :icon "👔"}})

(def task-table
  #{{:id 1 :summary "Finish project foobar" :list-id 1}})

(defn task-by-id
  "Get task matching ID."
  [id]
  (clojure.set/select #(= (:id %) id) task-table))

(defn all-lists
  "Get all lists"
  []
  list-table)

(defroutes handler

  (GET "/test" []
       {:status 200
        :body "Ring has been tested"})

  (GET "/task/by-id/:id" [id :<< as-int]
       {:headers {"Content-type" "text/edn"}
        :status  200
        :body    (pr-str (task-by-id id))})

  (GET "/list/all" []
       {:headers {"Content-type" "text/edn"}
        :status  200
        :body    (pr-str (all-lists))})

  (route/not-found
   {:status 404
    :body "<h1>Page not found</h1>"}))
