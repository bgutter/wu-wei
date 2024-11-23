(ns wu-wei.entities-test
  (:require [clojure.test :refer :all]
            [wu-wei.entities :refer :all]))

(deftest test--class-predicates
  (testing "entity?"
    (is (entity? {:id 1}))
    (is (not (entity? {})))
    (is (not (entity? 1))))
  (testing "task?"
    (is (task? {:status "OPEN" :summary "None" :id 1}))
    (is (not (task? {:summary "" :id 1})))
    (is (not (task? {:status ""})))))

(deftest test--entity-filters
  (let
      [an-open-task     {:id 1 :summary "Some Task" :status :open}
       an-event         {:id 2 :summary "Another one" :start-time "2024-1-1"}
       a-task-and-event {:id 3 :summary "Important meeting" :status :open :start-time "1970-1-1"}]
    (testing "basic predicates"
      (is ((compile-entity-filter :task?) an-open-task))
      (is (not ((compile-entity-filter :task?) {})))
      (is (not ((compile-entity-filter :event?) an-open-task)))
      (is ((compile-entity-filter :event?) an-event)))
    (testing "logical groupings"
      (is ((compile-entity-filter [:and :task? :event?]) a-task-and-event)))))

#_
    (testing "grouped predicate sequences"
      (is (test-predicate-seq [:and
                               :task?
                               [:due-within "1w"]
                               [:any-dependencies :open?]
                               [:each-dependency [:or :overdue :done]]])))
