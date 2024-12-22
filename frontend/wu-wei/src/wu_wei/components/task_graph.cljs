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
  [cache root-task-id hover-task-id component fn-on-click fn-on-mouse-enter fn-on-mouse-leave]
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
                                             (clj->js {:data task-id
                                                       :name (str "NODE " task-id)
                                                       :children (into []
                                                                       (for [sid (:subtask-ids task)]
                                                                         (foo sid)))})
                                             (clj->js {:data task-id
                                                       :name (str "NODE " task-id)
                                                       :children []}))))]
                                 (foo root-task-id))

        root (-> js/d3
                 (.hierarchy hierarchical-task-data)
                 (.sort (fn [d]
                          d)))

        ;; ugly, for side-effects
        _ (tree root)

        max-depth (-> js/d3
                      (.max (.leaves root)
                            (fn [d] (.-y d))))

        _ (-> root
              (.each (fn [d]
                       (set! (.-y d) (max 10 (min (- width 50) (.-y d))))
                       (let [children (.-children d)]
                         (if (or (not children) (<= (count children) 0))
                           (do
                             (js/console.log "Setting max depth")
                             (set! (.-y d) (- width 50))))))))

        links (-> g
                  (.selectAll ".link")
                  (.data (-> root (.descendants) (.slice 1)))
                  (.enter)
                  (.append "path")
                  (.attr "class" "link")
                  (.classed "link-downstream" (fn [d]
                                                (let [task-id (-> d .-data .-data)]
                                                  (entity-cache/descendent-task? cache
                                                                                 (entity-cache/lookup-id cache task-id)
                                                                                 (entity-cache/lookup-id cache hover-task-id)))))
                  (.attr "d" (fn [d]
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
                 (.classed "node-hovered" (fn [d]
                                            (let [task-id (-> d .-data .-data)]
                                              (= task-id hover-task-id))))
                 (.attr "transform" (fn [d]
                                      (str "translate(" (.-y d)"," (.-x d) ")"))))

        _ (-> node
              (.classed "node-hovered-downstream" (fn [d]
                                                    (let [task-id (-> d .-data .-data)]
                                                      (entity-cache/descendent-task? cache
                                                                                     (entity-cache/lookup-id cache task-id)
                                                                                     (entity-cache/lookup-id cache hover-task-id))))))

        _ (-> node
              (.append "circle")
              (.attr "r" 2.5)
              (.on "click" fn-on-click)
              (.on "mouseenter" fn-on-mouse-enter)
              (.on "mouseleave" fn-on-mouse-leave))

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
                                        "start"))))
              ;; (.text (fn [d]
              ;;          (let [task-id (-> d .-data .-data)]
              ;;            (:summary (entity-cache/lookup-id cache task-id)))))

        ]))

(defn task-graph [entity-cache-atom selected-task-id-atom hover-id-atom]
  (r/create-class
   (letfn
       [(fn-on-node-click [event data]
          (let [task-id (-> data .-data .-data)]
            (reset! selected-task-id-atom task-id)))
        (fn-on-mouse-enter [event data]
          (js/console.log event)
          (let [task-id (-> data .-data .-data)]
            (if (not (= task-id @hover-id-atom))
              (reset! hover-id-atom task-id))))
        (fn-on-mouse-leave [event data]
          (js/console.log event)
          (reset! hover-id-atom nil))]
     {:component-will-mount
      (fn [this]
        (letfn
            [(handler-func [_ _ _ _]
               (let
                   [selected-id @selected-task-id-atom
                    cache @entity-cache-atom]
                 (draw-task-graph cache
                                  selected-id
                                  @hover-id-atom
                                  this
                                  fn-on-node-click
                                  fn-on-mouse-enter
                                  fn-on-mouse-leave)))]
          (add-watch entity-cache-atom
                     :task-map-redraw
                     handler-func)
          (add-watch selected-task-id-atom
                     :task-map-redraw
                     handler-func)
          (add-watch hover-id-atom
                     :task-map-redraw
                     handler-func)))

      :component-will-unmount
      (fn [this]
        (remove-watch entity-cache-atom
                      :task-map-redraw)
        (remove-watch selected-task-id-atom
                      :task-map-redraw)
        (remove-watch hover-id-atom
                      :task-map-redraw))
      :component-did-mount
      (fn [this]
        (draw-task-graph @entity-cache-atom
                         @selected-task-id-atom
                         @hover-id-atom
                         this
                         fn-on-node-click))

      :reagent-render
      (fn []
         [:svg {:style {:width "100%" :height "100px"}}])})))

