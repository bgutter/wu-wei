(ns wu-wei.components.task-graph
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [wu-wei.entities :as entities]
            [wu-wei.util :as util]
            [wu-wei.entities.caching :as entity-cache]
            [cljsjs.d3]))

(defrecord GraphDrawContext [component
                             entity-cache
                             selected-task-id
                             hover-task-id
                             drag-task-id
                             fn-on-task-clicked
                             fn-on-task-mouse-entered
                             fn-on-task-mouse-left
                             fn-on-task-dragged
                             should-hide-completed-tasks])

(defn clear-container-create-groups
  "Delete all existing groups and append a new blank one in the SVG."
  [container]
  (let [svg (-> js/d3 (.select container) (.select "svg"))]
    (-> svg
        (.selectAll "g")
        (.remove))
    (-> svg
        (.append "rect")
        (.attr "width" "100%")
        (.attr "height" "100%")
        (.attr "fill" "rgb(255,255,255)"))
    (-> svg
        (.append "rect")
        (.attr "width" "25%")
        (.attr "height" "100%")
        (.attr "x" "75%")
        (.attr "fill" "rgb(240,240,240)"))
    (-> svg
        (.append "text")
        (.attr "x" "75%")
        (.attr "y" "20px")
        (.attr "width" "25%")
        (.text "Actionable Tasks"))
    (-> svg
        (.append "g")
        (.attr "id" "link-layer"))
    (-> svg
        (.append "g")
        (.attr "id" "node-layer")))
    nil)

(defn hierarchical-task-data
  "Create a hierarchy of task data in the format expected by D3"
  [ctx]
  (letfn [(recursive-tree-builder [task-id]
            (let [task (entity-cache/lookup-id (:entity-cache ctx) task-id)]
              (clj->js
               {:data task-id
                :name (str "NODE " task-id)
                :children (into []
                                (keep identity
                                      (for [sid (entities/task-subtask-ids task)]
                                        (let [child-task (entity-cache/lookup-id (:entity-cache ctx) sid)]
                                          (if (or (not (:should-hide-completed-tasks ctx))
                                                  (entities/task-incomplete? child-task)
                                                  (entities/task-has-subtasks? child-task))
                                            (recursive-tree-builder sid))))))})))]
    (recursive-tree-builder (:selected-task-id ctx))))

(defn make-cluster-hierarchy
  "Create a cluster layout and a hierarchy root node, apply the former to the latter, and return the latter."
  [container hierarchy-data]
  (let
      [width (.-clientWidth container)
       height (.-clientHeight container)
       tree (-> js/d3
                (.cluster)
                (.size (clj->js [height (* width 0.7)])))
       root (-> js/d3
                (.hierarchy hierarchy-data)
                (.sort (fn [d]
                         d)))]

    ;; Apply cluster layout to data
    (tree root)

    ;; Tweak sizing
    (-> root
        (.each (fn [d]
                 (set! (.-y d) (+ (* 0.1 width) (.-y d))))))

    ;; Return the data
    root))

(defn svg-path-cmd-move-to [x y]
  (str "M " x ", " y "\n"))

(defn svg-path-cmd-cubic-bezier [x1 y1 x2 y2 x y]
  (str "C " x1 ", " y1 ", " x2 ", " y2 ", " x ", " y "\n"))

(defn interpolate [from to ratio]
  (+ from (* ratio (- to from))))

