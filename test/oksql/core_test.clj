(ns oksql.core-test
  (:require [clojure.test :refer :all]
            [oksql.core :refer :all]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc])
  (:import (java.util UUID)))

(deftest get-name-test
  (testing "nil"
    (is (nil? (get-name nil))))

  (testing "empty vector"
    (is (nil? (get-name []))))

  (testing "valid vector 1"
    (is (= "hello" (get-name ["-- name:hello"]))))

  (testing "valid vector"
    (is (= "hello" (get-name ["-- name: hello"])))))

(deftest get-sql-test
  (testing "nil"
    (is (nil? (get-sql nil))))

  (testing "empty vector"
    (is (nil? (get-sql []))))

  (testing "valid vector"
    (is (= "select * from items" (get-sql ["-- name: hello" "select * from items"])))))

(deftest get-fn-test
  (testing "nil"
    (is (= identity (get-fn nil))))

  (testing "empty vector"
    (is (= identity (get-fn []))))

  (testing "valid vector"
    (is (= (resolve (symbol "first")) (get-fn ["-- name: hello" "-- f: first" "select * from items"])))))

(deftest process-line-test
  (testing "nil"
    (is (nil? (process-line nil))))

  (testing "empty vector"
    (is (nil? (process-line []))))

  (testing "valid vector"
    (is (= {:fetch {:sql "select * from items where id = :id"
                    :f (resolve (symbol "first"))}}
           (process-line ["-- name: fetch"
                          "-- f: first"
                          "-- This is a comment"
                          "select * from items where id = :id"])))))

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

(deftest process-lines-test
  (testing "nil"
    (is (nil? (process-lines nil))))

  (testing "empty string"
    (is (nil? (process-lines ""))))

  (testing "valid string"
    (is (= {:fetch {:sql "select * from items where id = :id"
                    :f (resolve (symbol "first"))}}
           (process-lines "-- name: fetch\n-- f: first\n-- This is a comment\nselect\n*\nfrom\nitems\nwhere id = :id")))))

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
      (is (= nil (query db :items/fetch {:id (UUID/randomUUID)}))))))
