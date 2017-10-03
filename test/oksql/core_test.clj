(ns oksql.core-test
  (:require [clojure.test :refer :all]
            [oksql.core :refer :all]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc])
  (:import (java.util UUID Date)
           (java.sql Timestamp)))

(deftest sql-vec-test
  (testing "nil"
    (is (nil? (sql-vec nil nil))))

  (testing "empty values"
    (is (= [""] (sql-vec "" {}))))

  (testing "valid values"
    (is (= ["select * from items where id = ?" 1] (sql-vec "select * from items where id = :id" {:id 1}))))

  (testing "mismatched parameters exception"
    (is (thrown-with-msg? Exception #"Parameter mismatch. Expected :id. Got :item-id"
          (sql-vec "select * from items where id = :id" {:item-id 123})))))

(defn exec [db sql]
  (jdbc/with-db-connection [conn db]
    (with-open [s (.createStatement (jdbc/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(deftest query-test
  (let [conn {:connection-uri "jdbc:postgresql://localhost:5432/template1"}
        _ (exec conn "drop database if exists oksql_test")
        _ (exec conn "create database oksql_test")
        _ (exec conn "drop table if exists items")
        _ (exec conn "create table items (id uuid, name text, created_at timestamp)")
        db {:connection-uri "jdbc:postgresql://localhost:5432/oksql_test"}]

    (testing "all"
      (is (= '() (query db :items/all))))

    (testing "fetch"
      (is (= '() (query db :items/fetch {:id (UUID/randomUUID)}))))

    (testing "missing name"
      (is (= '() (query db :items/missing {:id 123}))))

    (testing "insert returning"
      (let [expected {:id (UUID/randomUUID)
                      :name "name"
                      :created_at (Timestamp. (.getTime (new Date)))}]
        (is (= expected (query db :items/insert expected)))))))
