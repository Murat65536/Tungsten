package kaptainwutax.tungsten.constants.physics;

import kaptainwutax.tungsten.path.KeyboardInput;

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

        /** The range of yaw values to consider at an offset from the direct yaw */
        public static final float YAW_RANGE = 22.5f;

        /** Number of yaw values to consider. */
        public static final int YAW_PRECISION = 3;

        /** Every valid movement input */
        public static final KeyboardInput[] ALL_INPUTS = new KeyboardInput[16]; // Pre-computed capacity

        static {
            int index = 0;
            for (boolean jump : new boolean[] {true, false}) {
                for (boolean forward : new boolean[] {true, false}) {
                    for (boolean back : new boolean[] {true, false}) {
                        if (!(forward && back)) {
                            for (boolean left : new boolean[] {false, true}) {
                                for (boolean right : new boolean[] {false, true}) {
                                    if (!(left && right)) {
                                        if (forward || back || left || right) {
                                            ALL_INPUTS[index++] = new KeyboardInput(forward, back, left, right, jump, false, true);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Length of the section of {@link Inputs#ALL_INPUTS} that doesn't include jumping
         */
        public static final int NO_JUMP_INPUT_LENGTH = ALL_INPUTS.length / 2;
    }
}