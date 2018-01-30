(ns oksql.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [oksql.parser :as parser]
            [clojure.walk :as walk])
  (:refer-clojure :exclude [update]))

(def qualified-keyword-pattern #":([\w-\.]+/?[\w-\.]+)Z?")

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

(defn parameterize [s m]
  (string/replace s qualified-keyword-pattern (fn [[_ s]]
                                                (let [k (keyword s)
                                                      v (get m k)]
                                                  (if (coll? v)
                                                    (->> (map (fn [_] (str "?")) v)
                                                         (string/join ","))
                                                    "?")))))

(defn sql-vec [sql m]
  (when (and (string? sql)
             (map? m))
    (let [m (snake-case m)
          sql-ks (mapv #(-> % second keyword) (re-seq qualified-keyword-pattern sql))
          sql-ks (mapv snake sql-ks)
          params (map #(get m %) sql-ks)
          diff (clojure.set/difference (set sql-ks) (set (keys (select-keys m sql-ks))))
          f-sql (parameterize sql m)
          s-vec (vec (concat [f-sql] (->> (map (fn [val] (if (coll? val) (flatten val) val)) params)
                                          (flatten))))]
      (if (empty? diff)
        s-vec
        (throw (Exception. (str "Parameter mismatch. Expected " (string/join ", " (map kebab sql-ks)) ". Got " (string/join ", " (map kebab (keys m))))))))))

(defn get-lines [s]
  (let [s (string/replace s "-" "_")
        resource (io/resource (str "sql/" s))]
    (if (nil? resource)
      (throw (Exception. (str s " doesn't exist!")))
      (slurp resource))))

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

(defn schema-table [k]
  (let [k (snake k)
        schema (or (namespace k) "public")
        table (name k)]
    (str schema "." table)))

(defn query
  ([db k m]
   (if (and (keyword? k)
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
  (let [m (snake-case m)
        cols (->> (keys m)
                  (map name)
                  (string/join ", "))
        vars (->> (map str (keys m))
                  (string/join ", "))
        sql (str "insert into " (schema-table k) " (" cols ") values (" vars ") returning *")
        sql-params (sql-vec sql m)]
    (first (db-query db sql-params))))

(defn update [db k m where where-map]
  (let [m (snake-case m)
        where-map (snake-case where-map)
        {:keys [sql]} (part where)
        cols (->> (keys m)
                  (map #(str (name %) " = " (str %)))
                  (string/join ", "))
        update-sql-vec (-> (str "update " (schema-table k) " set " cols " ")
                           (sql-vec m))
        where-sql-vec (sql-vec sql where-map)
        update-sql (string/join " " [(first update-sql-vec) (first where-sql-vec)])
        where-sql [(rest update-sql-vec) (rest where-sql-vec)]
        sql (vec (flatten [update-sql where-sql]))]
    (first (db-query db sql))))

(defn delete [db k where where-map]
  (let [where-map (snake-case where-map)
        {:keys [sql]} (part where)
        sql (str "delete from " (schema-table k) " " sql)
        sql-params (sql-vec sql where-map)]
    (first (db-query db sql-params))))