(defn update-links
  "Create .link elements in graph."
  [group root ctx]
  (let
      [link-data
       (-> root (.descendants) (.slice 1))

       link-selection
       (-> group
           (.selectAll ".link")
           (.data link-data
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
                                        (entity-cache/descendent-task? (:entity-cache ctx)
                                                                       (entity-cache/lookup-id (:entity-cache ctx) task-id)
                                                                       (entity-cache/lookup-id (:entity-cache ctx) (:hover-task-id ctx))))))
        (.classed "link-upstream" (fn [d]
                                    (let [task-id (-> d .-data .-data)]
                                      (or (= task-id (:hover-task-id ctx))
                                          (entity-cache/descendent-task? (:entity-cache ctx)
                                                                         (entity-cache/lookup-id (:entity-cache ctx) (:hover-task-id ctx))
                                                                         (entity-cache/lookup-id (:entity-cache ctx) task-id))))))
        (.attr "d" (fn [d]
                     (let
                         [task-id          (-> d .-data .-data)
                          hover-task-id    (:hover-task-id ctx)
                          this-x           (.-x d)
                          this-y           (.-y d)
                          parent-node-info (if (and hover-task-id
                                                    (:drag-task-id ctx)
                                                    (not (= hover-task-id (:drag-task-id ctx)))
                                                    (= task-id (:drag-task-id ctx))) ;; this is the dragged task -- its temporary parent is the hover task id
                                              (->> link-data
                                                   (filter #(= (-> % .-data .-data) hover-task-id))
                                                   first)
                                             (.-parent d))
                          parent-x         (.-x parent-node-info)
                          parent-y         (.-y parent-node-info)]
                       (str
                        (svg-path-cmd-move-to this-y
                                              this-x)
                        (svg-path-cmd-cubic-bezier (interpolate this-y parent-y 0.5)
                                                   this-x
                                                   (interpolate this-y parent-y 0.5)
                                                   parent-x
                                                   parent-y
                                                   parent-x))))))

    ;; Update links
    (-> link-selection
        (.classed "link-downstream" (fn [d]
                                      (let [task-id (-> d .-data .-data)]
                                        (entity-cache/descendent-task? (:entity-cache ctx)
                                                                       (entity-cache/lookup-id (:entity-cache ctx) task-id)
                                                                       (entity-cache/lookup-id (:entity-cache ctx) (:hover-task-id ctx))))))
        (.classed "link-upstream" (fn [d]
                                    (let [task-id (-> d .-data .-data)]
                                      (or (= task-id (:hover-task-id ctx))
                                          (entity-cache/descendent-task? (:entity-cache ctx)
                                                                         (entity-cache/lookup-id (:entity-cache ctx) (:hover-task-id ctx))
                                                                         (entity-cache/lookup-id (:entity-cache ctx) task-id))))))
        (.transition)
        (.ease (.-easeQuadOut js/d3))
        (.duration 250)
        (.attr "d" (fn [d]
                     (let
                         [task-id          (-> d .-data .-data)
                          hover-task-id    (:hover-task-id ctx)
                          this-x           (.-x d)
                          this-y           (.-y d)
                          parent-node-info (if (and hover-task-id
                                                    (:drag-task-id ctx)
                                                    (not (= hover-task-id (:drag-task-id ctx)))
                                                    (= task-id (:drag-task-id ctx))) ;; this is the dragged task -- its temporary parent is the hover task id
                                              (->> link-data
                                                   (filter #(= (-> % .-data .-data) hover-task-id))
                                                   first)
                                             (.-parent d))
                          parent-x         (.-x parent-node-info)
                          parent-y         (.-y parent-node-info)]
                       (str
                        (svg-path-cmd-move-to this-y
                                              this-x)
                        (svg-path-cmd-cubic-bezier (interpolate this-y parent-y 0.5)
                                                   this-x
                                                   (interpolate this-y parent-y 0.5)
                                                   parent-x
                                                   parent-y
                                                   parent-x))))))

    ;; clear old ones
    (-> link-selection
        (.exit)
        (.remove))))

(defn make-new-node-groups
  "Create a new group for each new node. Return the groups."
  [node-enter-selections ctx]
  (let
      [groups (-> node-enter-selections
                  (.append "g")
                  (.attr "class" (fn [d]
                                   (if (.-children d)
                                     (str "node node--internal")
                                     (str "node node--leaf"))))
                  (.attr "transform" (fn [d]
                                       (str "translate(" (.-y d)"," (.-x d) ")"))))]
    ;; Visible portion is mostly just a circle
    (-> groups
        (.append "circle")
        (.attr "r" 3)
        (.on "click" (:fn-on-task-clicked ctx))
        (.on "mouseenter" (:fn-on-task-mouse-entered ctx))
        (.on "mouseleave" (:fn-on-task-mouse-left ctx)))

    ;; Add a label as well
    (-> groups
        (.append "text")
        (.classed "node-label-text" true)
        (.attr "dy" 3)
        (.attr "x" (fn [d]
                     (if (.-children d)
                       -8
                       8)))
        (.style "text-anchor" (fn [d]
                                (if (.-children d)
                                  "end"
                                  "start")))
        (.style "text-anchor" "center")
        (.on "click" (:fn-on-task-clicked ctx))
        (.on "mouseenter" (:fn-on-task-mouse-entered ctx))
        (.on "mouseleave" (:fn-on-task-mouse-left ctx)))

    ;; Fade these new nodes into view
    (-> groups
        (.transition)
        (.ease (.-easeQuadOut js/d3))
        (.duration 250)
        (.attr "opacity" 1))

    ;; Add a drag handler
    (-> groups
        (.call (-> js/d3
                   (.drag)
                   (.on "start"
                        (fn [event data]
                          ((:fn-on-task-dragged ctx) event data)
                          (-> js/d3
                              (.select "#drag-drop-div")
                              (.classed "drag-drop-div--hidden" false)
                              (.select ".drag-drop-div-message")
                              (.text "Subtree of N nodes"))))
                   (.on "drag"
                        (fn [event]
                          (let
                              [ex (.-x (.-sourceEvent event))
                               ey (.-y (.-sourceEvent event))
                               prect (-> js/d3
                                          (.select ".ww-task-graph")
                                          (.node)
                                          (.getBoundingClientRect))
                               px (.-left prect)
                               py (.-top prect)
                               x (- ex px)
                               y (- ey py)]
                            (-> js/d3
                                (.select "#drag-drop-div")
                                (.style "left" (str x "px"))
                                (.style "top" (str y "px"))))))
                   (.on "end"
                        (fn []
                          ((:fn-on-task-dragged ctx) nil nil)
                          (-> js/d3
                              (.select "#drag-drop-div")
                              (.classed "drag-drop-div--hidden" true)))))))

    ;; Return the groups
    groups))

(defn update-nodes
  "Create .node elements in graph."
  [group root ctx]
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
       (make-new-node-groups new-nodes ctx)]

    (-> node-selection
        (.attr "opacity" 1))

    ;; Move existing nodes
    (-> node-selection
        (.transition)
        (.ease (.-easeQuadOut js/d3))
        (.duration 250)
        (.attr "transform" (fn [d]
                             (str "translate(" (.-y d)"," (.-x d) ")"))))

    ;; Update ellipses
    (-> new-nodes-groups
        (.merge node-selection)
        (.select ".node-label-text")
        (.text (fn [d]
                 (let [task-id (-> d .-data .-data)
                       summary (:summary (entity-cache/lookup-id (:entity-cache ctx) task-id))]
                   (if (and (> (count summary) 15)
                            (not (= (:hover-task-id ctx) task-id)))
                     (str (subs summary 0 13) "...")
                     summary))))
        (.style "font-weight" (fn [d]
                                (let [task-id (-> d .-data .-data)]
                                  (if (= (:hover-task-id ctx) task-id)
                                    "bold"
                                    "normal")))))

    ;; Add context highlighting
    (-> new-nodes-groups
        (.merge node-selection)
        (.classed "node-hovered" (fn [d]
                                   (let [task-id (-> d .-data .-data)]
                                     (= task-id (:hover-task-id ctx)))))
        (.classed "node-hovered-downstream"
                  (fn [d]
                    (let [task-id (-> d .-data .-data)]
                      (entity-cache/descendent-task? (:entity-cache ctx)
                                                     (entity-cache/lookup-id (:entity-cache ctx) task-id)
                                                     (entity-cache/lookup-id (:entity-cache ctx) (:hover-task-id ctx)))))))

    (-> node-selection
        (.exit)
        (.remove))))

