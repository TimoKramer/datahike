;; # Datahike Schema
(ns datahike.notebooks.schema
  (:require [datahike.api :as d]
            [nextjournal.clerk :as clerk])
  (:import [clojure.lang ExceptionInfo]))

;; The first example assumes you know your data model in advance,
;; so you we can use a schema-on-write approach in contrast to a schema-on-read
;; approach. Have a look at the documentation in `/doc/schema.md` for more
;; information on the different types of schema flexibility. After the first
;; example we will have a short schema-on-read example.

;; ## Schema-on-Write
;; Define data model
(def schema [{:db/ident :contributor/name
              :db/valueType :db.type/string
              :db/unique :db.unique/identity
              :db/index true
              :db/cardinality :db.cardinality/one
              :db/doc "a contributor's name"}
             {:db/ident :contributor/email
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/many
              :db/doc "a contributor's email"}
             {:db/ident :repository/name
              :db/valueType :db.type/string
              :db/unique :db.unique/identity
              :db/index true
              :db/cardinality :db.cardinality/one
              :db/doc "a repository's name"}
             {:db/ident :repository/contributors
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "the repository's contributors"}
             {:db/ident :repository/public
              :db/valueType :db.type/boolean
              :db/cardinality :db.cardinality/one
              :db/doc "toggle whether the repository is public"}
             {:db/ident :repository/tags
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "the repository's tags"}
             {:db/ident :language/clojure}
             {:db/ident :language/rust}])

;; Define (schema-on-write) configuration
(def sow-config {:store {:backend :mem}
                 :id "schema-intro"
                 :schema-flexibility :write})

;; Cleanup previous database
(when (d/database-exists? sow-config)
  (d/delete-database sow-config))

;; Create the in-memory database
(d/create-database sow-config)

;; Connect to it
(def sow-conn (d/connect sow-config))

;; Add the schema
(d/transact sow-conn schema)

;; Let's insert our first user Alice
(d/transact sow-conn [{:contributor/name "Alice" :contributor/email "alice@exam.ple"}])

;; Let's find Alice with a query
(def find-name-email '[:find ?e ?n ?em :where [?e :contributor/name ?n] [?e :contributor/email ?em]])

(d/q find-name-email @sow-conn)

;; Let's find her directly
;; Since contributor/name is a unique, indexed identity you can use attribute and value as a lookup ref instead of an entity-ID
(d/pull @sow-conn '[*] [:contributor/name "Alice"])

;; Add a second email
;; The email-attribute is defined with many-cardinality in our schema, so we can have several ones for a user
(d/transact sow-conn [{:db/id [:contributor/name "Alice"] :contributor/email "alice@test.test"}])

;; Let's see both emails (We need to wait half a second for the transaction to show up)
(do (Thread/sleep 500)
    (d/q find-name-email @sow-conn))

;; Try to add something completely not defined in the schema
(try (d/transact sow-conn [{:something "different"}])
     (catch ExceptionInfo e
       (ex-message e)))

;; Try to add wrong contributor values
(try (d/transact sow-conn [{:contributor/email :alice}])
     (catch ExceptionInfo e
       (ex-message e)))

;; Add another contributor by using the alternative transaction schema that expects a hash map with tx-data attribute
(d/transact sow-conn {:tx-data [{:contributor/name "Bob" :contributor/email "bob@ac.me"}]})

(d/q find-name-email @sow-conn)

(d/pull @sow-conn '[*] [:contributor/name "Bob"])

;; Change Bob's name to Bobby
(d/transact sow-conn [{:db/id [:contributor/name "Bob"] :contributor/name "Bobby"}])

;; Check it
(d/q find-name-email @sow-conn)

(d/pull @sow-conn '[*] [:contributor/name "Bobby"])

;; Bob is not related anymore as index and throws an exception when using it as a lookup ref
(try (d/pull @sow-conn '[*] [:contributor/name "Bob"])
     (catch ExceptionInfo e
       (ex-message e)))

;; Create a repository, with refs from uniques, and an ident as enum
(d/transact sow-conn [{:repository/name "top secret"
                       :repository/public false
                       :repository/contributors [[:contributor/name "Bobby"] [:contributor/name "Alice"]]
                       :repository/tags :language/clojure}])

;; Let's search with a pull-expression inside the query
(def find-repositories '[:find (pull ?e [*])
                         :where [?e :repository/name ?n]])

;; Looks good
(d/q find-repositories @sow-conn)

;; Let's go further and fetch the related contributor data as well
(def find-repositories-with-contributors '[:find (pull ?e [* {:repository/contributors [*] :repository/tags [*]}])
                                           :where [?e :repository/name ?n]])

(d/q find-repositories-with-contributors @sow-conn)

;; The schema is part of the index, so we can query the schema-entities too.
;; Let's find all attribute names and their description.
(d/q '[:find ?a ?d :where [?e :db/ident ?a] [?e :db/doc ?d]] @sow-conn)

;; Cleanup the database before leaving
(d/delete-database sow-config)

;; ## Schema On Read

;; Let's create another database that can hold any arbitrary data
(def sor-config {:store {:backend :mem
                         :id "schemaless"}
                 :schema-flexibility :read})

(when (d/database-exists? sor-config)
  (d/delete-database sor-config))

(d/create-database sor-config)

(def sor-conn (d/connect sor-config))

;; Now we can go wild and transact anything
(d/transact sor-conn [{:any "thing"}])

;; Use a simple query on this data
(d/q '[:find ?v :where [_ :any ?v]] @sor-conn)

;; Be aware: Although there is no schema, you should tell the database if some attributes can have specific cardinality or indices.
;; You may add that as schema transactions like before even later. That means you can first play around with your data and database
;; and later tighten the schema.
(d/transact sor-conn [{:db/ident :any :db/cardinality :db.cardinality/many}])

;; Let's add more data to the first any entity
(def any-eid (d/q '[:find ?e . :where [?e :any "thing"]] @sor-conn))
(d/transact sor-conn [{:db/id any-eid :any "thing else"}])

(do (Thread/sleep 500)
    (d/q '[:find ?v :where [_ :any ?v]] @sor-conn))
