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
        public static final KeyboardInput[] ALL_INPUTS = new KeyboardInput[41]; // Pre-computed capacity

        static {
            int index = 0;
            for (boolean jump : new boolean[] {false, true}) {
                for (boolean sprint : new boolean[]{false, true}) {
                    for (boolean sneak : new boolean[]{false, true}) {
                        if (!(sprint && sneak)) {
                            for (boolean forward : new boolean[]{true, false}) {
                                if (!(sprint && !forward)) {
                                    for (boolean back : new boolean[]{false, true}) {
                                        if (!(forward && back)) {
                                            for (boolean left : new boolean[]{false, true}) {
                                                for (boolean right : new boolean[]{false, true}) {
                                                    if (!(left && right)) {
                                                        if (forward || back || left || right || jump || sneak) {
                                                            ALL_INPUTS[index++] = new KeyboardInput(forward, back, left, right, jump, sneak, sprint);
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

        /**
         * Length of the section of {@link Inputs#ALL_INPUTS} that doesn't include jumping
         */
        public static final int NO_JUMP_INPUT_LENGTH = ALL_INPUTS.length / 2;
    }
}