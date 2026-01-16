package kaptainwutax.tungsten.constants.physics;

/**
 * Constants related to gravity, falling, and vertical movement physics.
 */
public final class GravityConstants {

    private GravityConstants() {}

    /**
     * Gravity acceleration values for different conditions.
     * Values are in blocks per tick squared.
     */
    public static final class Gravity {
        private Gravity() {}
        /** Gravity acceleration constant used in BlockNode calculations */
        public static final double GRAVITY_ACCELERATION = 32.656;
    }
}