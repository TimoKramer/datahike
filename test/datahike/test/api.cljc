(ns datahike.test.api
  (:require
   #?(:cljs [cljs.test :as t :refer-macros [is are deftest testing]]
      :clj  [clojure.test :as t :refer [is are deftest testing use-fixtures]])
   [datahike.test.core]
   [datahike.api :as d]))

(deftest test-transact-query-docs
  (let [cfg {:store {:backend :mem
                     :id "hashing"}
             :keep-history? false
             :schema-flexibility :read}
        _ (d/delete-database cfg)
        _ (d/create-database cfg)
        conn (d/connect cfg)]
    ;;;;;;;;;;;;;;;;;;;;;;;;;
    ;; TESTING TRANSACT API
    ;; add a single datom to an existing entity (1)
    (is (d/transact conn [[:db/add 1 :name "Ivan"]]))

    ;; retract a single datom
    (is (d/transact conn [[:db/retract 1 :name "Ivan"]]))

    ;; retract single entity attribute
    (is (d/transact conn [[:db.fn/retractAttribute 1 :name]]))

    ;; retract all entity attributes (effectively deletes entity)
    (is (d/transact conn [[:db.fn/retractEntity 1]]))

    ;; create a new entity (`-1`, as any other negative value, is a tempid
    ;; that will be replaced with DataScript to a next unused eid)
    (is (d/transact conn [[:db/add -1 :name "Ivan"]]))

    ;; check assigned id (here `*1` is a result returned from previous `transact` call)
    (def report *1)
    (:tempids report) ; => {-1 296}

    ;; check actual datoms inserted
    (:tx-data report) ; => [#datahike/Datom [296 :name "Ivan"]]

    ;; tempid can also be a string
    (is (d/transact conn [[:db/add "ivan" :name "Ivan"]]))
    (:tempids *1) ; => {"ivan" 297}

    ;; reference another entity (must exist)
    (is (d/transact conn [[:db/add -1 :friend 296]]))

    ;; create an entity and set multiple attributes (in a single transaction
    ;; equal tempids will be replaced with the same unused yet entid)
    (is (d/transact conn [[:db/add -1 :name "Ivan"]
                          [:db/add -1 :likes "fries"]
                          [:db/add -1 :likes "pizza"]
                          [:db/add -1 :friend 296]]))

    ;; create an entity and set multiple attributes (alternative map form)
    (is (d/transact conn [{:db/id  -1
                           :name   "Ivan"
                           :likes  ["fries" "pizza"]
                           :friend 296}]))

    ;; update an entity (alternative map form). Can’t retract attributes in
    ;; map form. For cardinality many attrs, value (fish in this example)
    ;; will be added to the list of existing values
    (is (d/transact conn [{:db/id  296
                           :name   "Oleg"
                           :likes  ["fish"]}]))

    ;; ref attributes can be specified as nested map, that will create netsed entity as well
    (is (d/transact conn [{:db/id  -1
                           :name   "Oleg"
                           :friend {:db/id -2
                                    :name "Sergey"}}]))

    ;; schema is needed for using a reverse attribute
    (is (d/transact conn [{:db/valueType :db.type/ref
                           :db/cardinality :db.cardinality/one
                           :db/ident :friend}]))

    ;; reverse attribute name can be used if you want created entity to become
    ;; a value in another entity reference
    (is (d/transact conn [{:db/id  -1
                           :name   "Oleg"
                           :_friend 296}]))

    ;; equivalent to
    (is (d/transact conn [{:db/id  -1, :name   "Oleg"}
                          {:db/id 296, :friend -1}]))))

(deftest test-pull-docs
  (let [cfg {:store {:backend :mem
                     :id "hashing"}
             :keep-history? false
             :schema-flexibility :read}
        _ (d/delete-database cfg)
        _ (d/create-database cfg)
        conn (d/connect cfg)]
    ;;;;;;;;;;;;;;;;;;;;;;;;;
    ;; TESTING PULL API
    (is (d/transact conn [{:db/ident :likes
                           :db/cardinality :db.cardinality/many}
                          {:db/ident :friends
                           :db/cardinality :db.cardinality/many}]))
    (is (d/transact conn [{:db/id 1
                           :name "Ivan"
                           :likes :pizza
                           :friends 2}
                          {:db/id 2
                           :name "Oleg"}]))

    #_(is (= (d/datoms @conn :eavt [])
             []))

    (is (= {:db/id   1,
            :name    "Ivan"
            :likes   [:pizza]
            :friends [{:db/id 2, :name "Oleg"}]}
           (d/pull @conn '{:selector [:db/id :name :likes {:friends [:db/id :name]}] :eid 1})))

    (is (= {:db/id   1,
            :name    "Ivan"
            :likes   [:pizza]
            :friends [{:db/id 2, :name "Oleg"}]}
           (d/pull @conn '[:db/id :name :likes {:friends [:db/id :name]}] 1)))

    ;;;;;;;;;;;;;;;;;;;;;;;;;
    ;; TESTING PULL-MANY API
    (is (= (d/pull-many @conn [:db/id :name] [1 2])
           [{:db/id 1, :name "Ivan"}
            {:db/id 2, :name "Oleg"}]))

    ;;;;;;;;;;;;;;;;;;;;;;;;;
    ;; TESTING Q API
    (is (= #{["fries"] ["candy"] ["pie"] ["pizza"]}
           (d/q '[:find ?value :where [_ :likes ?value]]
                #{[1 :likes "fries"]
                  [2 :likes "candy"]
                  [3 :likes "pie"]
                  [4 :likes "pizza"]})))

    (is (= #{["fries"] ["candy"] ["pie"] ["pizza"]}
           (d/q {:query '[:find ?value :where [_ :likes ?value]]
                 :args [#{[1 :likes "fries"]
                          [2 :likes "candy"]
                          [3 :likes "pie"]
                          [4 :likes "pizza"]}]})))

    (is (= #{["pizza"]}
           (d/q {:query '[:find ?value :where [_ :likes ?value]]
                 :offset 2
                 :limit 1
                 :args [#{[1 :likes "fries"]
                          [2 :likes "candy"]
                          [3 :likes "pie"]
                          [4 :likes "pizza"]}]})))

    (is (= #{["candy"] ["pizza"]}
           (d/q {:query '[:find ?value :where [_ :likes ?value]]
                 :offset 2
                 :timeout 50
                 :args [#{[1 :likes "fries"]
                          [2 :likes "candy"]
                          [3 :likes "pie"]
                          [4 :likes "pizza"]}]})))

    ;; Datomic supports the query as string, map and list
    ;; Datahike should support string
    ;; Datahike supports map of vectors
    ;; Datahike does not support list
    (is (= #{["fries"] ["candy"] ["pie"] ["pizza"]}
           (d/q '{:find [?value] :where [[_ :likes ?value]]}
                #{[1 :likes "fries"]
                  [2 :likes "candy"]
                  [3 :likes "pie"]
                  [4 :likes "pizza"]})))

    ;; TODO delete because using a list is not possible
    #_(is (= #{["fries"] ["candy"] ["pie"] ["pizza"]}
             (d/q {:query '([:find ?value :where [_ :likes ?value]])
                   :args [#{[1 :likes "fries"]
                            [2 :likes "candy"]
                            [3 :likes "pie"]
                            [4 :likes "pizza"]}]})))

    (is (= #{["fries"] ["candy"] ["pie"] ["pizza"]}
           (d/q {:query "[:find ?value :where [_ :likes ?value]]"
                 :args [#{[1 :likes "fries"]
                          [2 :likes "candy"]
                          [3 :likes "pie"]
                          [4 :likes "pizza"]}]})))

    (is (= [{:db/id 2,
             :name "Oleg",
             :db/cardinality :db.cardinality/many,
             :db/ident :friends}
            {:db/id 1,
             :friends [2],
             :likes [:pizza],
             :name "Ivan",
             :db/cardinality :db.cardinality/many,
             :db/ident :likes}]
           (d/q '[:find [(pull ?e [*]) ...]
                  :where [?e ?a ?v]]
                @conn)))))

