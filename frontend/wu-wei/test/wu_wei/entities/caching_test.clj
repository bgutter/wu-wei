(ns wu-wei.entities-test
  (:require [clojure.test :refer :all]
            [wu-wei.entities :refer :all]
            [wu-wei.util :as util]
            [wu-wei.entities.caching :as entity-cache]))


(entity-cache/declare-exist {} (util/ts-now) 1 2 3)

(entity-cache/set-entity-data {} (util/ts-now) 123 {:sumary "NO"})
