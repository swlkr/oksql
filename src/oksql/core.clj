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

(defn part
  ([k m]
   (let [lines (get-lines (str (namespace k) ".sql"))
         queries (parser/parse lines)]
     (get queries (name k))))
  ([k]
   (part k {})))

(defn query
  ([db k m]
   (if (and (some? k)
            (some? (namespace k))
            (some? (name k)))
     (let [{:keys [sql f]} (part k m)
           q (sql-vec sql m)
           f (or f identity)]
       (or (f (db-query db q))
           '()))
     '()))
  ([db k]
   (query db k {})))

(defn insert [db k m]
  (let [table (name k)
        schema (or (namespace k) "public")
        cols (->> (keys m)
                  (map name))
        col-str (string/join ", " cols)
        vars (map #(str %) (keys m))
        vars-str (string/join ", " vars)
        sql (str "insert into " schema "." table " (" col-str ") values (" vars-str ") returning *")
        sql-params (sql-vec sql m)]
    (first (jdbc/query db sql-params))))

(defn update [db k m where where-map]
  (let [table (name k)
        schema (or (namespace k) "public")
        {:keys [sql f]} (part where)
        cols (->> (keys m)
                  (map #(str (name %) " = " (str %))))
        col-str (string/join ", " cols)
        sql (str "update " schema "." table " set " col-str " " sql)
        sql-params (sql-vec sql (merge m where-map))]
    (first (jdbc/query db sql-params))))

(defn delete [db k where where-map]
  (let [table (name k)
        schema (or (namespace k) "public")
        {:keys [sql f]} (part where)
        sql (str "delete from " schema "." table " " sql)
        sql-params (sql-vec sql where-map)]
    (first (jdbc/query db sql-params))))
