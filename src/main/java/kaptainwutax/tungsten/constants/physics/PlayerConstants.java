package kaptainwutax.tungsten.constants.physics;

import kaptainwutax.tungsten.path.PathInput;

/**
 * Constants related to player dimensions, eye heights, and physical attributes.
 * These values are used for collision detection, rendering, and movement calculations.
 */
public final class PlayerConstants {

    private PlayerConstants() {}

    /**
     * Step heights for automatic step-up mechanics.
     * Determines the maximum height the player can step up automatically.
     */
    public static final class StepHeight {
        private StepHeight() {}

        /** Default step height in blocks */
        public static final float DEFAULT = 0.6f;
        /** Step height when riding horses */
        public static final float HORSE = 1.0f;
    }

    /**
     * Movement input mechanics.
     * Stores information related to player movement inputs.
     */
    public static final class Inputs {
        private Inputs() {}

        /** Number of yaw directions to consider. Higher values are more accurate but slower. Each increment doubles the number of considered values. */
        public static final int YAW_DIRECTION_MAGNITUDE = 4;

        /** Every valid movement input */
        public static final PathInput[] ALL_INPUTS = new PathInput[42 << YAW_DIRECTION_MAGNITUDE]; // Pre-computed capacity

        static {
            int index = 0;
            for (boolean sprint : new boolean[] {false, true}) {
                for (boolean sneak : new boolean[] {false, true}) {
                    if (!(sprint && sneak)) {
                        for (boolean forward : new boolean[] {false, true}) {
                            if (!(sprint && !forward)) {
                                for (boolean back : new boolean[] {false, true}) {
                                    if (!(forward && back)) {
                                        for (boolean left : new boolean[] {false, true}) {
                                            for (boolean right : new boolean[] {false, true}) {
                                                if (!(left && right)) {
                                                    for (boolean jump : new boolean[] {false, true}) {
                                                        for (float yaw = -180f; yaw < 180f; yaw += 360f / (1 << YAW_DIRECTION_MAGNITUDE)) {
                                                            ALL_INPUTS[index] = new PathInput(forward, back, right, left, jump, sneak, sprint, 0, yaw);
                                                            index++;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}