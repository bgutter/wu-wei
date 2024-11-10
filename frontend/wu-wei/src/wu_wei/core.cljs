(ns wu_wei.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.edn :as edn]))

(def selected-list-id (r/atom nil))

(def list-table (r/atom #{}))

(def task-table (r/atom #{}))

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

(defn on-task-summary-edit-completed [event task]
  (.blur (.-target event))
  (let [summary-element (.-target event)
        task-item-element (.-parentElement (.-parentElement summary-element))]
    (flash-element task-item-element)))

(defn expand-task-list-item-expansion-panel [task]
  (let [elem (js/document.getElementById (str "task-list-item-expansion-panel:" (:id task)))]
    (js/console.log "Expanding " elem)
    (-> elem .-classList (.add "ww-task-list-item-expansion-panel--expanded"))))

(defn unexpand-task-list-item-expansion-panel [task]
  (let [elem (js/document.getElementById (str "task-list-item-expansion-panel:" (:id task)))]
    (js/console.log "Expanding " elem)
    (-> elem .-classList (.remove "ww-task-list-item-expansion-panel--expanded"))))

(defn task-list
  "Component showing task list."
  []
  [:div.ww-task-list
   (when (and (not-empty @list-table) @selected-list-id)
       (for [t (sort-by #(* 1 (js/parseInt (:id %))) (clojure.set/select #(= (:list-id %) @selected-list-id) @task-table))]
         ^{:key (:id t)}
         [:div.ww-task-list-item
          [:div.ww-task-list-item-top-panel
           [:div.ww-task-list-item-checkbox
            {:on-click #(js/console.log "Clicked!")}
            "▢"]
           [:div.ww-task-list-item-summary
            {;; User can edit these directly
             :contenteditable "true"
             ;; when enter key pressed, lose focus
             :on-key-down #(if (= (.-key %) "Enter") (on-task-summary-edit-completed % t))
             ;; when exiting focus, apply changes
             :on-blur #(do
                         (update-task {:id (:id t) :summary (.-textContent (.-target %))})
                         (unexpand-task-list-item-expansion-panel t))
             ;; 
             :on-focus #(expand-task-list-item-expansion-panel t)
             }
            (:summary t)]
           [:div.ww-task-list-item-time-til-due (:id t)]]
          [:div.ww-task-list-item-expansion-panel
           {:id (str "task-list-item-expansion-panel:" (:id t))}
           "XXX"]]))])

(defn app
  "Main Application Component"
  []
  [:div.ww-app-body
   [list-menu]
   [task-list]])

(rd/render [app] (.-body js/document))
(refresh-lists)
(refresh-tasks)
