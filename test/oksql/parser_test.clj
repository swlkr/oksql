(ns oksql.parser-test
  (:require [clojure.test :refer :all]
            [oksql.parser :refer :all]))

(deftest get-name-test
  (testing "valid vector 1"
    (is (= "hello" (parse-name "-- name:hello"))))

  (testing "valid vector"
    (is (= "hello" (parse-name "-- name: hello")))))

(deftest parse-fn-test
  (testing "nil"
    (is (= (resolve (symbol "identity")) (parse-fn nil))))

  (testing "valid vector"
    (is (= (resolve (symbol "first")) (parse-fn "-- fn: first")))))

(deftest parse-query-test
  (testing "valid string"
    (is (= {"fetch" {:sql "select * from items where id = :id"
                     :f (resolve (symbol "first"))}}
          (parse-query "-- name: fetch\n-- fn: first\n-- This is a comment\nselect\n*\nfrom\nitems\nwhere id = :id")))))

