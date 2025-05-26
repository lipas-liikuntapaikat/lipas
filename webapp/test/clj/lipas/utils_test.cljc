(ns lipas.utils-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [lipas.utils :as utils]))

(deftest sortable-name-test
  (testing "removes quotes and parentheses, converts to lowercase"
    (is (= (utils/->sortable-name "\"Bantis\" beachvolleykenttä (2)") "bantis beachvolleykenttä 2"))
    (is (= (utils/->sortable-name "Test (Name)") "test name"))
    (is (= (utils/->sortable-name "\"Complex\" (Test) \"Case\"") "complex test case")))
  (testing "handles nil input"
    (is (= (utils/->sortable-name nil) "")))
  (testing "handles empty string"
    (is (= (utils/->sortable-name "") "")))
  (testing "handles normal strings without special characters"
    (is (= (utils/->sortable-name "Normal String") "normal string"))))

(deftest index-by-test
  (testing "index-by with single argument (idx-fn)"
    (let [data [{:id 1 :name "John"} {:id 2 :name "Jane"}]
          result (utils/index-by :id data)]
      (is (= result {1 {:id 1 :name "John"} 2 {:id 2 :name "Jane"}}))))
  (testing "index-by with two arguments (idx-fn and value-fn)"
    (let [data [{:id 1 :name "John"} {:id 2 :name "Jane"}]
          result (utils/index-by :id :name data)]
      (is (= result {1 "John" 2 "Jane"}))))
  (testing "index-by with empty collection"
    (is (= (utils/index-by :id []) {}))))

(deftest this-year-test
  (testing "this-year is current year"
    (is (number? utils/this-year))
    (is (= utils/this-year 2025))) ; Based on current date in REPL
  (testing "this-year? function"
    (is (true? (utils/this-year? 2025)))
    (is (false? (utils/this-year? 2024)))
    (is (true? (utils/this-year? "2025")))))