#_(deftest test-datoms-docs
    (let [cfg {:store {:backend :mem
                       :id "datoms"
                       :initial-tx [{:db/ident :name
                                     :db/type :db.type/string
                                     :db/cardinality :db.cardinality/one}
                                    {:db/ident :likes
                                     :db/type :db.type/string
                                     :db/cardinality :db.cardinality/many}
                                    {:db/ident :friends
                                     :db/type :db.type/ref
                                     :db/cardinality :db.cardinality/many}]}
               :keep-history? false
               :schema-flexibility :read}
          _ (d/delete-database cfg)
          _ (d/create-database cfg)
          db (d/connect cfg)
          _ (d/transact db [{:db/id 1 :name "Ivan"}
                            {:db/id 1 :likes ["fries" "pizza"]}
                            {:db/id 1 :friends 2}])
          dvec #(vector (:e %) (:a %) (:v %))]

    ;; find all datoms for entity id == 1 (any attrs and values)
    ;; sort by attribute, then value
      (is (= "fail"
             (map dvec (d/datoms @db :eavt))))
    ;; => (#datahike/Datom [1 :friends 2]
    ;;     #datahike/Datom [1 :likes \"fries\"]
    ;;     #datahike/Datom [1 :likes \"pizza\"]
    ;;     #datahike/Datom [1 :name \"Ivan\"])

    ;; find all datoms for entity id == 1 and attribute == :likes (any values)
    ;; sorted by value
      (is (= "fail"
             (map dvec (d/datoms @db :eavt 1 :likes))))
    ;; => (#datahike/Datom [1 :likes \"fries\"]
    ;;     #datahike/Datom [1 :likes \"pizza\"])

    ;; find all datoms for entity id == 1, attribute == :likes and value == \"pizza\"
      (is (= "fail"
             (map dvec (d/datoms @db :eavt 1 :likes "pizza"))))
    ;; => (#datahike/Datom [1 :likes \"pizza\"])

    ;; find all datoms for attribute == :likes (any entity ids and values)
    ;; sorted by entity id, then value
      (is (= "fail"
             (map dvec (d/datoms @db :aevt :likes))))
    ;; => (#datahike/Datom [1 :likes \"fries\"]
    ;;     #datahike/Datom [1 :likes \"pizza\"]
    ;;     #datahike/Datom [2 :likes \"candy\"]
    ;;     #datahike/Datom [2 :likes \"pie\"]
    ;;     #datahike/Datom [2 :likes \"pizza\"])

    ;; find all datoms that have attribute == `:likes` and value == `\"pizza\"` (any entity id)
    ;; `:likes` must be a unique attr, reference or marked as `:db/index true`
      (is (= "fail"
             (map dvec (d/datoms @db :avet :likes "pizza"))))
    ;; => (#datahike/Datom [1 :likes \"pizza\"]
    ;;     #datahike/Datom [2 :likes \"pizza\"])

    ;; find all datoms sorted by entity id, then attribute, then value
      (is (= "fail"
             (map dvec (d/datoms @db :eavt)))) ; => (...)))

    ;; get all values of :db.cardinality/many attribute
      (is (= "fail"
             (->> (d/datoms @db :eavt 1 :likes) (map :v))))

    ;; lookup entity ids by attribute value
      (is (= "fail"
             (->> (d/datoms @db :avet :likes "pizza") (map :e))))

    ;; find all entities with a specific attribute
      (is (= "fail"
             (->> (d/datoms @db :aevt :name) (map :e))))

    ;; find “singleton” entity by its attr
      (is (= "fail"
             (->> (d/datoms @db :aevt :name) first :e)))

    ;; find N entities with lowest attr value (e.g. 10 earliest posts)
      (is (= "fail"
             (->> (d/datoms @db :avet :name) (take 2))))

    ;; find N entities with highest attr value (e.g. 10 latest posts)
      (is (= "fail"
             (->> (d/datoms @db :avet :name) (reverse) (take 2))))))

#_(deftest test-seek-datoms-doc
    (let [cfg {:store {:backend :mem
                       :id "seek-datoms"}
               :initial-tx [{:db/ident :name
                             :db/type :db.type/string
                             :db/cardinality :db.cardinality/one}
                            {:db/ident :likes
                             :db/type :db.type/string
                             :db/cardinality :db.cardinality/many}
                            {:db/ident :friends
                             :db/type :db.type/ref
                             :db/cardinality :db.cardinality/many}]
               :keep-history? false
               :schema-flexibility :read}
          _ (d/delete-database cfg)
          _ (d/create-database cfg)
          db (d/connect cfg)
          _ (d/transact db [{:db/id 1 :name "Ivan"}
                            {:db/id 1 :likes ["fries" "pizza"]}
                            {:db/id 1 :friends 2}])
          _ (d/transact db [{:db/id 2 :likes ["candy" "pizza" "pie"]}
                            {:db/id 2 :friends 2}])
          dvec #(vector (:e %) (:a %) (:v %))]

      (is (= '([1 :friends 2]
               [1 :likes "fries"]
               [1 :likes "pizza"]
               [1 :name "Ivan"]
               [2 :likes "candy"]
               [2 :likes "pie"]
               [2 :likes "pizza"])
             (map dvec (d/seek-datoms @db :eavt 1))))

      (is (= '([1 :name "Ivan"]
               [2 :likes "candy"]
               [2 :likes "pie"]
               [2 :likes "pizza"])
             (map dvec (d/seek-datoms @db :eavt 1 :name))))

      (is (= '([2 :likes "candy"]
               [2 :likes "pie"]
               [2 :likes "pizza"])
             (map dvec (d/seek-datoms @db :eavt 2))))

      (is (= '([2 :likes "pie"]
               [2 :likes "pizza"])
             (map dvec (d/seek-datoms @db :eavt 2 :likes "fish"))))))

