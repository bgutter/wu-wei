(ns wu-wei.components.task-graph
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [wu-wei.entities :as entities]
            [wu-wei.util :as util]
            [wu-wei.entities.caching :as entity-cache]
            [cljsjs.d3]))

(defn clear-container-create-group [container]
  "Delete all existing groups and append a new blank one in the SVG."
  (let [svg (-> js/d3 (.select container) (.select "svg"))]
    (-> svg
        (.selectAll "g")
        (.remove))
    (-> svg
        (.append "g"))))

(defn get-existing-container-group [container]
  (-> js/d3
      (.select container)
      (.select "svg")
      (.select "g")))

(defn hierarchical-task-data [cache root-task-id should-hide-completed-nodes]
  "Create a hierarchy of task data in the format expected by D3"
  (letfn [(recursive-tree-builder [task-id]
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
                                            (recursive-tree-builder sid))))))})))]
    (recursive-tree-builder root-task-id)))

(defn make-cluster-hierarchy [container hierarchy-data]
  "Create a cluster layout and a hierarchy root node, apply the former to the latter, and return the latter."
  (let
      [width (.-clientWidth container)
       height (.-clientHeight container)
       tree (-> js/d3
                (.cluster)
                (.size (clj->js [height width])))
       root (-> js/d3
                (.hierarchy hierarchy-data)
                (.sort (fn [d]
                         d)))]

    ;; Apply cluster layout to data
    (tree root)

    ;; Tweak sizing
    (-> root
        (.each (fn [d]
                 (set! (.-y d) (max 100 (min (- width 150) (.-y d))))
                 (let [children (.-children d)]
                   (if (or (not children) (<= (count children) 0))
                     (do
                       (set! (.-y d) (- width 150))))))))

    ;; Return the data
    root))

