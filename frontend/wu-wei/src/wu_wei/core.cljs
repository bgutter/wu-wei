(ns wu-wei.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [wu-wei.util :as util]
            [wu-wei.entities :as entities]
            [wu-wei.entities.caching :as entity-cache]
            [wu-wei.requests :as requests]
            [wu-wei.components.task-list :refer [task-list]]
            [cljsjs.react-flip-move]
            [cljsjs.d3]))

;;
;; Entity Caching & Retrieval
;;


(def entity-cache-atom
  "Atom containing a mapping from entity ID to cached entity data."
  (r/atom (entity-cache/new-cache)))

(defn fetch-entity!
  "Update `entity-cache-atom` with details for this entity from the backend"
  [id]
  (requests/backend-get
   (str "/entity/" id)
   (fn [status-code entity-data]
     (if (= status-code 200)
       ;; (swap! entity-cache assoc id entity-data)))))
       (swap! entity-cache-atom entity-cache/set-entity-data (util/ts-now) id entity-data)))))

(defn fetch-all-entities!
  "Retrieve data for all entities from backend.

  Updates `entity-cache-atom` asynchronously -- does not return a value."
  []
  (requests/backend-post
   "/search-entities"
   :true ;; Matches all entities
   (fn [status-code entity-ids]
     (if (= status-code 200)
       (dorun
        (for [id entity-ids]
          (fetch-entity! id)))))))

(defn new-entity!
  "Send a new entity to the backend"
  [entity & [with-new-id]]
  (requests/backend-put
   "/entity"
   entity
   (fn [status-code new-id]
     (fetch-entity! new-id)
     (if with-new-id
       (with-new-id new-id)))))

(defn update-entity!
  "Send an updated entity to the backend"
  [entity]
  (requests/backend-patch
   "/entity"
   entity
   (fn [status-code _]
     (fetch-entity! (:id entity)))))

(defn delete-entity!
  "Delete an entity entirely"
  [entity-or-id]
  (let
      [id-to-delete (if (entities/entity? entity-or-id)
                        (:id entity-or-id)
                        entity-or-id)]
  (requests/backend-delete
   (str "/entity/" id-to-delete)
   (fn [status-code _]
     (if (= 200 status-code)
       (swap! entity-cache-atom entity-cache/remove-entity-data id-to-delete))))))

(fetch-all-entities!)

;;
;; UI
;;

;; TODO GOALS
#_(def selected-list-id (r/atom 1))

#_(def context-stack       (r/atom []))

#_(def list-table (r/atom #{}))

(def active-perspective (r/atom :task-list))

(def task-list-selected-entity-id-atom (r/atom nil))
(def task-list-query-forms-atom (r/atom nil))

#_(def selected-task-item-id (r/atom nil))
#_(def task-list-selected-task-item-summary-edited (r/atom nil))

#_(defn select-list-id
  ""
  [id]
  (reset! selected-list-id id))

