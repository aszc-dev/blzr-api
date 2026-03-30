(ns router
  "Minimal pattern-matching router for Babashka.
   Supports path params like :id via simple segment matching.")

(defn- path-segments [path]
  (filterv seq (clojure.string/split path #"/")))

(defn- match-route
  "Try to match a request path against a route pattern.
   Returns path-params map on match, nil otherwise."
  [pattern-segments path-segments]
  (when (= (count pattern-segments) (count path-segments))
    (reduce (fn [params [pat seg]]
              (if (clojure.string/starts-with? pat ":")
                (assoc params (keyword (subs pat 1)) seg)
                (if (= pat seg) params (reduced nil))))
            {}
            (map vector pattern-segments path-segments))))

(defn- parse-query-params [query-string]
  (if (seq query-string)
    (into {}
          (map (fn [pair]
                 (let [[k v] (clojure.string/split pair #"=" 2)]
                   [(java.net.URLDecoder/decode (or k "") "UTF-8")
                    (java.net.URLDecoder/decode (or v "") "UTF-8")])))
          (clojure.string/split query-string #"&"))
    {}))

(defn router
  "Create a ring handler from a route table.
   Routes: [[method pattern handler-fn] ...]
   Example: [[:get \"/api/patches/:id\" get-patch-handler]]"
  [routes]
  (let [compiled (mapv (fn [[method pattern handler]]
                         {:method   method
                          :segments (path-segments pattern)
                          :handler  handler})
                       routes)]
    (fn [request]
      (let [method       (keyword (clojure.string/lower-case (name (:request-method request))))
            req-segments (path-segments (:uri request))
            query-params (parse-query-params (:query-string request))]
        (or (some (fn [{:keys [segments handler] :as route}]
                    (when (= method (:method route))
                      (when-let [path-params (match-route segments req-segments)]
                        (handler (assoc request
                                        :path-params path-params
                                        :query-params query-params)))))
                  compiled)
            {:status 404
             :headers {"Content-Type" "application/json"}
             :body "{\"error\":\"not found\"}"})))))
