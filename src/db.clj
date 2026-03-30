(ns db
  "SQLite database layer for patch and module storage.
   Uses babashka go-sqlite3 pod."
  (:require [babashka.pods :as pods]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(pods/load-pod 'org.babashka/go-sqlite3 "0.1.0")
(require '[pod.babashka.go-sqlite3 :as sqlite])

(def ^:private default-db-path "blzr.db")

(defn db-path []
  (or (System/getenv "DB_PATH") default-db-path))

(defn migrate!
  "Run all pending migrations. Idempotent — safe to call on every startup."
  ([] (migrate! (db-path)))
  ([path]
   (let [sql-text (slurp (io/resource "migrations/001-init.sql"))
         statements (->> (str/split sql-text #";\s*\n")
                         (map str/trim)
                         (remove str/blank?))]
     (doseq [stmt statements]
       (sqlite/execute! path [(str stmt ";")])))
   (println "Migrations applied.")))

;; -- Patches --

(defn insert-patch!
  [path {:keys [id structure-hash params-hash full-hash
                descriptor name description source node-count edge-count]}]
  (sqlite/execute! path
    ["INSERT INTO patches (id, structure_hash, params_hash, full_hash, descriptor, name, description, source, node_count, edge_count)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
     id structure-hash params-hash full-hash descriptor
     name description (clojure.core/name (or source :user))
     (or node-count 0) (or edge-count 0)]))

(defn get-patch
  "Fetch a patch by short ID. Returns nil if not found or soft-deleted."
  [path id]
  (first (sqlite/query path
           ["SELECT * FROM patches WHERE id = ? AND deleted_at IS NULL" id])))

(defn get-patch-meta
  "Fetch only metadata for a patch (no descriptor blob)."
  [path id]
  (first (sqlite/query path
           ["SELECT id, structure_hash, params_hash, full_hash, name, description,
                    source, node_count, edge_count, created_at, updated_at
             FROM patches WHERE id = ? AND deleted_at IS NULL" id])))

(defn find-by-full-hash
  "Find existing patch with the same full hash (deduplication)."
  [path full-hash]
  (first (sqlite/query path
           ["SELECT id FROM patches WHERE full_hash = ? AND deleted_at IS NULL" full-hash])))

(defn find-by-structure
  "Find all patches sharing the same topology hash."
  [path structure-hash]
  (sqlite/query path
    ["SELECT id, name, source, node_count, edge_count, created_at
      FROM patches
      WHERE structure_hash = ? AND deleted_at IS NULL
      ORDER BY created_at DESC
      LIMIT 50" structure-hash]))

;; -- Modules --

(defn insert-module!
  [path {:keys [id patch-id name category library-desc source display-order]}]
  (sqlite/execute! path
    ["INSERT INTO modules (id, patch_id, name, category, library_desc, source, display_order)
      VALUES (?, ?, ?, ?, ?, ?, ?)"
     id patch-id name
     (clojure.core/name (or category :user))
     library-desc
     (clojure.core/name (or source :user))
     (or display-order 0)]))

(defn list-modules
  ([path] (list-modules path nil))
  ([path source]
   (if source
     (sqlite/query path
       ["SELECT m.*, p.descriptor
         FROM modules m JOIN patches p ON m.patch_id = p.id
         WHERE m.source = ?
         ORDER BY m.display_order, m.name" (clojure.core/name source)])
     (sqlite/query path
       ["SELECT m.*, p.descriptor
         FROM modules m JOIN patches p ON m.patch_id = p.id
         ORDER BY m.display_order, m.name"]))))
