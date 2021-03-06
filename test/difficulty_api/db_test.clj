(ns difficulty-api.db-test
  (:require [difficulty-api.db :as db]
            [datomic.api :as d]
            [clj-time.coerce :refer [to-date from-date]]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [deftest is use-fixtures]]
            [clj-time.core :as t])
  (:import [java.util GregorianCalendar Date TimeZone]))

(def test-uri "datomic:mem://difficulty-api-test")

;; -2 beat -4, so -4 should be easy for -3 and -4 should not have a difficulty for -1
;; -2 lost to -5, so -5 should be impossible for -1 and -5 should not have a difficulty for -3
;; -1 beat -6 and -3 lost to -6, so -6 should be medium for -1, -2, and -3
;; -2 beat -7 and lost to -7, so -7 should be impossible for -1, medium for -2, and easy for -3

(def test-data
  [{:db/id #db/id[:db.part/user -1]
    :player/api-key "foo"
    :player/torn-id 1
    :player/battle-stats 5.0}
   {:db/id #db/id[:db.part/user -2]
    :player/torn-id 2
    :player/battle-stats 10.0}
   {:db/id #db/id[:db.part/user -3]
    :player/torn-id 3
    :player/battle-stats 20.0}
   {:db/id #db/id[:db.part/user -4]
    :player/torn-id 4
    :player/battle-stats 15.0}
   {:db/id #db/id[:db.part/user -5]
    :player/torn-id 5}
   {:db/id #db/id[:db.part/user -6]
    :player/torn-id 6}
   {:db/id #db/id[:db.part/user -7]
    :player/torn-id 7}
   {:db/id #db/id[:db.part/user -8]
    :attack/torn-id 1
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -4]
    :attack/timestamp-started (Date. (long 1))
    :attack/timestamp-ended (Date. (long 100))
    :attack/result :attack.result/hospitalize}
   {:db/id #db/id[:db.part/user -9]
    :attack/torn-id 2
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -5]
    :attack/timestamp-started (Date. (long 1))
    :attack/timestamp-ended (Date. (long 100))
    :attack/result :attack.result/lose}
   {:db/id #db/id[:db.part/user -11]
    :attack/torn-id 4
    :attack/attacker #db/id[:db.part/user -1]
    :attack/defender #db/id[:db.part/user -6]
    :attack/timestamp-started (Date. (long 1))
    :attack/timestamp-ended (Date. (long 100))
    :attack/result :attack.result/mug}
   {:db/id #db/id[:db.part/user -12]
    :attack/torn-id 5
    :attack/attacker #db/id[:db.part/user -3]
    :attack/defender #db/id[:db.part/user -6]
    :attack/timestamp-started (Date. (long 1))
    :attack/timestamp-ended (Date. (long 100))
    :attack/result :attack.result/stalemate}
   {:db/id #db/id[:db.part/user -13]
    :attack/torn-id 6
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -7]
    :attack/timestamp-started (Date. (long 1))
    :attack/timestamp-ended (Date. (long 100))
    :attack/result :attack.result/hospitalize}
   {:db/id #db/id[:db.part/user -14]
    :attack/torn-id 7
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -7]
    :attack/timestamp-started (Date. (long 1))
    :attack/timestamp-ended (Date. (long 100))
    :attack/result :attack.result/timeout}
   ])

(def test-player {:player/torn-id 8
                  :player/api-key "foo"
                  :player/battle-stats 100.0
                  :player/last-attack-update (t/date-time 2000 1 1 1 1 1 1)
                  :player/last-battle-stats-update (t/date-time 2000 1 1 1 1 1 1)})

(def schema-test-attack {:attack/torn-id 15
                         :attack/attacker 1
                         :attack/defender 2
                         :attack/timestamp-started (Date. (long 1))
                         :attack/timestamp-ended (Date. (long 100))
                         :attack/result :attack.result/hospitalize})

;; ready for entry to datomic
(def db-test-attack {:attack/torn-id 15
                     :attack/attacker {:player/torn-id 1}
                     :attack/defender {:player/torn-id 2}
                     :attack/timestamp-started (Date. (long 1))
                     :attack/timestamp-ended (Date. (long 100))
                     :attack/result [:db/ident :attack.result/hospitalize]})

