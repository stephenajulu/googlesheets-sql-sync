(ns googlesheets-sql-sync.system
  (:require
   [clojure.core.async :as async :refer [<! >!! chan close! dropping-buffer go-loop]]
   [clojure.java.browse :refer [browse-url]]
   [googlesheets-sql-sync.config :as config]
   [googlesheets-sql-sync.db :as db]
   [googlesheets-sql-sync.interval :as interval]
   [googlesheets-sql-sync.oauth :as oauth]
   [googlesheets-sql-sync.sheets :as sheets]
   [googlesheets-sql-sync.web :as web]))

(defn stop [{:keys [stop-server timeout> work>]}]
  (println "\nShutting down")
  (when work>
    (close! work>))
  (when timeout>
    (close! timeout>))
  (when stop-server
    (stop-server)))

(defn- show-init-message [c]
  (let [url (oauth/url c)]
    (println "Please visit the oauth url in your browser:\n" url)
    (when (oauth/local-redirect? c)
      (browse-url url))))

(defn- do-sync [{:keys [auth-only config-file timeout>] :as ctx}]
  (try
    (let [c (config/read-file config-file)]
      (try
        (if-let [token (oauth/refresh-token config-file)]
          (if auth-only
            (do (println "Authentication done")
                (stop ctx))
            (do
              (->> (:sheets c)
                   (map #(sheets/get-rows % token))
                   (run! #(db/update-table c %)))
              (println "Sync done")))
          (show-init-message c))
        (catch Exception e (println (.getMessage e) "\nSync failed")))
      (println "Next sync in" (interval/->string (:interval c)))
      (async/put! timeout> (interval/->ms (:interval c))))
    (catch Exception e (do (println "Failed reading config file" (.getMessage e))
                           (stop ctx)))))

(defn- start-worker [{:keys [work>] :as ctx}]
  (go-loop []
    (if-let [[job code] (<! work>)]
      (do (case job
            :sync (do-sync ctx)
            :code (do
                    (oauth/handle-code ctx code)
                    (do-sync ctx)))
          (recur))
      (println "Stopping worker")))
  (async/put! work> [:sync])
  (println "Worker started"))

(defn start [ctx]
  (if (and (:auth-only ctx) (:no-server ctx))
    (do
      (println "Server is required for authentication")
      :not-ok)
    (try
      (-> ctx
          (assoc :work> (chan))
          (assoc :timeout> (chan (dropping-buffer 1)))
          web/start
          (doto
           interval/connect-timeouts
            start-worker))
      (catch Exception e (do (println "Error while starting:" (.getMessage e))
                             :not-ok)))))

(defn trigger-sync [{:keys [work>]}]
  (println "Sync triggered")
  (>!! work> [:sync]))
