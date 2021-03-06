(ns difficulty-api.torn-api-test
  (:require [difficulty-api.torn-api :as api]
            [cheshire.core :as json]
            [clojure.walk :refer [keywordize-keys]]
            [medley.core :refer [map-keys]]
            [clojure.test :refer [deftest is]]))

(defn test-client [resp]
  (reify
    api/HttpClient
    (http-get [this url] {:body (json/encode resp)})))

(deftest user-query-url-test
  (is (= "https://api.torn.com/user/?selections=basic&key=foo"
         (api/user-query-url "foo" ["basic"])))
  (is (= "https://api.torn.com/user/?selections=basic,profile&key=foo"
         (api/user-query-url "foo" ["basic" "profile"])))
  (is (= "https://api.torn.com/user/1234?selections=basic&key=foo"
         (api/user-query-url "foo" ["basic"] 1234)))
  (is (thrown? RuntimeException
               (api/user-query-url "" ["basic"])))
  (is (thrown? RuntimeException
               (api/user-query-url "foo" []))))

(def basic-info-test-data {"level" 1 "gender" "Male" "status" "Civilian" "player_id" 1234 "name" "foo"})

(deftest basic-info-test
  (is (= (keywordize-keys basic-info-test-data)
         (api/basic-info (test-client basic-info-test-data) "foo")))
  (is (thrown? RuntimeException
               (api/basic-info (test-client basic-info-test-data ""))))
  (is (thrown? RuntimeException
               (api/basic-info (test-client {:error {:code 2}})))))

(deftest valid-api-key-test
  (is (api/valid-api-key? (test-client basic-info-test-data) "foo"))
  (is (not (api/valid-api-key? (test-client {:error {:code 2}}) "bar")))
  (is (not (api/valid-api-key? (test-client {:error {:code 1}}) ""))))

(def battle-stats-test-data {:strength 1.0 :speed 2.0 :dexterity 3.0 :defense 4.0
                             :strength_modifier -2 :speed_modifier 0
                             :dexterity_modifier -4 :defense_modifier -5
                             :strength_info ["foo" "bar"]
                             :speed_info []
                             :dexterity_info ["test"]
                             :defense_info ["baz"]})

(deftest battle-stats-test
  (is (= (keywordize-keys battle-stats-test-data)
         (api/battle-stats (test-client battle-stats-test-data) "foo")))
  (is (thrown? RuntimeException
               (api/battle-stats (test-client battle-stats-test-data) "")))
  (is (thrown? RuntimeException
               (api/battle-stats (test-client {:error {:code 2}}) ""))))

