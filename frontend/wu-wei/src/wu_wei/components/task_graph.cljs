(ns wu-wei.components.task-graph
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [wu-wei.entities :as entities]
            [wu-wei.util :as util]
            [wu-wei.entities.caching :as entity-cache]
            [cljsjs.d3]))


(defn draw-task-graph
  [cache root-task-id component fn-on-click]
  (let [dom-node (rd/dom-node component)
        svg (-> js/d3 (.select dom-node))

        _ (-> svg
              (.selectAll "g")
              (.remove))

        g (-> svg
              (.append "g")
              ;; (.attr "transform" "translate(40,40)")
              )

        container dom-node

        width (.-clientWidth container)

        height (.-clientHeight container)

        tree (-> js/d3
                 (.tree)
                 (.size (clj->js [height width])))

        hierarchical-task-data (letfn [(foo [task-id]
                                         (let [task (entity-cache/lookup-id cache task-id)]
                                           (if (> (count (:subtask-ids task)) 0)
                                             (clj->js {"data" task-id
                                                       "name" (str "NODE " task-id)
                                                       "children" (into []
                                                                        (for [sid (:subtask-ids task)]
                                                                          (foo sid)))})
                                             (clj->js {"data" task-id
                                                       "name" (str "NODE " task-id)
                                                       "children" []}))))]
                                 (foo root-task-id))

        root (-> js/d3 (.hierarchy hierarchical-task-data))

        ;; ugly, for side-effects
        _ (tree root)

        links (-> g
                  (.selectAll ".link")
                  (.data (-> root (.descendants) (.slice 1)))
                  (.enter)
                  (.append "path")
                  (.attr "class" "link")
                  (.attr "d" (fn [d]
                               (js/console.log d)
                               (str
                                "M" (.-y d) "," (.-x d)
                                "C" (/ (+ (.-y d) (.-y (.-parent d))) 2) "," (.-x d)
                                " " (/ (+ (.-y d) (.-y (.-parent d))) 2) "," (.-x (.-parent d))
                                " " (.-y (.-parent d)) "," (.-x (.-parent d))))))

        node (-> g
                 (.selectAll ".node")
                 (.data (-> root (.descendants)))
                 (.enter)
                 (.append "g")
                 (.attr "class" (fn [d]
                                  (if (.-children d)
                                    (str "node node--internal")
                                    (str "node node--leaf"))))
                 (.attr "transform" (fn [d]
                                      (str "translate(" (.-y d)"," (.-x d) ")")))
                 (.on "click" fn-on-click))

        _ (-> node
              (.append "circle")
              (.attr "r" 2.5))

        _ (-> node
              (.append "text")
              (.attr "dy" 3)
              (.attr "x" (fn [d]
                           (if (.-children d)
                             -8
                             8)))
              (.style "text-anchor" (fn [d]
                                      (if (.-children d)
                                        "end"
                                        "start")))
              (.text (fn [d]
                       (-> d .-data .-summary))))]
    (println "XxX" height width)))

(defn task-graph [entity-cache-atom selected-task-id-atom]
  (r/create-class
   (letfn
       [(fn-on-node-click [event data]
          (let [real-data (-> data .-data .-data)]
            (reset! selected-task-id-atom real-data)
            (js/console.log "CLICKED " real-data)))]
     {:component-will-mount
      (fn [this]
        (letfn
            [(handler-func [_ _ _ _]
               (let
                   [selected-id @selected-task-id-atom
                    cache @entity-cache-atom]
                 (draw-task-graph cache selected-id this
                                  fn-on-node-click)))]
          (add-watch entity-cache-atom
                     :task-map-redraw
                     handler-func)
          (add-watch selected-task-id-atom
                     :task-map-redraw
                     handler-func)))

      :component-will-unmount
      (fn [this]
        (remove-watch entity-cache-atom
                      :task-map-redraw)
        (remove-watch selected-task-id-atom
                      :task-map-redraw))
      :component-did-mount
      (fn [this]
        (draw-task-graph @entity-cache-atom @selected-task-id-atom this
                         fn-on-node-click))

      :reagent-render
      (fn []
         [:svg {:style {:width "100%" :height "100px"}}])})))