;; tuple return from datomic
(def return-test-attack [{:attack/torn-id 15
                          :attack/attacker {:player/torn-id 1}
                          :attack/defender {:player/torn-id 2}
                          :attack/timestamp-started (Date. (long 1))
                          :attack/timestamp-ended (Date. (long 100))}
                         :attack.result/hospitalize])

(def duplicate-test-attack {:attack/torn-id 1
                            :attack/attacker 2
                            :attack/defender 4
                            :attack/timestamp-started (Date. (long 1))
                            :attack/timestamp-ended (Date. (long 100))
                            :attack/result :attack.result/hospitalize})

(def unknown-player-attack {:attack/torn-id 16
                            :attack/attacker 1
                            :attack/defender 9
                            :attack/timestamp-started (Date. (long 1))
                            :attack/timestamp-ended (Date. (long 100))
                            :attack/result :attack.result/stalemate})

(def schema-anon-attack {:attack/torn-id 17
                         :attack/attacker nil
                         :attack/defender 1
                         :attack/timestamp-started (Date. (long 1))
                         :attack/timestamp-ended (Date. (long 100))
                         :attack/result :attack.result/stalemate})

;; ready for entry to datomic
(def db-anon-attack {:attack/torn-id 17
                     :attack/defender {:player/torn-id 1}
                     :attack/timestamp-started (Date. (long 1))
                     :attack/timestamp-ended (Date. (long 100))
                     :attack/result [:db/ident :attack.result/stalemate]})

;; tuple return from datomic
(def return-anon-attack [{:attack/torn-id 17
                          :attack/defender {:player/torn-id 1}
                          :attack/timestamp-started (Date. (long 1))
                          :attack/timestamp-ended (Date. (long 100))}
                         :attack.result/stalemate])

(defn speculate [db t]
  (:db-after
   (d/with db t)))

(defn connect-apply-schema-create-dbs [f]
  (let [db (component/start (db/new-database test-uri))
        conn (:conn db)]
    (def test-db (speculate (d/db conn) test-data))
    (f)
    (component/stop db)))

(use-fixtures :once connect-apply-schema-create-dbs)

;; What should the difficulty api look like? The main use case for queries is
;; we're given a torn ID for the would-be attacker and a list of torn IDs for
;; would-be defenders. We need to return a difficulty rating of unknown, easy,
;; medium, or impossible. If a target has been beaten by someone with equal or
;; lower stats, they're given an easy rating. If a target has successfully
;; defended against someone with equal or better stats, they're given a
;; impossible rating. If someone has been given an easy rating and a impossible
;; rating, they combine to make a medium rating. If a target has been given no
;; ratings, their difficulty is unknown.

;; The main function for this will be
;; (difficulty attacker-id [target-ids])
;; which will return a map with keys equal to the target-ids and values either
;; :unknown, :easy, :medium, or :impossible.

(deftest difficulties-test
  (is (= {4 :easy 5 :unknown 6 :medium 7 :easy}
         (db/difficulties* test-db 3 [4 5 6 7])))
  (is (= {4 :easy 5 :impossible 6 :medium 7 :medium}
         (db/difficulties* test-db 2 [4 5 6 7])))
  (is (= {4 :unknown 5 :impossible 6 :medium 7 :impossible}
         (db/difficulties* test-db 1 [4 5 6 7])))
  (is (= {3 :unknown}
         (db/difficulties* test-db 4 [3])))
  (is (= {0 :unknown}
         (db/difficulties* test-db 1 [0])))
  (is (= {} (db/difficulties* test-db 1 [])))
  (is (thrown? RuntimeException (db/difficulties* test-db 0 []))))

(deftest player-by-torn-id-test
  (is (= {:player/torn-id 1 :player/battle-stats 5.0 :player/api-key "foo"}
         (db/player-by-torn-id* test-db 1)))
  (is (= {:player/torn-id 4 :player/battle-stats 15.0} (db/player-by-torn-id* test-db 4)))
  (is (nil? (db/player-by-torn-id* test-db 0))))

