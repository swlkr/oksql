(ns oksql.parser)

(def name-regex #"^--\s*name\s*:\s*(.+)$")
(def fn-regex #"^--\s*fn\s*:\s*(.+)$")

(defn name-line? [s]
  (not (nil? (re-matches name-regex s))))

(defn fn-line? [s]
  (not (nil? (re-matches fn-regex s))))

(defn sql-line? [s]
  (nil? (re-matches #"^--.*$" s)))

(defn parse-name [s]
  (when s
    (let [[_ name] (re-matches name-regex s)]
      name)))

(defn parse-fn [s]
  (let [[_ f] (re-matches fn-regex (or s ""))]
    (if (nil? f)
      (resolve (symbol "identity"))
      (resolve (symbol f)))))

(defn parse-query [query-string]
  (let [query-lines (clojure.string/split query-string #"\n")
        name (-> (filter name-line? query-lines)
                 (first)
                 (parse-name))
        f (->> (filter fn-line? query-lines)
               first
               parse-fn)
        sql (clojure.string/join " " (filter sql-line? query-lines))]
    (if (nil? name)
      nil
      {name {:sql sql
             :f f}})))

(defn parse [lines]
  (let [query-lines (clojure.string/split lines #"\n\n")]
    (into {} (filter (comp not nil?) (map parse-query query-lines)))))
