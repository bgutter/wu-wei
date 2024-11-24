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
    (is (not (task? {:status ""}))))
  (testing "event?"
    (is (not (event? {})))))

(deftest test--compile-query
  (let
      [an-open-task     {:id 1 :summary "Some Task" :status :open}
       an-event         {:id 2 :summary "Another one" :start-time "2024-1-1"}
       a-task-and-event {:id 3 :summary "Important meeting" :status :open :start-time "1970-1-1"}]
    (testing "basic predicates"
      (is ((compile-query :task?) an-open-task))
      (is (not ((compile-query :task?) {})))
      (is (not ((compile-query :event?) an-open-task)))
      (is ((compile-query :event?) an-event)))
    (testing "logical groupings"
      (let
          [task-and-event-matcher (compile-query [:and :task? :event?])
           task-or-event-matcher  (compile-query [:or :task? :event?])]
        (is (task-and-event-matcher a-task-and-event))
        (is (not (task-and-event-matcher an-open-task)))
        (is (not (task-and-event-matcher an-event)))
        (is (not (task-and-event-matcher {})))
        (is (task-or-event-matcher an-open-task))
        (is (task-or-event-matcher an-event))
        (is (task-or-event-matcher a-task-and-event))
        (is (not (task-or-event-matcher {})))))))

#_
    (testing "grouped predicate sequences"
      (is (test-predicate-seq [:and
                               :task?
                               [:due-within "1w"]
                               [:any-dependencies :open?]
                               [:each-dependency [:or :overdue :done]]])))
