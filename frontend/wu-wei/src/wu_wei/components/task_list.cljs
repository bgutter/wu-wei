(ns wu-wei.components.task-list
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [wu-wei.entities :as entities]
            [wu-wei.entities.caching :as entity-cache]
            [cljsjs.react-flip-move]))

(defn task-box-keygen [task]
  (str "key-task-box-" (:id task)))

(defn task-list-item
  "An individual item within the task-list"
  [t {:keys [is-selected? on-select on-recurse]}]
  (let
      [selected-task-id nil ;@selected-task-item-id
       context nil
       is-selected-item is-selected?] ; (and selected-task-item-id (= selected-task-id (:id t)))]
    ^{:key (task-box-keygen t)}
    [:div.ww-task-list-item
     {:id (:item-eid context)
      :class (if is-selected-item "ww-task-list-item--selected")}

     [:div.ww-task-list-item-top-panel {:on-click on-select}
      [:div.ww-task-list-item-checkbox {:class (if is-selected-item "ww-task-list-item-checkbox--expanded")}
       "OPEN"]
      (if (seq (:subtask-ids t))
        [:div "⋮⋮⋮"])
      [:div.ww-task-list-item-summary
       {:id (:summary-eid context)
        ;; User can edit these directly
        :content-editable "true"
        ;; when enter key pressed, lose focus
        ;; :on-key-down #(do
        ;;                 (if (= (.-key %) "Enter")
        ;;                   (.blur (.-target %))
        ;;                   (reset! task-list-selected-task-item-summary-edited true)))
        ;; ;; when exiting focus, apply changes
        ;; :on-blur #(do
        ;;             (on-task-summary-edit-completed context)
        ;;             (update-task {:id (:id t) :summary (.-textContent (.-target %))})
        ;;             (reset! task-list-selected-task-item-summary-edited false)
        ;;             ;; (unexpand-task-list-item-expansion-panel (:expansion-panel-eid context))
        ;;             )}
        }
       (:summary t)]
      [:div.ww-task-list-item-time-til-due (:id t)]]

     [:div.ww-task-list-item-expansion-panel
      {:class (if is-selected-item "ww-task-list-item-expansion-panel--expanded")}

      [:div.ww-task-list-item-body
       {:content-editable "true"
        :data-ph "Enter a description..."}]

      [:div.ww-task-list-item-bottom-panel
       [:div.ww-task-list-item-scheduling
          {:on-click on-recurse}
          "⤵️ Recurse"]
       [:div.ww-task-list-item-scheduling "Start: November 2nd"]
       [:div.ww-task-list-item-scheduling "Due: November 11th"]
       [:div.ww-task-list-item-scheduling "Owner: Samantha"]
       [:div.ww-task-list-item-scheduling "Effort: 3D"]
       (if (not (:subtask-ids t))
         [:div.ww-task-list-item-scheduling "Add Subtask"])
       [:div.ww-flexbox-spacer]]

      (if (not (empty? (:subtask-ids t)))
        [:div.ww-task-list-expansion-panel-section-header-div "⋮⋮⋮ SUBTASKS"])

      ;; [:div.ww-task-list-item-subtasks-panel
      ;;  (if (:subtask-ids t)
      ;;    [:div.ww-task-list-item-subtasks-blurb "➕ Add"])
      ;;  ;; (if (seq (:subtask-ids t))
      ;;    [:div.ww-task-list-item-scheduling
      ;;     ;; {:on-click #(recurse-into-task t)}
      ;;     "⤵️ Recurse"]
      ;;  (doall
      ;;   (for [subtask-id (:subtask-ids t)]
      ;;     (let [subtask (get @entity-cache subtask-id)]
      ;;       [:div.ww-task-list-item-subtasks-blurb
      ;;        (str " ▢ " (:summary subtask))])))]
      ]]))

(defn context-task-list-item
  "An item in a `task-list` that represents a task in the context-stack of the list."
  [task {:keys [on-select is-bottom]}]
  (println (str "CONTEXT: " task))
  ^{:key (task-box-keygen task)}
  [:div.ww-task-list-context-item
   {:style {:position "relative" :z-index 1000}
    :on-click on-select
    :class (if is-bottom "ww-task-list-context-item--final")}
   (str "⤵️ " (:summary task))])

(defn task-creation-box
  "This is the area where users can enter text and inline-actions for new tasks."
  [{:keys [placeholder-text]}]
  ^{:key "task-creation-box"}
  [:div.ww-task-creation-box
   {:content-editable true
    :data-ph placeholder-text
    ;; :on-key-down #(do
    ;;                 (js/console.log "Pressed" (.-key %))
    ;;                 (if (= (.-key %) "Enter")
    ;;                   (do
    ;;                     (make-new-task-current-context
    ;;                      {:summary (.-textContent (.-target %))})
    ;;                     (set! (-> % .-target .-textContent) "")
    ;;                     (.preventDefault %))))}])
    }])