#_(defn update-task
  ""
  [task-update-map]
  (requests/backend-patch "/entity" task-update-map #(fetch-all-entities)))

#_(defn make-new-task-current-context [task-content]
  (let
      [completed-task         (merge entities/task-defaults {:list-id @selected-list-id} task-content)
       parent-task            (some-> @context-stack last)
       parent-task-id         (:id parent-task)
       parent-subtask-ids     (:subtask-ids parent-task)]
    (requests/backend-put "/entity" completed-task
                 (fn callback [status new-id]
                   (let
                       [parent-task         (get @entity-cache parent-task-id)
                        updated-subtask-ids (conj (or (:subtask-ids parent-task) #{}) new-id)
                        updated-task        (merge parent-task {:subtask-ids updated-subtask-ids})]
                     (update-task updated-task))))))

(defn list-menu-entry
  ""
  [milestone {:keys [on-select]}]
  [:div.ww-list-menu-entry {:on-click on-select}
   ;; {:class (if (= @selected-list-id (:id milestone)) "ww-list-menu-entry--selected" "")
    ;; :on-click #(select-list-id (:id milestone))}
   ;; [:p.ww-list-menu-entry-icon (or (:icon milestone) "X")]
   [:p.ww-list-menu-entry-title (:icon milestone) " " (:summary milestone)]
   [:p.ww-flexbox-spacer]
   [:p.ww-list-menu-entry-count (:id milestone)]])

(defn list-menu-lists-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Milestones"]
   (for [milestone (entity-cache/query @entity-cache-atom :milestone?)]
     ^{:key (str "list-menu-entry-" (:id milestone))}
     [list-menu-entry milestone
      {:on-select (fn []
                    (reset! task-list-selected-entity-id-atom (:id milestone))
                    (reset! task-list-query-forms-atom :true))}])])

(defn list-menu-filters-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Filters"]
   [list-menu-entry { :summary "All Tasks" :icon "🌎" }
    {:on-select (fn []
                  (reset! task-list-selected-entity-id-atom nil)
                  (reset! task-list-query-forms-atom :true))}]
   [list-menu-entry { :summary "Milestones" :icon "📥" }
    {:on-select (fn []
                  (reset! task-list-selected-entity-id-atom nil)
                  (reset! task-list-query-forms-atom :milestone?))}]
  [list-menu-entry { :summary "Top-Level Tasks" :icon "🎯" }
    {:on-select (fn []
                  (reset! task-list-selected-entity-id-atom nil)
                  (reset! task-list-query-forms-atom [:not :subtask?]))}]])

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

;; (defn on-task-summary-edit-completed [ctx]
;;   (if @task-list-selected-task-item-summary-edited
;;     (let [summary-element (js/document.getElementById (:summary-eid ctx))
;;         task-item-element (js/document.getElementById (:item-eid ctx))]
;;     (flash-element summary-element))))

(defn controls-panel
  ""
  []
  [:div.ww-controls-panel
   [:div "👤"]
   ;; [:div.ww-flexbox-spacer]
   [:div
    {:on-click #(reset! active-perspective :notes)}
    "🗒️"]
   [:div
    {:on-click #(reset! active-perspective :task-list)}
     "☑️"]
   [:div
    {:on-click #(reset! active-perspective :task-graph)}
     "🌳"]
   [:div.ww-flexbox-spacer]
   [:div "⚙️"]])

(defn notes-menu
  ""
  []
  [:div.ww-list-menu
   ])

;; (defn get-div-caret-pos
;;   [div]
;;   (.focus div)
;;   (let
;;       [range-a 
;;        range-b (.cloneRange range-a)]
;;     (println "RANGE A: " )
;;     (.selectNodeContents range-b div)
;;     (.setEnd range-b (.-endContainer range-a) (.-endOffset range-a))
;;     (-> range-b .toString .-length)))
(def note-cache (r/atom {0 {:id 0
                           :summary ""
                           :body ""
                           :subnotes [1 3]}
                         1 {:id 1
                           :summary "This is a headline"
                           :body "Some top-level text"
                           :subnotes [2]}
                         2 {:id 2
                           :summary "This is a subheadline"
                           :body "Some text under the second headline"
                           :subnotes []}
                         3 {:id 3
                           :summary "Second headline at top level"
                           :body ""
                           :subnotes []}}))

(defn scan-div-new-heading
  [div]
  (let
      [range       (-> js/document .getSelection (.getRangeAt 0))
       active-line (subs (.-wholeText (.-endContainer range)) 0 (.-endOffset range))]
    (every? #(= % "*") active-line)))

(defn pop-div-summary-body
  [div]
  (let [selection-range  (-> js/document .getSelection (.getRangeAt 0))
        range-1          (let [rng (-> js/document (.createRange))]
                           (-> rng (.setStartBefore (.-endContainer selection-range)))
                           (-> rng (.setEndAfter (.-endContainer selection-range)))
                           rng)
        range-2          (let [rng (-> js/document (.createRange))]
                           (-> rng (.setStartAfter (.-endContainer selection-range)))
                           (-> rng (.setEndAfter div))
                           rng)
        summary-unparsed (-> range-1 (.extractContents) .-textContent)
        summary-re-ret   (re-matches #"(\*+)\s*(.*)" summary-unparsed)
        asterisks        (get summary-re-ret 1)
        summary          (get summary-re-ret 2)
        body             (-> range-2 (.extractContents) .-textContent)]
    [asterisks summary body]))

(defn read-div-text
  "textContent and innerText both fail to convert <br> to \n in Firefox
  so just DIY it."
  [div ]
  (let
      [tokens (doall (for [child (.-children div)]
                       (do
                         (case (.-tagName child)
                           "br" "\n"
                           "div" (.-textContent div)
                           ""))))]
    (apply str tokens)))

(defn note-node-body-region-key-down-handler
  [event note]
  (let
      [key-pressed (.-key event)
       div         (.-target event)
       div-content (read-div-text div)]
    (do
      (case key-pressed
        " " (if (scan-div-new-heading div)
              (do
                (let
                    [[asterisks summary body] (pop-div-summary-body div)
                     updated-body             div-content
                     updated-note             (merge note
                                                     {:body updated-body
                                                     :subnotes 99})
                     ]
                  (reset! note-cache (merge @note-cache
                                            {(:id updated-note) updated-note
                                             99 {:id 99 :summary summary :body body :subnotes []}}))
                  (.preventDefault event)
                  ;; Add new note node
                  ;; prepend it as a subnote to the parent of this note
                  ;; Remove clipped content from current note
                  )))
        nil))))

(defn note-node [id]
  (let
      [note (get @note-cache id)]
    [:div.ww-note-node
     [:div.ww-note-node-indent-region "•"]
     [:div.ww-note-node-content-region
      [:div.ww-note-node-summary-region
       {:content-editable true}
       (:summary note)]
      [:div.ww-note-node-body-region
       {:content-editable true
        :on-key-down #(note-node-body-region-key-down-handler % note)}
       (:body note)]
     (doall (for [child-id (:subnotes note)]
       [note-node child-id]))]]))

(defn notes-view
  ""
  []
  [:div.ww-notes-view
   [note-node 0]])

(defn draw-task-graph
  [cache root-task-id component]
  (let [dom-node (rd/dom-node component)
            svg (-> js/d3 (.select dom-node))

            g (-> svg
                  (.append "g")
                  (.attr "transform" "translate(40,40)"))

            tree (-> js/d3
                     (.tree)
                     (.size (clj->js [460 460])))

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
                                          (str "translate(" (.-y d)"," (.-x d) ")"))))

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
        nil))

(defn d3-component [entity-cache-atom selected-task-id-atom]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (draw-task-graph @entity-cache-atom @selected-task-id-atom this))

    :reagent-render
    (fn []
      [:svg {:style {:width "100%" :height "100%"}}])}))

(defn app
  "Main Application Component"
  []
  [:div.ww-app-body
   [controls-panel]
   [:div.ww-perspective-area
    (case @active-perspective
      :task-list [:div.ww-task-list-perspective
                  [list-menu]
                  [task-list {:selected-id-atom task-list-selected-entity-id-atom
                              :entity-cache-atom entity-cache-atom
                              :query-forms-atom task-list-query-forms-atom
                              :fn-new-entity new-entity!
                              :fn-update-entity update-entity!
                              :fn-delete-entity delete-entity!}]]
      :notes     [:div.ww-notes-perspective
                  [notes-menu]
                  [notes-view]]
      :task-graph [:div.ww-task-graph-perspective
                   [d3-component entity-cache-atom task-list-selected-entity-id-atom]
                   #_[task-graph {:entity-cache-atom entity-cache-atom
                                :selected-id-atom task-list-selected-entity-id-atom}]])]])

(rd/render [app] (.-body js/document))

#_(rd/render [task-list nil entity-cache-atom] (.-body js/document))

#_(fetch-all-entities)
