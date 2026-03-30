(ns handlers
  "HTTP request handlers for the patch API."
  (:require [db]
            [id]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

;; -- Transit helpers --

(defn- transit-read [s]
  (let [in (ByteArrayInputStream. (.getBytes s "UTF-8"))]
    (t/read (t/reader in :json))))

(defn- transit-write [data]
  (let [out (ByteArrayOutputStream.)]
    (t/write (t/writer out :json) data)
    (.toString out "UTF-8")))

(defn- json-response [status body]
  {:status  status
   :headers {"Content-Type" "application/json"}
   :body    body})

(defn- transit-response [status body]
  {:status  status
   :headers {"Content-Type" "application/transit+json"}
   :body    body})

;; -- Hash computation (mirrors ClojureScript audio/hash.cljs) --

(defn- djb2 [s]
  (reduce (fn [h c] (long (bit-and (+ (bit-shift-left h 5) h (long c)) 0xFFFFFFFF)))
          5381
          s))

(defn- canonical-topology-str
  "Build a canonical topology string from a descriptor's structure.
   Mirrors the ClojureScript implementation for hash compatibility."
  [structure]
  (let [nodes (:nodes structure)
        edges (:edges structure)
        sorted-node-ids (sort (keys nodes))
        id->idx (into {} (map-indexed (fn [i id] [id i]) sorted-node-ids))
        node-strs (map (fn [nid]
                         (let [node (get nodes nid)]
                           (str (id->idx nid) ":" (name (:type node)))))
                       sorted-node-ids)
        edge-strs (map (fn [e]
                         (str (id->idx (:source e)) ":" (:sourceHandle e)
                              "->" (id->idx (:target e)) ":" (:targetHandle e)))
                       (sort-by (juxt :source :sourceHandle :target :targetHandle) edges))]
    (str "N[" (clojure.string/join "," node-strs) "]"
         "E[" (clojure.string/join "," edge-strs) "]")))

(defn- structure-hash [structure]
  (str (djb2 (canonical-topology-str structure))))

(defn- params-hash [params]
  (let [sorted (->> params
                    (sort-by key)
                    (mapv (fn [[k ps]] [k (into (sorted-map) ps)])))]
    (str (djb2 (pr-str sorted)))))

(defn- full-hash [structure params]
  (str (djb2 (str (structure-hash structure) ":" (params-hash params)))))

;; -- Handlers --
;; Each handler takes [db-path request] and returns a ring response map.

(defn create-patch
  "POST /api/patches — store a patch descriptor, return short ID."
  [db-path request]
  (let [body   (slurp (:body request))
        desc   (transit-read body)
        struct (get desc :structure)
        params (get desc :params)
        s-hash (structure-hash struct)
        p-hash (params-hash params)
        f-hash (full-hash struct params)]
    ;; Deduplication: if identical patch exists, return existing ID
    (if-let [existing (db/find-by-full-hash db-path f-hash)]
      (json-response 200
        (transit-write {:id  (:id existing)
                        :url (str "https://blzr.app/p/" (:id existing))
                        :deduplicated true}))
      (let [short-id (id/generate)
            n-count  (count (:nodes struct))
            e-count  (count (:edges struct))
            metadata (get desc :metadata)]
        (db/insert-patch! db-path
          {:id             short-id
           :structure-hash s-hash
           :params-hash    p-hash
           :full-hash      f-hash
           :descriptor     body
           :name           (:name metadata)
           :description    (:description metadata)
           :source         (or (:source metadata) :user)
           :node-count     n-count
           :edge-count     e-count})
        (json-response 201
          (transit-write {:id  short-id
                          :url (str "https://blzr.app/p/" short-id)}))))))

(defn get-patch
  "GET /api/patches/:id — return full Transit descriptor."
  [db-path request]
  (let [patch-id (get-in request [:path-params :id])]
    (if-let [row (db/get-patch db-path patch-id)]
      (transit-response 200 (:descriptor row))
      (json-response 404 "{\"error\":\"not found\"}"))))

(defn get-patch-meta
  "GET /api/patches/:id/meta — return metadata only (for link previews)."
  [db-path request]
  (let [patch-id (get-in request [:path-params :id])]
    (if-let [row (db/get-patch-meta db-path patch-id)]
      (json-response 200
        (transit-write {:id         (:id row)
                        :name       (:name row)
                        :source     (:source row)
                        :node-count (:node-count row)
                        :edge-count (:edge-count row)
                        :created-at (:created-at row)}))
      (json-response 404 "{\"error\":\"not found\"}"))))

(defn find-by-structure
  "GET /api/structures/:hash — all patches sharing the same topology."
  [db-path request]
  (let [hash    (get-in request [:path-params :hash])
        results (db/find-by-structure db-path hash)]
    (json-response 200 (transit-write (vec results)))))

(defn save-module
  "POST /api/modules — save a module linked to a patch."
  [db-path request]
  (let [body (slurp (:body request))
        data (transit-read body)
        desc     (:descriptor data)
        struct   (get desc :structure)
        params   (get desc :params)
        s-hash   (structure-hash struct)
        p-hash   (params-hash params)
        f-hash   (full-hash struct params)
        patch-id (or (:id (db/find-by-full-hash db-path f-hash))
                     (let [pid (id/generate)]
                       (db/insert-patch! db-path
                         {:id             pid
                          :structure-hash s-hash
                          :params-hash    p-hash
                          :full-hash      f-hash
                          :descriptor     (transit-write desc)
                          :source         :user
                          :node-count     (count (:nodes struct))
                          :edge-count     (count (:edges struct))})
                       pid))
        mod-id (str (java.util.UUID/randomUUID))]
    (db/insert-module! db-path
      {:id           mod-id
       :patch-id     patch-id
       :name         (:name data)
       :category     (:category data)
       :library-desc (:library-desc data)
       :source       :user})
    (json-response 201
      (transit-write {:id mod-id :patch-id patch-id}))))

(defn list-modules
  "GET /api/modules — list modules, optionally filtered by ?source=..."
  [db-path request]
  (let [source (get (:query-params request) "source")
        results (if source
                  (db/list-modules db-path (keyword source))
                  (db/list-modules db-path))]
    (json-response 200 (transit-write (vec results)))))

(defn health
  "GET /health — healthcheck."
  [_db-path _request]
  (json-response 200 "{\"status\":\"ok\"}"))
