(ns wu_wei.core
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]))

(defn list-menu-entry
  ""
  [icon title entry-count]
  [:div.ww-list-menu-entry
   [:p.ww-list-menu-entry-icon icon]
   [:p.ww-list-menu-entry-title title]
   [:p.ww-flexbox-spacer]
   [:p.ww-list-menu-entry-count entry-count]])

(defn list-menu-lists-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Lists"]])

(defn list-menu-filters-section
  ""
  []
  [:div.ww-list-menu-section
   [:div.ww-list-menu-section-title "Filters"]
   [list-menu-entry "🍹" "Work" 213]
   [list-menu-entry "🪷" "Health & Wellness" 3]])

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

(defn app
  "Main Application Component"
  []
  [:div.ww-app-body
   [list-menu]
   ])

(rd/render [app] (.-body js/document))