(defn update-task-graph
  [ctx]
  (let [container (rd/dom-node (:component ctx))
        root (make-cluster-hierarchy container (hierarchical-task-data ctx))]
    (let [node-group (-> js/d3
                         (.select container)
                         (.select "svg")
                         (.select "#node-layer"))
          link-group (-> js/d3
                         (.select container)
                         (.select "svg")
                         (.select "#link-layer"))]
      (update-links link-group root ctx)
      (update-nodes node-group root ctx))))

(defn initialize-task-graph
  [ctx]
  (let [container (rd/dom-node (:component ctx))
        root (make-cluster-hierarchy container
                                     (hierarchical-task-data ctx))]
    (clear-container-create-groups container)
    (let [node-group (-> js/d3
                         (.select container)
                         (.select "svg")
                         (.select "#node-layer"))
          link-group (-> js/d3
                         (.select container)
                         (.select "svg")
                         (.select "#link-layer"))]
      (update-links link-group root ctx)
      (update-nodes node-group root ctx))))

(defn task-graph [entity-cache-atom selected-task-id-atom hover-id-atom this-task-item-id]
  (r/create-class
   (let
       [watcher-kw (keyword (str "task-map-redraw-" this-task-item-id))
        drag-id-atom (r/atom nil)
        should-hide-completed-nodes-atom (r/atom false)]
     (letfn
         [(fn-on-task-clicked [event data]
            (let [task-id (-> data .-data .-data)]
              (reset! selected-task-id-atom task-id)))
          (fn-on-task-mouse-entered [event data]
            (let [task-id (-> data .-data .-data)]
              (if (not (= task-id @hover-id-atom))
                (reset! hover-id-atom task-id))))
          (fn-on-task-mouse-left [event data]
            (reset! hover-id-atom nil))
          (fn-on-task-dragged [event data]
            (if (and event data)
              (let [task-id (-> data .-data .-data)]
                (reset! drag-id-atom task-id))
              (reset! drag-id-atom nil)))]

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
            [:div.ww-flexbox-spacer]]
           [:div.drag-drop-div.drag-drop-div--hidden
            {:id "drag-drop-div"}
            [:div.drag-drop-div-message]
            ]])

        :component-did-mount
        (fn [this]
          (letfn
              [(handler-func [_ _ old-state new-state]
                 (let
                     [selected-id @selected-task-id-atom
                      cache @entity-cache-atom]
                   (if (or (nil? this-task-item-id) (= selected-id this-task-item-id))
                     (update-task-graph (->GraphDrawContext this
                                                            cache
                                                            selected-id
                                                            @hover-id-atom
                                                            @drag-id-atom
                                                            fn-on-task-clicked
                                                            fn-on-task-mouse-entered
                                                            fn-on-task-mouse-left
                                                            fn-on-task-dragged
                                                            @should-hide-completed-nodes-atom)))))]
            (initialize-task-graph (->GraphDrawContext this
                                                       @entity-cache-atom
                                                       @selected-task-id-atom
                                                       @hover-id-atom
                                                       @drag-id-atom
                                                       fn-on-task-clicked
                                                       fn-on-task-mouse-entered
                                                       fn-on-task-mouse-left
                                                       fn-on-task-dragged
                                                       @should-hide-completed-nodes-atom))
            (add-watch should-hide-completed-nodes-atom
                       watcher-kw
                       handler-func)
            (add-watch entity-cache-atom
                       watcher-kw
                       handler-func)
            (add-watch selected-task-id-atom
                       watcher-kw
                       (fn [a1 a2 a3 a4]
                         (handler-func a1 a2 a3 a4)))
            (add-watch hover-id-atom
                       watcher-kw
                       handler-func)
            (add-watch drag-id-atom
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
                        watcher-kw)
          (remove-watch drag-id-atom
                        watcher-kw
                        handler-func))}))))

