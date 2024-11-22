(ns wu-wei.backend
  (:require
   [compojure.core :refer [defroutes GET PATCH PUT POST]]
   [compojure.coercions :refer [as-int]]
   [compojure.route :as route]
   [clojure.data.json :as json]
   [wu-wei.entities :as entities]))

(def entity-table
  "Atom containing a map of entity-id to entity."
  (atom {}))

(add-watch entity-table
           :update-disk-for-entity-table
           (fn [key atom old-state new-state]
             (let [edn-data (with-out-str (clojure.pprint/pprint new-state))]
               (with-open [writer (clojure.java.io/writer app-data-path)]
                 (-> writer (.write edn-data))))))

(def app-data-path
  "Where entity data will be stored as an EDN.
  This will be deprecated soon -- we're going to graduate to a database eventually."
  (str (System/getProperty "user.home") "/wu-wei/task-data.edn"))

(defn read-entities-from-disk
  "Load all backend state from `app-data-path'"
  []
  (let
      [entities (clojure.edn/read-string (slurp app-data-path))]
    (reset! entity-table entities)))

;; Read all app data before continuing
(read-entities-from-disk)

(defn next-free-id []
  (inc (apply max (filter some? (keys @entity-table)))))

(defn entity-by-id
  "Get entity matching ID."
  [id]
  (get @entity-table id))

(defn update-entity
  "Update fields for a given task.
  Task partial data must include :id."
  [partial-entity]
  (let
      [id (:id partial-entity)
       orig-entity (entity-by-id id)
       updated-entity (merge orig-entity partial-entity)]
    (swap! entity-table assoc id updated-entity)))

(defn create-entity
  "Slap an :id in here and add it to the table."
  [partial-entity]
  (let
      [id         (next-free-id)
       new-entity (assoc partial-entity :id id)]
    (swap! entity-table assoc id new-entity)
    new-entity))

(defn edn-from-request
  "Parse a request body as EDN data."
  [request]
  (-> (:body request) slurp read-string))

(defn edn-response
  "Create a Response with an EDN body payload."
  [status-code response]
  {:headers {"Content-type" "text/edn"}
   :status  status-code
   :body    (pr-str response)})

(defroutes handler

  (PUT "/entity" request
    (let [new-entity (edn-from-request request)]
      (edn-response 200 (create-entity new-entity))))

  (POST "/entity" request
    (let [new-entity (edn-from-request request)]
      (edn-response 200 (create-entity new-entity))))

  (GET "/entity/:id" [id :<< as-int]
     (edn-response 200 (entity-by-id id)))

  (PATCH "/entity" request
    (let [updated-entity (edn-from-request request)]
      (edn-response 200 (update-entity updated-entity))))

  (GET "/task/all" []
    (edn-response 200 (filter entities/task? @entity-table)))

  (route/not-found
   {:status 404
    :body "<h1>Page not found</h1>"}))

