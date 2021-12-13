(ns datahike.integration-test
  "This namespace is the minimum test a Datahike backend needs to pass for compatibility assessment."
  (:require [datahike.api :as d]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer :all :include-macros true])))

(defn integration-test-fixture [config]
  (d/delete-database config)
  (d/create-database config)
  (let [conn (d/connect config)]
    (d/transact conn {:tx-data [{:db/ident :name
                                 :db/valueType :db.type/string
                                 :db/cardinality :db.cardinality/one}
                                {:db/ident :age
                                 :db/valueType :db.type/long
                                 :db/cardinality :db.cardinality/one}]})

    ;; lets add some data and wait for the transaction
    (d/transact conn {:tx-data [{:name  "Alice", :age   20}
                                {:name  "Bob", :age   30}
                                {:name  "Charlie", :age   40}
                                {:age 15}]})
    (d/release conn)))

(defn integration-test [config]
  (let [conn (d/connect config)]
    (is (= #{[3 "Alice" 20] [4 "Bob" 30] [5 "Charlie" 40]}
           (d/q {:query '{:find [?e ?n ?a]
                          :where [[?e :name ?n]
                                  [?e :age ?a]]}
                 :args [@conn]})))

    (d/transact conn {:tx-data [{:db/id 3 :age 25}]})

    (is (= #{[5 "Charlie" 40] [4 "Bob" 30] [3 "Alice" 25]}
           (d/q {:query '{:find [?e ?n ?a]
                          :where [[?e :name ?n]
                                  [?e :age ?a]]}
                 :args [@conn]})))

    ;; query the history of the data
    (is (= #{[20] [25]}
           (d/q {:query '{:find [?a]
                          :where [[?e :name "Alice"]
                                  [?e :age ?a]]}
                 :args [(d/history @conn)]})))

    (is (= '((3 :age 25 536870915 true) (3 :name "Alice" 536870914 true))
           (map seq (d/datoms @conn {:index :eavt
                                     :components [3]}))))

    (let [source-datoms (->> [[:db/add 4 :age 35 536870913 true]
                              [:db/add 4 :name "Bob" 536870913 true]]
                             (mapv #(-> % rest vec))
                             (concat [[536870913 :db/txInstant #inst "2020-03-11T14:54:27.979-00:00" 536870913 true]]))]
      @(d/load-entities conn source-datoms)
      (is (= '((7 :age 35 536870916 true) (7 :name "Bob" 536870916 true))
             (map seq (d/datoms @conn {:index :eavt
                                       :components [7]})))))

    (is (= {:name "Bob", :age 35}
           (d/pull @conn '[:name :age] 7)))

    (is (= [{:name "Bob", :age 30} {:name "Charlie", :age 40}]
           (d/pull-many @conn '[:name :age] [4 5])))

    (is (= {:name
            #:db{:ident :name,
                 :valueType :db.type/string,
                 :cardinality :db.cardinality/one,
                 :id 1},
            :age
            #:db{:ident :age,
                 :valueType :db.type/long,
                 :cardinality :db.cardinality/one,
                 :id 2}}
           (d/schema @conn)))

    (is (= #:db{:ident #{:age :name}}
          (d/reverse-schema @conn)))

    (is (= '((2 :db/ident :age 536870913 true) (1 :db/ident :name 536870913 true))
           (map seq (d/seek-datoms @conn {:index :avet
                                          :components [:name]}))))


    (let [date (java.util.Date.)]
      (d/transact conn {:tx-data [{:name "Eve" :age 22}]})
      (is (= #{["Eve" 22]}
             (d/q {:query '[:find ?n ?a
                            :in $ $since
                            :where [$ ?e :name ?n]
                                   [$since ?e :age ?a]]
                   :args [@conn
                          (d/since @conn date)]}))))

    (let [reports (atom [])]
      (d/listen conn :test #(swap! reports conj %))
      (d/transact conn {:tx-data [[:db/add -1 :name "Dima"]
                                  [:db/add -1 :age 19]
                                  [:db/add -2 :name "Evgeny"]]
                        :tx-meta {:some-metadata 1}})
      (is (= '((9 :name "Dima" 536870919 true)
               (9 :age 19 536870919 true)
               (10 :name "Evgeny" 536870919 true))
             (map seq (rest (:tx-data (first @reports)))))))

    (is (= nil (d/release conn)))

    ;; database should exist
    (is (d/database-exists? config))

    ;; clean up the database if it is not needed any more
    (d/delete-database config)

    ;; database should not exist
    (is (not (d/database-exists? config)))))

(comment
  (def config {:store {:backend :mem}
               :schema-flexibility :write
               :keep-history? true})
  (def conn (d/connect config))
  (def reports (atom [])))
