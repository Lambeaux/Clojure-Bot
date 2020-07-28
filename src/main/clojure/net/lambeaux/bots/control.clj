(ns net.lambeaux.bots.control
  (:import (rlbot ControllerState Bot)))

(defn create-controller-state
  "Creates a controller state from the raw data."
  [steer throttle pitch yaw roll jump? boost? handbrake? use-item?]
  (reify ControllerState
    (getSteer [this] (float steer))
    (getThrottle [this] (float throttle))
    (getPitch [this] (float pitch))
    (getYaw [this] (float yaw))
    (getRoll [this] (float roll))
    (holdJump [this] jump?)
    (holdBoost [this] boost?)
    (holdHandbrake [this] handbrake?)
    (holdUseItem [this] use-item?)))

(defn- process-packet
  "This is the core update function for a Clojure bot that maps
  game packets to controller inputs frame-by-frame."
  [game-packet]
  (create-controller-state 0.0, 1.0, 0.0, 0.0, 0.0, false, false, false, false))

(defn create-bot
  "Creates the actual bot impl that will be ticked every frame."
  [player-index]
  (reify Bot
    (getIndex [this] (do (println "Fetching index") player-index))
    (processInput [this packet] (process-packet packet))
    (retire [this] (println (str "Retiring sample bot " player-index)))))