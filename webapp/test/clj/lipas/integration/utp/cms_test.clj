(ns lipas.integration.utp.cms-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.integration.utp.cms :as cms]))

(deftest sanitize-filename-test
  (testing "folds Finnish/Scandinavian letters to ASCII and lowercases the extension"
    (is (= "aoa.png" (cms/sanitize-filename "äöå.png")))
    (is (= "AOA.png" (cms/sanitize-filename "ÄÖÅ.PNG")))
    (is (= "Kuva-a.jpg" (cms/sanitize-filename "Kuva ä.JPG"))))

  (testing "folds other diacritics"
    (is (= "resume.png" (cms/sanitize-filename "résumé.png"))))

  (testing "replaces spaces and path separators with a single hyphen"
    (is (= "my-picture.png" (cms/sanitize-filename "my picture.png")))
    (is (= "a-b-c.png" (cms/sanitize-filename "a/b\\c.png")))
    (is (= "a-b.png" (cms/sanitize-filename "a@@@b.png"))))

  (testing "leaves already-safe names unchanged (apart from extension case)"
    (is (= "photo_123.jpeg" (cms/sanitize-filename "photo_123.jpeg")))
    (is (= "noext" (cms/sanitize-filename "noext"))))

  (testing "trims leading/trailing dots and hyphens from the basename"
    (is (= "hidden.png" (cms/sanitize-filename "..hidden.png"))))

  (testing "preserves internal dots in the basename"
    (is (= "photo.tar.gz" (cms/sanitize-filename "photo.tar.gz"))))

  (testing "falls back to \"image\" when nothing usable remains"
    (is (= "image.png" (cms/sanitize-filename "   .png")))
    (is (= "image.png" (cms/sanitize-filename "@@@.png"))))

  (testing "handles nil/blank input"
    (is (= "image" (cms/sanitize-filename nil)))
    (is (= "image" (cms/sanitize-filename "")))))
