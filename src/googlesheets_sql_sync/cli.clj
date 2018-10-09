(ns googlesheets-sql-sync.cli
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]
   [googlesheets-sql-sync.config :as config]
   [googlesheets-sql-sync.core :as core]
   [googlesheets-sql-sync.util :refer [valid-port?]]
   [signal.handler :as signal])
  (:gen-class))

(def usage "

  1. Generate a config file by running:

    java -jar googlesheets_sql_sync.jar --init

  2. Fill out the config file.

  3. Then run:

    java -jar googlesheets_sql_sync.jar

  4. Follow setup instructions and you are good to go.

")

(def cli-options
  ;; An option with a required argument
  [["-p" "--port PORT" "Port number"
    :default 9955
    :parse-fn #(Integer/parseInt %)
    :validate [valid-port? "Must be a number between 0 and 65536"]]
   [nil "--config-file PATH" "Config file path"
    :default "googlesheets_sql_sync.json"]
   [nil "--auth-file PATH" "File path to store Google auth secrets, file is updated on sync"
    :default "googlesheets_sql_sync.auth.json"]
   [nil "--oauth-route" "Set route to use in OAuth redirect URL"
    :default "/oauth"]
   [nil "--metrics-route" "Set route to serve metrics at"
    :default "/metrics"]
   [nil "--api-rate-limit" "Max interval calling Google API in ms"
    :default 1000
    :parse-fn #(Integer/parseInt %)
    :validate [pos-int? "Must be a positive integer"]]
   [nil "--init" "Initialize a new config file"]
   [nil "--auth-only" "Setup authentication, then quit, don't sync"]
   [nil "--no-server" "Disable server, disables authentication and metrics"]
   [nil "--no-metrics" "Disable metrics"]
   ["-h" "--help"]])

(defn- print-list [errs]
  (run! println errs))

(defn- invalid-flags
  "Make sure combination of flags is valid."
  [options]
  (let [bad [[:init :auth-only]
             [:init :no-server]
             [:auth-only :no-server]]
        errs (->> bad
                  (filter #(every? options %))
                  (map #(str "Sorry, you cannot combine flags "
                             (string/join " and " (map name %))
                             ".")))]
    (when-not (empty? errs) errs)))

(comment
  (= (invalid-flags {:init true :auth-only true})
     '("Sorry, you cannot combine flags init and auth-only."))
  (= (invalid-flags {})
     nil))

(defn- print-usage [{:keys [summary]}]
  (println usage)
  (println summary))

(defn- handle-signals
  "Connect OS signals with system"
  [system]
  (signal/with-handler :term (core/stop system))
  (signal/with-handler :int (core/stop system))
  (signal/with-handler :alrm (core/trigger-sync system)))

(defn -main
  "Handles args parsing and does the appropriate action."
  [& args]
  (let [opts      (parse-opts args cli-options)
        options   (:options opts)
        errs      (:errors opts)
        flag-errs (invalid-flags options)]
    (cond
      errs            (do (print-list errs) (System/exit 1))
      flag-errs       (do (print-list flag-errs) (System/exit 1))
      (:help options) (print-usage opts)
      (:init options) (try (config/generate options)
                           (catch Exception e (do (println (.getMessage e))
                                                  (System/exit 1))))
      :else           (let [system (core/start
                                    (assoc options :sys-exit #(System/exit %)))]
                        (handle-signals system)
                        (core/wait system)))))
