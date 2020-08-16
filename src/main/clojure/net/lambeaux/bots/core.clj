(ns net.lambeaux.bots.core
  "The primary bot namespace for wiring everything together."
  (:require [clojure.pprint :as pretty]
            [net.lambeaux.bots.math :as mat])
  (:import (rlbot ControllerState Bot)
           (rlbot.manager BotLoopRenderer)
           (rlbot.flat GameTickPacket Rotator PlayerInfo BoxShape ScoreInfo Physics Touch BoostPadState TeamInfo)
           (java.awt Color Point)))

;; ----------------------------------------------------------------------
;; ### Bot state
;;
;; This will get reworked to be less stateful but given the nature of
;; the program it's necessary for now.
;;

(def last-game-packet-capture (atom nil))
(def last-game-map-capture (atom nil))
(def bot-model-state (atom nil))

;; ----------------------------------------------------------------------
;; ### Adapters
;;
;; Transformers for the bot data model and some standard representations.
;;

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

;;
;; The x-axis is negated in a similar fashion to the example Java bot to align the
;; quadrant system correctly.
;;
;; Fully qualify to distinguish between rlbot.flat and rlbot.vector
;;
(defn- expand-vector3 [^rlbot.flat.Vector3 *v3*] [(* -1.0 (.x *v3*)) (.y *v3*) (.z *v3*)])
(defn- expand-rotator [^Rotator *rt*] {:pitch (.pitch *rt*) :roll (.roll *rt*) :yaw (.yaw *rt*)})

(defn- expand-boxshape [^BoxShape *box*]
  {:length (-> *box* .length)
   :width  (-> *box* .width)
   :height (-> *box* .height)})

(defn- expand-stats [^ScoreInfo *scr*]
  {:score     (-> *scr* .score)
   :shots     (-> *scr* .shots)
   :goals     (-> *scr* .goals)
   :assists   (-> *scr* .assists)
   :saves     (-> *scr* .saves)
   :demos     (-> *scr* .demolitions)
   :own-goals (-> *scr* .ownGoals)})

(defn- expand-physics [^Physics *phys*]
  {:location         (-> *phys* .location expand-vector3)
   :velocity         (-> *phys* .velocity expand-vector3)
   :angular-velocity (-> *phys* .angularVelocity expand-vector3)
   :rotation         (-> *phys* .rotation expand-rotator)})

(defn- expand-touch [^Touch *touch*]
  (when *touch*
    {:touch-time     (-> *touch* .gameSeconds)
     :touch-location (-> *touch* .location expand-vector3)
     :touch-normal   (-> *touch* .normal expand-vector3)
     :player-index   (-> *touch* .playerIndex)
     :player-name    (-> *touch* .playerName)
     :player-team    (-> *touch* .team)}))

(defn- expand-boost [^BoostPadState *pad* ^Integer pad-index]
  {:index   pad-index
   :active? (-> *pad* .isActive)
   :timer   (-> *pad* .timer)})

(defn- expand-player [^PlayerInfo *player* ^Integer player-index]
  {:about   {:name          (-> *player* .name)
             :spawn-id      (-> *player* .spawnId)
             :team          (-> *player* .team)
             :hitbox        (-> *player* .hitbox expand-boxshape)
             :hitbox-offset (-> *player* .hitboxOffset expand-vector3)}
   :boost   (-> *player* .boost)
   :flags   {:has-jumped?        (-> *player* .jumped)
             :has-double-jumped? (-> *player* .doubleJumped)
             :has-wheel-contact? (-> *player* .hasWheelContact)
             :is-bot?            (-> *player* .isBot)
             :is-demolished?     (-> *player* .isDemolished)
             :is-supersonic?     (-> *player* .isSupersonic)}
   :index   player-index
   :physics (-> *player* .physics expand-physics)
   :stats   (-> *player* .scoreInfo expand-stats)})

(defn- expand-team [^TeamInfo *team*]
  {:index (-> *team* .teamIndex)
   :score (-> *team* .score)})

;;
;; Final questions
;; - How do you find boost pad location?
;;
;; Things we're skipping for now
;; - packet.ball().shapeType();
;;
;; Also note I'll want to subdivide the players by team
;;
(comment
  (game-packet->game-map @last-game-packet-capture 0))
