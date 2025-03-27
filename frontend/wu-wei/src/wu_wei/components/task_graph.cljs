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
  [cache
   root-task-id
   hover-task-id
   component
   fn-on-click
   fn-on-mouse-enter
   fn-on-mouse-leave
   should-hide-completed-nodes]
  (let [dom-node (rd/dom-node component)
        svg (-> js/d3 (.select dom-node) (.select "svg"))

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
                 (.cluster)
                 (.size (clj->js [height width])))

        hierarchical-task-data (letfn [(foo [task-id]
                                         (let [task (entity-cache/lookup-id cache task-id)]
                                           (clj->js
                                            {:data task-id
                                             :name (str "NODE " task-id)
                                             :children (into []
                                                             (keep identity
                                                                   (for [sid (entities/task-subtask-ids task)]
                                                                     (let [child-task (entity-cache/lookup-id cache sid)]
                                                                       (if (or (not should-hide-completed-nodes)
                                                                               (entities/task-incomplete? child-task)
                                                                               (entities/task-has-subtasks? child-task))
                                                                         (foo sid))))))})))]
                                 (foo root-task-id))

        root (-> js/d3
                 (.hierarchy hierarchical-task-data)
                 (.sort (fn [d]
                          d)))

        ;; ugly, for side-effects
        _ (tree root)

        _ (-> root
              (.each (fn [d]
                       (set! (.-y d) (max 100 (min (- width 150) (.-y d))))
                       (let [children (.-children d)]
                         (if (or (not children) (<= (count children) 0))
                           (do
                             (set! (.-y d) (- width 150))))))))

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
              (.attr "r" 3)
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
                                        "start")))
              (.text (fn [d]
                       (let [task-id (-> d .-data .-data)
                             summary (:summary (entity-cache/lookup-id cache task-id))]
                         (if (> (count summary) 15)
                           (str (subs summary 0 13) "...")
                           summary))))
              (.style "text-anchor" "center")
                  (.on "click" fn-on-click)
                  (.on "mouseenter" fn-on-mouse-enter)
                  (.on "mouseleave" fn-on-mouse-leave))

        ]))

(defn task-graph [entity-cache-atom selected-task-id-atom hover-id-atom this-task-item-id]
  (r/create-class
   (let
       [watcher-kw (keyword (str "task-map-redraw-" this-task-item-id))
        should-hide-completed-nodes-atom (r/atom false)]
     (letfn
         [(fn-on-node-click [event data]
            (let [task-id (-> data .-data .-data)]
              (reset! selected-task-id-atom task-id)))
          (fn-on-mouse-enter [event data]
            (let [task-id (-> data .-data .-data)]
              (if (not (= task-id @hover-id-atom))
                (reset! hover-id-atom task-id))))
          (fn-on-mouse-leave [event data]
            (reset! hover-id-atom nil))]

       {:component-will-mount
        (fn [this]
        (letfn
            [(handler-func [_ _ old-state new-state]
               (let
                   [selected-id @selected-task-id-atom
                    cache @entity-cache-atom]
                 (if (or (nil? this-task-item-id) (= selected-id this-task-item-id))
                   (draw-task-graph cache
                                    selected-id
                                    @hover-id-atom
                                    this
                                    fn-on-node-click
                                    fn-on-mouse-enter
                                    fn-on-mouse-leave
                                    @should-hide-completed-nodes-atom))))]
          (add-watch entity-cache-atom
                     watcher-kw
                     handler-func)
          (add-watch selected-task-id-atom
                     watcher-kw
                     (fn [a1 a2 a3 a4]
                       (println (str "SELECTION CHANGED " a3 " -> " a4))
                       (handler-func a1 a2 a3 a4)))
          (add-watch hover-id-atom
                     watcher-kw
                     handler-func)))

      :component-will-unmount
      (fn [this]
        (remove-watch entity-cache-atom
                      watcher-kw)
        (remove-watch selected-task-id-atom
                      watcher-kw)
        (remove-watch hover-id-atom
                      watcher-kw))
      :component-did-mount
      (fn [this]
        (draw-task-graph @entity-cache-atom
                         @selected-task-id-atom
                         @hover-id-atom
                         this
                         fn-on-node-click
                         fn-on-mouse-enter
                         fn-on-mouse-leave
                         @should-hide-completed-nodes-atom))

      :reagent-render
        (fn []
          [:div.ww-task-graph
           {:class (list
                    (if (nil? @selected-task-id-atom)
                      "ww-task-graph--hidden"))}
           [:div.ww-task-graph-svg-container
            [:svg {:style {:width "100%" :height "100%"}}]]
           [:div.ww-task-graph-controls-panel
            [:div.ww-task-list-item-mini-button
             {:on-click (fn [evnt]
                          (swap! should-hide-completed-nodes-atom not))}
             [:div.ww-task-list-item-mini-button-label
              (if @should-hide-completed-nodes-atom
                "Show All"
                "Hide Completed")]]
            [:div.ww-flexbox-spacer]]])}))))

