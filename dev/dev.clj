(ns dev
  (:require
   [clojure.core.async :as async :refer [<! >! >!! chan close! dropping-buffer go go-loop pipeline-async timeout]]
   [clojure.java.browse :refer [browse-url]]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.java.io :as io]
   [clojure.java.jdbc :as jdbc]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [cider-nrepl.main :as cider]
   [org.httpkit.client :as http-client]
   [jsonista.core :as json]
   [expound.alpha :as expound]
   [googlesheets-sql-sync.config :as config]
   [googlesheets-sql-sync.db :as db]
   [googlesheets-sql-sync.interval :as interval]
   [googlesheets-sql-sync.http :as http]
   [googlesheets-sql-sync.log :as log]
   [googlesheets-sql-sync.metrics :as metrics]
   [googlesheets-sql-sync.oauth :as oauth]
   [googlesheets-sql-sync.sheets :as sheets]
   [googlesheets-sql-sync.core :as core]
   [googlesheets-sql-sync.throttle :as throttle]
   [googlesheets-sql-sync.util :as util]
   [googlesheets-sql-sync.web :as web]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn -main [& args]
   (go (cider/init ["cider.nrepl/cider-middleware"]))
   (clojure.main/repl :init #(in-ns 'dev)))

(def options {:port 9955
              :config-file "googlesheets_sql_sync.json"
              :auth-file "googlesheets_sql_sync.auth.json"
              :sys-exit #(println "System/exit" %)
              ; :no-server true
              ; :no-metrics true
              ; :auth-only true
              :oauth-route "/oauth"
              :metrics-route "/metrics"
              :api-rate-limit 4000})

(def system)

(defn start []
  (alter-var-root #'system (constantly (core/start options))))

(defn stop []
  (alter-var-root #'system #(do (core/stop %) nil)))

(comment
  (config/generate options)
  (start)
  (stop)
  (core/trigger-sync system)
  (core/wait system))
