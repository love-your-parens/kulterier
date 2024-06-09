(ns tasks
  (:require [com.biffweb.tasks :as tasks]
            [com.biffweb.config :as config]
            [clojure.java.shell :refer [sh]]))

(def config (config/get-env))
(def app-name (get config "APP_NAME"))
(def domain (get config "DOMAIN"))

(defn restart-prod
  []
  (println (format "Restarting `%s` at `%s`..." app-name domain))
  (sh "ssh"
      domain
      (format "sudo systemctl reset-failed %s-app.service; sudo systemctl restart %s-app" app-name app-name)))

(defn deploy
  "Wrapper over the built-in deployment task.
  Mitigates the shortcoming wherein app/service name is hardcoded to `app`.
  This normally results in the task crashing after service-restart."
  []
  (try (tasks/deploy)
       (catch Exception _))
  (restart-prod))

;; Tasks should be vars (#'hello instead of hello) so that `clj -M:dev help` can
;; print their docstrings.
(def custom-tasks
  {"deploy" #'deploy
   "restart-prod" #'restart-prod})

(def tasks (merge tasks/tasks custom-tasks))
