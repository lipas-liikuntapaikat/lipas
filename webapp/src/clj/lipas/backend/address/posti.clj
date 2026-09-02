(ns lipas.backend.address.posti
  "Parsers for Posti's open postal data files: PCF (Postal Code File,
  *postinumerotiedosto*) and BAF (Basic Address File, *perusosoitteisto*).

  Both are fixed-width record files encoded in ISO-8859-1, one record per line.
  The column positions documented here are 1-based, matching the tables in
  Posti's service descriptions. Because the encoding is single-byte, character
  positions equal byte positions.

  Parsing is pure: the `parse-*-line` fns take an already decoded string and
  `parse-*-lines` take a seq of such strings. `read-lines` and the
  `parse-*-file` conveniences are the only I/O here — they exist so callers
  don't have to remember the charset.

  Error handling deliberately avoids exceptions and avoids dropping data: a
  `parse-*-line` call returns either a record map or a map with an `:error`
  message (see `parse-error?`), and `parse-*-lines` returns

      {:records [record ...]
       :errors  [{:line-number 12 :error \"...\" :line \"KATUN...\"} ...]}

  so an import job can fail loudly with the offending line numbers instead of
  silently importing a truncated file."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.text Normalizer Normalizer$Form]))

;;; Fixed-width primitives ;;;

(def charset
  "Posti publishes the .dat files in ISO-8859-1 (a.k.a. latin1)."
  "ISO-8859-1")

(defn- field
  "The trimmed contents of `length` characters starting at 1-based `start`."
  [^String line start length]
  (str/trim (subs line (dec start) (+ (dec start) length))))

(defn- blank->nil
  [s]
  (when-not (str/blank? s) s))

(defn- iso-date
  "'yyyymmdd' -> 'yyyy-mm-dd', nil when `s` is not 8 digits."
  [s]
  (when (re-matches #"\d{8}" s)
    (str (subs s 0 4) "-" (subs s 4 6) "-" (subs s 6 8))))

(defn- error
  [fmt & args]
  {:error (apply format fmt args)})

(defn parse-error?
  "True when `parsed` is a parse error rather than a record. Records never
  carry an `:error` key."
  [parsed]
  (contains? parsed :error))

;;; Name normalization ;;;

(defn name-key
  "Lookup key for a street or municipality name: lowercased, diacritics folded
  (ä -> a, ö -> o, å -> a, é -> e, ...) and everything outside [a-z0-9]
  collapsed into single spaces.

  Folding makes lookups forgiving — Hämeentie and Hameentie meet at
  'hameentie', '7. Linja' at '7 linja' — while the display names are stored
  untouched alongside the key."
  [name]
  (-> name
      str/lower-case
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{M}+" "")
      (str/replace #"[^a-z0-9]+" " ")
      str/trim))

;;; PCF — Postal Code File ;;;

(def pcf-line-length 220)
(def pcf-record-id "PONOT")

(def postal-code-types
  "PCF field 'postal code type' (position 111). Unknown codes parse as
  `:unknown` rather than failing the line."
  {"1" :normal
   "2" :po-box
   "3" :corporate
   "4" :compilation
   "5" :reply-mail
   "6" :smart-post
   "7" :pickup-point
   "8" :technical})

(defn parse-pcf-line
  "Parses one PCF record: a Finnish postal code with its postitoimipaikka,
  region (NUTS) and municipality. Returns the record map, or an `:error` map
  when the line is not a valid PCF record.

  Columns (1-based, length):

      1   (5)  record id 'PONOT'
      6   (8)  run date yyyymmdd
      14  (5)  postal code
      19  (30) postitoimipaikka fi
      49  (30) postitoimipaikka sv
      79  (12) abbreviation fi
      91  (12) abbreviation sv
      103 (8)  valid from yyyymmdd
      111 (1)  postal code type (see `postal-code-types`)
      112 (5)  region code        ; labelled 'advertising area' in older specs,
      117 (30) region name fi     ; current files carry the NUTS region here
      147 (30) region name sv     ; (e.g. FI1B1 Uusimaa)
      177 (3)  municipality code
      180 (20) municipality name fi
      200 (20) municipality name sv
      220 (1)  municipality language code"
  [line]
  (let [length (count line)]
    (cond
      (not= pcf-line-length length)
      (error "expected %d chars, got %d" pcf-line-length length)

      (not (str/starts-with? line pcf-record-id))
      (error "expected record id %s, got '%s'"
             pcf-record-id (subs line 0 (min length (count pcf-record-id))))

      :else
      (let [run-date    (iso-date (field line 6 8))
            postal-code (field line 14 5)
            type-code   (field line 111 1)]
        (cond
          (nil? run-date)
          (error "invalid run date '%s'" (field line 6 8))

          (not (re-matches #"\d{5}" postal-code))
          (error "invalid postal code '%s'" postal-code)

          :else
          {:run-date                  run-date
           :postal-code               postal-code
           :name-fi                   (field line 19 30)
           :name-sv                   (blank->nil (field line 49 30))
           :abbreviation-fi           (blank->nil (field line 79 12))
           :abbreviation-sv           (blank->nil (field line 91 12))
           :valid-from                (iso-date (field line 103 8))
           :type-code                 type-code
           :type                      (get postal-code-types type-code :unknown)
           :region-code               (field line 112 5)
           :region-name-fi            (field line 117 30)
           :region-name-sv            (blank->nil (field line 147 30))
           :municipality-code         (field line 177 3)
           :municipality-name-fi      (field line 180 20)
           :municipality-name-sv      (blank->nil (field line 200 20))
           :municipality-language-code (field line 220 1)})))))

;;; BAF — Basic Address File ;;;

(def baf-line-length 256)
(def baf-record-id "KATUN")

(def street-sides
  "BAF field 'side of street' (position 187). '0' means the record carries no
  street address at all."
  {"1" :odd
   "2" :even})

(defn- building-number
  "Parses one BAF building number group into

      {:number 6 :letter \"a\" :number2 8 :letter2 \"b\"}

  Nil when the whole group is blank, an `:error` map when it is not numeric.
  The second number/letter is the tail of a dual building number such as
  '13/2' or '6a/8b'."
  [number letter number2 letter2]
  (cond
    (every? str/blank? [number letter number2 letter2])
    nil

    (not (re-matches #"\d+" number))
    (error "invalid building number '%s%s/%s%s'" number letter number2 letter2)

    (and (seq number2) (not (re-matches #"\d+" number2)))
    (error "invalid second building number '%s'" number2)

    :else
    {:number  (parse-long number)
     :letter  (blank->nil letter)
     :number2 (some-> (blank->nil number2) parse-long)
     :letter2 (blank->nil letter2)}))

(defn parse-baf-line
  "Parses one BAF record: a street x postal code x side-of-street segment.
  Returns the record map, or an `:error` map when the line is not a valid BAF
  record. Åland is not included in BAF.

  `:side` is `:odd` (odd house numbers), `:even` or nil. Side code '0' marks
  records with no street address data at all — street name and building number
  fields are blank and only the postal code <-> municipality link is meaningful
  (~10 % of rows, e.g. PO-box-only codes). `:min-bound` and `:max-bound` are
  building numbers (see `building-number`) and bound one side of the street.

  Columns (1-based, length):

      1   (5)  record id 'KATUN'
      6   (8)  run date yyyymmdd
      14  (5)  postal code
      19  (30) postitoimipaikka fi
      49  (30) postitoimipaikka sv
      79  (12) abbreviation fi
      91  (12) abbreviation sv
      103 (30) street name fi
      133 (30) street name sv
      163 (24) blank filler
      187 (1)  side of street (see `street-sides`)
      188 (5)  smallest building number
      193 (1)  its letter
      194 (1)  '/' separator
      195 (5)  smallest building number, second part
      200 (1)  its letter
      201 (5)  highest building number
      206 (1)  its letter
      207 (1)  '/' separator
      208 (5)  highest building number, second part
      213 (1)  its letter
      214 (3)  municipality code
      217 (20) municipality name fi
      237 (20) municipality name sv"
  [line]
  (let [length (count line)]
    (cond
      (not= baf-line-length length)
      (error "expected %d chars, got %d" baf-line-length length)

      (not (str/starts-with? line baf-record-id))
      (error "expected record id %s, got '%s'"
             baf-record-id (subs line 0 (min length (count baf-record-id))))

      :else
      (let [run-date    (iso-date (field line 6 8))
            postal-code (field line 14 5)
            side-code   (field line 187 1)
            min-bound   (building-number (field line 188 5) (field line 193 1)
                                         (field line 195 5) (field line 200 1))
            max-bound   (building-number (field line 201 5) (field line 206 1)
                                         (field line 208 5) (field line 213 1))]
        (cond
          (nil? run-date)
          (error "invalid run date '%s'" (field line 6 8))

          (not (re-matches #"\d{5}" postal-code))
          (error "invalid postal code '%s'" postal-code)

          (not (or (str/blank? side-code)
                   (= "0" side-code)
                   (contains? street-sides side-code)))
          (error "invalid side code '%s'" side-code)

          (parse-error? min-bound) min-bound
          (parse-error? max-bound) max-bound

          :else
          {:run-date             run-date
           :postal-code          postal-code
           :postal-code-name-fi  (field line 19 30)
           :postal-code-name-sv  (blank->nil (field line 49 30))
           :abbreviation-fi      (blank->nil (field line 79 12))
           :abbreviation-sv      (blank->nil (field line 91 12))
           :street-name-fi       (blank->nil (field line 103 30))
           :street-name-sv       (blank->nil (field line 133 30))
           :side                 (get street-sides side-code)
           :min-bound            min-bound
           :max-bound            max-bound
           :municipality-code    (field line 214 3)
           :municipality-name-fi (field line 217 20)
           :municipality-name-sv (blank->nil (field line 237 20))})))))

;;; Whole files ;;;

(defn- parse-lines
  [parse-line lines]
  (reduce (fn [acc [idx line]]
            (if (zero? (count line))     ; trailing newline, never a record
              acc
              (let [parsed (parse-line line)]
                (if (parse-error? parsed)
                  (update acc :errors conj (assoc parsed
                                                  :line-number (inc idx)
                                                  :line line))
                  (update acc :records conj parsed)))))
          {:records [] :errors []}
          (map-indexed vector lines)))

(defn parse-pcf-lines
  "Parses a seq of decoded PCF lines into `{:records [...] :errors [...]}`.
  Errors carry the 1-based `:line-number` and the offending `:line`."
  [lines]
  (parse-lines parse-pcf-line lines))

(defn parse-baf-lines
  "Parses a seq of decoded BAF lines into `{:records [...] :errors [...]}`.
  Errors carry the 1-based `:line-number` and the offending `:line`."
  [lines]
  (parse-lines parse-baf-line lines))

(defn read-lines
  "Decodes `source` — anything `io/input-stream` accepts: a byte array, an
  InputStream, File, URL or path string — as ISO-8859-1 and applies `f` to the
  lazy seq of its lines. `f` must realize what it needs before returning; the
  stream is closed on the way out."
  [source f]
  (with-open [reader (io/reader (io/input-stream source) :encoding charset)]
    (f (line-seq reader))))

(defn parse-pcf-file
  "Reads and parses a whole PCF file. See `read-lines` for accepted sources."
  [source]
  (read-lines source parse-pcf-lines))

(defn parse-baf-file
  "Reads and parses a whole BAF file. See `read-lines` for accepted sources."
  [source]
  (read-lines source parse-baf-lines))
