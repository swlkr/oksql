(ns oksql.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:refer-clojure :exclude [update]))

(defn valid-vec? [v]
  (and (vector? v)
       (not (empty? v))))

(defn get-name [v]
  (when (valid-vec? v)
    (let [name (first (filter #(string/starts-with? % "-- name:") v))]
      (string/trim (second (string/split name #":"))))))

(defn get-sql [v]
  (when (valid-vec? v)
    (let [sql-lines (filter #(not (string/starts-with? % "--")) v)]
      (string/join " " sql-lines))))

(defn get-fn [v]
  (when (valid-vec? v))
  (let [f (first (filter #(string/starts-with? % "-- f:") v))]
    (if (nil? f)
      identity
      (resolve (symbol (string/trim (second (string/split f #":"))))))))

(defn process-line [v]
  (when (valid-vec? v)
    (let [name (get-name v)
          sql (get-sql v)
          f (get-fn v)]
      {(keyword name) {:sql sql
                       :f f}})))

(defn sql-vec [sql m]
  (when (and (string? sql)
             (map? m))
    (let [sql-ks (map #(-> % second keyword) (re-seq #":(\w+)" sql))
          params (map #(get m %) sql-ks)
          diff (clojure.set/difference (set sql-ks) (set (keys m)))
          f-sql (string/replace sql #":\w+" "?")
          s-vec (vec (concat [f-sql] params))]
      (if (empty? diff)
        s-vec
        (throw (Exception. (str "Parameter mismatch. Expected " (string/join ", " sql-ks) ". Got " (string/join ", " (keys m)))))))))

(defn process-lines [s]
  (when (and (string? s)
             (not (empty? s)))
    (let [lines (string/split s #"\n\n")
          vecs (map #(string/split % #"\n") lines)]
      (into {} (map process-line vecs)))))

(defn get-lines [s]
  (slurp (io/resource (str "sql/" s))))

(defn query
  ([db k m]
   (when (and (some? k)
              (some? (namespace k))
              (some? (name k)))
     (let [lines (get-lines (str (namespace k) ".sql"))
           file-map (process-lines lines)
           sql-map (get file-map (keyword (name k)))
           {:keys [sql f]} sql-map]
       (when (not (nil? sql-map))
         (f (jdbc/query db (sql-vec sql m)))))));
  ([db k]
   (query db k {})))
