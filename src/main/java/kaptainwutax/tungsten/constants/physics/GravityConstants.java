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

        /** Standard gravity acceleration */
        public static final double STANDARD_GRAVITY = 0.08;
        /** Gravity when under Slow Falling effect */
        public static final double SLOW_FALLING_GRAVITY = 0.01;
        /** Gravity acceleration constant used in BlockNode calculations */
        public static final double GRAVITY_ACCELERATION = 32.656;
        /** Levitation effect strength per level */
        public static final double LEVITATION_PER_LEVEL = 0.05;
    }
}