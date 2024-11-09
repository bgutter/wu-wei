(ns wu_wei.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require react-dom
            [reagent.core :as r]
            [reagent.dom :as rd]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.edn :as edn]))

(def list-table (r/atom #{}))

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
   (for [l @list-table]
     [list-menu-entry (:icon l) (:name l) (:id l)])])

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

;; (defn test-ring []
;;   (go (let [response (<! (http/get "http://localhost:9500/test"
;;                                    {:with-credentials? false
;;                                     :query-params {"since" 135}}))]
;;         (prn (:status response))
;;         (prn (map :login (:body response))))))

(defn backend-request [endpoint cb]
  (go (let [response (<! (http/get (str "http://localhost:9500" endpoint)
                                   {:with-credentials? false
                                    :query-params {"since" 135}}))]
        (let [status (:status response)]
          (if (= status 200)
            (apply cb [status (edn/read-string (:body response))])
            (apply cb [status nil]))))))

(defn get-task [id callback]
  (backend-request (str "/task/by-id/" id) callback))

(defn get-lists [callback]
  (backend-request "/list/all" callback))

(defn refresh-lists
  ""
  []
  (get-lists #(reset! list-table %2)))

(rd/render [app] (.-body js/document))
(refresh-lists)
