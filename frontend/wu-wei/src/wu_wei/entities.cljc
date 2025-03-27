(ns wu-wei.entities
  (:require
   [wu-wei.util :as util]
   ;; [clojure.spec.alpha :as s]
   ;; [clojure.spec.test.alpha :as st]
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

(defn set-summary
  [entity new-summary]
  ;; expect encoding, formatting, checking, etc
  (merge entity {:summary new-summary}))

(defn set-status
  [entity new-status]
  (merge entity {:status new-status}))

(defn task-is-completed?
  [entity]
  (#{:done} (:status entity)))

(defn task-incomplete?
  [entity]
  (not (task-is-completed? entity)))

(defn task-subtask-ids
  [entity]
  (let [subtask-ids (:subtask-ids entity)]
    (if (nil? subtask-ids)
      []
      subtask-ids)))

(defn task-has-subtasks?
  [entity]
  (let [subtask-ids (:subtask-ids entity)]
    (and subtask-ids
         (> (count subtask-ids) 0))))

(defn subtask?
  "Is first task a subtask of the other?"
  [task-a task-b]
  (and
   (task? task-a)
   (task? task-b)
   (contains? (:subtask-ids task-b) (:id task-a))))

(defn add-subtask
  "Add B as a subtask to A"
  [task-a task-b]
  (if
      (and
       (task? task-a)
       (task? task-b))
    (merge task-a {:subtask-ids (conj (:subtask-ids task-a) (:id task-b))})))

(defn remove-subtask
  [task-a task-b]
  (merge task-a {:subtask-ids (into #{} (remove #(= % (:id task-b)) (:subtask-ids task-a)))}))

(defn add-subtask-by-id
  "Add subtask-id as a subtask of A"
  [task subtask-id]
  (if
      (task? task)
    (merge task {:subtask-ids (into #{} (conj (:subtask-ids task) subtask-id))})))
