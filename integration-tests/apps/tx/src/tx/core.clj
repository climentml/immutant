(ns tx.core
  (:use clojure.test)
  (:require [immutant.xa :as ixa]
            [immutant.cache :as ic]
            [immutant.messaging :as imsg]
            [clojure.java.jdbc :as sql]))

;;; Create a JMS queue
(imsg/start "/queue/test")

;;; And an Infinispan cache
(def cache (ic/cache "test"))

;;; And some transactional databases
(defonce h2 (ixa/datasource "h2" {:adapter "h2" :database "mem:foo"}))
(defonce oracle (ixa/datasource "oracle" {:adapter "oracle"
                                          :host "oracle.cpct4icp7nye.us-east-1.rds.amazonaws.com"
                                          :username "myuser"
                                          :password "mypassword"
                                          :database "mydb"}))
(defonce mysql (ixa/datasource "mysql" {:adapter "mysql"
                                          :host "mysql.cpct4icp7nye.us-east-1.rds.amazonaws.com"
                                          :username "myuser"
                                          :password "mypassword"
                                          :database "mydb"}))

(def spec {:datasource h2})

;;; Helper methods to verify database activity
(defn write-thing-to-db [name]
  (sql/with-connection spec
    (sql/insert-records :things {:name name})))
(defn read-thing-from-db [name]
  (sql/with-connection spec
    (sql/with-query-results rows ["select name from things where name = ?" name]
      (first rows))))
(defn count-things-in-db []
  (sql/with-connection spec
    (sql/with-query-results rows ["select count(*) c from things"]
      (int ((first rows) :c)))))

;;; Ensure each test starts with an empty table called THINGS
(use-fixtures :each (fn [f]
                      (ic/delete-all cache)
                      (sql/with-connection spec
                        (try (sql/drop-table :things) (catch Exception _))
                        (sql/create-table :things [:name "varchar(50)"]))
                      (f)))

(deftest db+msg+cache-should-commit "Test happy-path XA transaction involving three resources"
  (ixa/transaction
   (write-thing-to-db "kiwi")
   (imsg/publish "/queue/test" "kiwi")
   (ic/put cache :a 1))
  (is (= "kiwi" (:name (read-thing-from-db "kiwi"))))
  (is (= 1 (count-things-in-db)))
  (is (= 1 (:a cache)))
  (is (= "kiwi" (imsg/receive "/queue/test"))))

(deftest db+msg+cache-should-rollback "Test that all three resources rollback when exception is tossed"
  (try
    (ixa/transaction
     (write-thing-to-db "kiwi")
     (imsg/publish "/queue/test" "kiwi")
     (ic/put cache :a 1)
     (throw (Exception. "Rollback everything")))
    (catch Exception _))
  (is (nil? (read-thing-from-db "kiwi")))
  (is (= 0 (count-things-in-db)))
  (is (nil? (:a cache)))
  (is (nil? (imsg/receive "/queue/test" :timeout 2000))))