(deftest test-with-docs
  (let [cfg {:store {:backend :mem
                     :id "with"}
             :keep-history? false
             :schema-flexibility :read}
        _ (d/delete-database cfg)
        _ (d/create-database cfg)
        conn (d/connect cfg)
        dvec #(vector (:e %) (:a %) (:v %))]
    ;; add a single datom to an existing entity (1)
    (let [res (d/with @conn {:tx-data [[:db/add 1 :name "Ivan"]]})]
      (is (= nil
             (:tx-meta res)))
      (is (= '([1 :name "Ivan"])
             (map dvec (:tx-data res)))))
    (let [res (d/with @conn {:tx-data [[:db/add 1 :name "Ivan"]]
                             :tx-meta {:foo :bar}})]
      (is (= {:foo :bar}
             (:tx-meta res)))
      (is (= '([1 :name "Ivan"])
             (map dvec (:tx-data res)))))))

;; TODO testing properly on what?
#_(deftest test-db-docs
  (let [cfg {:store {:backend :mem
                     :id "db"}
             :keep-history? false
             :schema-flexibility :read}
        _ (d/delete-database cfg)
        _ (d/create-database cfg)
        conn (d/connect cfg)]
    (is (= {:max-tx 536870912 :max-eid 0}
           (into {} (d/db conn))))))

#_(deftest test-history-docs
  (let [cfg {:store {:backend :mem
                     :id "history"}
             :keep-history? true
             :schema-flexibility :read}
        _ (d/delete-database cfg)
        _ (d/create-database cfg)
        conn (d/connect cfg)]
    (is (= {:max-tx 536870912 :max-eid 0}
           (d/history @conn)))))

#_(deftest test-as-of-docs
    (let [cfg {:store {:backend :mem
                       :id "as-of"}
               :keep-history? false
               :schema-flexibility :read}
          _ (d/delete-database cfg)
          _ (d/create-database cfg)
          conn (d/connect cfg)]
      (is (= "foo"
             (into {} (d/as-of @conn))))))

#_(deftest test-since-docs
    (let [cfg {:store {:backend :mem
                       :id "since"}
               :keep-history? true
               :schema-flexibility :read}
          _ (d/delete-database cfg)
          _ (d/create-database cfg)
          conn (d/connect cfg)]
      (is (= "foo"
             (into {} (d/since @conn (java.util.Date.)))))))

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
