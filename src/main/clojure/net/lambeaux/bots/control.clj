(ns net.lambeaux.bots.control
  (:require [clojure.pprint :as pretty])
  (:import (rlbot ControllerState Bot)
           (rlbot.flat GameTickPacket)
           (rlbot.manager BotLoopRenderer)
           (java.awt Color Point)
           (rlbot.vector Vector3)))

;; ----------------------------------------------------------------------
;; ### Bot state
;;
;; This will get reworked to be less stateful but given the nature of
;; the program it's necessary for now.

(def last-game-packet-capture (atom nil))
(def last-game-map-capture (atom nil))
(def bot-model-state (atom nil))

;; ----------------------------------------------------------------------
;; ### Math
;;
;; Building blocks of 3D vector math.

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
  - There are several versions of 'arc tan' in most C libraries, each for a different range of output values.")

(defn- sum
  "Adds two vectors together."
  [v1 v2]
  [(+ (first v1) (first v2)) (+ (second v1) (second v2))])

(defn- sub
  "Subtracts v2 from v1."
  [v1 v2]
  [(- (first v1) (first v2)) (- (second v1) (second v2))])

(defn- mag
  "Returns the magnitude of a vector."
  [v]
  (Math/sqrt (+ (Math/pow (first v) 2) (Math/pow (second v) 2))))

(defn- dir
  "Returns the direction of a vector in positive degrees between 0 and 360."
  [v]
  (let [rel-deg (Math/toDegrees (Math/atan2 (second v) (first v)))]
    (if (< rel-deg 0) (+ rel-deg 360) rel-deg)))

(defn- norm
  "Returns a unit vector pointing in the same direction as the input vector."
  [v]
  (let [m (mag v)] [(/ (first v) m) (/ (second v) m)]))

(defn- dot
  "Returns the dot product of two vectors."
  ([v1 v2]
   (dot (mag v1) (mag v2) (dir v1) (dir v2)))
  ([mag1 mag2 angle1 angle2]
   (dot mag1 mag2 (Math/abs ^Double (- angle1 angle2))))
  ([mag1 mag2 theta]
   (* mag1 mag2 (Math/cos (Math/toRadians theta)))))

(defn- parts
  "Returns the component vector of a magnitude and direction."
  [mag dir]
  [(* mag (Math/cos (Math/toRadians dir))) (* mag (Math/sin (Math/toRadians dir)))])

(defn- matrix-dot
  "An implementation of dot product according to the rules of column matrices."
  [v1 v2]
  (+ (* (first v1) (first v2)) (* (second v1) (second v2))))

(comment
  ;;
  ;; Basics
  (sum [1.0 1.0] [2.0 3.0])
  (sub [5.0 5.0] [3.0 4.0])
  (mag [1.0 1.0])
  (dir [1.0 1.0])
  (parts (mag [1.0 1.0]) (dir [1.0 1.0]))
  ;;
  ;; Unit vectors
  (norm [1.0 1.0])
  (mag (norm [1.0 1.0]))
  ;;
  ;; Dot product
  (dot [-2.0 -20.0] [5.0 2.0])
  (dot (mag [-2.0 -20.0]) (mag [5.0 2.0]) (dir [-2.0 -20.0]) (dir [5.0 2.0]))
  (dot (mag [-2.0 -20.0]) (mag [5.0 2.0]) (Math/abs (- (dir [-2.0 -20.0]) (dir [5.0 2.0]))))
  (matrix-dot [-2.0 -20.0] [5.0 2.0])
  ;;
  ;; Dot product - reverse relationship / verification
  (* 20.1 5.38 (Math/cos (Math/toRadians 117.512)))
  (Math/toDegrees
    (Math/acos
      (/ -50.0 (* (mag [-2.0 -20.0])
                  (mag [5.0 2.0])))))
  ;;
  ;; Dot product - fix decimal precision (result should be zero)
  (dot [1.0 1.0] [-1.0 1.0])
  (dot [-1.0 1.0] [1.0 1.0])
  (float (dot [1.0 1.0] [-1.0 1.0]))
  (float (dot [-1.0 1.0] [1.0 1.0]))
  ;;
  ;; Issues
  (dot (mag [1.0 1.0]) (mag [-1.0 1.0]) (- (dir [1.0 1.0]) (dir [-1.0 1.0])))
  (Math/toRadians -90.0)
  (/ (Math/PI))
  (Math/cos (Math/toRadians 0))
  (Math/toRadians 45)
  ;;
  ;; Other
  (Math/toDegrees (Math/atan2 1.0 1.0))
  (Math/toDegrees (Math/atan 1.0)))

