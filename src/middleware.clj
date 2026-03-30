(ns middleware
  "Ring middleware for CORS and request processing.")

(def ^:private allowed-origins
  #{"https://blzr.app" "http://localhost:3000"})

(defn- origin-allowed? [origin]
  (contains? allowed-origins origin))

(defn- cors-headers [origin]
  {"Access-Control-Allow-Origin"  origin
   "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type, Accept"
   "Access-Control-Max-Age"       "86400"})

(defn wrap-cors
  "CORS middleware. Handles preflight OPTIONS and adds headers to responses."
  [handler]
  (fn [request]
    (let [origin (get-in request [:headers "origin"])]
      (if (and origin (origin-allowed? origin))
        (if (= :options (:request-method request))
          ;; Preflight
          {:status 204
           :headers (cors-headers origin)
           :body ""}
          ;; Normal request — add CORS headers to response
          (let [response (handler request)]
            (update response :headers merge (cors-headers origin))))
        ;; No origin or not allowed — pass through without CORS headers
        (handler request)))))
