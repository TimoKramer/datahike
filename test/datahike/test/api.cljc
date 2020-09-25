(ns datahike.test.api
  (:require
   #?(:cljs [cljs.test :as t :refer-macros [is are deftest testing]]
      :clj  [clojure.test :as t :refer [is are deftest testing use-fixtures]])
   [datahike.test.core]
   [datahike.api :refer [transact] :as d]))

(deftest test-transact-docs
  (let [cfg {:store {:backend :mem
                     :id "hashing"}
             :keep-history? false
             :schema-flexibility :read}
        _ (d/delete-database cfg)
        _ (d/create-database cfg)
        conn (d/connect cfg)]
    ;; add a single datom to an existing entity (1)
    (is (transact conn [[:db/add 1 :name "Ivan"]]))

    ;; retract a single datom
    (is (transact conn [[:db/retract 1 :name "Ivan"]]))

    ;; retract single entity attribute
    (is (transact conn [[:db.fn/retractAttribute 1 :name]]))

    ;; retract all entity attributes (effectively deletes entity)
    (is (transact conn [[:db.fn/retractEntity 1]]))

    ;; create a new entity (`-1`, as any other negative value, is a tempid
    ;; that will be replaced with DataScript to a next unused eid)
    (is (transact conn [[:db/add -1 :name "Ivan"]]))

    ;; check assigned id (here `*1` is a result returned from previous `transact` call)
    (def report *1)
    (:tempids report) ; => {-1 296}

    ;; check actual datoms inserted
    (:tx-data report) ; => [#datahike/Datom [296 :name "Ivan"]]

    ;; tempid can also be a string
    (is (transact conn [[:db/add "ivan" :name "Ivan"]]))
    (:tempids *1) ; => {"ivan" 297}

    ;; reference another entity (must exist)
    (is (transact conn [[:db/add -1 :friend 296]]))

    ;; create an entity and set multiple attributes (in a single transaction
    ;; equal tempids will be replaced with the same unused yet entid)
    (is (transact conn [[:db/add -1 :name "Ivan"]
                        [:db/add -1 :likes "fries"]
                        [:db/add -1 :likes "pizza"]
                        [:db/add -1 :friend 296]]))

    ;; create an entity and set multiple attributes (alternative map form)
    (is (transact conn [{:db/id  -1
                         :name   "Ivan"
                         :likes  ["fries" "pizza"]
                         :friend 296}]))

    ;; update an entity (alternative map form). Can’t retract attributes in
    ;; map form. For cardinality many attrs, value (fish in this example)
    ;; will be added to the list of existing values
    (is (transact conn [{:db/id  296
                         :name   "Oleg"
                         :likes  ["fish"]}]))

    ;; ref attributes can be specified as nested map, that will create netsed entity as well
    (is (transact conn [{:db/id  -1
                         :name   "Oleg"
                         :friend {:db/id -2
                                  :name "Sergey"}}]))

    ;; schema is needed for using a reverse attribute
    (is (transact conn [{:db/valueType :db.type/ref
                         :db/cardinality :db.cardinality/one
                         :db/ident :friend}]))

    ;; reverse attribute name can be used if you want created entity to become
    ;; a value in another entity reference
    (is (transact conn [{:db/id  -1
                         :name   "Oleg"
                         :_friend 296}]))

    ;; equivalent to
    (is (transact conn [{:db/id  -1, :name   "Oleg"}
                        {:db/id 296, :friend -1}]))))

  (deftest test-database-hash
    (testing "Hashing without history"
      (let [cfg {:store {:backend :mem
                         :id "hashing"}
                 :keep-history? false
                 :schema-flexibility :read}
            _ (d/delete-database cfg)
            _ (d/create-database cfg)
            conn (d/connect cfg)
            hash-0 0]
        (testing "first hash equals zero"
          (is (= hash-0 (hash @conn))))
        (testing "hash remains 0 after reconnecting"
          (is (= hash-0 (-> (d/connect cfg) deref hash))))
        (testing "add entity to database"
          (let [_ (d/transact conn [{:db/id 1 :name "Max Mustermann"}])
                hash-1 (hash @conn)]
            (is (= hash-1 (-> (d/connect cfg) deref hash)))
            (testing "remove entity again"
              (let [_ (d/transact conn [[:db/retractEntity 1]])
                    hash-2 (hash @conn)]
                (is (not= hash-2 hash-1))
                (is (= hash-0 hash-2))))))))
    (testing "Hashing with history"
      (let [cfg {:store {:backend :mem
                         :id "hashing-with-history"}
                 :keep-history? true
                 :schema-flexibility :read}
            _ (d/delete-database cfg)
            _ (d/create-database cfg)
            conn (d/connect cfg)
            hash-0 (hash @conn)]
        (testing "first hash equals zero"
          (is (= hash-0 (hash @conn))))
        (testing "hash remains 0 after reconnecting"
          (is (= hash-0 (-> (d/connect cfg) deref hash))))
        (testing "add entity to database"
          (let [_ (d/transact conn [{:db/id 1 :name "Max Mustermann"}])
                hash-1 (hash @conn)]
            (is (= hash-1 (-> (d/connect cfg) deref hash)))
            (testing "retract entity again"
              (let [_ (d/transact conn [[:db/retractEntity 1]])
                    hash-2 (hash @conn)]
                (is (not= hash-1 hash-2))
                (is (not= hash-0 hash-2)))))))))
