(ns oksql.core-test
  (:require [clojure.test :refer :all]
            [oksql.core :refer :all]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc])
  (:refer-clojure :exclude [update])
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
          (sql-vec "select * from items where id = :id" {:item-id 123}))))

  (testing "ignore more keys just use keys from sql statement"
    (is (= ["select * from items where id = ?" 321] (sql-vec "select * from items where id = :id" {:item-id 123 :id 321}))))

  (testing "in clause"
    (is (= ["select * from items where id in (?,?,?)" 1 2 3]
           (sql-vec "select * from items where id in (:ids)" {:ids [1 2 3]})))))

(defn exec [db sql]
  (jdbc/with-db-connection [conn db]
    (with-open [s (.createStatement (jdbc/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(let [conn {:connection-uri "jdbc:postgresql://localhost:5432/postgres"}
      _ (exec conn "drop database if exists oksql_test")
      _ (exec conn "create database oksql_test")
      db {:connection-uri "jdbc:postgresql://localhost:5432/oksql_test"}
      _ (exec db "drop table if exists items")
      _ (exec db "create table items (id serial primary key, name text, status text, checked boolean, created_at timestamp)")
      _ (exec db "drop table if exists foo_bar")
      _ (exec db "create table foo_bar (name text)")
      created-at (Timestamp. (.getTime (new Date)))
      expected {:id 1
                :name "name"
                :created-at created-at
                :status "pending"
                :checked true}]

  (deftest query-test
    (testing "all"
      (is (= '() (query db :items/all))))

    (testing "fetch"
      (is (= '() (query db :items/fetch {:id 123}))))

    (testing "missing name"
      (is (= '() (query db :items/missing {:id 123}))))

    (testing "insert returning"
      (is (= expected (query db :items/insert expected))))

    (testing "select recently inserted"
      (is (= expected (query db :items/fetch {:id 1})))))

  (deftest write-test
    (testing "delete"
      (is (= expected (delete db :items :items/where {:id 1}))))

    (testing "insert"
      (let [expected (assoc expected :name "hello")]
        (is (= expected (insert db :items expected)))))

    (testing "update"
      (let [expected (assoc expected :name "world")]
        (is (= expected (update db :items {:name "world"
                                           :status "pending"
                                           :checked true} :items/where {:id 1}))))))

  (deftest snake-case-table-test
    (testing "insert"
      (let [expected {:name "insert me"}]
        (is (= expected (insert db :foo-bar expected)))))

    (testing "delete"
      (let [_ (insert db :foo-bar {:name "delete me"})
            expected {:name "delete me"}]
        (is (= expected (delete db :foo-bar :foo-bar/where expected)))))

    (testing "update"
      (let [_ (insert db :foo-bar {:name "update me"})
            expected {:name "updated me"}]
        (is (= expected (update db :foo-bar {:name "updated me"} :foo-bar/where {:name "update me"})))))))
