;; # 3 - Datahike Store
(ns datahike.notebooks.3_store
  (:require [datahike.api :as d]))

(def schema [{:db/ident :name
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one}])

(def query '[:find ?n :where [?e :name ?n]])

;; Let's cleanup, create, and connect all in one
(defn cleanup-and-create-conn [cfg]
  (d/delete-database cfg)
  (d/create-database cfg)
  (let [conn (d/connect cfg)]
    (d/transact conn schema)
    conn))

(defn transact-and-find [conn name]
  (d/transact conn [{:name name}])
  (d/q query @conn))

;; ## In-memory-store
;; First let's have a look at the memory store which uses an atom internally to store data
;; only a simple identifier is needed, we use
(def mem-cfg {:store {:backend :mem :id "mem-example"}})

;; Create it
(def mem-conn (cleanup-and-create-conn mem-cfg))

;; Add and find data
(transact-and-find mem-conn "Alice")

;; ## File-store
;; Next we try out file based store which can be used as the simplest form of persistence
;; the datoms are serialized at `/tmp/file_example`
(def file-cfg {:store {:backend :file :path "/tmp/file_example"}})

(def file-conn (cleanup-and-create-conn file-cfg))

(transact-and-find file-conn "Bob")

;; ## Multiple stores
;; Of course we can combine the data from all databases using queries with multiple inputs
(d/q '[:find ?mem ?file
       :in $mem-db $file-db
       :where
       [$mem-db ?e0 :name ?mem]
       [$file-db ?e1 :name ?file]]
     (d/db mem-conn)
     (d/db file-conn))

;; ## Cleanup
(do
  (d/delete-database mem-cfg)
  (d/delete-database file-cfg))

(comment
  (require '[nextjournal.clerk :as clerk])
  (clerk/serve! {:browse? false})
  (clerk/show! "notebooks/3_store.clj")
  (clerk/clear-cache!))
