(ns wu-wei.entities-test
  (:require [clojure.test :refer :all]
            [wu-wei.entities :refer :all]))

(deftest test--class-predicates
  "Test class predicates like `wu-wei.entities/task?`, etc."
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
  "Test the `wu-wei.entities/compile-query` function."
  (let
      [a-nothing        {}
       an-open-task     {:id 1 :summary "Some Task" :status :open}
       an-event-in-2015 {:id 2 :summary "Another one" :start-time (time-from-str "2015-1-1")}
       a-task-and-event {:id 3 :summary "Important meeting" :status :open :start-time (time-from-str "2024-7-1")}]
    (testing "basic predicates"
      (let
          [task-matcher  (compile-query :task?)
           event-matcher (compile-query :event?)]
        (is (task-matcher an-open-task))
        (is (not (task-matcher a-nothing)))
        (is (not (event-matcher an-open-task)))
        (is (event-matcher an-event-in-2015))))
    (testing "logical groupings"
      (let
          [task-and-event-matcher (compile-query [:and :task? :event?])
           task-or-event-matcher  (compile-query [:or :task? :event?])]
        (is (task-and-event-matcher a-task-and-event))
        (is (not (task-and-event-matcher an-open-task)))
        (is (not (task-and-event-matcher an-event-in-2015)))
        (is (not (task-and-event-matcher a-nothing)))
        (is (task-or-event-matcher an-open-task))
        (is (task-or-event-matcher an-event-in-2015))
        (is (task-or-event-matcher a-task-and-event))
        (is (not (task-or-event-matcher a-nothing)))))
    (testing "event time filters"
      (let
          [event-before-2014-matcher (compile-query [:occurs-before? (time-from-str "2014-1-1")])
           event-before-2016-matcher  (compile-query [:occurs-before? (time-from-str "2016-1-1")])]
        (is (not (event-before-2014-matcher an-event-in-2015)))
        (is (event-before-2016-matcher an-event-in-2015))))))

#_
    (testing "grouped predicate sequences"
      (is (test-predicate-seq [:and
                               :task?
                               [:due-within "1w"]
                               [:any-dependencies :open?]
                               [:each-dependency [:or :overdue :done]]])))
