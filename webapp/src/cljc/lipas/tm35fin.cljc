(ns lipas.tm35fin
  "WGS84 -> ETRS-TM35FIN (EPSG:3067), the projection PTV keeps a service
   location's coordinates in, without a projection library, so that the
   browser and the JVM compute the same point. Transverse Mercator on the
   GRS80 ellipsoid (central meridian 27° E, scale 0.9996, false easting
   500 000 m), by the Krüger series of JHS 154. Within millimetres of
   proj4 over Finland, see lipas.tm35fin-test.")

(def ^:private a 6378137.0)
(def ^:private f (/ 1.0 298.257222101))
(def ^:private k0 0.9996)
(def ^:private lon0 (/ (* 27 Math/PI) 180))
(def ^:private false-easting 500000.0)

(def ^:private n (/ f (- 2 f)))
(def ^:private e (Math/sqrt (- (* 2 f) (* f f))))
(def ^:private A1
  (* (/ a (+ 1 n))
     (+ 1 (/ (* n n) 4.0) (/ (Math/pow n 4) 64.0))))

(def ^:private h
  "The series' coefficients h1'..h4'. (No ratio literals: ClojureScript
   has none.)"
  (let [n2 (* n n)
        n3 (Math/pow n 3)
        n4 (Math/pow n 4)]
    [(+ (/ n 2.0) (/ (* -2.0 n2) 3) (/ (* 5.0 n3) 16) (/ (* 41.0 n4) 180))
     (+ (/ (* 13.0 n2) 48) (/ (* -3.0 n3) 5) (/ (* 557.0 n4) 1440))
     (+ (/ (* 61.0 n3) 240) (/ (* -103.0 n4) 140))
     (/ (* 49561.0 n4) 161280)]))

(defn- asinh [x] (Math/log (+ x (Math/sqrt (+ (* x x) 1)))))

(defn- atanh [x] (* 0.5 (Math/log (/ (+ 1 x) (- 1 x)))))

(defn from-wgs84
  "[easting northing] in metres for a WGS84 [lon lat] in degrees; nil for
   nil."
  [[lon lat]]
  (when (and (number? lon) (number? lat))
    (let [phi (/ (* lat Math/PI) 180)
          lam (/ (* lon Math/PI) 180)
          q (- (asinh (Math/tan phi)) (* e (atanh (* e (Math/sin phi)))))
          beta (Math/atan (Math/sinh q))
          eta' (atanh (* (Math/cos beta) (Math/sin (- lam lon0))))
          xi' (Math/asin (* (Math/sin beta) (Math/cosh eta')))
          [xi eta] (reduce (fn [[xi eta] [j hj]]
                             (let [k (* 2 j)]
                               [(+ xi (* hj (Math/sin (* k xi')) (Math/cosh (* k eta'))))
                                (+ eta (* hj (Math/cos (* k xi')) (Math/sinh (* k eta'))))]))
                           [xi' eta']
                           (map vector (range 1 5) h))]
      [(+ false-easting (* A1 k0 eta))
       (* A1 k0 xi)])))
