(ns server
  "blzr-api: Content-addressable patch storage for blzr.app.
   Babashka HTTP service with SQLite persistence."
  (:require [org.httpkit.server :as http]
            [router :as r]
            [middleware :as mw]
            [handlers]
            [db]))

(defn- wrap-db-path
  "Wrap a handler to inject db-path as first argument."
  [handler-fn db-path]
  (fn [request]
    (handler-fn db-path request)))

(defn app [db-path]
  (-> (r/router
        [[:get  "/health"               (wrap-db-path handlers/health db-path)]
         [:post "/api/patches"          (wrap-db-path handlers/create-patch db-path)]
         [:get  "/api/patches/:id/meta" (wrap-db-path handlers/get-patch-meta db-path)]
         [:get  "/api/patches/:id"      (wrap-db-path handlers/get-patch db-path)]
         [:get  "/api/structures/:hash" (wrap-db-path handlers/find-by-structure db-path)]
         [:get  "/api/modules"          (wrap-db-path handlers/list-modules db-path)]
         [:post "/api/modules"          (wrap-db-path handlers/save-module db-path)]])
      mw/wrap-cors))

(defn -main [& _args]
  (let [port    (parse-long (or (System/getenv "PORT") "3001"))
        db-path (db/db-path)]
    (db/migrate! db-path)
    (println (str "blzr-api running on :" port " (db: " db-path ")"))
    (http/run-server (app db-path) {:port port})
    @(promise)))