;; ----------------------------------------------------------------------
;; ### Adapters
;;
;; Transformers for the bot data model and some standard representations.

(def default-input
  {:steer      0.0
   :throttle   0.0
   :pitch      0.0
   :yaw        0.0
   :roll       0.0
   :jump?      false
   :boost?     false
   :handbreak? false
   :item?      false})

(def default-model
  ;; Won't know index or renderer until bot init but it's still part of the model
  {:player-index nil
   :renderer     nil
   :game-maps    (list)
   :control-maps (list default-input)})

(defn- control-map->controller-state [controls]
  (let [{steer      :steer
         throttle   :throttle
         pitch      :pitch
         roll       :roll
         yaw        :yaw
         jump?      :jump?
         boost?     :boost?
         handbrake? :handbreak?
         item?      :item?} controls]
    (reify ControllerState
      (getSteer [this] (float steer))
      (getThrottle [this] (float throttle))
      (getPitch [this] (float pitch))
      (getRoll [this] (float roll))
      (getYaw [this] (float yaw))
      (holdJump [this] jump?)
      (holdBoost [this] boost?)
      (holdHandbrake [this] handbrake?)
      (holdUseItem [this] item?))))

(defn- game-packet->game-map
  "Converts the Java packet object to a Clojure map. The x-axis is negated in a
  similar fashion to the example Java bot to align the quadrant system correctly."
  [^GameTickPacket packet]
  (let [ball-location (-> packet .ball .physics .location)
        ball-velocity (-> packet .ball .physics .velocity)
        ball-ang-velocity (-> packet .ball .physics .angularVelocity)
        ball-rotation (-> packet .ball .physics .rotation)
        player-location (-> packet (.players 0) .physics .location)
        player-velocity (-> packet (.players 0) .physics .velocity)
        player-ang-velocity (-> packet (.players 0) .physics .angularVelocity)
        player-rotation (-> packet (.players 0) .physics .rotation)
        expand-vector3 (fn [v3] [(* -1.0 (.x v3)) (.y v3) (.z v3)])]
    {:ball-location           (expand-vector3 ball-location)
     :ball-velocity           (expand-vector3 ball-velocity)
     :ball-angular-velocity   (expand-vector3 ball-ang-velocity)
     :ball-rotation           {:pitch (.pitch ball-rotation)
                               :roll  (.roll ball-rotation)
                               :yaw   (.yaw ball-rotation)}
     :player-location         (expand-vector3 player-location)
     :player-velocity         (expand-vector3 player-velocity)
     :player-angular-velocity (expand-vector3 player-ang-velocity)
     :player-rotation         {:pitch (.pitch player-rotation)
                               :roll  (.roll player-rotation)
                               :yaw   (.yaw player-rotation)}}))

(defn- game-map->string
  "Creates a data string for printing on the Rocket League UI; helps keep data
  elements from aggressively wrapping and unwrapping as they vary in size."
  [game-map]
  (->> game-map
       (map (fn [[k v]] (str k
                             (System/lineSeparator)
                             "    "
                             (print-str v)
                             (System/lineSeparator))))
       (reduce str)))

;; ----------------------------------------------------------------------
;; ### Core
;;
;; Primary bot control logic.

(comment
  (println (with-out-str (pretty/pprint {:name "steve" :pass "true"})))
  (println (game-map->string {:name "steve" :pass "true"}))
  @last-game-packet-capture
  @last-game-map-capture
  @bot-model-state)

(defn- with-color
  ([color opacity]
   (with-color (.getRed color) (.getGreen color) (.getBlue color) (* 255 opacity)))
  ([r g b a]
   (Color. (int r) (int g) (int b) (int a))))

(defn- draw-line!
  ([v1 v2]
   (draw-line! Color/MAGENTA v1 v2))
  ([color v1 v2]
   (let [renderer (:renderer @bot-model-state)]
     (when renderer
       (.drawLine3d renderer
                    color
                    ;; re-adjust the x-axis when posting back to the game
                    (Vector3. (float (* -1.0 (first v1))) (float (second v1)) (float 50))
                    (Vector3. (float (* -1.0 (first v2))) (float (second v2)) (float 50)))))))

(defn- draw-rect! [color x y w h filled?]
  (let [renderer (:renderer @bot-model-state)]
    (when renderer
      (.drawRectangle2d renderer color (Point. x y) w h filled?))))

(defn- draw-string! [st color x y scale-x scale-y]
  (let [renderer (:renderer @bot-model-state)]
    (when renderer
      (.drawString2d renderer st color (Point. x y) scale-x scale-y))))

