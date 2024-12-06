(ns wu_wei.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.edn :as edn]
            [wu-wei.entities :as entities]
            [wu-wei.requests :as requests]
            [cljsjs.react-flip-move]))

;;
;; Entity Caching & Retrieval
;;

(def entity-cache
  "Atom containing a mapping from entity ID to cached entity data."
  (r/atom {}))

(defn fetch-entity
  "Retrieve data for this entity ID from backend.

  Updates `entity-cache` asynchronously -- does not return a value."
  [id]
  (requests/backend-get
   (str "/entity/" id)
   (fn [status-code entity-data]
     (if (= status-code 200)
       (swap! entity-cache assoc id entity-data)))))

(defn fetch-all-entities
  "Retrieve data for all entities from backend.

  Updates `entity-cache` asynchronously -- does not return a value."
  []
  (requests/backend-post
   "/search-entities"
   :true ;; Matches all entities
   (fn [status-code entity-ids]
     (if (= status-code 200)
       (dorun
        (for [id entity-ids]
          (fetch-entity id)))))))

(defn query-entities
  "Find all entities matching a query form."
  [query-forms]
  (let
      [matcher (entities/compile-query query-forms #(get @entity-cache %))]
    (filter matcher (map second @entity-cache))))

;;
;; UI
;;

;; TODO GOALS
(def selected-list-id (r/atom 1))

(def context-stack       (r/atom []))

(def list-table (r/atom #{}))

(def active-perspective (r/atom :task-list))

(def selected-task-item-id (r/atom nil))
(def task-list-selected-task-item-summary-edited (r/atom nil))

(defn select-list-id
  ""
  [id]
  (reset! selected-list-id id))


(defn update-task
  ""
  [task-update-map]
  (requests/backend-patch "/entity" task-update-map #(fetch-all-entities)))

(defn make-new-task-current-context [task-content]
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
  [milestone]
  (println milestone)
  [:div.ww-list-menu-entry
   {:class (if (= @selected-list-id (:id milestone)) "ww-list-menu-entry--selected" "")
    :on-click #(select-list-id (:id milestone))}
   [:p.ww-list-menu-entry-icon (or (:icon milestone) "X")]
   [:p.ww-list-menu-entry-title (:summary milestone)]
   [:p.ww-flexbox-spacer]
   [:p.ww-list-menu-entry-count (:id milestone)]])

(defn list-menu-lists-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Goals"]
   [list-menu-entry { :summary "Inbox" :icon "📥" }]
   (for [milestone (query-entities :milestone?)]
     [list-menu-entry milestone])])

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

(defn task-box-keygen [task]
  (str "key-task-box-" (:id task)))

(defn task-list-context-stack
  "This is the stack of tasks that have been recursed into, shown at the
  top of the task list."
  [context-stack]
  (doall
   (map-indexed
    (fn [context-index task]
      ^{:key (task-box-keygen task)}
      [:div.ww-task-list-context-item
       {:style {:position "relative" :z-index 1000}
        :on-click #(do
                     (reset! context-stack (subvec @context-stack 0 (inc context-index)))
                     (reset! selected-task-item-id nil))}
       (str "⤵️ " (:summary task))])
    @context-stack)))

(defn task-creation-box
  "This is the area where users can enter text and inline-actions for new tasks."
  []
  ^{:key "task-creation-box"}
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
      [selected-task-id @selected-task-item-id
       is-selected-item (and selected-task-item-id (= selected-task-id (:id t)))]
    ^{:key (task-box-keygen t)}
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
       ;; (if (seq (:subtask-ids t))
         [:div.ww-task-list-item-scheduling
          {:on-click #(recurse-into-task t)}
          "⤵️ Recurse"]
       (doall
        (for [subtask-id (:subtask-ids t)]
          (let [subtask (get @entity-cache subtask-id)]
            [:div.ww-task-list-item-subtasks-blurb
             (str " ▢ " (:summary subtask))])))]]]))

(defn task-list
  "Component showing task list."
  []
  (let
      [context-stack-items @context-stack
       recursed-into-task  (seq context-stack-items)
       task-query-forms    (cond
                            recursed-into-task [:subtask-of? (:id (last context-stack-items))]
                            (not (nil? @selected-list-id)) :task? ;; TODO
                            :default :task?)
       tasks            (query-entities task-query-forms)]
    [(r/adapt-react-class js/FlipMove) {:class "ww-task-list"
                                        :appear-animation nil
                                        :enter-animation "fade"
                                        :leave-animation "fade"
                                        :duration 350} ;; debug
     (concat
      (task-list-context-stack context-stack)
      (when recursed-into-task
        ^{:key "direct-subtask-separator"}
       [[:div.ww-task-list-context-separator
          "Direct Subtasks"]])
     [(task-creation-box)]
     (doall (for [t (sort-by #(* -1 (js/parseInt (:id %))) tasks)]
              (let [make-eid (fn [kind] (str "task-list-item-" kind ":" (:id t)))
                    context  {:task                t
                              :item-eid            (make-eid "item")
                              :top-panel-eid       (make-eid "top-panel")
                              :summary-eid         (make-eid "summary")
                              :expansion-panel-eid (make-eid "expansion-panel")}]
                (task-list-item t context)))))]))

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

(defn app
  "Main Application Component"
  []
  [:div.ww-app-body
   [controls-panel]
   [:div.ww-perspective-area
    (case @active-perspective
      :task-list [:div.ww-task-list-perspective
                  [list-menu]
                  [task-list]]
      :notes     [:div.ww-notes-perspective
                  [notes-menu]
                  [notes-view]])]])

(rd/render [app] (.-body js/document))

(fetch-all-entities)
