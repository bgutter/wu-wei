(ns wu_wei.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.edn :as edn]))

(def selected-list-id (r/atom 1))

(def list-table (r/atom #{}))

(def task-table (r/atom #{}))

(def selected-task-item-id (r/atom nil))
(def task-list-selected-task-item-summary-edited (r/atom nil))

(def context-stack (r/atom []))

(defn task-by-id [id]
  (first (clojure.set/select #(= (:id %) id) @task-table)))

(defn select-list-id
  ""
  [id]
  (reset! selected-list-id id))

(defn backend-put [endpoint body cb]
  (go (let [response (<! (http/put (str "http://localhost:9500" endpoint)
                                   {:with-credentials? false
                                    :body (pr-str body)
                                    :headers {"Content-type" "text/edn"}}))]
        (let [status (:status response)]
          (if (= status 200)
            (apply cb [status (edn/read-string (:body response))])
            (apply cb [status nil]))))))

(defn backend-request [endpoint cb]
  (go (let [response (<! (http/get (str "http://localhost:9500" endpoint)
                                   {:with-credentials? false
                                    :query-params {"since" 135}}))]
        (let [status (:status response)]
          (if (= status 200)
            (apply cb [status (edn/read-string (:body response))])
            (apply cb [status nil]))))))

(defn backend-patch [endpoint body callback]
  (println body)
  (go (let [response (<! (http/patch (str "http://localhost:9500" endpoint)
                                     {:with-credentials? false
                                      :body (pr-str body)
                                      :headers {"Content-type" "text/edn"}}))]
        (let [status (:status response)]
          (if (= status 200)
            (apply callback [status (edn/read-string (:body response))])
            (apply callback [status nil]))))))

(declare refresh-tasks)
(defn update-task
  ""
  [task-update-map]
  (println "Updating " task-update-map)
  (backend-patch "/task" task-update-map #())
  (refresh-tasks))

(defn make-new-task-current-context [task-content]
  (let
      [completed-task         (merge {:list-id @selected-list-id} task-content)
       parent-task            (last @context-stack)
       parent-subtask-ids     (:subtask-ids parent-task)]
    (println (str "MAKEW NEW TASK: " completed-task parent-task parent-subtask-ids))
    (backend-put "/task" completed-task
                 (fn callback [status body-edn]
                   (println (str "Processing callback: " status body-edn))
                   (let
                       [updated-subtask-ids (conj parent-subtask-ids (:id body-edn))
                        updated-task        (merge parent-task {:subtask-ids updated-subtask-ids})]
                     (update-task updated-task))))))

(defn get-task [id callback]
  (backend-request (str "/task/by-id/" id) callback))

(defn get-lists [callback]
  (backend-request "/list/all" callback))

(defn get-all-tasks [callback]
  (backend-request "/task/all" callback))

(defn refresh-lists
  ""
  []
  (get-lists #(reset! list-table %2)))

(defn refresh-tasks
  ""
  []
  (get-all-tasks #(reset! task-table %2)))

(defn sync-task
  ""
  [task]
  (backend-patch "/task" task #()))

(defn list-menu-entry
  ""
  [list]
  [:div.ww-list-menu-entry
   {:class (if (= @selected-list-id (:id list)) "ww-list-menu-entry--selected" "")
    :on-click #(select-list-id (:id list))}
   [:p.ww-list-menu-entry-icon (:icon list)]
   [:p.ww-list-menu-entry-title (:name list)]
   [:p.ww-flexbox-spacer]
   [:p.ww-list-menu-entry-count (:id list)]])

(defn list-menu-lists-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Lists"]
   (for [list @list-table]
     [list-menu-entry list])])

(defn list-menu-filters-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Filters"]])

(defn list-menu-tags-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Tags"]])

(defn list-menu
  "Component showing list menu."
  []
  [:div.ww-list-menu
   [list-menu-filters-section]
   [list-menu-lists-section]
   [list-menu-tags-section]])

(defn recurse-into-task
  ""
  [task]
  (swap! context-stack conj task))

(defn reset-context []
  (reset! context-stack [])
  (reset! selected-task-item-id nil))

(defn flash-element [element]
  (.add (.-classList element) "ww-task-list-item--edit-flash")
  (js/setTimeout #(.remove (.-classList element) "ww-task-list-item--edit-flash") 500))

(defn on-task-summary-edit-completed [ctx]
  (if @task-list-selected-task-item-summary-edited
    (let [summary-element (js/document.getElementById (:summary-eid ctx))
        task-item-element (js/document.getElementById (:item-eid ctx))]
    (flash-element summary-element))))

(defn task-list-context-stack
  "This is the stack of tasks that have been recursed into, shown at the
  top of the task list."
  []
  [:div.ww-task-context-list
   (when (seq @context-stack)
     [:div
      (let
          [list (first (clojure.set/select #(= (:id %) @selected-list-id) @list-table))]
        [:div.ww-task-list-context-item
         {:on-click reset-context}
         (:icon list)
         (:name list)
         ])
      (doall
       (map-indexed
        (fn [context-index t]
          ^{:key (str "STACK:" (:id t))}
          [:div.ww-task-list-context-item
           {:on-click #(do
                         (reset! context-stack (subvec @context-stack 0 (inc context-index)))
                         (reset! selected-task-item-id nil))}
           (str "⤵️ " (:summary t))])
        @context-stack))])])

(defn task-creation-box
  "This is the area where users can enter text and inline-actions for new tasks."
  []
  [:div.ww-task-creation-box
   {:content-editable true
    :data-ph (if (seq @context-stack) "New Subtask..." "New task...")
    :on-key-down #(do
                    (js/console.log "Pressed" (.-key %))
                    (if (= (.-key %) "Enter")
                      (do
                        (make-new-task-current-context
                         {:summary (.-textContent (.-target %))})
                        (set! (-> % .-target .-textContent) "")
                        (.preventDefault %))))}])

(defn task-list-item
  "An individual item within the task-list"
  [t context]
  (let
      [is-selected-item (= @selected-task-item-id (:id t))]
    ^{:key (:id t)}
    [:div.ww-task-list-item
     {:id (:item-eid context)
      :class (if is-selected-item "ww-task-list-item--selected")}

     [:div.ww-task-list-item-top-panel {:on-click #(reset! selected-task-item-id (:id t))}
      [:div.ww-task-list-item-checkbox {:class (if is-selected-item "ww-task-list-item-checkbox--expanded")}
       "OPEN"]
      (if (seq (:subtask-ids t))
        [:div "⋮⋮⋮"])
      [:div.ww-task-list-item-summary
       {:id (:summary-eid context)
        ;; User can edit these directly
        :content-editable "true"
        ;; when enter key pressed, lose focus
        :on-key-down #(do
                        (if (= (.-key %) "Enter")
                          (.blur (.-target %))
                          (reset! task-list-selected-task-item-summary-edited true)))
        ;; when exiting focus, apply changes
        :on-blur #(do
                    (on-task-summary-edit-completed context)
                    (update-task {:id (:id t) :summary (.-textContent (.-target %))})
                    (reset! task-list-selected-task-item-summary-edited false)
                    ;; (unexpand-task-list-item-expansion-panel (:expansion-panel-eid context))
                    )}
       (:summary t)]
      [:div.ww-task-list-item-time-til-due (:id t)]]

     [:div.ww-task-list-item-expansion-panel
      {:class (if is-selected-item "ww-task-list-item-expansion-panel--expanded")}

      [:div.ww-task-list-item-body
       {:content-editable "true"
        :data-ph "Enter a description..."}]

      [:div.ww-task-list-item-bottom-panel
       [:div.ww-task-list-item-scheduling "Start: November 2nd"]
       [:div.ww-task-list-item-scheduling "Due: November 11th"]
       [:div.ww-task-list-item-scheduling "Owner: Samantha"]
       [:div.ww-task-list-item-scheduling "Effort: 3D"]
       (if (not (:subtask-ids t))
         [:div.ww-task-list-item-scheduling "Add Subtask"])
       [:div.ww-flexbox-spacer]]

      (if (not (empty? (:subtask-ids t)))
        [:div.ww-task-list-expansion-panel-section-header-div "⋮⋮⋮ SUBTASKS"])

      [:div.ww-task-list-item-subtasks-panel
       (if (:subtask-ids t)
         [:div.ww-task-list-item-subtasks-blurb "➕ Add"])
       (if (seq (:subtask-ids t))
         [:div.ww-task-list-item-scheduling
          {:on-click #(recurse-into-task t)}
          "⤵️ Recurse"])
       (doall
        (for [subtask-id (:subtask-ids t)]
          (let [subtask (task-by-id subtask-id)]
            [:div.ww-task-list-item-subtasks-blurb
             (str " ▢ " (:summary subtask))])))]]]))

(defn task-list
  "Component showing task list."
  []
  [:div.ww-task-list
   [task-list-context-stack]
   (when (seq @context-stack)
     [:div.ww-task-list-context-separator
        "Direct Subtasks"])
   [task-creation-box]
   (when (and (seq @list-table) @selected-list-id)
     (let
         [task-seq (if (seq @context-stack)
                       ;; (map task-by-id (:subtask-ids (last @context-stack))))
                       (filter #((:subtask-ids (last @context-stack)) (:id %)) @task-table)
                       (clojure.set/select #(= (:list-id %) @selected-list-id) @task-table))]
     (doall (for [t (sort-by #(* 1 (js/parseInt (:id %))) task-seq)]
              (let [make-eid (fn [kind] (str "task-list-item-" kind ":" (:id t)))
                    context  {:task                t
                              :item-eid            (make-eid "item")
                              :top-panel-eid       (make-eid "top-panel")
                              :summary-eid         (make-eid "summary")
                              :expansion-panel-eid (make-eid "expansion-panel")}]
                [task-list-item t context])))))])

(defn controls-panel
  ""
  []
  [:div.ww-controls-panel
   [:div "👤"]
   [:div.ww-flexbox-spacer]
   [:div "⚙️"]])

(defn app
  "Main Application Component"
  []
  [:div.ww-app-body
   [list-menu]
   [task-list]
   [controls-panel]])

(rd/render [app] (.-body js/document))
(refresh-lists)
(refresh-tasks)
