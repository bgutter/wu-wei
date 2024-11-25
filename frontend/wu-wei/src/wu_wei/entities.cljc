(ns wu-wei.entities
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.set :refer [subset?]]))

;;
;; Entity Classes
;;

(defn entity?
  "Predicate for whether an object qualifies as an entity."
  [maybe-entity]
  (boolean (:id maybe-entity)))

(defn task?
  "Predicate for whether an entity can function as a task."
  [entity]
  (boolean (and
            (entity? entity)
            (subset? #{:status :summary} (set (keys entity))))))

(def task-defaults {:status :open :summary ""})

(defn event?
  "Predicate for whether an entity can function as an event."
  [entity]
  (boolean
   (and
    (entity? entity)
    (subset? #{:start-time} (set (keys entity))))))

;;
;; Time APIs
;;

;; TODO: Replace with real functions that can parse relative times,
;; like "+2d" or "3w4h"

(defn current-time
  "Get the current time as UTC seconds."
  []
  #?(:clj  (int (/ (.getTime (java.util.Date.)) 1000.))
     :cljs (js/Math.floor (/ (.getTime (js/Date.)) 1000))))

(defn time-from-str
  "Get UTC seconds from a time string"
  [time-string]
  #?(:clj (let [df (java.text.SimpleDateFormat. "yyyy-MM-dd")]
            (quot (.getTime (.parse df time-string)) 1000))
     :cljs (let [df (js/Date. time-string)]
             (quot (.getTime df) 1000))))

;;
;; Event Objects
;;

(defn event-occurs-before?
  "Does this event begin before some time?"
  [event datetime]
  (< (:start-time event) datetime))


;;
;; Task Objects
;;

(defn subtask?
  "Is first task a subtask of the other?"
  [task-a task-b]
  (and
   (task? task-a)
   (task? task-b)
   (contains? (:subtask-ids task-b) (:id task-a))))

;;
;; Entity Queries
;;

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
  [query-forms lookup-entity-fn]
  (let
      [parse-error              (fn [msg] (throw (ex-info msg)))
       always-false             (fn [& _] false)
       always-true              (fn [& _] true)
       require-all-recursions   (fn [ent args]
                                  (every? identity (map (fn [func] (func ent))
                                                        (map #(compile-query % lookup-entity-fn) args))))
       require-any-recursion    (fn [ent args]
                                  (some identity (map (fn [func] (func ent))
                                                      (map #(compile-query % lookup-entity-fn) args))))
       check-occur-before       (fn [ent args] (apply event-occurs-before? ent args))
       check-is-subtask-of      (fn [ent args] (subtask? ent (lookup-entity-fn (first args))))
       keyword-predicate-map    {:task? #'task?
                                 :event? #'event?
                                 :true always-true
                                 :false always-false}
       expression-predicate-map {:and require-all-recursions
                                 :or require-any-recursion
                                 :occurs-before? check-occur-before
                                 :subtask-of? check-is-subtask-of}]
    (fn apply-filter [entity]
      (cond
        (keyword? query-forms)
          ((get keyword-predicate-map query-forms always-false) entity)
        (and (vector? query-forms) (keyword? (first query-forms)))
          ((get expression-predicate-map (first query-forms) always-false) entity (rest query-forms))
        :default
          (parse-error (format "Illegal entity filter: %s" query-forms))))))

;;
;; Specs
;;

;; ;; TODO disable in prod

;; (s/def ::entity
;;   (s/and map? (s/every-kv keyword? any?)))

;; (s/fdef task?
;;   :args (s/cat :entity ::entity)
;;   :ret boolean?)

;; (st/instrument `task?)
