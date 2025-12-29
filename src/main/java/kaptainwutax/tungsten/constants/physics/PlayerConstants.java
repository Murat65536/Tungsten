package kaptainwutax.tungsten.constants.physics;

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
}