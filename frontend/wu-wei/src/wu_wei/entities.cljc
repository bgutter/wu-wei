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

(defn event?
  "Predicate for whether an entity can function as an event."
  [entity]
  (boolean
   (and
    (entity? entity)
    (subset? #{:start-time} (set (keys entity))))))

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
  [query-forms]
  (let
      [parse-error              (fn [msg] (throw (ex-info msg)))
       always-false             (fn [& _] false)
       require-all-recursions   (fn [ent args]
                                  (every? identity (map (fn [func] (func ent))
                                                        (map compile-query args))))
       require-any-recursion    (fn [ent args]
                                  (some identity (map (fn [func] (func ent))
                                                      (map compile-query args))))
       keyword-predicate-map    {:task? #'task?
                                 :event? #'event?}
       expression-predicate-map {:and require-all-recursions
                                 :or require-any-recursion}]
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

;; TODO disable in prod

(s/def ::entity
  (s/and map? (s/every-kv keyword? any?)))

(s/fdef task?
  :args (s/cat :entity ::entity)
  :ret boolean?)

(st/instrument `task?)
