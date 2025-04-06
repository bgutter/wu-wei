(ns wu-wei.entities.caching
  (:require [wu-wei.entities :as entities]
            [clojure.edn :refer [read-string]]))

;; Working with entity caches

(defn new-cache
  "Create an empty cache"
  []
  {:entity-records {}
   :task-graph-cache {}})

;; (defn declare-exist
;;   "Create an empty data node for a set of entity IDs. This lets the
;;   cache know that some ID exists, but data has not yet been fetched for it."
;;   [cache timestamp & ids-that-exist]
;;   (merge cache (into {} (map #(vector % {:retrieved timestamp :entity-data nil})) ids-that-exist)))

(declare update-task-graph-cache)
(defn set-entity-data
  "Return `cache` with new `entity-data` associated with ID `id` and given update `timestamp`"
  [cache timestamp id entity-data]
  (let [updated-cache (update cache :entity-records assoc id {:retrieved timestamp :entity-data entity-data})]
    (update-task-graph-cache updated-cache id)))

(defn remove-entity-data
  "Return `cache` with data for entity id `id` removed"
  [cache id]
  (let [updated-cache (update cache :entity-records dissoc id)]
    (update-task-graph-cache updated-cache id)))

(defn lookup-id
  "Retrieve entity data stored for given ID `id` in `cache`"
  [cache id]
  (let [cache-data (get (:entity-records cache) id)]
    (when cache-data
      (:entity-data cache-data))))

(defn all-ids
  "Get all IDs in the cache"
  [cache]
  (into #{} (keys (:entity-records cache))))

;; task graph cache

(defn update-task-graph-cache
  ""
  [cache modified-id]
  (let [task-graph-cache (:task-graph-cache cache)
        entity-records   (:entity-records cache)]
    (merge cache {:task-graph-cache

                  (if (contains? entity-records modified-id)

                    ;; modified-id still exists
                    ;; check every entity to see if it references the modified ID
                    (reduce (fn [tgc-in-progress [_ entity-record]]
                              (let [entity-data (:entity-data entity-record)]
                                ;; Check to see if this entity has any relation to the modified ID
                                (cond
                                  (contains? (:subtask-ids entity-data) modified-id)
                                  (merge tgc-in-progress {modified-id {:parent-id (:id entity-data)}})

                                  (contains? (:subtask-ids (lookup-id cache modified-id)) (:id entity-data))
                                  (merge tgc-in-progress {(:id entity-data) {:parent-id modified-id}})

                                  true
                                  tgc-in-progress)))

                            task-graph-cache
                            entity-records)

                    ;; modified-id is a deleted id
                    ;; delete cache items that reference it
                    (into {} (filter (fn [[id-key cache-info]]
                              (and (not (= id-key modified-id))
                                   (not (= (:parent-id cache-info) modified-id))))
                                     task-graph-cache)))})))
;; TODO where should this live?

(defn task-subtasks
  [cache task]
  (map #(lookup-id cache %) (:subtask-ids task)))

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

(defn parent-task-id
  "Find parent task of a given task"
  [cache task]
  (let [item-tgc-entry (get (:task-graph-cache cache) (:id task))]
    (if item-tgc-entry
      (:parent-id item-tgc-entry))))

(defn parent-task
  "Find the parent task of a given task"
  [cache task]
  (let [parent-id (parent-task-id cache task)]
    (lookup-id cache parent-id)))

(defn task-ancestry-ids
  [cache task & {:keys [up-to-id]}]
  (if (and up-to-id (or
                     (= (lookup-id cache up-to-id) task)
                     (descendent-task? cache (lookup-id cache up-to-id) task)))

    ;; If up-to-id is a descendent of task, return none
    []

    ;; Recurse parents
    (let
        [inner-fn (fn [cache task progression up-to-id]
                    (let [parent-id (parent-task-id cache task)]
                      (if (and parent-id (or (nil? up-to-id) (not (= up-to-id parent-id))))
                        (recur cache (lookup-id cache parent-id) (conj progression parent-id) up-to-id)
                        (reverse progression))))]
      (inner-fn cache task [] up-to-id))))

(defn ancestor-task?
  "Is A an ancestor of B?"
  [cache task-a task-b]
  (let
      [relevant-ancestors (task-ancestry-ids cache task-b :up-to-id (:id task-a))]
    (println task-a)
    (println task-b)
    (println relevant-ancestors)
    (seq relevant-ancestors)))

(defn task-effort-value-valid-p
  [value]
  (and (not (nil? value)) (> (count value) 0)))

(def duration-format-units-bak
  {"w" (* 5 8 60 60)
   "d" (* 8 60 60)
   "h" (* 60 60)
   "m" (* 60)
   "s" 1})

(defn decode-duration-str
  "Convert a string representing spans of time into integer seconds."
  [duration-str]
  (let [unit-multipliers duration-format-units-bak]
    (reduce (fn [total part]
              (let [[_ num unit] (re-find #"(\d+)([wdhms])" part)]
                (+ total (* (read-string num) (get unit-multipliers unit)))))
            0
            (re-seq #"\d+[wdhms]" duration-str))))

(def duration-format-units
  {:week   {:char "w" :size (* 5 8 60 60)}
   :day    {:char "d" :size (* 8 60 60)}
   :hour   {:char "h" :size (* 60 60)}
   :minute {:char "m" :size (* 60)}
   :second {:char "s" :size 1}})

(defn encode-duration-str [duration-sec]
  (let [units (vals duration-format-units)]
    (loop [partial-str ""
           remaining-sec duration-sec
           [unit & rest :as unit-list] units]
      (if (empty? unit-list)
        partial-str
        (let [{:keys [size char]} unit
              unit-count (quot remaining-sec size)]
          (if (pos? unit-count)
            (recur (str partial-str "" unit-count char)
                   (- remaining-sec (* unit-count size))
                   rest)
            (recur partial-str remaining-sec rest)))))))

(defn task-own-effort
  "Get the effort estimate for a single task."
  [cache task]
  (let [effort-estimate (:effort-estimate task)]
    (if (task-effort-value-valid-p effort-estimate)
      effort-estimate
      nil)))

(defn task-total-effort-sec
  "Get the effort of this task plus all of its subtasks"
  [cache task]
  (let
      [own-effort (task-own-effort cache task)
       own-effort-sec (if own-effort (decode-duration-str own-effort) 0)]
    (+ own-effort-sec
       (apply +
              (map #(task-total-effort-sec cache %)
                   (task-subtasks cache task))))))

(defn task-subtask-effort-sec
  [cache task]
  (apply + (map #(task-total-effort-sec cache %)
                (task-subtasks cache task))))

(defn task-total-effort
  [cache task]
  (encode-duration-str (task-total-effort-sec cache task)))

(defn task-subtask-effort
  [cache task]
  (encode-duration-str (task-subtask-effort-sec cache task)))

(defn task-subtree-has-complete-effort-p
  "Among the subtask tree, do all LEAF nodes have effort estimates?"
  [cache task]
  (let [subtasks (task-subtasks cache task)]
    (if (<= (count subtasks) 0)
      (task-effort-value-valid-p (task-own-effort cache task))
      (every? #(task-subtree-has-complete-effort-p cache %) subtasks))))

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
      [parse-error              (fn [msg]
                                  (throw (ex-info msg {})))
       always-false             (fn [& _] false)
       always-true              (fn [& _] true)
       require-all-recursions   (fn [cache ent args]
                                  (every? identity (map (fn [func] (func ent))
                                                        (map #(compile-query cache %) args))))
       require-any-recursion    (fn [cache ent args]
                                  (some identity (map (fn [func] (func ent))
                                                      (map #(compile-query cache %) args))))
       logical-not              (fn [cache ent args]
                                  (not-any? identity (map (fn [func] (func ent))
                                                          (map #(compile-query cache %) args))))
       check-occur-before       (fn [cache ent args] (apply entities/event-occurs-before? ent args))
       check-is-subtask-of      (fn [cache ent args] (entities/subtask? ent (lookup-id cache (first args))))
       check-is-descendent-of   (fn [cache ent args] (descendent-task? cache ent (lookup-id cache (first args))))
       check-is-subtask         (fn [cache ent] (not (nil? (parent-task-id cache ent))))
       check-is-task            (fn [cache ent] (entities/task? ent))
       check-is-event           (fn [cache ent] (entities/event? ent))
       check-is-milestone       (fn [cache ent] (entities/milestone? ent))
       keyword-predicate-map    {:task? check-is-task
                                 :event? check-is-event
                                 :milestone? check-is-milestone
                                 :subtask? check-is-subtask
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
          ((get keyword-predicate-map query-forms always-false) cache entity)
        (and (vector? query-forms) (keyword? (first query-forms)))
          ((get expression-predicate-map (first query-forms) always-false) cache entity (rest query-forms))
        :default
          (parse-error (str "Illegal entity filter: " query-forms))))))

(defn query [cache query-forms]
  (let [query-func (compile-query cache query-forms)]
    (filter query-func
            (map #(:entity-data (second %)) (:entity-records cache)))))
