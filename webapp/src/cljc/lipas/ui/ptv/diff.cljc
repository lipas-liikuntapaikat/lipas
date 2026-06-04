(ns lipas.ui.ptv.diff
  "Word-level text diff for the PTV drift panel. Produces a sequence of
   ops the UI can render with inline highlighting:

     [:equal   \"unchanged token\"]   ;; in both texts
     [:removed \"only in a\"]         ;; in `a` but not in `b`
     [:added   \"only in b\"]         ;; in `b` but not in `a`

   Tokens are alternating runs of whitespace and non-whitespace, so the
   output preserves the source spacing verbatim when reassembled.

   The algorithm is the classic Wagner–Fischer LCS dynamic-programming
   table followed by a backwards walk to recover the diff path. The
   table is stored in a flat int array (a JS typed array in CLJS, a
   Java int[] on the JVM) for tight inner loops.

   Worst-case work is O(m · n) in tokens. For the LIPAS drift use case
   the inputs are PTV summaries (max ~150 chars) and descriptions
   (max ~2500 chars), so cells stay well under 200k even at the high
   end. See the bench comment block at the bottom for measured numbers."
  (:require [clojure.string :as str]))

(defn tokenize
  "Split `s` into alternating non-whitespace and whitespace runs.

   Returns a vector. Empty/nil input yields an empty vector."
  [s]
  (if (str/blank? (or s ""))
    []
    (vec (re-seq #"\s+|\S+" s))))

#?(:clj
   (defn- new-int-array [n]
     (int-array n))
   :cljs
   (defn- new-int-array [n]
     (js/Int32Array. n)))

(defn- lcs-table
  "Build the LCS DP table for token vectors `a` and `b`. Returned as a
   flat int array of size (inc m) * (inc n) plus the dimensions, since
   the caller needs them for the back-walk."
  [a b]
  (let [m (count a)
        n (count b)
        stride (inc n)
        t (new-int-array (* (inc m) stride))]
    (dotimes [i m]
      (let [ai (nth a i)
            row-prev (* i stride)
            row-curr (* (inc i) stride)]
        (dotimes [j n]
          (let [bj (nth b j)
                v (if (= ai bj)
                    (inc (aget t (+ row-prev j)))
                    (max (aget t (+ row-prev (inc j)))
                         (aget t (+ row-curr j))))]
            (aset t (+ row-curr (inc j)) (int v))))))
    {:t t :stride stride :m m :n n}))

(defn diff
  "Compute a word-level diff between strings `a` and `b`.

   Returns a vector of [op token] pairs, where op is one of :equal,
   :added, :removed. The concatenation of `:equal`/`:removed` tokens
   reconstructs `a`; the concatenation of `:equal`/`:added` tokens
   reconstructs `b`."
  [a b]
  (let [a-tokens (tokenize a)
        b-tokens (tokenize b)]
    (cond
      (and (empty? a-tokens) (empty? b-tokens)) []
      (empty? a-tokens) (mapv (fn [t] [:added t]) b-tokens)
      (empty? b-tokens) (mapv (fn [t] [:removed t]) a-tokens)
      :else
      (let [{:keys [t stride m n]} (lcs-table a-tokens b-tokens)]
        ;; Walk from (m, n) back to (0, 0) prepending ops to a list.
        ;; List head-cons is O(1); convert to vec at the end.
        (loop [i m
               j n
               acc ()]
          (cond
            (and (pos? i) (pos? j) (= (nth a-tokens (dec i)) (nth b-tokens (dec j))))
            (recur (dec i) (dec j) (cons [:equal (nth a-tokens (dec i))] acc))

            (and (pos? j)
                 (or (zero? i)
                     (>= (aget t (+ (* i stride) (dec j)))
                         (aget t (+ (* (dec i) stride) j)))))
            (recur i (dec j) (cons [:added (nth b-tokens (dec j))] acc))

            (pos? i)
            (recur (dec i) j (cons [:removed (nth a-tokens (dec i))] acc))

            :else (vec acc)))))))

(defn coalesce
  "Merge adjacent ops of the same kind into a single op with concatenated
   value. Reduces the number of nodes the UI has to render for runs of
   consecutive equal/added/removed tokens."
  [ops]
  (reduce (fn [acc [op v :as item]]
            (if-let [[lop lv] (peek acc)]
              (if (= op lop)
                (conj (pop acc) [op (str lv v)])
                (conj acc item))
              [item]))
          []
          ops))

(comment
  ;; Quick correctness checks (run any of these in a REPL).
  (= [[:equal "hello"]] (diff "hello" "hello"))
  (= [[:equal "hello"] [:equal " "] [:added "world"]]
     (diff "hello " "hello world"))
  (coalesce (diff "the quick fox" "the lazy fox"))
  ;; => [[:equal "the "] [:removed "quick"] [:added "lazy"] [:equal " fox"]]

  ;; --- Browser REPL benchmarks ---------------------------------------
  ;;
  ;; (user/browser-repl)            ; switch the nREPL session to CLJS
  ;; (require '[lipas.ui.ptv.diff :as d] :reload)
  ;;
  ;; (defn bench [label f n]
  ;;   (let [t0 (.now js/performance)]
  ;;     (dotimes [_ n] (f))
  ;;     (let [total (- (.now js/performance) t0)]
  ;;       (println (format "%s: %.3f ms total / %d runs = %.3f ms/run"
  ;;                        label total n (/ total n))))))
  ;;
  ;; (let [short-a "Halli on monitoimitila."
  ;;       short-b "Halli on monitoimi-tila."
  ;;       summary-a (apply str (repeat 6 "Linnukka-halli on Limingassa sijaitseva liikuntahalli, jossa voi harrastaa useita eri sisäpalloilulajeja. "))
  ;;       summary-b (str/replace summary-a "useita" "monia")
  ;;       desc-a (apply str (repeat 30 "Linnukka-halli on monipuolinen sisäliikuntapaikka, joka on myös koulujen käytössä. Hallissa on synteettinen Taraflex-pinnoite, ja tilat on jaettavissa useampaan osaan. "))
  ;;       desc-b (-> desc-a
  ;;                  (str/replace "synteettinen" "uusi")
  ;;                  (str/replace "monipuolinen" "ensiluokkainen"))]
  ;;   (bench "short (3 words)"           #(d/diff short-a short-b)   10000)
  ;;   (bench "summary (~50 tokens)"      #(d/diff summary-a summary-b) 1000)
  ;;   (bench "long description (~360 tokens)" #(d/diff desc-a desc-b)    100))
  )
