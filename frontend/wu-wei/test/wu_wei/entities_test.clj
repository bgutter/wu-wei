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
  (testing "milestone?"
    (is (milestone? {:id 1 :status :open :milestone true :summary ""})))
  (testing "event?"
    (is (not (event? {})))))

(deftest test--task-apis
  "Test basic functions for working with tasks."
  (testing "subtask?"
    (is (subtask? {:id 1 :status :open :summary ""}
                  {:id 2 :status :open :summary "" :subtask-ids #{1}}))
    (is (not (subtask? {:id 2 :status :open :summary "" :subtask-ids #{1}}
                       {:id 1 :status :open :summary ""}))))
  (testing "downstream-tasks"
    (let
        [task-dict {0 {:id 0 :status :open :summary "A"}
                    1 {:id 1 :status :open :summary "B" :subtask-ids #{0}}
                    2 {:id 2 :status :open :summary "C" :subtask-ids #{1}}
                    3 {:id 3 :status :open :summary "D" :subtask-ids #{2 4}}
                    4 {:id 4 :status :open :summary "E"}
                    5 {:id 5 :status :open :summary "F"}}]
      (is (= (into #{} (map #(get task-dict %) [0 1 2 4]))
             (downstream-tasks (get task-dict 3) task-dict))))))

(deftest test--compile-query
  "Test the `wu-wei.entities/compile-query` function."
  (let
      [entities               {1 {:id 1 :summary "Some Task" :status :open}
                               2 {:id 2 :summary "Another one" :start-time (time-from-str "2015-1-1")}
                               3 {:id 3 :summary "Important meeting" :status :open :start-time (time-from-str "2024-7-1")}
                               4 {:id 4 :summary "Do some stuff" :status :open :subtask-ids #{1 3}}
                               5 {:id 5 :summary "My Milestone" :status :open :subtask-ids #{4} :milestone true}}
       lookup-entity-fn       (partial get entities)
       a-nothing              {}
       an-open-task           (lookup-entity-fn 1)
       an-event-in-2015       (lookup-entity-fn 2)
       a-task-and-event       (lookup-entity-fn 3)
       parent-task-of-1-and-3 (lookup-entity-fn 4)
       a-milestone            (lookup-entity-fn 5)]
    (testing "basic predicates"
      (let
          [task-matcher  (compile-query :task? lookup-entity-fn)
           event-matcher (compile-query :event? lookup-entity-fn)
           milestone-matcher (compile-query :milestone? lookup-entity-fn)]
        (is (task-matcher an-open-task))
        (is (not (task-matcher a-nothing)))
        (is (not (event-matcher an-open-task)))
        (is (event-matcher an-event-in-2015))
        (is (milestone-matcher a-milestone))
        (is (not (milestone-matcher an-event-in-2015)))))
    (testing "logical groupings"
      (let
          [task-and-event-matcher (compile-query [:and :task? :event?] lookup-entity-fn)
           task-or-event-matcher  (compile-query [:or :task? :event?] lookup-entity-fn)]
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
          [event-before-2014-matcher (compile-query [:occurs-before? (time-from-str "2014-1-1")] lookup-entity-fn)
           event-before-2016-matcher  (compile-query [:occurs-before? (time-from-str "2016-1-1")] lookup-entity-fn)]
        (is (not (event-before-2014-matcher an-event-in-2015)))
        (is (event-before-2016-matcher an-event-in-2015))))
    (testing "ascendents and descendents"
      (let
          [subtask-of-4-matcher (compile-query [:subtask-of? 4] lookup-entity-fn)]
        (is (subtask-of-4-matcher an-open-task))
        (is (subtask-of-4-matcher a-task-and-event))
        (is (not (subtask-of-4-matcher parent-task-of-1-and-3)))))))

#_
    (testing "grouped predicate sequences"
      (is (test-predicate-seq [:and
                               :task?
                               [:due-within "1w"]
                               [:any-dependencies :open?]
                               [:each-dependency [:or :overdue :done]]])))
