(ns oksql.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [oksql.parser :as parser]
            [clojure.walk :as walk])
  (:refer-clojure :exclude [update]))

(defn kebab [k]
  (if (keyword? k)
    (let [s (string/replace (str k) #"^:" "")
          s (string/replace s #"_" "-")]
      (keyword s))
    k))

(defn kebab-case [m]
  (walk/postwalk kebab m))

(defn snake [k]
  (if (keyword? k)
    (let [s (string/replace (str k) #"^:" "")
          s (string/replace s #"-" "_")]
      (keyword s))
    k))

(defn snake-case [m]
  (walk/postwalk snake m))

(defn sql-vec [sql m]
  (when (and (string? sql)
             (map? m))
    (let [m (snake-case m)
          sql-ks (mapv #(-> % second keyword) (re-seq #":(\w+)" sql))
          sql-ks (mapv snake sql-ks)
          params (map #(get m %) sql-ks)
          diff (clojure.set/difference (set sql-ks) (set (keys (select-keys m sql-ks))))
          f-sql (string/replace sql #":\w+" "?")
          s-vec (vec (concat [f-sql] params))]
      (if (empty? diff)
        s-vec
        (throw (Exception. (str "Parameter mismatch. Expected " (string/join ", " (map kebab sql-ks)) ". Got " (string/join ", " (map kebab (keys m))))))))))

(defn get-lines [s]
  (slurp (io/resource (str "sql/" s))))

(defn db-query
  ([db sql-vec]
   (when (not (nil? sql-vec))
     (jdbc/with-db-connection [conn db]
       (->> (jdbc/query conn sql-vec)
            (map kebab-case))))))

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
        m (snake-case m)
        cols (->> (keys m)
                  (map name))
        col-str (string/join ", " cols)
        vars (map #(str %) (keys m))
        vars-str (string/join ", " vars)
        sql (str "insert into " schema "." table " (" col-str ") values (" vars-str ") returning *")
        sql-params (sql-vec sql m)]
    (first (db-query db sql-params))))

(defn update [db k m where where-map]
  (let [table (name k)
        schema (or (namespace k) "public")
        m (snake-case m)
        where-map (snake-case where-map)
        {:keys [sql f]} (part where)
        cols (->> (keys m)
                  (map #(str (name %) " = " (str %))))
        col-str (string/join ", " cols)
        sql (str "update " schema "." table " set " col-str " " sql)
        sql-params (sql-vec sql (merge m where-map))]
    (first (db-query db sql-params))))

(defn delete [db k where where-map]
  (let [table (name k)
        schema (or (namespace k) "public")
        where-map (snake-case where-map)
        {:keys [sql f]} (part where)
        sql (str "delete from " schema "." table " " sql)
        sql-params (sql-vec sql where-map)]
    (first (db-query db sql-params))))