(defn task-list-divider
  [section-name]
  [:div.ww-task-list-context-separator section-name])

(defn task-list
  "Reagent component showing task list.

  `root-task-id` is the :id property of the task containing all of the
  tasks to draw in the list."
  [initial-context entity-cache-atom]
  (let
      [context-stack-atom (r/atom (or initial-context [])) ;; vector of entity IDs
       selected-entity-id-atom (r/atom nil)] ;; int ID
    (fn []
      (let
          [context-task-ids @context-stack-atom
           context-tasks (map #(entity-cache/lookup-id @entity-cache-atom %) context-task-ids)
           direct-subtasks (entity-cache/query @entity-cache-atom [:subtask-of? (:id (last context-tasks))])
           indirect-subtasks (entity-cache/query @entity-cache-atom [:and
                                                                     [:descendent-of? (:id (last context-tasks))]
                                                                     [:not [:subtask-of? (:id (last context-tasks))]]])
           task-to-task-list-item (fn [task]
                                    (task-list-item task
                                                    {:is-selected?
                                                     (and @selected-entity-id-atom
                                                          (= @selected-entity-id-atom (:id task)))
                                                     :on-select
                                                     (fn []
                                                       (reset! selected-entity-id-atom (:id task)))
                                                     :on-recurse
                                                     (fn []
                                                       (reset! selected-entity-id-atom nil)
                                                       (swap! context-stack-atom conj (:id task)))}))]
        (println context-task-ids)
        [(r/adapt-react-class js/FlipMove)
         {:class "ww-task-list"
          :appear-animation nil
          :enter-animation "fade"
          :leave-animation "fade"
          :duration 350} ;; debug
         (if (seq context-tasks)

           ;; Recursed mode
           ;; - Shows 3 sections of tasks:
           ;;   - Context stack
           ;;   - direct subtasks
           ;;   - indirect subtasks
           (concat

            ;; The context stack
            (doall
             (map-indexed (fn [context-index task]
                            (context-task-list-item task
                                                    {:on-select
                                                     (fn []
                                                       (swap! context-stack-atom subvec 0 (inc context-index)))
                                                     :is-bottom
                                                     (= context-index (dec (count context-tasks)))}))
                          context-tasks))

            ;; The task creation box
            [(task-creation-box {:placeholder-text (str "New Subtask of '" (:summary (last context-tasks)) "'")})]

            ;; The direct subtasks
            [(task-list-divider "Direct Subtasks")]
            (doall (map task-to-task-list-item direct-subtasks))

            ;; The indirect subtasks
            [(task-list-divider "Indirect Subtasks")]
            (doall (map task-to-task-list-item indirect-subtasks)))

           ;; Un-Recursed Mode
           ;; - Displays all tasks
           (concat
            [(task-creation-box {:placeholder-text "New Goal"})]
            (doall (map task-to-task-list-item
                        (entity-cache/query @entity-cache-atom :task?)))))]))))

    ;;   [context-stack-items @context-stack
    ;;    recursed-into-task  (seq context-stack-items)
    ;;    task-query-forms    (cond
    ;;                         recursed-into-task [:subtask-of? (:id (last context-stack-items))]
    ;;                         (not (nil? @selected-list-id)) :task? ;; TODO
    ;;                         :default :task?)
    ;;    tasks            (query-entities task-query-forms)]
    ;; [(r/adapt-react-class js/FlipMove) {:class "ww-task-list"
    ;;                                     :appear-animation nil
    ;;                                     :enter-animation "fade"
    ;;                                     :leave-animation "fade"
    ;;                                     :duration 350} ;; debug
    ;;  (concat
    ;;   (task-list-context-stack context-stack)
    ;;   (when recursed-into-task
    ;;     ^{:key "direct-subtask-separator"}
    ;;    [[:div.ww-task-list-context-separator
    ;;       "Direct Subtasks"]])
    ;;  [(task-creation-box)]
    ;;  (doall (for [t (sort-by #(* -1 (js/parseInt (:id %))) tasks)]
    ;;           (let [make-eid (fn [kind] (str "task-list-item-" kind ":" (:id t)))
    ;;                 context  {:task                t
    ;;                           :item-eid            (make-eid "item")
    ;;                           :top-panel-eid       (make-eid "top-panel")
    ;;                           :summary-eid         (make-eid "summary")
    ;;                           :expansion-panel-eid (make-eid "expansion-panel")}]
    ;;             (task-list-item t context)))))]))