(defn draw-links [cache group root hover-task-id]
  "Create .link elements in graph."
  (let
      [link-selection
       (-> group
           (.selectAll ".link")
           (.data (-> root (.descendants) (.slice 1))
                  (fn [d]
                    (.-data (.-data d)))))

       new-links
       (-> link-selection
           (.enter))]

    ;; Create new links
    (-> new-links
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

    ;; Update links
    (-> link-selection
        (.classed "link-downstream" (fn [d]
                                      (let [task-id (-> d .-data .-data)]
                                        (entity-cache/descendent-task? cache
                                                                       (entity-cache/lookup-id cache task-id)
                                                                       (entity-cache/lookup-id cache hover-task-id)))))
        (.transition)
        (.ease (.-easeQuadOut js/d3))
        (.duration 250)
        (.attr "d" (fn [d]
                     (str
                      "M" (.-y d) "," (.-x d)
                      "C" (/ (+ (.-y d) (.-y (.-parent d))) 2) "," (.-x d)
                      " " (/ (+ (.-y d) (.-y (.-parent d))) 2) "," (.-x (.-parent d))
                      " " (.-y (.-parent d)) "," (.-x (.-parent d))))))

    ;; clear old ones
    (-> link-selection
        (.exit)
        (.remove))))

(defn draw-nodes [cache group root hover-task-id fn-on-click fn-on-mouse-enter fn-on-mouse-leave]
  "Create .node elements in graph."
  (let
      [node-selection
       (-> group
           (.selectAll ".node")
           (.data (-> root (.descendants))
                  (fn [d]
                    (.-data (.-data d)))))

       new-nodes
       (-> node-selection
           (.enter))

       new-nodes-groups
       (-> new-nodes
           (.append "g")
           (.attr "class" (fn [d]
                            (if (.-children d)
                              (str "node node--internal")
                              (str "node node--leaf"))))
           (.attr "transform" (fn [d]
                             (str "translate(" (.-y d)"," (.-x d) ")")))
           (.classed "node-hovered" (fn [d]
                                      (let [task-id (-> d .-data .-data)]
                                        (= task-id hover-task-id)))))]

    ;; Fade in new nodes -- not old ones
    (-> new-nodes-groups
        (.transition)
        (.ease (.-easeQuadOut js/d3))
        (.duration 250)
        (.attr "opacity" 1))
    (-> node-selection
        (.attr "opacity" 1))

    ;; Add circle
    (-> new-nodes-groups
        (.append "circle")
        (.attr "r" 3)
        (.on "click" fn-on-click)
        (.on "mouseenter" fn-on-mouse-enter)
        (.on "mouseleave" fn-on-mouse-leave))

    ;; Add label
    (-> new-nodes-groups
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

    ;; Move existing nodes
    (-> node-selection
        (.transition)
        (.ease (.-easeQuadOut js/d3))
        (.duration 250)
        (.attr "transform" (fn [d]
                             (str "translate(" (.-y d)"," (.-x d) ")"))))

    ;; Add context highlighting
    (-> node-selection
        (.classed "node-hovered" (fn [d]
                                      (let [task-id (-> d .-data .-data)]
                                        (= task-id hover-task-id))))
        (.classed "node-hovered-downstream"
                  (fn [d]
                    (let [task-id (-> d .-data .-data)]
                      (entity-cache/descendent-task? cache
                                                     (entity-cache/lookup-id cache task-id)
                                                     (entity-cache/lookup-id cache hover-task-id))))))

    (-> node-selection
        (.exit)
        (.remove))))

(defn initialize-task-graph
  [cache
   root-task-id
   hover-task-id
   component
   fn-on-click
   fn-on-mouse-enter
   fn-on-mouse-leave
   should-hide-completed-nodes]
  (let [container (rd/dom-node component)
        group (clear-container-create-group container)
        root (make-cluster-hierarchy container
                                     (hierarchical-task-data cache root-task-id should-hide-completed-nodes))]

    (draw-links cache group root hover-task-id)
    (draw-nodes cache group root hover-task-id fn-on-click fn-on-mouse-enter fn-on-mouse-leave)))

(defn update-task-graph
  [cache
   root-task-id
   hover-task-id
   component
   fn-on-click
   fn-on-mouse-enter
   fn-on-mouse-leave
   should-hide-completed-nodes]
  (let [container (rd/dom-node component)
        group (get-existing-container-group container)
        root (make-cluster-hierarchy container
                                     (hierarchical-task-data cache root-task-id should-hide-completed-nodes))]

    (draw-links cache group root hover-task-id)
    (draw-nodes cache group root hover-task-id fn-on-click fn-on-mouse-enter fn-on-mouse-leave)))

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

       {:reagent-render
        (fn []
          [:div.ww-task-graph
           {:class (list
                    (if (nil? @selected-task-id-atom)
                      "ww-task-graph--hidden"
                      "ww-task-graph--visible"))}
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
            [:div.ww-flexbox-spacer]]])

        :component-did-mount
        (fn [this]
          (letfn
              [(handler-func [_ _ old-state new-state]
                 (let
                     [selected-id @selected-task-id-atom
                      cache @entity-cache-atom]
                   (if (or (nil? this-task-item-id) (= selected-id this-task-item-id))
                     (update-task-graph cache
                                        selected-id
                                        @hover-id-atom
                                        this
                                        fn-on-node-click
                                        fn-on-mouse-enter
                                        fn-on-mouse-leave
                                        @should-hide-completed-nodes-atom))))]
            (initialize-task-graph @entity-cache-atom
                                   @selected-task-id-atom
                                   @hover-id-atom
                                   this
                                   fn-on-node-click
                                   fn-on-mouse-enter
                                   fn-on-mouse-leave
                                   @should-hide-completed-nodes-atom)
            (add-watch should-hide-completed-nodes-atom
                       watcher-kw
                       handler-func)
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
                        watcher-kw)
          (remove-watch should-hide-completed-nodes-atom
                        watcher-kw))}))))

