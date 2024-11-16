(ns wu-wei.backend
  (:require
   [compojure.core :refer [defroutes GET PATCH PUT]]
   [compojure.coercions :refer [as-int]]
   [compojure.route :as route]
   [clojure.data.json :as json]))

(def app-data-path (str (System/getProperty "user.home") "/wu-wei/task-data.edn"))

(declare list-table task-table)
(defn write-all-data
  "Dump all backend state to `app-data-path'."
  []
  (with-open [wr (clojure.java.io/writer app-data-path)]
    (.write wr
     (with-out-str
       (clojure.pprint/pprint
        {:list-table list-table
         :task-table @task-table})))))

(defn read-all-data
  "Load all backend state from `app-data-path'"
  []
  (let
      [{lists :list-table tasks :task-table} (clojure.edn/read-string (slurp app-data-path))]
    (def list-table lists)
    (def task-table (atom tasks))))

;; Read all app data before continuing
(read-all-data)

(defn task-by-id
  "Get task matching ID."
  [id]
  (first (clojure.set/select #(= (:id %) id) @task-table)))

(defn next-free-id []
  (inc (apply max (filter some? (map :id @task-table)))))

(defn update-task
  "Update fields for a given task.
  Task partial data must include :id."
  [task-partial]
  (let
      [id (:id task-partial)
       orig-task (task-by-id id)
       new-task (merge orig-task task-partial)
       dropped-table (remove #(= (:id %) id) @task-table)
       updated-table (set (conj dropped-table new-task))]
    (reset! task-table updated-table)
    (println "Updating task " orig-task " to " new-task " -- " updated-table)
    (write-all-data)))

(defn create-task
  "Slap an :id in here and add it to the table."
  [task-partial]
  (let
      [id            (next-free-id)
       new-task      (assoc task-partial :id id)
       updated-table (set (conj @task-table new-task))]
    (reset! task-table updated-table)
    (println "Added new task " new-task)
    new-task))

(defroutes handler

   (GET "/test" []
     {:status 200
      :body "Ring has been tested"})

  (PUT "/task" request
       (let [body (-> (:body request)
                        slurp
                        read-string)]
         (let [new-task (create-task body)]
           {:headers {"Content-type" "text/edn"}
            :status  200
            :body    (pr-str new-task)})))

   (GET "/task/by-id/:id" [id :<< as-int]
     {:headers {"Content-type" "text/edn"}
      :status  200
      :body    (pr-str (task-by-id id))})

  (PATCH "/task" request
         (let [body (-> (:body request)
                        slurp
                        read-string)]
           (update-task body)
           {:status 200}))

   (GET "/task/all" []
     {:headers {"Content-type" "text/edn"}
      :status  200
      :body    (pr-str @task-table)})

   (GET "/list/all" []
     {:headers {"Content-type" "text/edn"}
      :status  200
      :body    (pr-str list-table)})

   (route/not-found
    {:status 404
     :body "<h1>Page not found</h1>"}))

