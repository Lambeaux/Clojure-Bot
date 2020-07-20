package net.lambeaux.bots;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;

/**
 * Core bot logic that maps game packets to controller inputs frame-by-frame.
 */
public class BotImpl implements Bot {

    private final int playerIndex;

    public BotImpl(int playerIndex) {
        System.out.println("Constructing bot");
        this.playerIndex = playerIndex;
    }

    @Override
    public int getIndex() {
        System.out.println("Fetching index");
        return this.playerIndex;
    }

    @Override
    public ControllerState processInput(GameTickPacket packet) {
        return new ControllerState() {
            @Override
            public float getSteer() {
                return 0.0f;
            }

            @Override
            public float getThrottle() {
                return 1.0f;
            }

            @Override
            public float getPitch() {
                return 0.0f;
            }

            @Override
            public float getYaw() {
                return 0.0f;
            }

            @Override
            public float getRoll() {
                return 0.0f;
            }

            @Override
            public boolean holdJump() {
                return false;
            }

            @Override
            public boolean holdBoost() {
                return false;
            }

            @Override
            public boolean holdHandbrake() {
                return false;
            }

            @Override
            public boolean holdUseItem() {
                return false;
            }
        };
    }

    @Override
    public void retire() {
        System.out.println("Retiring sample bot " + playerIndex);
    }
}