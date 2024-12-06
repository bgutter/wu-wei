(ns wu-wei.requests
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.edn :as edn]))

(defn backend-address
  "Generate a path on the server, assumed to be the same location that served the frontend.

  TODO: Should be using SSL if this is ever exposed to the internet."
  [path]
  (str "http://" (.-hostname js/location) ":" (.-port js/location) path))

(defn backend-request
  "Send an HTTP request to the backend, given a verb, endpoint, body EDN, and callback."
  [verb endpoint body callback]
  (println (str "frontend -> backend: '" endpoint "' '" body "'"))
  (go (let [response (<! (verb (backend-address endpoint)
                                   {:with-credentials? false
                                    :body (pr-str body)
                                    :headers {"Content-type" "text/edn"}}))]
        (let [status (:status response)]
          (if (= status 200)
            (apply callback [status (edn/read-string (:body response))])
            (apply callback [status nil]))))))

(defn backend-put
  "PUT on the backend -- given an endpoint, EDN body, and a callback."
  [endpoint body cb]
  (backend-request http/put endpoint body cb))

(defn backend-post
  "POST on the backend -- given an endpoint, EDN body, and a callback."
  [endpoint body cb]
  (backend-request http/post endpoint body cb))

(defn backend-get
  "GET on the backend -- given an endpoint and a callback."
  [endpoint cb]
  (backend-request http/get endpoint nil cb))

(defn backend-patch
  "PATCH on the backend -- given an endpoint, EDN body, and a callback."
  [endpoint body cb]
  (backend-request http/patch endpoint body cb))

