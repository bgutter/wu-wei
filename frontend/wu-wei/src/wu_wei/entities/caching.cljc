(ns wu-wei.entities.caching
  (:require [wu-wei.entities :as entities]))

(defn new-cache
  "Create an empty cache"
  []
  (hash-map))

(defn declare-exist
  "Create an empty data node for a set of entity IDs. This lets the
  cache know that some ID exists, but data has not yet been fetched for it."
  [cache timestamp & ids-that-exist]
  (merge cache (into {} (map #(vector % {:retrieved timestamp :entity-data nil})) ids-that-exist)))

(defn set-entity-data
  "Return `cache` with new `entity-data` associated with ID `id` and given update `timestamp`"
  [cache timestamp id entity-data]
  (merge cache {id {:retrieved timestamp :entity-data entity-data}}))

(defn remove-entity-data
  "Return `cache` with data for entity id `id` removed"
  [cache id]
  (dissoc cache id))

(defn lookup-id
  "Retrieve entity data stored for given ID `id` in `cache`"
  [cache id]
  (if (contains? cache id)
    (let [cache-data (get cache id)]
      ;; (println (str "LOOKUP(" id ") => " cache-data))
      (:entity-data cache-data))
    nil))

;; TODO where should this live?

(defn downstream-tasks
  "A set of all tasks which are subtasks (direct or indirect) of `task`."
  [cache task]
  (let [direct-subtask-ids (:subtask-ids task)
        direct-subtasks    (map #(lookup-id cache %) direct-subtask-ids)
        recursion-results  (map #(downstream-tasks cache %) direct-subtasks)]
    (set (apply concat direct-subtasks recursion-results))))

(defn descendent-task?
  "Is A a descendent of B?"
  [cache task-a task-b]
  (contains? (downstream-tasks cache task-b) task-a))

;; End TODO

(defn compile-query
  "Produce a function that determines whether an entity matches the given entity filter, `query-forms`.

  `query-forms` should be either a keyword, or a vector of nested
  vectors and keywords. There are three classes of keywords that are
  recognized:

  - predicates
  - logical-groupings
  - dependency-projections

  Starting with an example:
    [:and :task? [:any-dependency [:due-before? \"2024-6-12\"]]

  - :and is a logical grouping.
  - :task? is a predicate.
  - :any-dependency is a dependency projection
  - [:due-before? \"2024-6-12\"] is a predicate with an argument.

  All together, this expression returns all entities which are tasks
  and have at least one dependency which is due before 2024-6-12.

  It will compile to a function that looks something like this:
  (fn [entity]
    (and
      (task? entity)
      (any? (for [dep (:dependencies entity)]
              (due-before? dep \"2024-6-12\")))))

  Supported Predicates
  ====================
  - :task?
      Does the entity implement the task interface?
  - :event?
      Does the entity implement the event interface?
  - [:due-before? DATE]
      Is the entity a task with a due date that is before DATE?
  - [:due-after? DATE]
      Is the entity a task with a due date that is after DATE?
  - [:due-between? A B]
      Is the entity a task with a due date between A and B?
  - [:occurs-before? DATE]
      Is the entity an event with a start-time before DATE?

  Supported Logical Groupings
  ===========================
  - [:and ...]
      Matches only if all following expressions match
  - [:or ...]
      Matches if any of the following expressions match

  Supported Dependency Projections
  ================================
  - [:all-dependencies ...]
      Matches only if *all* following expressions match for *all* dependencies
      of a task
  - [:any-dependency ...]
      Matches if *all* of the following expressions match *any* of the
      dependencies of a task
  "
  [cache query-forms]
  (let
      [parse-error              (fn [msg] (throw (ex-info msg)))
       always-false             (fn [& _] false)
       always-true              (fn [& _] true)
       require-all-recursions   (fn [ent args]
                                  (every? identity (map (fn [func] (func ent))
                                                        (map #(compile-query cache %) args))))
       require-any-recursion    (fn [ent args]
                                  (some identity (map (fn [func] (func ent))
                                                      (map #(compile-query cache %) args))))
       logical-not              (fn [ent args]
                                  (not-any? identity (map (fn [func] (func ent))
                                                          (map #(compile-query cache %) args))))
       check-occur-before       (fn [ent args] (apply entities/event-occurs-before? ent args))
       check-is-subtask-of      (fn [ent args] (entities/subtask? ent (lookup-id cache (first args))))
       check-is-descendent-of   (fn [ent args] (descendent-task? cache ent (lookup-id cache (first args))))
       keyword-predicate-map    {:task? #'entities/task?
                                 :event? #'entities/event?
                                 :milestone? #'entities/milestone?
                                 ;; :part-of-milestone? #(boolean (seq (milestones-upstream-of-task (lookup-entity-fn %))))
                                 :true always-true
                                 :false always-false}
       expression-predicate-map {:and require-all-recursions
                                 :or require-any-recursion
                                 :not logical-not
                                 :occurs-before? check-occur-before
                                 :subtask-of? check-is-subtask-of
                                 :descendent-of? check-is-descendent-of}]
    (fn apply-filter [entity]
      (cond
        (keyword? query-forms)
          ((get keyword-predicate-map query-forms always-false) entity)
        (and (vector? query-forms) (keyword? (first query-forms)))
          ((get expression-predicate-map (first query-forms) always-false) entity (rest query-forms))
        :default
          (parse-error (format "Illegal entity filter: %s" query-forms))))))

(defn query [cache query-forms]
  (let [query-func (compile-query cache query-forms)]
    (filter query-func
            (map #(:entity-data (second %)) cache))))
