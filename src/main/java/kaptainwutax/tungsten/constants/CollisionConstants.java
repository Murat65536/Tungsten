package kaptainwutax.tungsten.constants;

/**
 * Collision detection and handling constants used throughout the agent system.
 * These values control precision, thresholds, and boundaries for collision calculations.
 */
public final class CollisionConstants {

    private CollisionConstants() {} // Prevent instantiation

    // ========== Collision Epsilon Values ==========
    public static final double BOUNDING_BOX_EPSILON = 1.0E-7D;
    public static final double SHAPE_COLLISION_EPSILON = 1.0E-5F;

    // ========== Collision Box Adjustments ==========
    public static final double COLLISION_BOX_CONTRACT = 0.001D;
    public static final double COLLISION_BOX_PADDING = 0.001D;
    public static final double COLLISION_BOX_FULL_BLOCK = 1.0D;

    // ========== Collision Type Checks ==========
    // These are used for different collision response types
    public static final int COLLISION_TYPE_MOVEMENT = 1;
    public static final int COLLISION_TYPE_STEP = 2;
    public static final int COLLISION_TYPE_OTHER = 3;
}