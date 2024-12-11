(ns wu-wei.entities
  (:require
   [wu-wei.util :as util]
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

(defn milestone?
  "Predicate for whether an entity can function as a milestone."
  [entity]
  (boolean (and (task? entity)
                (contains? entity :milestone))))

(defn toggle-milestone
  [entity]
  (if (:milestone entity)
    (dissoc entity :milestone)
    (merge entity {:milestone true})))

(def task-defaults {:status :open :summary ""})

(defn event?
  "Predicate for whether an entity can function as an event."
  [entity]
  (boolean
   (and
    (entity? entity)
    (subset? #{:start-time} (set (keys entity))))))

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
