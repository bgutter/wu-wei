(ns wu-wei.components.task-list
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [wu-wei.entities :as entities]
            [wu-wei.util :as util]
            [wu-wei.entities.caching :as entity-cache]
            [cljsjs.react-flip-move]))

(defn task-box-keygen [task-id]
  (str "key-task-box-" task-id))

(defn task-list-item
  "An individual item within the task-list"
  [this-task-id entity-cache-atom selected-id-atom
   {:keys [on-select on-recurse on-modify-entity fn-delete-entity]}]
  (let
      [is-summary-edited-atom (r/atom false)]
    (fn []
      (let
          [cache                 @entity-cache-atom
           selected-id           @selected-id-atom
           selected-task         (entity-cache/lookup-id cache selected-id)
           selected-ancestry-ids (entity-cache/task-ancestry-ids cache selected-task)
           t                     (entity-cache/lookup-id cache this-task-id)
           display-mode          (if (nil? selected-id)
                                   :normal
                                   (if (= selected-id this-task-id)
                                     :context-final
                                     (if (some #(= this-task-id %) selected-ancestry-ids)
                                       :context
                                       :normal)))]
      [:div
       {:class (list (case display-mode
                       :context "ww-task-list-context-item"
                       :context-final "ww-task-list-context-item--final"
                       :normal "ww-task-list-item"))}

       [:div.ww-task-list-item-top-panel {:on-click #(reset! selected-id-atom this-task-id)}

        ;; Checkbox or recursion icon depending on display mode
        (if (some #{display-mode} [:context :context-final])
          [:div
           (str "⤵️ ")]
          [:div.ww-task-list-item-checkbox
           "OPEN"])

        ;; Editable display of task summary line
        [:div.ww-task-list-item-summary
         {:content-editable "true"
          :on-click (fn [evnt] (-> evnt .stopPropagation))
          ;; when enter key pressed, lose focus
          :on-key-down #(when (= (.-key %) "Enter")
                          (reset! is-summary-edited-atom true)
                          (.blur (.-target %)))
          ;; when exiting focus, apply changes
          :on-blur (fn [event & _]
                     (on-modify-entity (entities/set-summary t (.-textContent (.-target event)))))}
         (:summary t)]

        ;; Show ancestry
        [:div.ww-task-list-item-ancestry
         (let [ancestry-ids (entity-cache/task-ancestry-ids cache t)]
           (if (empty? ancestry-ids)
             ""
             (str
              (str/join " > " (map (comp :summary (partial entity-cache/lookup-id cache)) ancestry-ids))
              )))]

        ;; Marker for normal display items representing tasks with subtasks
        (if (and (seq (:subtask-ids t)) (= display-mode :normal))
          [:div
           "+"])

        ;; numeric field at end of item
        ;; for dev purposes, just shows ID if item for now
        [:div.ww-task-list-item-time-til-due (:id t)]]

       ;; The Expansion Panel
       [:div.ww-task-list-item-expansion-panel

        ;; Expansion Panel: Text field for showing and editing the body field
        [:div.ww-task-list-item-body
         {:content-editable "true"
          :data-ph "Enter a description..."}]

        ;; Expansion Panel: Bottom Panel
        [:div.ww-task-list-item-bottom-panel
         [:div.ww-task-list-item-mini-button
          {:class (if (entities/milestone? t) "ww-task-list-item-mini-button--selected")
           :on-click #(on-modify-entity (entities/toggle-milestone t))}
          (str "🧭 Milestone" (if (entities/milestone? t) "!" "?"))]
         [:div.ww-task-list-item-mini-button "Start: November 2nd"]
         [:div.ww-task-list-item-mini-button "Due: November 11th"]
         [:div.ww-task-list-item-mini-button "Owner: Samantha"]
         [:div.ww-task-list-item-mini-button "Effort: 3D"]
         [:div.ww-task-list-item-mini-button
          {:on-click (fn []
                       (if (= this-task-id @selected-id-atom)
                         (let [parent-id (entity-cache/parent-task-id cache t)]
                           (reset! selected-id-atom parent-id)))
                       (fn-delete-entity this-task-id))}
          "🗑️ Archive"
          ]
         [:div.ww-flexbox-spacer]]]]))))

(defn task-creation-box
  "This is the area where users can enter text and inline-actions for new tasks."
  [{:keys [placeholder-text parent-task fn-new-entity fn-update-entity]}]
  ^{:key "task-creation-box"}
  [:div.ww-task-creation-box
   {:content-editable true
    :data-ph placeholder-text
    :on-key-down (fn [evnt]
                    (js/console.log "Pressed" (.-key evnt))
                    (if (= (.-key evnt) "Enter")
                      (do
                        (fn-new-entity {:status :open :summary (.-textContent (.-target evnt))}
                                       (fn [new-id]
                                         (if parent-task
                                           (fn-update-entity (entities/add-subtask-by-id parent-task new-id)))))
                        (set! (-> evnt .-target .-textContent) "")
                        (-> evnt .preventDefault))))}])

(defn task-list-divider
  [section-name]
  ^{:key (str "task-list-divider-" section-name)}
  [:div.ww-task-list-context-separator section-name])

(defn task-list
  "Reagent component showing task list.

  `root-task-id` is the :id property of the task containing all of the
  tasks to draw in the list."
  [{:keys [entity-cache-atom
           query-forms-atom
           selected-id-atom
           fn-new-entity
           fn-update-entity
           fn-delete-entity]}]
  (println "Making task-list")
  (let
      [entity-cache-state @entity-cache-atom
       selected-task-id @selected-id-atom
       task-list-item-callbacks {:on-modify-entity
                                 fn-update-entity
                                 :fn-delete-entity
                                 fn-delete-entity}]
       ;; mk-task-list-item
       ;; (fn [task]
         ;; [task-list-item (:id task) entity-cache-atom selected-id-atom
                         ;; ])]

    ;;
    ;; The components
    ;;
    [(r/adapt-react-class js/FlipMove)
         {:class "ww-task-list"
          :easing "ease-out"
          :appear-animation nil
          :enter-animation "fade"
          :leave-animation "fade"
          ;; :duration 5000
          }
     (if selected-task-id
       ;; Contents With Context Mode
       ;; - Shows 3 sections of tasks:
       ;;   - Context stack
       ;;   - direct subtasks
       ;;   - indirect subtasks
       (let
           [selected-task     (entity-cache/lookup-id entity-cache-state selected-task-id)
            ancestry-ids      (entity-cache/task-ancestry-ids entity-cache-state selected-task)
            context-task-ids  (reverse (conj (reverse ancestry-ids) selected-task-id))
            context-tasks     (map #(entity-cache/lookup-id entity-cache-state %) context-task-ids)
            direct-subtasks   (entity-cache/query entity-cache-state [:subtask-of? (:id (last context-tasks))])
            indirect-subtasks (entity-cache/query entity-cache-state [:and
                                                                      [:descendent-of? (:id (last context-tasks))]
                                                                      [:not [:subtask-of? (:id (last context-tasks))]]])]
         (println "CONTEXT: " context-task-ids)
         (concat

          ;; The context stack
          (doall
           (for [task context-tasks]
             ^{:key (task-box-keygen (:id task))}
             [task-list-item (:id task) entity-cache-atom selected-id-atom task-list-item-callbacks]))

          ;; The task creation box
          [(task-creation-box {:placeholder-text
                               (str "➕ New Subtask of '" (:summary (last context-tasks)) "'")
                               :fn-new-entity
                               fn-new-entity
                               :parent-task
                               (entity-cache/lookup-id entity-cache-state selected-task-id)
                               :fn-update-entity
                               fn-update-entity})]

          ;; The direct subtasks
          [(task-list-divider "Direct Subtasks")]
          (doall
           (for [task direct-subtasks]
             ^{:key (task-box-keygen (:id task))}
             [task-list-item (:id task) entity-cache-atom selected-id-atom task-list-item-callbacks]))

          ;; The indirect subtasks
          [(task-list-divider "Indirect Subtasks")]
          (doall
           (for [task indirect-subtasks]
             ^{:key (task-box-keygen (:id task))}
             [task-list-item (:id task) entity-cache-atom selected-id-atom task-list-item-callbacks]))))

       ;; Un-Recursed Mode
       ;; - Displays all tasks
         (concat
          [(task-creation-box {:placeholder-text
                               "➕ New Goal"
                               :fn-new-entity
                               fn-new-entity
                               :fn-update-entity
                               fn-update-entity})]
          (doall
           (for [task (entity-cache/query @entity-cache-atom (or @query-forms-atom :task?))]
             ^{:key (task-box-keygen (:id task))}
             [task-list-item (:id task) entity-cache-atom selected-id-atom task-list-item-callbacks]))))]))
