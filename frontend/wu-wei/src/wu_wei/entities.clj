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
   (subset? #{:status :summary} (keys entity))))

(s/def ::entity
  (s/and map? (s/every-kv keyword? any?)))

(s/fdef task?
  :args (s/cat :entity ::entity)
  :ret boolean?)

(st/instrument `task?)