(deftest player-by-api-key-test
  (is (= {:player/torn-id 1 :player/battle-stats 5.0 :player/api-key "foo"}
         (db/player-by-api-key* test-db "foo")))
  (is (nil? (db/player-by-api-key* test-db ""))))

;; There should be two main ways to insert data into the database.
;; First, if we're adding a new player, we'll need to insert them into the database and spawn a call to download their attack log.
;; Second, we'll need to be able to add a list of attacks to the database.

(deftest add-player-test
  (is (= test-player
         (db/player-by-torn-id* (speculate test-db (db/add-player-tx test-player)) (:player/torn-id test-player))))
  (let [existing-player (assoc test-player :player/torn-id 1)]
    (is (= existing-player
           (db/player-by-torn-id* (speculate test-db (db/add-player-tx existing-player)) 1))))
  (let [new-api-player (assoc test-player :player/api-key "foo")]
    (is (= new-api-player
           (db/player-by-torn-id* (speculate test-db (db/add-player-tx new-api-player)) (:player/torn-id new-api-player))))))

(deftest attack-by-torn-id-test
  (is (= {:attack/torn-id 1
          :attack/attacker 2
          :attack/defender 4
          :attack/timestamp-started (Date. (long 1))
          :attack/timestamp-ended (Date. (long 100))
          :attack/result :attack.result/hospitalize}
         (db/attack-by-torn-id* test-db 1)))
  (is (= nil (db/attack-by-torn-id* test-db 0))))

(deftest add-attack-test
  (is (= schema-test-attack
         (db/attack-by-torn-id* (speculate test-db (db/add-attack-tx schema-test-attack))
                                (:attack/torn-id schema-test-attack))))
  (is (= unknown-player-attack
         (db/attack-by-torn-id* (speculate test-db (db/add-attack-tx unknown-player-attack))
                                (:attack/torn-id unknown-player-attack))))
  (let [get-attacks '[:find ?attack :where [?attack :attack/torn-id]]]
    (is (= (count (d/q get-attacks test-db))
           (count (d/q get-attacks (speculate test-db (db/add-attack-tx duplicate-test-attack)))))))
  (is (= duplicate-test-attack
         (db/attack-by-torn-id* (speculate test-db (db/add-attack-tx duplicate-test-attack))
                                (:attack/torn-id duplicate-test-attack)))))

(deftest update-battle-stats-test
  (let [updated-db (speculate test-db (db/update-battle-stats-tx test-db 1 6.0))
        updated-player (db/player-by-torn-id* updated-db 1)]
    (is (= 6.0 (:player/battle-stats updated-player)))
    (is (.after (:player/last-battle-stats-update (d/entity updated-db [:player/torn-id 1]))
                (Date. (- (.getTime (Date.)) 1000))))))

(deftest db-attack->schema-attack-test
  (is (= schema-test-attack
         (db/db-attack->schema-attack return-test-attack)))
  (is (= schema-anon-attack
         (db/db-attack->schema-attack return-anon-attack))))

(deftest schema-attack->db-attack-test
  (is (= db-test-attack
         (db/schema-attack->db-attack schema-test-attack)))
  (is (= db-anon-attack
         (db/schema-attack->db-attack schema-anon-attack))))

(def schema-player
  {:player/torn-id 1
   :player/api-key "key"
   :player/battle-stats 3.14
   :player/last-attack-update (t/date-time 1986 10 14 4 3 27)
   :player/last-battle-stats-update (t/date-time 1986 10 14 4 3 27)})

(def db-player
  {:player/torn-id 1
   :player/api-key "key"
   :player/battle-stats 3.14
   :player/last-attack-update (.getTime (doto (GregorianCalendar. 1986 9 14 4 3 27)
                                          (. setTimeZone (TimeZone/getTimeZone "UTC"))))
   :player/last-battle-stats-update (.getTime (doto (GregorianCalendar. 1986 9 14 4 3 27)
                                                (. setTimeZone (TimeZone/getTimeZone "UTC"))))})

(deftest db-player->schema-player-test
  (is (= db-player
         (db/schema-player->db-player schema-player)))
  (is (= schema-player
         (db/db-player->schema-player db-player))))
