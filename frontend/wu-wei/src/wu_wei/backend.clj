(ns wu-wei.backend
  (:require
   [compojure.core :refer [defroutes GET PATCH PUT POST DELETE]]
   [compojure.coercions :refer [as-int]]
   [compojure.route :as route]
   [clojure.data.json :as json]
   [wu-wei.entities :as entities]
   [wu-wei.entities.caching :as entity-cache]))

(def entity-cache-atom (atom (entity-cache/new-cache)))

(def app-data-path
  "Where entity data will be stored as an EDN.
  This will be deprecated soon -- we're going to graduate to a database eventually."
  (str (System/getProperty "user.home") "/wu-wei/task-data.edn"))

(add-watch entity-cache-atom
           :update-disk-for-entity-cache-atom
           (fn [key atom old-state new-state]
             (let [edn-data (with-out-str (clojure.pprint/pprint new-state))]
               (with-open [writer (clojure.java.io/writer app-data-path)]
                 (-> writer (.write edn-data))))))

(defn read-entities-from-disk
  "Load all backend state from `app-data-path'"
  []
  (let
      [entities-cache (clojure.edn/read-string (slurp app-data-path))]
    (reset! entity-cache-atom entities-cache)))

;; Read all app data before continuing
(read-entities-from-disk)

(defn next-free-id []
  (let [existing-keys (entity-cache/all-ids @entity-cache-atom)]
    (if (seq existing-keys)
      (inc (apply max existing-keys))
      0)))

(defn create-entity!
  "Slap an :id in here and add it to the table."
  [partial-entity]
  (println "Creating entity " partial-entity)
  (let
      [id         (next-free-id)
       new-entity (assoc partial-entity :id id)]
    (swap! entity-cache-atom entity-cache/set-entity-data 1 id new-entity)
    new-entity))

(defn delete-entity!
  [id]
  (swap! entity-cache-atom entity-cache/remove-entity-data id))

(defn filter-entities
  ""
  [query-forms]
  (entity-cache/query @entity-cache-atom query-forms))

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
    (println "GET /entity/" id)
    (edn-response 200 (entity-cache/lookup-id @entity-cache-atom id)))

  (DELETE "/entity/:id" [id :<< as-int]
    (println "DELETE /entity/" id)
    (delete-entity! id)
    (edn-response 200 nil))

  (POST "/search-entities" request
    (println "POST /search-entities")
    (let [query-forms (edn-from-request request)]
       (let
           [ids-matching-query (map :id (filter-entities query-forms))]
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
    (println "PUT /entity")
    (let [new-entity (edn-from-request request)]
      (edn-response 200 (:id (create-entity! new-entity)))))

  (POST "/entity" request
    (println "POST /entity")
    (let [new-entity (edn-from-request request)]
      (edn-response 200 (:id (create-entity! new-entity)))))

  (PATCH "/entity" request
    (println "PATCH /entity")
    (let [updated-entity (edn-from-request request)]
      (edn-response 200 (do
                          (swap! entity-cache-atom
                                 entity-cache/set-entity-data
                                 1
                                 (:id updated-entity)
                                 updated-entity)
                          nil))))

  (route/not-found
   {:status 404
    :body "<h1>Page not found</h1>"}))

