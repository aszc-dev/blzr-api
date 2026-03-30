(ns db
  "JSON file-based storage for patches and modules.
   Stores all data in a single EDN file. Simple, reliable, no native deps.
   Suitable for low-traffic usage — upgrade to SQLite when needed."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def ^:private default-db-path "blzr-data.edn")

(defn db-path []
  (or (System/getenv "DB_PATH") default-db-path))

(defn- ensure-parent-dir! [path]
  (let [parent (.getParentFile (io/file path))]
    (when (and parent (not (.exists parent)))
      (.mkdirs parent))))

(defn- load-db [path]
  (if (.exists (io/file path))
    (edn/read-string (slurp path))
    {:patches {} :modules {}}))

(defn- save-db! [path data]
  (ensure-parent-dir! path)
  (spit path (pr-str data)))

(def ^:private db-lock (Object.))

(defn- with-db
  "Execute f with current db state, save result. Thread-safe via lock."
  [path f]
  (locking db-lock
    (let [data (load-db path)
          [result new-data] (f data)]
      (save-db! path new-data)
      result)))

(defn migrate!
  "No-op for file-based storage. Ensures data file exists."
  ([] (migrate! (db-path)))
  ([path]
   (when-not (.exists (io/file path))
     (save-db! path {:patches {} :modules {}}))
   (println (str "Data file ready: " path))))

;; -- Patches --

(defn insert-patch!
  [path {:keys [id structure-hash params-hash full-hash
                descriptor name description source node-count edge-count]}]
  (with-db path
    (fn [data]
      (let [patch {:id id
                   :structure-hash structure-hash
                   :params-hash params-hash
                   :full-hash full-hash
                   :descriptor descriptor
                   :name name
                   :description description
                   :source (clojure.core/name (or source :user))
                   :node-count (or node-count 0)
                   :edge-count (or edge-count 0)
                   :created-at (.toString (java.time.Instant/now))
                   :updated-at (.toString (java.time.Instant/now))}]
        [patch (assoc-in data [:patches id] patch)]))))

(defn get-patch [path id]
  (get-in (load-db path) [:patches id]))

(defn get-patch-meta [path id]
  (when-let [p (get-patch path id)]
    (dissoc p :descriptor)))

(defn find-by-full-hash [path full-hash]
  (some (fn [[_ p]]
          (when (= (:full-hash p) full-hash) p))
        (:patches (load-db path))))

(defn find-by-structure [path structure-hash]
  (->> (:patches (load-db path))
       vals
       (filter #(= (:structure-hash %) structure-hash))
       (sort-by :created-at #(compare %2 %1))
       (take 50)
       (mapv #(dissoc % :descriptor))))

;; -- Modules --

(defn insert-module!
  [path {:keys [id patch-id name category library-desc source display-order]}]
  (with-db path
    (fn [data]
      (let [module {:id id
                    :patch-id patch-id
                    :name name
                    :category (clojure.core/name (or category :user))
                    :library-desc library-desc
                    :source (clojure.core/name (or source :user))
                    :display-order (or display-order 0)
                    :created-at (.toString (java.time.Instant/now))}]
        [module (assoc-in data [:modules id] module)]))))

(defn list-modules
  ([path] (list-modules path nil))
  ([path source]
   (let [data (load-db path)
         modules (vals (:modules data))
         filtered (if source
                    (filter #(= (:source %) (clojure.core/name source)) modules)
                    modules)]
     (->> filtered
          (sort-by (juxt :display-order :name))
          (mapv (fn [m]
                  (if-let [patch (get-in data [:patches (:patch-id m)])]
                    (assoc m :descriptor (:descriptor patch))
                    m)))))))
