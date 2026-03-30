(ns id
  "Short ID generation for patch URLs.
   Produces 8-character base62 IDs (62^8 ≈ 218 trillion combinations).")

(def ^:private base62-chars
  "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")

(defn generate
  "Generate a cryptographically random 8-character base62 ID."
  ([] (generate 8))
  ([length]
   (let [rng (java.security.SecureRandom.)]
     (apply str (repeatedly length #(nth base62-chars (.nextInt rng 62)))))))
