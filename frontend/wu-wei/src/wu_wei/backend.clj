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

(def app-data-path
  "Where entity data will be stored as an EDN.
  This will be deprecated soon -- we're going to graduate to a database eventually."
  (str (System/getProperty "user.home") "/wu-wei/task-data.edn"))

(add-watch entity-table
           :update-disk-for-entity-table
           (fn [key atom old-state new-state]
             (let [edn-data (with-out-str (clojure.pprint/pprint new-state))]
               (with-open [writer (clojure.java.io/writer app-data-path)]
                 (-> writer (.write edn-data))))))

(defn read-entities-from-disk
  "Load all backend state from `app-data-path'"
  []
  (let
      [entities (clojure.edn/read-string (slurp app-data-path))]
    (reset! entity-table entities)))

;; Read all app data before continuing
(read-entities-from-disk)

(defn entity-by-id
  [id]
  (get @entity-table id))

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
    (println "!!! Backend updating " partial-entity)
    (swap! entity-table assoc id updated-entity)))

(defn create-entity
  "Slap an :id in here and add it to the table."
  [partial-entity]
  (let
      [id         (next-free-id)
       new-entity (assoc partial-entity :id id)]
    (swap! entity-table assoc id new-entity)
    new-entity))

(defn filter-entities
  ""
  [query-forms]
  (let [match-fn (entities/compile-query query-forms entity-by-id)]
    (filter #(match-fn (second %)) @entity-table)))

;;
;; Request Processing
;;

(defn edn-from-request
  "Parse a request body as EDN data."
  [request]
  (-> (:body request) slurp read-string))

(defn edn-response
  "Create a Response with an EDN body payload."
  [status-code response]
  (let
      [response-data (pr-str response)]
    (println (str "backend -> frontend: '" response-data  "'"))
    {:headers {"Content-type" "text/edn"}
     :status  status-code
     :body    response-data}))

(defroutes handler

  ;; Getting Entities
  ;; GET /entity/id
  ;;     Body: None
  ;;     Response: EDN data describing the entity
  ;; POST /search-entities with predicate vector as EDN in body

  (GET "/entity/:id" [id :<< as-int]
     (edn-response 200 (entity-by-id id)))

  (POST "/search-entities" request
     (let [query-forms (edn-from-request request)]
       (let
           [ids-matching-query (into [] (map #(:id (second %)) (filter-entities query-forms)))]
         (edn-response 200 ids-matching-query))))

  ;; Creating and editing entities
  ;; PUT or POST /entitiy
  ;;     Body: EDN representation of a NEW entity.
  ;;           Should have no :id property.
  ;;     Response: id of created entity.
  ;; PATCH /entity
  ;;     Body: EDN representation of a MODIFIED entity
  ;;           Must have a valid :id property
  ;;     Response: Nothing.

  (PUT "/entity" request
    (let [new-entity (edn-from-request request)]
      (edn-response 200 (:id (create-entity new-entity)))))

  (POST "/entity" request
    (let [new-entity (edn-from-request request)]
      (edn-response 200 (:id (create-entity new-entity)))))

  (PATCH "/entity" request
    (let [updated-entity (edn-from-request request)]
      (edn-response 200 (do
                          (update-entity updated-entity)
                          nil))))

  (route/not-found
   {:status 404
    :body "<h1>Page not found</h1>"}))

