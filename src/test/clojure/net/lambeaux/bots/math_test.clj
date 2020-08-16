(ns net.lambeaux.bots.math-test
  (:require [clojure.test :refer :all]
            [net.lambeaux.bots.math :as mat]))

(deftest test-vsum
  (is (= [1 1 1] (mat/vsum [1 1 1]))
      "Sum with 1 arg should return itself")
  (is (= [2 2 2] (mat/vsum [1 1 1] [1 1 1]))
      "Sum with 2 stable args should linearly scale")
  (is (= [3 3 3] (mat/vsum [1 1 1] [1 1 1] [1 1 1]))
      "Sum with 3 stable args should linearly scale")
  (is (= [50 50 50] (apply mat/vsum (repeat 50 [1 1 1])))
      "Sum with 50 stable args should linearly scale")
  (is (= [1 1 1] (apply mat/vsum (into '([50 50 50]) (repeat 49 [-1 -1 -1]))))
      "Sum with 49 negative stable args should linearly scale")
  (is (= [2 0 0] (mat/vsum [1 0 0] [1 0 0]))
      "Sum should work for only X")
  (is (= [0 2 0] (mat/vsum [0 1 0] [0 1 0]))
      "Sum should work for only Y")
  (is (= [0 0 2] (mat/vsum [0 0 1] [0 0 1]))
      "Sum should work for only Z")
  (is (= [0 0 0] (mat/vsum [1 0 0] [-1 0 0]))
      "Sum should work for only negative X")
  (is (= [0 0 0] (mat/vsum [0 1 0] [0 -1 0]))
      "Sum should work for only negative Y")
  (is (= [0 0 0] (mat/vsum [0 0 1] [0 0 -1]))
      "Sum should work for only negative Z"))

(deftest test-vscale
  (is (= [4 4 4] (mat/vscale 1 [4 4 4]))
      "Scaling by 1 should not change input")
  (is (= [-4 -4 -4] (mat/vscale -1 [4 4 4]))
      "Scaling by -1 should negate the input")
  (is (= [8 8 8] (mat/vscale 2 [4 4 4]))
      "Scaling by 2 should double the input")
  (is (= [-8 -8 -8] (mat/vscale -2 [4 4 4]))
      "Scaling by -2 should double the negated input")
  (is (= [8 10 12] (mat/vscale 2 [4 5 6]))
      "Scaling should be done by component")
  (is (= [0 0 0] (mat/vscale 0 [4 4 4]))
      "Scaling by 0 always yields zero vector")
  (is (= [0 8 8] (mat/vscale 2 [0 4 4]))
      "X component, when zero, should ignore scaling")
  (is (= [8 0 8] (mat/vscale 2 [4 0 4]))
      "Y component, when zero, should ignore scaling")
  (is (= [8 8 0] (mat/vscale 2 [4 4 0]))
      "Z component, when zero, should ignore scaling"))

(deftest test-vneg
  (is (= [-1 -1 -1] (mat/vneg [1 1 1])) "Components should be negating when all positive")
  (is (= [1 -1 -1] (mat/vneg [-1 1 1])) "Components should be negating when just X is negative")
  (is (= [-1 1 -1] (mat/vneg [1 -1 1])) "Components should be negating when just Y is negative")
  (is (= [-1 -1 1] (mat/vneg [1 1 -1])) "Components should be negating when just Z is negative")
  (is (= [1 1 1] (mat/vneg [-1 -1 -1])) "Components should be negating when all are negative"))

(deftest test-vmag
  (is (= 1 1) "TODO"))

(deftest test-vnorm
  (is (= 1 1) "TODO"))

(deftest test-vdot
  (is (= 1 1) "TODO"))

(deftest test-vcross
  (is (= 1 1) "TODO"))