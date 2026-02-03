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

    /**
     * Velocity decay and dampening factors.
     */
    public static final class VelocityDecay {
        private VelocityDecay() {}

        /** Vertical velocity decay factor */
        public static final float VERTICAL_DECAY = 0.98f;
        /** Velocity threshold for near-zero checks */
        public static final double VELOCITY_EPSILON = 0.003;
        /** Small velocity threshold */
        public static final float SMALL_VELOCITY_THRESHOLD = 1.0E-5f;
        /** Levitation factor */
        public static final double LEVITATION_FACTOR = 0.2;
        /** Fall velocity precision threshold */
        public static final double FALL_VELOCITY_PRECISION = 0.005;
        /** Fall speed divisor */
        public static final double FALL_SPEED_DIVISOR = 16.0;
    }
}