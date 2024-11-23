(ns wu-wei.entities-test
  (:require [clojure.test :refer :all]
            [wu-wei.entities :refer :all]))

(deftest test-predicate-seq--test
  (testing "entity?"
    (is (entity? {:id 1}))
    (is (not (entity? {}))))
  (testing "task?"
    (is (task? {:status "OPEN" :summary "None" :id 1}))
    (is (not (task? {:summary "" :id 1})))))
