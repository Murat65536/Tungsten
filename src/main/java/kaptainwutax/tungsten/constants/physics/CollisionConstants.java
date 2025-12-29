package kaptainwutax.tungsten.constants.physics;

/**
 * Constants related to collision detection, bounding box adjustments,
 * and precision thresholds for physics calculations.
 */
public final class CollisionConstants {

    private CollisionConstants() {}

    /**
     * Epsilon values for various precision checks.
     * Used to handle floating-point precision issues.
     */
    public static final class Epsilon {
        private Epsilon() {}

        /** Epsilon for collision detection precision */
        public static final double COLLISION_EPSILON = 1.0E-7;
        /** Epsilon for velocity near-zero checks */
        public static final double VELOCITY_EPSILON = 0.003;
    }

    /**
     * Collision detection thresholds and angles.
     */
    public static final class Thresholds {
        private Thresholds() {}

        /** Soft collision angle threshold in radians */
        public static final double SOFT_COLLISION_ANGLE_THRESHOLD = 0.13962633907794952;
    }

    /**
     * Bounding box adjustments and offsets.
     */
    public static final class BoxAdjustments {
        private BoxAdjustments() {}

        /** Push out of blocks offset */
        public static final double PUSH_OUT_OF_BLOCKS_OFFSET = 0.35;
        /** Push velocity when colliding with blocks */
        public static final double PUSH_VELOCITY = 0.1;
        /** Collision check Y offset */
        public static final float COLLISION_CHECK_Y_OFFSET = 0.6f;
        /** Horizontal collision Y velocity */
        public static final float HORIZONTAL_COLLISION_Y_VELOCITY = 0.3f;
    }

    /**
     * Velocity thresholds for different states.
     */
    public static final class VelocityThresholds {
        private VelocityThresholds() {}

        /** Minimum velocity to be considered stationary */
        public static final double MIN_VELOCITY_STATIONARY = 0.07;
        /** Minimum velocity in water */
        public static final double MIN_VELOCITY_WATER = 0.2;
    }
}