(defn- correction-angle
  "Returns the number of radians v-in needs to be rotated by
  in order to line up with v-goal."
  [v-in v-goal]
  (let [r-in (Math/atan2 (second v-in) (first v-in))
        r-goal (Math/atan2 (second v-goal) (first v-goal))]
    (if
      (<= (Math/abs (- r-in r-goal)) Math/PI)
      (- r-goal r-in)
      (let [r-in-pos (if (< r-in 0)
                       (+ r-in (* Math/PI 2))
                       r-in)
            r-goal-pos (if (< r-goal 0)
                         (+ r-goal (* Math/PI 2))
                         r-goal)]
        (- r-goal-pos r-in-pos)))))

(defn- nose-vector [game-map]
  (let [player-rotation (:player-rotation game-map)
        {pitch :pitch roll :roll yaw :yaw} player-rotation]
    [(* -1 (Math/cos pitch) (Math/cos yaw))
     (* (Math/cos pitch) (Math/sin yaw))
     (Math/sin pitch)]))

(defn- drive-to-ball [game-map throttle]
  (let [{ball-location   :ball-location
         player-location :player-location} game-map
        car-to-ball (sub ball-location player-location)
        car-direction (nose-vector game-map)
        correction (correction-angle car-direction car-to-ball)]
    {:control-maps (list (conj default-input
                               [:throttle throttle]
                               [:steer (if (> correction 0) -1.0 1.0)]))}))

(defn- drive-forward [throttle]
  {:control-maps (list (conj default-input
                             [:throttle throttle]))})

(defn- drive-nowhere []
  {:control-maps (list default-input)})

(defn- next-bot-model
  "This is the core update function for a Clojure bot that maps
  game packets to controller inputs frame-by-frame. It is modeled
  as a reduce operation of (bot-state, packet) -> (bot-state) and
  lays the foundation for expressing bots as values."
  [bot-model game-map]
  ;; Draw quadrants.
  (draw-line! Color/BLUE [0 0] [0 5000])
  (draw-line! Color/BLUE [0 0] [5000 0])
  (draw-line! Color/RED [0 0] [0 -5000])
  (draw-line! Color/RED [0 0] [-5000 0])
  ;; Draw target and nose.
  (draw-line! Color/MAGENTA
              (:ball-location game-map)
              (:player-location game-map))
  (draw-line! Color/CYAN
              (:player-location game-map)
              (->> game-map
                   nose-vector
                   (repeat 150)
                   (into (list (:player-location game-map)))
                   (reduce sum)))
  ;; Draw live game data.
  (draw-rect! (with-color Color/BLACK 0.75) 5 25 700 350 true)
  (draw-string! (game-map->string game-map) Color/WHITE 10 40 1 1)
  ;; Standard processing, the value of the last function is returned.
  (drive-nowhere)
  (drive-forward 0.5)
  (drive-to-ball game-map 0.5))

(defn create-bot
  "Entry point for a Clojure bot (see BotServerImpl). Builds the bot and sets initial
  state.

  Creates the actual bot impl that will be ticked every frame. The (reify) function
  allows Clojure to return an implementation of a Java interface. Also handles some
  state management for the bot which allows the core tick function logic (next-bot-model)
  to remain stateless."
  [player-index]
  (reify Bot
    (getIndex [this] (println (str "Fetching index " player-index)) player-index)
    (retire [this] (println (str "Retiring sample bot " player-index)))
    (processInput [this packet]
      ;; Define initial conditions. Done in the game loop in case of hot reload which
      ;; resets namespace vars.
      (when (nil? @bot-model-state)
        (swap! bot-model-state
               (constantly (merge default-model {:player-index player-index
                                                 :renderer     (BotLoopRenderer/forBotLoop this)}))))
      ;; Compute some values we need to reuse.
      (let [game-map (game-packet->game-map packet)
            bot-model (next-bot-model @bot-model-state game-map)]
        ;; Record the game packet / map on every hot reload (side-effect).
        (if (nil? @last-game-packet-capture) (swap! last-game-packet-capture (constantly packet)))
        (if (nil? @last-game-map-capture) (swap! last-game-map-capture (constantly game-map)))
        ;; Record the new bot model for next time (side-effect).
        (swap! bot-model-state
               (constantly (merge default-model bot-model {:player-index player-index
                                                           :renderer     (BotLoopRenderer/forBotLoop this)})))
        ;; Return the latest controller state.
        (-> bot-model
            :control-maps
            first
            control-map->controller-state)))))