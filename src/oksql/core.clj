(ns oksql.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [oksql.parser :as parser])
  (:refer-clojure :exclude [update]))

(defn sql-vec [sql m]
  (when (and (string? sql)
             (map? m))
    (let [sql-ks (mapv #(-> % second keyword) (re-seq #":(\w+)" sql))
          params (map #(get m %) sql-ks)
          diff (clojure.set/difference (set sql-ks) (set (keys (select-keys m sql-ks))))
          f-sql (string/replace sql #":\w+" "?")
          s-vec (vec (concat [f-sql] params))]
      (if (empty? diff)
        s-vec
        (throw (Exception. (str "Parameter mismatch. Expected " (string/join ", " sql-ks) ". Got " (string/join ", " (keys m)))))))))

(defn get-lines [s]
  (slurp (io/resource (str "sql/" s))))

(defn db-query [db v]
  (when v
    (jdbc/query db v)))

(defn query
  ([db k m]
   (if (and (some? k)
            (some? (namespace k))
            (some? (name k)))
     (let [lines (get-lines (str (namespace k) ".sql"))
           queries (parser/parse lines)
           query (get queries (name k))
           {:keys [sql f]} query
           q (sql-vec sql m)
           f (or f identity)]
       (or (f (db-query db q))
           '()))
     '()))
  ([db k]
   (query db k {})))
