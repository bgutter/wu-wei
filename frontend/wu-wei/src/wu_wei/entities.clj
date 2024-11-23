(ns wu-wei.entities
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.set :refer [subset?]]))

(defn entity?
  "Predicate for whether an object qualifies as an entity."
  [maybe-entity]
  (:id maybe-entity))

(defn task?
  "Predicate for whether an entity can function as a task."
  [entity]
  (and
   (entity? entity)
   (subset? #{:status :summary} (set (keys entity)))))

(defn event?
  "Predicate for whether an entity can function as an event."
  [entity]
  (and
   (entity? entity)
   (subset? #{:start-time} (set (keys entity)))))

(defn compile-entity-filter
  "Produce a function that determines whether an entity matches the given entity filter, `filter-vec`.

  `filter-vec` should be either a keyword, or a vector of nested
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
  - [:all ...]
      Matches only if all following expressions match
  - [:and ...]
      Same as :all
  - [:any ...]
      Matches if any of the following expressions match
  - [:or ...]
      Same as :any

  Supported Dependency Projections
  ================================
  - [:all-dependencies ...]
      Matches only if *all* following expressions match for *all* dependencies
      of a task
  - [:any-dependency ...]
      Matches if *all* of the following expressions match *any* of the
      dependencies of a task
  "
  [filter-vec]
  (let
      [parse-error              (fn [msg] (throw (ex-info msg)))
       always-false             (fn [_] false)
       keyword-predicate-map    {:task? #'task?
                                 :event? #'event?}
       expression-predicate-map {:and (fn [ent args]
                                        (every? identity (map (fn [func] (func ent))
                                                     (map compile-entity-filter args))))}]
    (fn apply-filter [entity]
      (cond
        (keyword? filter-vec)
          ((get keyword-predicate-map filter-vec always-false) entity)
        (and (vector? filter-vec) (keyword? (first filter-vec)))
          ((get expression-predicate-map (first filter-vec) always-false) entity (rest filter-vec))
        :default
          (parse-error (format "Illegal entity filter: %s" filter-vec))))))

;; TODO spec, disable in prod

(s/def ::entity
  (s/and map? (s/every-kv keyword? any?)))

(s/fdef task?
  :args (s/cat :entity ::entity)
  :ret boolean?)

(st/instrument `task?)
