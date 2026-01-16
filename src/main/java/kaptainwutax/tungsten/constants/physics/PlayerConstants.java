package kaptainwutax.tungsten.constants.physics;

import kaptainwutax.tungsten.path.KeyboardInput;

/**
 * Constants related to player dimensions, eye heights, and physical attributes.
 * These values are used for collision detection, rendering, and movement calculations.
 */
public final class PlayerConstants {

    private PlayerConstants() {}

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
        public static final KeyboardInput[] ALL_INPUTS = new KeyboardInput[42]; // Pre-computed capacity

        static {
            int index = 0;
            boolean[] toggle = new boolean[] {false, true};
            for (boolean jump : toggle) {
                for (boolean sprint : toggle) {
                    for (boolean sneak : toggle) {
                        if (!(sprint && sneak)) {
                            for (boolean forward : toggle) {
                                if (!(sprint && !forward)) {
                                    for (boolean back : toggle) {
                                        if (!(forward && back)) {
                                            for (boolean left : toggle) {
                                                for (boolean right : toggle) {
                                                    if (!(left && right)) {
                                                        ALL_INPUTS[index++] = new KeyboardInput(forward, back, right, left, jump, sneak, sprint);
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