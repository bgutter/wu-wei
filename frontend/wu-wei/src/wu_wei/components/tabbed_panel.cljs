(ns wu-wei.components.tabbed-panel
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [wu-wei.entities :as entities]
            [wu-wei.util :as util]
            [wu-wei.entities.caching :as entity-cache]))

(defn tabbed-panel
  [tabs-hash-map]
  (let
      [active-tab-atom (r/atom (first (keys tabs-hash-map)))]
    (fn []
      (let
          [[tab-buttons active-tab-node]
           (reduce (fn [[tab-name-buttons tab-content-panel] [tab-name tab-hiccup]]
                     (let
                         [is-selected-tab (= @active-tab-atom tab-name)
                          this-tab-name-button
                          ^{:key (str "tab-button-" tab-name)}
                          [:div.ww-task-list-item-mini-button
                           {:class (if is-selected-tab "ww-task-list-item-mini-button--set")
                           :on-click (fn [event]
                                        (reset! active-tab-atom tab-name))}
                           tab-name]

                          this-tab-content
                          ^{:key (str "tab-content-" tab-name)}
                          [:div.ww-tabbed-panel-content-container
                           tab-hiccup]]
                       [(conj tab-name-buttons this-tab-name-button)
                        (if is-selected-tab
                          this-tab-content
                          tab-content-panel)]))
                   [nil nil]
                   tabs-hash-map)]
        [:div.ww-tabbed-panel
         [:div.ww-tabbed-panel-buttons-panel
          (reverse tab-buttons)]
         [:div.ww-tabbed-panel-contents-panel
          active-tab-node]]))))
