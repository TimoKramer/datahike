;; # Time Variance
(ns datahike.notebooks.time-variance
  (:require [datahike.api :as d]))

;; For the purpose of auditing and analytics modern business information systems
;; need to be time variant. This means, they should have the ability to store,
;; track and query data entities that change over time. As a [temporal database](https://en.wikipedia.org/wiki/Temporal_database),
;; Datahike tracks by default the transaction time for each entity by using the
;; `:db/txInstant` attribute in the meta entity that is added to each
;; transaction. This uni-temporal approach allows different perspectives of the
;; data present in the index. Entities can be searched either at the [current point in time](#db),
;; [at a specific point in time](#as-of), [over the whole database existence](#history),
;; or [since a specific point in time](#since).

;; If the database does not require to be time variant you can choose to ignore the
;; temporal data and set the `keep-history?` parameter to `false` at database
;; creation like so:

(def cfg {:store {:backend :mem :id "time-invariant"} :keep-history? false})

;; Have a look at the `examples/time-travel` namespace in the examples project for more example queries and
;; interactions.

;; ## DB

;; The most common perspective of your data is the current state of the
;; system. Use `db` for this view. The following example shows a simple interaction:


;; define simple schema
(def schema1 [{:db/ident :name
               :db/valueType :db.type/string
               :db/unique :db.unique/identity
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :age
               :db/valueType :db.type/long
               :db/cardinality :db.cardinality/one}])

(def cfg1 {:store {:backend :mem :id "current-db"} :initial-tx schema1})

(d/delete-database cfg1)
;; create our temporal database
(d/create-database cfg1)

(def conn1 (d/connect cfg1))

;; add first data
(d/transact conn1 {:tx-data [{:name "Alice" :age 25}]})

;; define simple query for name and age
(def query '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]])

(d/q query @conn1)

;; update the entity
(d/transact conn1 {:tx-data [{:db/id [:name "Alice"] :age 30}]})

;; `db` reflects the latest state of the database
(d/q query @conn1)

;; ## As-Of

;; You can query the database at a specific point in time using `as-of`:


;; define simple schema
(def schema2 [{:db/ident :name
               :db/valueType :db.type/string
               :db/unique :db.unique/identity
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :age
               :db/valueType :db.type/long
               :db/cardinality :db.cardinality/one}])

;; create our temporal database
(def cfg2 {:store {:backend :mem :id "as-of-db"} :initial-tx schema2})

(d/delete-database cfg2)
(d/create-database cfg2)

(def conn2 (d/connect cfg2))


;; add first data
(d/transact conn2 {:tx-data [{:name "Alice" :age 25}]})

(def first-date (java.util.Date.))

;; define simple query for name and age
(def query '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]])

(d/q query  @conn2)

;; update the entity
(d/transact conn2 {:tx-data [{:db/id [:name "Alice"] :age 30}]})

;; let's compare the current and the as-of value:
(d/q query  @conn2)

(d/q query (d/as-of @conn2 first-date))

;; ## History

;; For querying all data over the whole time span you may use `history` which joins
;; current and all historical data:

;; define simple schema
(def schema3 [{:db/ident :name
               :db/valueType :db.type/string
               :db/unique :db.unique/identity
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :age
               :db/valueType :db.type/long
               :db/cardinality :db.cardinality/one}])

;; create our temporal database
(def cfg3 {:store {:backend :mem :id "history-db"} :initial-tx schema3})

(d/delete-database cfg3)
(d/create-database cfg3)

(def conn3 (d/connect cfg3))

;; add first data
(d/transact conn3 {:tx-data [{:name "Alice" :age 25}]})

;; define simple query for name and age
(def query '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]])

;; history should have only one entry
(d/q query (d/history @conn3))

;; update the entity
(d/transact conn3 {:tx-data [{:db/id [:name "Alice"] :age 30}]})

;; both entries are present
(d/q query (d/history @conn3))

;; ## Since

;; Changes since a specific point in time can be searched by using the `since`
;; database:

;; define simple schema
(def schema4 [{:db/ident :name
               :db/valueType :db.type/string
               :db/unique :db.unique/identity
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :age
               :db/valueType :db.type/long
               :db/cardinality :db.cardinality/one}])

;; create our temporal database
(def cfg4 {:store {:backend :mem :id "since-db"} :initial-tx schema4})

(d/delete-database cfg4)
(d/create-database cfg4)

(def conn4 (d/connect cfg4))


;; add first data
(d/transact conn4 {:tx-data [{:name "Alice" :age 25}]})

(def first-date (java.util.Date.))

;; define simple query for name and age
(def query '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]])

(d/q query @conn4)

;; update the entity
(d/transact conn4 {:tx-data [{:db/id [:name "Alice"] :age 30}]})

;; let's compare the current and the as-of value:
(d/q query @conn4)

;; now we want to know any additions after a specific time
(d/q query (d/since @conn4 first-date))
;;empty, because :name was transacted before the first date

;; let's build a query where we use the latest db to find the name and the since db to find out who's age changed
(d/q '[:find ?n ?a
       :in $ $since
       :where
       [$ ?e :name ?n]
       [$since ?e :age ?a]]
     @conn4
     (d/since @conn4 first-date))

;; ## Meta Entity

;; With each transaction a meta entity is added to the index that stores the
;; current point in time in the `:db/txInstant` attribute.

;; With this data present in the current index, you can search and analyze them for
;; your purposes.

;; define simple schema
(def schema5 [{:db/ident :name
               :db/valueType :db.type/string
               :db/unique :db.unique/identity
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :age
               :db/valueType :db.type/long
               :db/cardinality :db.cardinality/one}])

;; create our temporal database
(def cfg5 {:store {:backend :mem :id "meta-db"} :initial-tx schema5})

(d/delete-database cfg5)
(d/create-database cfg5)

(def conn5 (d/connect cfg5))

;; add first data
(d/transact conn5 {:tx-data [{:name "Alice" :age 25}]})

;; let's find all transaction dates, should be two: one for the schema and one
;; for the first data
(d/q '[:find ?t :where [_ :db/txInstant ?t]] @conn5)

;; you might join over the tx id to get the date of any transaction
(d/q '[:find ?n ?t :where [_ :name ?n ?tx] [?tx :db/txInstant ?t]] @conn5)

;; ## Data Purging

;; Since retraction only moves the datoms from the current index to a history, data
;; is in that way never completely deleted. If your use case (for instance related
;; to GDPR compliance) requires complete data removal use the `db.purge` functions
;; available in transactions:

;; - `:db/purge`: removes a datom with given entity identifier, attribute and value
;; - `:db.purge/attribute`: removes attribute datoms given an identifier and attribute name
;; - `:db.purge/entity`: removes all datoms related to an entity given an entity identifier
;; - `:db.history.purge/before`: removes all datoms from historical data before given date, useful for cleanup after some retention period

;; define simple schema
(def schema6 [{:db/ident :name
               :db/valueType :db.type/string
               :db/unique :db.unique/identity
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :age
               :db/valueType :db.type/long
               :db/cardinality :db.cardinality/one}])

;; create our temporal database
(def cfg6 {:store {:backend :mem
                   :id "purge-db"}
           :initial-tx schema6})

(when (d/database-exists? cfg6)
  (d/delete-database cfg6))

(when-not (d/database-exists? cfg6)
  (d/create-database cfg6))

(def conn6 (d/connect cfg6))

;; add data
(d/transact conn6 {:tx-data [{:name "Alice" :age 25}]})

;; define simple query for name and age
(def query '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]])

(d/q query  @conn6)

(d/transact conn6 {:tx-data [[:db.purge/entity [:name "Alice"]]]})

;; data was removed from current database view
(d/q query  @conn6)

;; data was also removed from history
(d/q query (d/history @conn6))

;; Have a look at the the `time-travel` namespace in the examples project for
;; more examples.

;; Be aware: these functions are only available if temporal index is active. Don't

;; use these functions to remove data by default.
