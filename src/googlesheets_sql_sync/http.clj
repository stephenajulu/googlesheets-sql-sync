(ns googlesheets-sql-sync.http
  (:refer-clojure :exclude [get])
  (:require
   [org.httpkit.client :as http-client]
   [jsonista.core :as json]
   [mount.core :as mount]
   [googlesheets-sql-sync.throttle :as throttle]))

(mount/defstate throttler
  :start (throttle/make (:api-rate-limit (mount/args))))

(defn- try-http [f]
  (let [{:keys [error status body] :as r} @(f)]
    (when error
      (throw error))
    (when (>= status 400)
      (throw (ex-info (str "bad status: " status "\n" body) r)))
    (json/read-value body (json/object-mapper {:decode-key-fn true}))))

(defn get [url req]
  (throttle/wait throttler)
  (try-http #(http-client/get url req)))

(defn post [url req]
  (throttle/wait throttler)
  (try-http #(http-client/post url req)))
