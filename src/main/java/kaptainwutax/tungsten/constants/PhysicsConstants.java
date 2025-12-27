package kaptainwutax.tungsten.constants;

/**
 * Physics constants used throughout the agent movement and simulation system.
 * These values are extracted from the original Agent.java implementation.
 */
public final class PhysicsConstants {

    private PhysicsConstants() {} // Prevent instantiation

    // ========== Gravity Constants ==========
    public static final double GRAVITY_DEFAULT = 0.08D;
    public static final double GRAVITY_SLOW_FALLING = 0.01D;

    // ========== Movement Speed Constants ==========
    public static final float STEP_HEIGHT_DEFAULT = 0.6F;
    public static final float MOVEMENT_SPEED_BASE = 0.1F;
    public static final float SPRINT_SPEED_MULTIPLIER = 0.3F;
    public static final float MOVEMENT_INPUT_MULTIPLIER = 0.3F;
    public static final float MOVEMENT_USING_ITEM_MULTIPLIER = 0.2F;
    public static final double MOVEMENT_SPEED_COEFFICIENT = 0.21600002F;
    public static final double SPEED_EFFECT_AMPLIFIER = 0.20000000298023224D;

    // ========== Air Movement ==========
    public static final float AIR_STRAFING_SPEED_BASE = 0.02F;
    public static final float AIR_STRAFING_SPEED_SLOW = 0.006F;

    // ========== Water/Fluid Movement ==========
    public static final float WATER_DESCENT_SPEED_SNEAKING = 0.04F;
    public static final double SWIMMING_SPEED_MULTIPLIER = 0.02F;
    public static final double SWIMMING_SPEED_UNDERWATER = 0.085D;
    public static final double SWIMMING_SPEED_SURFACE = 0.06D;
    public static final double SWIMMING_SPEED_SURFACE_UNDER = 0.1D;
    public static final double WATER_PUSH_UP_SPEED = 0.8F;
    public static final double FLUID_VELOCITY_MULTIPLIER = 0.007D;
    public static final double FLUID_VELOCITY_LAVA_MULTIPLIER = 0.0023333333333333335D;
    public static final double FLUID_FLOW_MIN = 0.0045000000000000005D;
    public static final double FLUID_FLOW_SCALE = 0.014D;

    // ========== Lava Movement ==========
    public static final double LAVA_VELOCITY_MULTIPLIER_Y = 0.5D;
    public static final double LAVA_VELOCITY_MULTIPLIER_XZ = 0.5D;

    // ========== Climbing Movement ==========
    public static final double CLIMBING_SPEED_BASE = 0.2D;
    public static final double CLIMBING_SPEED_MAX = 0.15000000596046448D;

    // ========== Jump Constants ==========
    public static final float JUMP_VELOCITY_BASE = 0.42F;
    public static final float JUMP_BOOST_MULTIPLIER = 0.1F;
    public static final double JUMP_HORIZONTAL_BOOST_SPRINTING = 0.2F;
    public static final double JUMP_HEIGHT_OFFSET = 0.5000001D;
    public static final double JUMP_VERTICAL_INERTIA = 0.4D;
    public static final double JUMP_HORIZONTAL_INERTIA = 0.04F;

    // ========== Velocity Thresholds ==========
    public static final double VELOCITY_EPSILON = 0.003D;
    public static final double VELOCITY_MIN_SQUARED = 1.0E-7D;
    public static final double VELOCITY_COLLISION_THRESHOLD = 0.005D;
    public static final double VELOCITY_SMALL_THRESHOLD_1 = 1.0E-4D;
    public static final double VELOCITY_SMALL_THRESHOLD_2 = 1.0E-5F;

    // ========== Drag/Friction Coefficients ==========
    public static final float GROUND_DRAG_DEFAULT = 0.91F;
    public static final float GROUND_DRAG_AIR = 0.98F;
    public static final float SLIPPERINESS_MULTIPLIER = 0.6F;
    public static final float MOVEMENT_DECAY = 0.98F;

    // ========== Elytra Flight Constants ==========
    public static final double ELYTRA_DRAG_X = 0.9900000095367432D;
    public static final double ELYTRA_DRAG_Y = 0.9800000190734863D;
    public static final double ELYTRA_DRAG_Z = 0.9900000095367432D;
    public static final double ELYTRA_PITCH_MIN = -0.5D;
    public static final double ELYTRA_PITCH_FACTOR = 0.13962633907794952D;

    // ========== Fall Damage Constants ==========
    public static final float FALL_DISTANCE_LAVA_MULTIPLIER = 0.5F;

    // ========== Block Interaction Constants ==========
    // Slime/Bed blocks
    public static final float BOUNCE_VELOCITY_MULTIPLIER_BED = 0.66F;
    public static final double SLIME_VELOCITY_CLAMP_MIN = 0.1D;
    public static final double SLIME_VELOCITY_BOOST = 0.4D;
    public static final double SLIME_HORIZONTAL_MULTIPLIER = 0.2D;
    public static final int BOUNCE_VELOCITY_INVERT = -1;

    // Cobweb
    public static final double COBWEB_VELOCITY_MULTIPLIER = 0.25D;
    public static final double COBWEB_FALL_SPEED_MULTIPLIER = 0.05F;

    // Honey block
    public static final double HONEY_SLIDE_SPEED = 0.13D;
    public static final double HONEY_SLIDE_DOWN_SPEED = 0.05D;

    // Sweet berry bush
    public static final float SWEET_BERRY_MOVEMENT_MULTIPLIER = 0.8F;
    public static final double SWEET_BERRY_VELOCITY_MULTIPLIER = 0.75D;

    // Bubble column
    public static final double BUBBLE_COLUMN_UP_SPEED = 0.1D;
    public static final double BUBBLE_COLUMN_DOWN_SPEED = 0.03D;
    public static final double BUBBLE_COLUMN_SURFACE_DRAG = 0.03D;

    // ========== Sneaking Constants ==========
    public static final double SNEAK_EDGE_CHECK_INCREMENT = 0.05D;

    // ========== Levitation Effect ==========
    public static final double LEVITATION_SPEED = 0.05D;

    // ========== Push/Collision Constants ==========
    public static final double PUSH_VELOCITY = 0.1D;
    public static final double PUSH_OUT_BLOCKS_MULTIPLIER = 0.35D;
    public static final double COLLISION_VERTICAL_VELOCITY = 0.3F;

    // ========== Walking/Running Thresholds ==========
    public static final double WALKING_THRESHOLD = 0.8D;

    // ========== Math Constants ==========
    public static final float DEGREES_TO_RADIANS = 0.017453292F;
    public static final double BLOCK_SLIPPERINESS_OFFSET = 0.5000001D;
    public static final double LANDING_POSITION_OFFSET = 0.2F;

    // ========== Eye Height Calculations ==========
    public static final double EYE_HEIGHT_FLUID_OFFSET = 0.1111111119389534D;

    // ========== Hunger Constants ==========
    public static final float HUNGER_SPRINT_THRESHOLD = 6.0F;
}