(deftest attacks-test
  (let [info {"attacks" {"1" {"defender_faction" 1
                              "defender_factionname" "foo-fac"
                              "attacker_faction" 2
                              "attacker_factionname" "bar-fac"
                              "defender_name" "foo"
                              "attacker_name" "bar"
                              "defender_id" 3
                              "attacker_id" 4
                              "result" "Hospitalize"
                              "respect_gain" 1.5
                              "timestamp_started" 10
                              "timestamp_ended" 500}
                         "2" {"defender_faction" 2
                              "defender_factionname" "bar-fac"
                              "attacker_faction" ""
                              "attacker_factionname" ""
                              "defender_name" "bar"
                              "attacker_name" nil
                              "defender_id" 4
                              "attacker_id" ""
                              "result" "Run away"
                              "respect_gain" 0.0
                              "timestamp_started" 5000
                              "timestamp_ended" 5200}}}]
    (is (= (-> info
               (keywordize-keys)
               (update :attacks (fn [m] (map-keys (comp #(Integer/parseInt %) name) m)))
               (assoc-in [:attacks 2 :attacker_id] nil)
               (assoc-in [:attacks 2 :attacker_faction] nil)
               (update-in [:attacks 1 :timestamp_started] #(java.util.Date. (* 1000 %)))
               (update-in [:attacks 1 :timestamp_ended]  #(java.util.Date. (* 1000 %)))
               (update-in [:attacks 2 :timestamp_started]  #(java.util.Date. (* 1000 %)))
               (update-in [:attacks 2 :timestamp_ended]  #(java.util.Date. (* 1000 %))))
           (api/attacks (test-client info) "foo")))
    (is (thrown? RuntimeException
                 (api/attacks (test-client info) ""))))
  (is (thrown? RuntimeException
               (api/attacks (test-client {:error {:code 2}}) ""))))

(def api-attack
  {:defender_id 1
   :defender_faction 2
   :defender_name "foo"
   :attacker_id 3
   :attacker_faction 4
   :attacker_name "bar"
   :result "Hospitalize"
   :timestamp_started (java.util.Date. 3)
   :timestamp_ended (java.util.Date. 4)
   :respect_gain 1.2})

(def anon-api-attack
  (assoc api-attack
         :attacker_id nil
         :attacker_faction nil
         :attacker_name nil))

(def schema-attack
  {:attack/defender 1
   :attack/attacker 3
   :attack/result :attack.result/hospitalize
   :attack/timestamp-started (java.util.Date. 3)
   :attack/timestamp-ended (java.util.Date. 4)})

(def anon-schema-attack
  (assoc schema-attack :attack/attacker nil))

(deftest api-attacks->schema-attacks-test
  (is (= [(assoc schema-attack :attack/torn-id 1)
          (assoc schema-attack :attack/torn-id 2 :attack/result :attack.result/mug)
          (assoc schema-attack :attack/torn-id 3 :attack/result :attack.result/leave)
          (assoc schema-attack :attack/torn-id 4 :attack/result :attack.result/lose)
          (assoc schema-attack :attack/torn-id 5 :attack/result :attack.result/stalemate)
          (assoc schema-attack :attack/torn-id 6 :attack/result :attack.result/run-away)
          (assoc schema-attack :attack/torn-id 7 :attack/result :attack.result/timeout)
          (assoc anon-schema-attack :attack/torn-id 8)]
         (api/api-attacks->schema-attacks
          {:attacks
           {1 api-attack
            2 (assoc api-attack :result "Mug")
            3 (assoc api-attack :result "Leave")
            4 (assoc api-attack :result "Lose")
            5 (assoc api-attack :result "Stalemate")
            6 (assoc api-attack :result "Run away")
            7 (assoc api-attack :result "Timeout")
            8 anon-api-attack}}))))

(def bodybagger-personalstats
  {"personalstats" {"bazaarcustomers" 3244,
                    "bazaarsales" 150735,
                    "bazaarprofit" 434532327577,
                    "useractivity" 24776281,
                    "itemswon" 0,
                    "itemsbought" 796,
                    "pointsbought" 118979,
                    "itemsboughtabroad" 15159,
                    "weaponsbought" 100,
                    "trades" 7112,
                    "itemssent" 266,
                    "auctionswon" 100,
                    "auctionsells" 1,
                    "pointssold" 401394,
                    "attackswon" 34253,
                    "attackslost" 486,
                    "attacksdraw" 386,
                    "bestkillstreak" 2539,
                    "moneymugged" 72427429674,
                    "attacksstealthed" 23605,
                    "attackhits" 121221,
                    "attackmisses" 13373,
                    "attackcriticalhits" 20913,
                    "respectforfaction" 47850,
                    "defendswon" 29529,
                    "defendslost" 2251,
                    "defendsstalemated" 589,
                    "roundsfired" 160154,
                    "yourunaway" 3926,
                    "theyrunaway" 1010,
                    "highestbeaten" 100,
                    "peoplebusted" 10002,
                    "failedbusts" 2324,
                    "peoplebought" 532,
                    "peopleboughtspent" 42285700,
                    "virusescoded" 545,
                    "cityfinds" 198,
                    "traveltimes" 1279,
                    "bountiesplaced" 112,
                    "bountiesreceived" 156,
                    "bountiescollected" 2551,
                    "totalbountyreward" 3115210202,
                    "revives" 1033,
                    "revivesreceived" 522,
                    "medicalitemsused" 25727,
                    "statenhancersused" 4797,
                    "trainsreceived" 0,
                    "totalbountyspent" 43096666,
                    "drugsused" 4712,
                    "overdosed" 123,
                    "meritsbought" 50,
                    "logins" 7619,
                    "personalsplaced" 0,
                    "classifiedadsplaced" 24,
                    "mailssent" 71832,
                    "friendmailssent" 8022,
                    "factionmailssent" 13210,
                    "companymailssent" 4670,
                    "spousemailssent" 7520,
                    "largestmug" 686981142,
                    "cantaken" 1128,
                    "exttaken" 50,
                    "kettaken" 50,
                    "lsdtaken" 309,
                    "opitaken" 50,
                    "shrtaken" 50,
                    "spetaken" 50,
                    "pcptaken" 50,
                    "xantaken" 1955,
                    "victaken" 1020,
                    "chahits" 505,
                    "heahits" 2858,
                    "axehits" 14549,
                    "grehits" 518,
                    "machits" 501,
                    "pishits" 732,
                    "rifhits" 1984,
                    "shohits" 4232,
                    "smghits" 1853,
                    "piehits" 1761,
                    "slahits" 1847,
                    "argtravel" 50,
                    "mextravel" 54,
                    "dubtravel" 50,
                    "hawtravel" 52,
                    "japtravel" 51,
                    "lontravel" 101,
                    "soutravel" 209,
                    "switravel" 320,
                    "chitravel" 50,
                    "cantravel" 50,
                    "dumpfinds" 1013,
                    "dumpsearches" 1014,
                    "itemsdumped" 5001,
                    "daysbeendonator" 1606,
                    "caytravel" 50,
                    "jailed" 3838,
                    "hospital" 1056,
                    "attacksassisted" 11,
                    "bloodwithdrawn" 1594,
                    "networth" 176218458815,
                    "refills" 1791}})

(def parsed-bodybagger-personalstats
  {:personalstats
   (map-keys keyword (get bodybagger-personalstats "personalstats"))})

(def noobie-personalstats
  {"personalstats" {"logins" 1 "refills" nil}})

(def parsed-noobie-personalstats
  {:personalstats
   (assoc (zipmap (keys (get parsed-bodybagger-personalstats :personalstats))
                  (repeat 0))
          :logins 1)})

(deftest personal-stats-test
  (is (= parsed-bodybagger-personalstats
         (api/personal-stats (test-client bodybagger-personalstats) "foo" 1)))
  (is (= parsed-noobie-personalstats
         (api/personal-stats (test-client noobie-personalstats) "foo" 2))))

(def bodybagger-profile
  {"role" "Civilian"
   "faction" {"position" "Member"
              "faction_id" 2013
              "faction_name" "Subversive Alliance"}
   "age" 3924
   "karma" 1635
   "job" {"position" "Director"
          "company_id" 19303
          "company_name" "BodyBagger&#39;s Oil"}
   "awards" 410
   "friends" 1313
   "gender" "Male"
   "rank" "Invincible Killer"
   "forum_posts" 3258
   "player_id" 99177
   "name" "BodyBagger"
   "level" 100
   "property_id" 618737
   "enemies" 214
   "status" ["Okay" ""]
   "property" "Private Island"
   "signup" "2005-07-19 03:00:34"
   "last_action" "2 hours ago"
   "donator" 1
   "life" {"current" 7500
           "maximum" 7500
           "increment" 375
           "interval" 300
           "ticktime" 55
           "fulltime" 0}
   "married" {"spouse_id" 158720
              "spouse_name" "Brainwave"
              "duration" 3075}})

(def parsed-bodybagger-profile
  {:rank "Invincible Killer"
   :level 100
   :gender "Male"
   :property "Private Island"
   :signup "2005-07-19 03:00:34"
   :awards 410
   :friends 1313
   :enemies 214
   :forum_posts 3258
   :karma 1635
   :age 3924
   :role "Civilian"
   :donator 1
   :player_id 99177
   :name "BodyBagger"
   :property_id 618737
   :last_action "2 hours ago"
   :life {:current 7500
          :maximum 7500
          :increment 375
          :interval 300
          :ticktime 55
          :fulltime 0}
   :status ["Okay" ""]
   :job {:position "Director"
         :company_id 19303
         :company_name "BodyBagger&#39;s Oil"}
   :faction {:position "Member"
             :faction_id 2013
             :faction_name "Subversive Alliance"}
   :married {:spouse_id 158720
             :spouse_name "Brainwave"
             :duration 3075}})

(def noobie-profile
  {"rank" "Absolute beginner "
   "level" 1
   "gender" "Female"
   "property" "Shack"
   "signup" "2016-03-27 10:40:35"
   "awards" 0
   "friends" 1
   "enemies" 0
   "forum_posts" nil
   "karma" 0
   "age" 20
   "role" "Civilian"
   "donator" 0
   "player_id" 1986152
   "name" "Mercedes69"
   "property_id" 0
   "last_action" "21 days ago"
   "life" {"current" 100
           "maximum" 100
           "increment" 5
           "interval" 300
           "ticktime" 245
           "fulltime" 0}
   "status" ["Okay" ""]
   "job" {"position" "None"
          "company_id" 0}
   "faction" {"position" "None"
              "faction_id" 0
              "faction_name" "None"}
   "married" {"spouse_id" 0
              "spouse_name" "None"
              "duration" 0}})

(def parsed-noobie-profile
  {:rank "Absolute beginner "
   :level 1
   :gender "Female"
   :property "Shack"
   :signup "2016-03-27 10:40:35"
   :awards 0
   :friends 1
   :enemies 0
   :forum_posts 0
   :karma 0
   :age 20
   :role "Civilian"
   :donator 0
   :player_id 1986152
   :name "Mercedes69"
   :property_id 0
   :last_action "21 days ago"
   :life {:current 100
          :maximum 100
          :increment 5
          :interval 300
          :ticktime 245
          :fulltime 0}
   :status ["Okay" ""]
   :job {:position "None"
         :company_id 0}
   :faction {:position "None"
             :faction_id 0
             :faction_name "None"}
   :married {:spouse_id 0
             :spouse_name "None"
             :duration 0}})

(deftest profile-test
  (is (= parsed-bodybagger-profile
         (api/profile (test-client bodybagger-profile) "foo" 0)))
  (is (= parsed-noobie-profile
         (api/profile (test-client noobie-profile) "foo" 1))))

(deftest public-info-test
  (is (= (merge parsed-bodybagger-profile parsed-bodybagger-personalstats)
         (api/public-info (test-client (merge bodybagger-profile bodybagger-personalstats)) "foo" 0)))
  (is (= (merge parsed-noobie-profile parsed-noobie-personalstats)
         (api/public-info (test-client (merge noobie-profile noobie-personalstats)) "foo" 1))))
