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

(defn select-list-id
  ""
  [id]
  (reset! selected-list-id id))

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
  (backend-patch "/task" task-update-map #())
  (refresh-tasks))

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

(defn flash-element [element]
  (.add (.-classList element) "ww-task-list-item--edit-flash")
  (js/setTimeout #(.remove (.-classList element) "ww-task-list-item--edit-flash") 500))

(defn on-task-summary-edit-completed [ctx]
  (if @task-list-selected-task-item-summary-edited
    (let [summary-element (js/document.getElementById (:summary-eid ctx))
        task-item-element (js/document.getElementById (:item-eid ctx))]
    (flash-element summary-element))))

(defn task-list
  "Component showing task list."
  []
  [:div.ww-task-list
   (when (and (not-empty @list-table) @selected-list-id)
     (doall (for [t (sort-by #(* 1 (js/parseInt (:id %))) (clojure.set/select #(= (:list-id %) @selected-list-id) @task-table))]
              (let [is-selected-item (= @selected-task-item-id (:id t))
                    make-eid (fn [kind] (str "task-list-item-" kind ":" (:id t)))
                    context  {:task                t
                              :item-eid            (make-eid "item")
                              :top-panel-eid       (make-eid "top-panel")
                              :summary-eid         (make-eid "summary")
                              :expansion-panel-eid (make-eid "expansion-panel")}]
         ^{:key (:id t)}
         [:div.ww-task-list-item
          {:id (:item-eid context)}
          [:div.ww-task-list-item-top-panel {:on-click #(reset! selected-task-item-id (:id t))}
           [:div.ww-task-list-item-checkbox {:class (if is-selected-item "ww-task-list-item-checkbox--expanded")}
            "OPEN"]
           [:div.ww-task-list-item-summary
            {:id (:summary-eid context)
             ;; User can edit these directly
             :contenteditable "true"
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
                         )
             }
            (:summary t)]
           [:div.ww-task-list-item-time-til-due (:id t)]]
          [:div.ww-task-list-item-expansion-panel
           {:class (if is-selected-item "ww-task-list-item-expansion-panel--expanded")}
           [:div.ww-task-list-item-body
            {:contenteditable "true"
             :data-ph "Enter a description..."}]
           ]]))))])

(defn app
  "Main Application Component"
  []
  [:div.ww-app-body
   [list-menu]
   [task-list]])

(rd/render [app] (.-body js/document))
(refresh-lists)
(refresh-tasks)
