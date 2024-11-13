(ns wu-wei.backend
  (:require
   [compojure.core :refer [defroutes GET PATCH]]
   [compojure.coercions :refer [as-int]]
   [compojure.route :as route]
   [clojure.data.json :as json]))

(def list-table
  #{{:id 1 :name "Work" :icon "👔"}
    {:id 2 :name "Dogs" :icon "🐕"}
    {:id 3 :name "Errands" :icon "🛒"}})

(def task-table
  (atom #{{:id 2 :list-id 1 :summary "Finish data cleaning script" }
          {:id 3 :list-id 1 :summary "Figure out why Clojure has metadata maps." }
          {:id 4 :list-id 1 :summary "Calibrate flux oximeter."}
          {:id 5 :list-id 1 :summary "Acquire synergy contract."}
          {:id 6 :list-id 1 :summary "Fly to the moon"}
          {:id 7 :list-id 1 :summary "Finish project foobar" :subtask-ids #{2 3 4 5 6 7} }
          {:id 1 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 8 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 9 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 10 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 11 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 12 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 13 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 14 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 15 :list-id 2 :summary "Walk the trans-america trail."}
          {:id 16 :list-id 2 :summary "Walk the trans-america trail."}
          {:id 17 :list-id 2 :summary "Walk the trans-america trail."}
          {:id 18 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 19 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 20 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 21 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 22 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 23 :list-id 1 :summary "Walk the trans-america trail."}
          {:id 24 :list-id 1 :summary "Walk the trans-america trail."}}))

(defn task-by-id
  "Get task matching ID."
  [id]
  (first (clojure.set/select #(= (:id %) id) @task-table)))

(defn all-lists
  "Get all lists"
  []
  list-table)

(defn update-task
  ""
  [task-partial]
  (let
      [id (:id task-partial)
       orig-task (task-by-id id)
       new-task (merge orig-task task-partial)
       dropped-table (remove #(= (:id %) id) @task-table)
       updated-table (set (conj dropped-table new-task))]
    (reset! task-table updated-table)
    (println "Updating task " orig-task " to " new-task " -- " updated-table)))

(defroutes handler

   (GET "/test" []
     {:status 200
      :body "Ring has been tested"})

   (GET "/task/by-id/:id" [id :<< as-int]
     {:headers {"Content-type" "text/edn"}
      :status  200
      :body    (pr-str (task-by-id id))})

  (PATCH "/task" request
         (let [body (-> (:body request)
                        slurp
                        read-string)]
           (update-task body)))

   (GET "/task/all" []
     {:headers {"Content-type" "text/edn"}
      :status  200
      :body    (pr-str @task-table)})

   (GET "/list/all" []
     {:headers {"Content-type" "text/edn"}
      :status  200
      :body    (pr-str (all-lists))})

   (route/not-found
    {:status 404
     :body "<h1>Page not found</h1>"}))