(deftest timestamp-test
  (testing "timestamp returns ISO format string"
    (let [ts (utils/timestamp)]
      (is (string? ts))
      (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3,6}Z" ts)))))

(deftest timestamp-conversion-test
  (testing "->ISO-timestamp converts old LIPAS format to ISO"
    (is (= (utils/->ISO-timestamp "2018-12-01 00:00:00.000")
           "2018-12-01T00:00:00.000Z"))
    (is (= (utils/->ISO-timestamp "2020-05-15 14:30:45.123")
           "2020-05-15T14:30:45.123Z")))
  (testing "->ISO-timestamp handles nil and empty strings"
    (is (nil? (utils/->ISO-timestamp nil)))
    (is (nil? (utils/->ISO-timestamp ""))))
  (testing "->old-lipas-timestamp converts ISO to old LIPAS format"
    (is (= (utils/->old-lipas-timestamp "2018-12-01T00:00:00.000Z")
           "2018-12-01 00:00:00.000"))
    (is (= (utils/->old-lipas-timestamp "2020-05-15T14:30:45.123Z")
           "2020-05-15 14:30:45.123")))
  (testing "->old-lipas-timestamp handles nil and empty strings"
    (is (nil? (utils/->old-lipas-timestamp nil)))
    (is (nil? (utils/->old-lipas-timestamp "")))))

(deftest zero-left-pad-test
  (testing "zero-left-pad formats numbers with leading zeros"
    (is (= (utils/zero-left-pad 5 3) "005"))
    (is (= (utils/zero-left-pad 42 5) "00042"))
    (is (= (utils/zero-left-pad 123 2) "123")) ; Should still work when number is longer
    (is (= (utils/zero-left-pad 0 4) "0000"))))

(deftest remove-nils-test
  (testing "removes nil values from maps"
    (is (= (utils/remove-nils {:a 1 :b nil :c 3}) {:a 1 :c 3}))
    (is (= (utils/remove-nils {:a nil :b nil}) {}))
    (is (= (utils/remove-nils {}) {})))
  (testing "preserves false and zero values"
    (is (= (utils/remove-nils {:a false :b 0 :c nil}) {:a false :b 0}))))

(deftest clean-test
  (testing "removes nil values and empty maps recursively"
    (is (= (utils/clean {:a 1 :b nil :c {:d nil :e 2} :f {}})
           {:a 1 :c {:e 2}}))
    (is (= (utils/clean {:a {:b {:c nil}} :d 1})
           {:d 1})))
  (testing "preserves non-empty nested structures"
    (is (= (utils/clean {:a {:b {:c 1 :d nil}} :e 2})
           {:a {:b {:c 1}} :e 2}))))

(deftest number-conversion-test
  (testing "->number converts valid string numbers"
    (is (= (utils/->number "42") 42))
    (is (= (utils/->number "3.14") 3.14))
    (is (= (utils/->number "-10") -10)))
  (testing "->number returns nil for invalid strings"
    (is (nil? (utils/->number "not-a-number")))
    (is (nil? (utils/->number "abc123")))
    (is (nil? (utils/->number ""))))
  (testing "->int converts numbers to integers"
    (is (= (utils/->int 42.7) 42))
    (is (= (utils/->int "42") 42))
    (is (= (utils/->int "42.7") 42)))
  (testing "->int returns nil for invalid input"
    (is (nil? (utils/->int "invalid")))
    (is (nil? (utils/->int nil)))))

(deftest uuid-test
  (testing "gen-uuid generates valid UUIDs"
    (let [uuid1 (utils/gen-uuid)
          uuid2 (utils/gen-uuid)]
      (is (uuid? uuid1))
      (is (uuid? uuid2))
      (is (not= uuid1 uuid2))))
  (testing "->uuid-safe converts valid UUID strings"
    (let [uuid (utils/gen-uuid)
          uuid-str (str uuid)]
      (is (= (utils/->uuid-safe uuid-str) uuid))
      (is (= (utils/->uuid-safe uuid) uuid))))
  (testing "->uuid-safe returns nil for invalid UUIDs"
    (is (nil? (utils/->uuid-safe "invalid-uuid")))
    (is (nil? (utils/->uuid-safe nil)))
    (is (nil? (utils/->uuid-safe 123)))))

(deftest bool-test
  (testing "->bool converts string representations"
    (is (true? (utils/->bool "true")))
    (is (false? (utils/->bool "false")))
    (is (true? (utils/->bool "TRUE")))
    (is (false? (utils/->bool "FALSE"))))
  (testing "->bool converts other types to string first, then parses"
    (is (true? (utils/->bool true))) ; true -> "true" -> true  
    (is (false? (utils/->bool false))) ; false -> "false" -> false
    (is (false? (utils/->bool 1))) ; 1 -> "1" -> false (parseBoolean only accepts "true")
    (is (false? (utils/->bool 0)))))

(deftest statistics-test
  (testing "mean calculates average"
    (is (= (utils/mean [1 2 3 4 5]) 3))
    (is (= (utils/mean [2 4 6]) 4))
    (is (= (utils/mean []) 0)))
  (testing "median finds middle value"
    (is (= (utils/median [1 2 3 4 5]) 3))
    (is (= (utils/median [1 2 3 4]) 5/2))
    (is (nil? (utils/median []))))
  (testing "mode finds most frequent values"
    (is (= (utils/mode [1 2 2 3 3 3]) '(3)))
    (is (= (set (utils/mode [1 1 2 2 3])) #{1 2}))
    (is (nil? (utils/mode []))))
  (testing "simple-stats provides comprehensive statistics"
    (let [stats (utils/simple-stats [1 2 3 4 5])]
      (is (= (:count stats) 5))
      (is (= (:sum stats) 15))
      (is (= (:mean stats) 3.0))
      (is (= (:median stats) 3))
      (is (seq (:mode stats))))))

(deftest deep-merge-test
  (testing "merges nested maps"
    (is (= (utils/deep-merge {:a 1 :b {:c 2}} {:b {:d 3} :e 4})
           {:a 1 :b {:c 2 :d 3} :e 4})))
  (testing "concatenates vectors"
    (is (= (utils/deep-merge {:a [1 2]} {:a [3 4]})
           {:a [1 2 3 4]})))
  (testing "overwrites non-collection values"
    (is (= (utils/deep-merge {:a 1} {:a 2})
           {:a 2}))))

(deftest csv-data-maps-test
  (testing "converts CSV data to maps"
    (let [csv-data [["name" "age"] ["John" "30"] ["Jane" "25"]]
          result (utils/csv-data->maps csv-data)]
      (is (= result '({"name" "John", "age" "30"} {"name" "Jane", "age" "25"}))))))

(deftest safe-addition-test
  (testing "+safe adds only valid numbers"
    (is (= (utils/+safe 1 2 3) 6))
    (is (= (utils/+safe 1 nil 3 "abc" 4) 8)))
  (testing "+safe returns nil when no valid numbers"
    (is (nil? (utils/+safe nil "abc")))
    (is (nil? (utils/+safe)))))

(deftest get-in-path-test
  (testing "navigates nested maps using dot notation"
    (let [data {:user {:profile {:name "John" :age 30}}}]
      (is (= (utils/get-in-path data "user.profile.name") "John"))
      (is (= (utils/get-in-path data "user.profile.age") 30))
      (is (nil? (utils/get-in-path data "user.profile.invalid"))))))

(deftest join-test
  (testing "joins collection with commas"
    (is (= (utils/join ["a" "b" "c"]) "a,b,c"))
    (is (= (utils/join [1 2 3]) "1,2,3"))
    (is (= (utils/join []) ""))))

(deftest case-conversion-test
  (testing "->kebab-case-keywords converts keys to kebab-case"
    (is (= (utils/->kebab-case-keywords {"firstName" "John" "lastName" "Doe"})
           {"first-name" "John", "last-name" "Doe"})))
  (testing "->camel-case-keywords converts keys to camelCase"
    (is (= (utils/->camel-case-keywords {:first-name "John" :last-name "Doe"})
           {:firstName "John", :lastName "Doe"})))
  (testing "->snake-case-keywords converts keys to snake_case"
    (is (= (utils/->snake-case-keywords {:first-name "John" :last-name "Doe"})
           {:first_name "John", :last_name "Doe"}))))

(deftest utility-functions-test
  (testing "trim function handles nil safely"
    (is (= (utils/trim "  hello  ") "hello"))
    (is (= (utils/trim nil) "")))
  (testing "sreplace function handles nil safely"
    (is (= (utils/sreplace "hello world" "world" "there") "hello there"))
    (is (= (utils/sreplace nil "a" "b") ""))))

(deftest mapv-indexed-test
  (testing "mapv-indexed works like map-indexed but returns vector"
    (let [result (utils/mapv-indexed (fn [i x] [i x]) [:a :b :c])]
      (is (vector? result))
      (is (= result [[0 :a] [1 :b] [2 :c]])))))

(deftest reverse-cmp-test
  (testing "reverse-cmp provides reverse comparison"
    (is (pos? (utils/reverse-cmp 1 2)))
    (is (neg? (utils/reverse-cmp 2 1)))
    (is (zero? (utils/reverse-cmp 1 1)))))

(deftest str-matches-test
  (testing "str-matches? performs case-insensitive substring matching"
    (is (true? (utils/str-matches? "test" "This is a TEST")))
    (is (true? (utils/str-matches? "john" "John Doe")))
    (is (false? (utils/str-matches? "xyz" "abc")))
    (is (true? (utils/str-matches? "123" 12345)))))

(deftest prefix-map-test
  (testing "->prefix-map adds prefix to map keys"
    (is (= (utils/->prefix-map {:name "John" :age 30} "user-")
           {:user-name "John" :user-age 30}))))

(deftest filter-newer-test
  (testing "filter-newer returns entries from m1 with newer timestamps"
    (let [m1 {:a {:timestamp "2023-01-01"} :b {:timestamp "2023-01-03"} :c {:timestamp "2023-01-02"}}
          m2 {:a {:timestamp "2023-01-02"} :b {:timestamp "2023-01-01"} :c {:timestamp "2023-01-03"}}
          result (utils/filter-newer m1 :timestamp m2 :timestamp)]
      (is (= result {:b {:timestamp "2023-01-03"}}))))
  (testing "filter-newer with nested timestamp access"
    (let [m1 {:a {:meta {:ts "2023-02-01"}} :b {:meta {:ts "2023-01-01"}}}
          m2 {:a {:meta {:ts "2023-01-01"}} :b {:meta {:ts "2023-02-01"}}}
          result (utils/filter-newer m1 #(get-in % [:meta :ts]) m2 #(get-in % [:meta :ts]))]
      (is (= result {:a {:meta {:ts "2023-02-01"}}})))))

(deftest spec-validation-test
  (testing "validate-noisy returns validation result with output"
    (let [valid-data {:name "John" :age 30}
          invalid-data {:name "John" :age -5}]
      ;; Note: validate-noisy prints to stdout, we just test the return value
      (is (true? (utils/validate-noisy map? valid-data)))
      (is (false? (utils/validate-noisy empty? valid-data)))))
  (testing "all-valid? checks all items in collection"
    (is (true? (utils/all-valid? map? [{:a 1} {:b 2}])))
    (is (false? (utils/all-valid? empty? [{} {:a 1}])))))

(deftest content-type-test
  (testing "content-type map contains expected MIME types"
    (is (= (:xlsx utils/content-type) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    (is (= (:csv utils/content-type) "text/csv"))
    (is (= (:json utils/content-type) "application/json"))))

(comment
  (run-tests *ns*))
