(ns net.lambeaux.bots.math
  "Namespace for math operations or Clojure bindings to other math libraries.")

(comment
  "Be cautious about plugging into these formulae and expecting correct answers,
  especially when programming in C or Java. Math libraries for a programming language
  can do unexpected things if you are not careful. There are three places to be
  especially cautious:
  - The argument for sin(), cos(), tan() is expected in radians.
  - The return value of atan() is in radians.
  - The argument for most math functions is expected to be a double.
    In C, if you supply a float or an int, you won't get a error message,
    just a horribly incorrect answer.
  - There are several versions of 'arc tan' in most C libraries, each for a different range of
    output values.")

(comment
  "The following operations will be needed eventually but are on hold for now. It's not clear
  how to best implement them given the current, more restrictive assumptions for simplicity's
  sake.
  - All vectors in the namespace are currently assumed to be 3-vectors.
  - 2D support against the 3D data is only provided when the result could
    be different and requires manually specifying components.
  - Ideally we normalize around either degrees or radians, not convert
    back and forth to support both.
  - If the assumption is 3D, it seems odd to have the (dir ...) function
    assume it should compute with respect to the floor plane.
  - The matrix definition of the dot product provides more precision in
    practice than the angular / trig derivation.
  - Not sure about supporting both vsum and vsub when you can just leverage
    the commutative benefit of vsum and vneg where necessary, regardless of
    argument order."

  (defn- dir
    "Returns the direction of a vector in positive degrees between 0 and 360."
    [v]
    (let [rel-deg (Math/toDegrees (Math/atan2 (second v) (first v)))]
      (if (< rel-deg 0) (+ rel-deg 360) rel-deg)))

  (defn- dot
    "Returns the dot product of two vectors."
    ([v1 v2]
     (dot (mag v1) (mag v2) (dir v1) (dir v2)))
    ([mag1 mag2 angle1 angle2]
     (dot mag1 mag2 (Math/abs ^Double (- angle1 angle2))))
    ([mag1 mag2 theta]
     (* mag1 mag2 (Math/cos (Math/toRadians theta)))))

  ;; Dot product - reverse relationship / verification
  (* 20.1 5.38 (Math/cos (Math/toRadians 117.512)))
  (Math/toDegrees
    (Math/acos
      (/ -50.0 (* (mag [-2.0 -20.0])
                  (mag [5.0 2.0])))))

  (defn- parts
    "Returns the component vector of a magnitude and direction."
    [mag dir]
    [(* mag (Math/cos (Math/toRadians dir))) (* mag (Math/sin (Math/toRadians dir)))])

  (defn vsub
    "Returns the collective, ordered difference of all the provided vectors."
    [& vargs]
    [(reduce - (map #(% x) vargs))
     (reduce - (map #(% y) vargs))
     (reduce - (map #(% z) vargs))]))

;;
;; ----------------------------------------------------------------------
;; ### Math
;;
;; Building blocks of 3D vector math. It is expected that all vectors are
;; 3D in that they have precisely 3 non-nil components at index 0, 1, and 2.
;;

(def x 0)
(def y 1)
(def z 2)

(defn vsum
  "Returns the sum of all the provided vectors."
  [& vargs]
  [(reduce + (map #(% x) vargs))
   (reduce + (map #(% y) vargs))
   (reduce + (map #(% z) vargs))])

(defn vscale
  "Returns the input vector v after being uniformly scaled by s."
  [s v]
  [(* s (v x))
   (* s (v y))
   (* s (v z))])

(defn vneg
  "Returns a negated version of the input vector."
  [v]
  (vscale -1 v))

(defn vmag
  "If one arg is provided, returns the magnitude of the provided 3-vector.
  If 2 or 3 args are provided, they are treated as the input vector's individual
  components instead of vectors themselves; x y and x y z respectively."
  ([v]
   (vmag (v x) (v y) (v z)))
  ([x y]
   (vmag x y 0))
  ([x y z]
   (Math/sqrt (+ (Math/pow x 2) (Math/pow y 2) (Math/pow z 2)))))

(comment
  (vmag [1.0 1.0 1.0])
  (vmag 1.0 1.0)
  (vmag 1.0 1.0 1.0))

(defn vnorm
  "Returns a unit vector pointing in the same direction as the input vector.
  If one arg is provided, returns the normalized version of the provided 3-vector.
  If 2 or 3 args are provided, they are treated as the input vector's individual
  components instead of vectors themselves; x y and x y z respectively."
  ([v]
   ;; What happens if z=0? I think it's fine.
   (vnorm (v x) (v y) (v z)))
  ([x y]
   (let [m (vmag x y)] [(/ x m) (/ y m) 0.0]))
  ([x y z]
   (let [m (vmag x y z)] [(/ x m) (/ y m) (/ z m)])))

(comment
  (vmag (vnorm [2 4 8])))

(defn vdot
  "Returns the dot product of two vectors."
  [v1 v2]
  (+ (* (v1 x) (v2 x))
     (* (v1 y) (v2 y))
     (* (v1 z) (v2 z))))

(comment
  ;; Dot product - base cases
  (vdot [1 1 1] [1 1 1])
  (vdot [1 1 1] [-1 -1 -1])
  (vdot [-2.0 -20.0 0.0] [5.0 2.0 0.0])
  ;; Dot product - fix decimal precision (result should be zero)
  (vdot [1 0 0] [0 1 0])
  (vdot [1.0 1.0 0.0] [-1.0 1.0 0.0])
  (vdot [-1.0 1.0 0.0] [1.0 1.0 0.0]))

(defn vcross
  "Returns the cross product of two vectors."
  [v1 v2]
  (let [v1x (v1 x) v1y (v1 y) v1z (v1 z)
        v2x (v2 x) v2y (v2 y) v2z (v2 z)
        cx (- (* v1y v2z) (* v1z v2y))
        cy (- (* v1z v2x) (* v1x v2z))
        cz (- (* v1x v2y) (* v1y v2x))]
    [cx cy cz]))

(comment
  (vcross [1 0 0] [0 1 0]))