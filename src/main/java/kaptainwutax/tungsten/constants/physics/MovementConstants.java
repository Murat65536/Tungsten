package kaptainwutax.tungsten.constants.physics;

/**
 * Constants related to player and entity movement mechanics.
 * Includes speeds, multipliers, drag coefficients, and other movement physics values.
 */
public final class MovementConstants {

    private MovementConstants() {}

    /**
     * Base movement speeds and multipliers.
     */
    public static final class Speed {
        private Speed() {}

        /** Sprint velocity in blocks per tick */
        public static final double SPRINT_VELOCITY = 5.8;
    }

    /**
     * Climbing constants (ladders, vines).
     */
    public static final class Climbing {
        private Climbing() {}
        /** Ladder horizontal movement limit */
        public static final double LADDER_HORIZONTAL_LIMIT = 2.3;
    }

    /**
     * Special movement constants.
     */
    public static final class Special {
        private Special() {}

        /** Neo movement check distance */
        public static final double NEO_MOVEMENT_CHECK_DISTANCE = 4.2;
    }
}