(defn- game-packet->game-map
  "Convert a Java RLBot packet object to a Clojure map."
  [^GameTickPacket *packet* ^Integer player-index]
  {:ball-last-touch      (-> *packet* .ball .latestTouch expand-touch)
   :ball-physics         (-> *packet* .ball .physics expand-physics)
   :game-speed           (-> *packet* .gameInfo .gameSpeed)
   :game-time-remaining  (-> *packet* .gameInfo .gameTimeRemaining)
   :game-seconds-elapsed (-> *packet* .gameInfo .secondsElapsed)
   :game-world-gravity-z (-> *packet* .gameInfo .worldGravityZ)
   :game-kickoff-paused? (-> *packet* .gameInfo .isKickoffPause)
   :game-match-ended?    (-> *packet* .gameInfo .isMatchEnded)
   :game-overtime?       (-> *packet* .gameInfo .isOvertime)
   :game-round-active?   (-> *packet* .gameInfo .isRoundActive)
   :game-unlimited-time? (-> *packet* .gameInfo .isUnlimitedTime)
   :player-me            (-> *packet* (.players player-index) (expand-player player-index))
   :players-all          (->> (-> *packet* .playersLength)
                              (range 0)
                              (map #(vector % (.players *packet* %)))
                              (map #(expand-player (second %) (first %))))
   :boost-pads           (->> (-> *packet* .boostPadStatesLength)
                              (range 0)
                              (map #(vector % (.boostPadStates *packet* %)))
                              (map #(expand-boost (second %) (first %))))
   :teams                (->> (-> *packet* .teamsLength)
                              (range 0)
                              (map #(.teams *packet* %))
                              (map expand-team))})

;; ----------------------------------------------------------------------
;; ### Core
;;
;; Primary bot control logic.
;;

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

(defn- nose-vector
  "Returns a unit vector that represents the direction the car is facing."
  [game-map]
  (let [player-rotation (get-in game-map [:player-me :physics :rotation])
        {pitch :pitch yaw :yaw} player-rotation]
    [(* -1 (Math/cos pitch) (Math/cos yaw))
     (* (Math/cos pitch) (Math/sin yaw))
     (Math/sin pitch)]))

(defn- drive-to-ball
  "Controls the car on a frame-by-frame basis and drives to the ball. Nothing
  fancy going on here."
  [game-map throttle]
  (let [ball-location (get-in game-map [:ball-physics :location])
        player-location (get-in game-map [:player-me :physics :location])
        car-to-ball (mat/vsum ball-location (mat/vneg player-location))
        car-direction (nose-vector game-map)
        correction (correction-angle car-direction car-to-ball)]
    {:control-maps (list (conj default-input
                               [:throttle throttle]
                               [:steer (if (> correction 0) -1.0 1.0)]))}))

(defn- drive-forward
  "Controls the car by simply driving forward, according to the provided throttle."
  [throttle]
  {:control-maps (list (conj default-input
                             [:throttle throttle]))})

(defn- drive-nowhere
  "Controls the car by simply returning default controls. The car remains idle."
  []
  {:control-maps (list default-input)})

;; ----------------------------------------------------------------------
;; ### Render Text
;;
;; Functions for drawing debug text on screen.
;;

(defn- game-print-format [f pair]
  (str (first pair)
       (System/lineSeparator)
       "    "
       (f (second pair))
       (System/lineSeparator)))

(defn- game-obj->string
  "Creates a data string for printing on the Rocket League UI; helps keep data
  elements from aggressively wrapping and unwrapping as they vary in size."
  [game-obj]
  (cond
    (map? game-obj)
    (->> game-obj
         (map #(game-print-format game-obj->string %))
         (reduce str))
    (seq? game-obj)
    (->> game-obj
         (map #(vector "" %))
         (map #(game-print-format game-obj->string %))
         (reduce str))
    :default
    (print-str game-obj)))

(defn- game-map->string
  "Creates a data string for printing on the Rocket League UI; helps keep data
  elements from aggressively wrapping and unwrapping as they vary in size."
  [game-map]
  (->> game-map
       (map #(game-print-format print-str %))
       (reduce str)))

;; ----------------------------------------------------------------------
;; ### Render
;;
;; Functions for drawing debug info on screen.
;;

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
                    ;; fully qualify to distinguish between rlbot.flat and rlbot.vector
                    ;; re-adjust the x-axis when posting back to the game
                    (rlbot.vector.Vector3. (float (* -1.0 (first v1))) (float (second v1)) (float 50))
                    (rlbot.vector.Vector3. (float (* -1.0 (first v2))) (float (second v2)) (float 50)))))))

(defn- draw-rect! [color x y w h filled?]
  (let [renderer (:renderer @bot-model-state)]
    (when renderer
      (.drawRectangle2d renderer color (Point. x y) w h filled?))))

(defn- draw-string! [st color x y scale-x scale-y]
  (let [renderer (:renderer @bot-model-state)]
    (when renderer
      (.drawString2d renderer st color (Point. x y) scale-x scale-y))))

(defn- draw-quadrants! []
  (draw-line! Color/BLUE [0 0] [0 5000])
  (draw-line! Color/BLUE [0 0] [5000 0])
  (draw-line! Color/RED [0 0] [0 -5000])
  (draw-line! Color/RED [0 0] [-5000 0]))

(defn- draw-line-to-ball! [game-map]
  (let [ball-location (get-in game-map [:ball-physics :location])
        player-location (get-in game-map [:player-me :physics :location])]
    (draw-line! Color/MAGENTA ball-location player-location)))

(defn- draw-line-to-car-direction! [game-map]
  (let [player-location (get-in game-map [:player-me :physics :location])]
    (draw-line! Color/CYAN
                player-location
                (->> game-map nose-vector (mat/vscale 200) (mat/vsum player-location)))))

(defn- draw-game-map-text! [game-map]
  (draw-rect!
    (with-color Color/BLACK 0.75) 5 25 750 880 true)
  (draw-string!
    (->> (-> game-map
             (dissoc :boost-pads :players-all :game-speed)
             (update-in [:player-me] #(apply dissoc % [:stats])))
         (into (sorted-map))
         pretty/pprint
         with-out-str) Color/WHITE 10 40 1 1))

(defn- draw-debug-info! [game-map]
  (draw-quadrants!)
  (draw-line-to-ball! game-map)
  (draw-line-to-car-direction! game-map)
  ;; Uncomment the below form to see the data on the game screen.
  #_(draw-game-map-text! game-map))

;; ----------------------------------------------------------------------
;; ### Main
;;
;; Entry point.
;;

(defn- next-bot-model
  "This is the core update function for a Clojure bot that maps
  game packets to controller inputs frame-by-frame. It is modeled
  as a reduce operation of (bot-state, packet) -> (bot-state) and
  lays the foundation for expressing bots as values."
  [bot-model game-map]
  ;; Functions merely being evaluated for side effects,
  ;; not return value. Suffix fn's with ! to denote this.
  (draw-debug-info! game-map)
  ;; Standard processing, the value of the last function is returned.
  ;; Comment out the last fn with #_ and see what happens.
  (drive-nowhere)
  (drive-forward 0.5)
  (drive-to-ball game-map 0.5))

(defn create-bot
  "Entry point for a Clojure bot.

  Creates the actual bot impl that will be ticked every frame. The (reify) function
  allows Clojure to return an implementation of a Java interface. Also handles some
  state management for the bot which allows the core tick function logic (next-bot-model)
  to remain stateless. See src/main/java/net/lambeaux/bots/BotServerImpl for more."
  [player-index]
  (reify Bot
    (getIndex [this] #_(println (str "Fetching index " player-index)) player-index)
    (retire [this] (println (str "Retiring sample bot " player-index)))
    (processInput [this packet]
      ;; Define initial conditions. Done in the game loop in case of hot reload which
      ;; resets namespace vars.
      (when (nil? @bot-model-state)
        (println "Initializing bot data model")
        (swap! bot-model-state
               (constantly (merge default-model {:player-index player-index
                                                 :renderer     (BotLoopRenderer/forBotLoop this)}))))
      ;; Compute some values we need to reuse.
      (let [game-map (game-packet->game-map packet player-index)
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