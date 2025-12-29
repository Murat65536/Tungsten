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

        /** Base movement speed in blocks per tick */
        public static final float BASE_MOVEMENT_SPEED = 0.1f;
        /** Sprint speed multiplier */
        public static final float SPRINT_MULTIPLIER = 0.3f;
        /** Sneak/crouch speed multiplier */
        public static final float SNEAK_MULTIPLIER = 0.3f;
        /** Speed multiplier when using an item */
        public static final float USING_ITEM_MULTIPLIER = 0.2f;
        /** Movement input multiplier */
        public static final float MOVEMENT_INPUT_MULTIPLIER = 0.98f;
        /** Ground movement speed coefficient */
        public static final float GROUND_SPEED_COEFFICIENT = 0.21600002f;
        /** Sprint velocity in blocks per tick */
        public static final double SPRINT_VELOCITY = 5.8;
        /** Speed effect amplifier per level */
        public static final double SPEED_EFFECT_AMPLIFIER = 0.20000000298023224;
    }

    /**
     * Air movement constants for when the player is not on ground.
     */
    public static final class Air {
        private Air() {}

        /** Base air strafing speed */
        public static final float STRAFE_SPEED_BASE = 0.02f;
        /** Additional strafing speed when sprinting in air */
        public static final float STRAFE_SPEED_SPRINT_BONUS = 0.006f;
        /** Flying strafing speed (creative mode) */
        public static final float FLYING_STRAFE_SPEED = 0.025999999f;
    }

    /**
     * Water movement constants for swimming and underwater movement.
     */
    public static final class Water {
        private Water() {}

        /** Base swimming speed */
        public static final float SWIM_SPEED_BASE = 0.02f;
        /** Swimming drag coefficient when not sprinting */
        public static final float SWIM_DRAG = 0.8f;
        /** Swimming drag coefficient when sprinting */
        public static final float SWIM_SPRINT_DRAG = 0.9f;
        /** Drag coefficient with Dolphin's Grace effect */
        public static final float DOLPHIN_GRACE_DRAG = 0.96f;
        /** Speed bonus per level of Depth Strider enchantment */
        public static final float DEPTH_STRIDER_BONUS = 0.54600006f;
        /** Vertical descent speed in water */
        public static final float DIVE_SPEED = 0.04f;
        /** Eye height threshold for swimming */
        public static final double SWIMMING_EYE_HEIGHT_THRESHOLD = 0.4;
        /** Water jump increment velocity */
        public static final float WATER_JUMP_INCREMENT = 0.04f;
        /** Water Y velocity decay */
        public static final float WATER_Y_VELOCITY_DECAY = 0.8f;
        /** Maximum depth strider level */
        public static final float MAX_DEPTH_STRIDER_LEVEL = 3.0f;
        /** Depth strider off ground multiplier */
        public static final float DEPTH_STRIDER_OFF_GROUND_MULT = 0.5f;
        /** Depth strider divisor */
        public static final float DEPTH_STRIDER_DIVISOR = 3.0f;
    }

    /**
     * Jump mechanics constants.
     */
    public static final class Jump {
        private Jump() {}

        /** Base vertical jump velocity in blocks per tick */
        public static final float BASE_JUMP_VELOCITY = 0.42f;
        /** Additional jump velocity per level of Jump Boost effect */
        public static final float JUMP_BOOST_PER_LEVEL = 0.1f;
        /** Horizontal velocity boost when sprint-jumping */
        public static final double SPRINT_JUMP_HORIZONTAL_BOOST = 0.2;
    }

    /**
     * Elytra flight constants.
     */
    public static final class Elytra {
        private Elytra() {}

        /** Horizontal velocity decay when flying with elytra */
        public static final double HORIZONTAL_VELOCITY_DECAY = 0.9900000095367432;
        /** Vertical velocity decay when flying with elytra */
        public static final double VERTICAL_VELOCITY_DECAY = 0.9800000190734863;
        /** Elytra fall velocity threshold */
        public static final double FALL_VELOCITY_THRESHOLD = -0.5;
        /** Elytra gravity factor */
        public static final double GRAVITY_FACTOR = 0.75;
        /** Elytra vertical velocity factor */
        public static final double VERTICAL_VELOCITY_FACTOR = -0.1;
        /** Elytra pitch control factor */
        public static final double PITCH_CONTROL_FACTOR = 0.04;
        /** Elytra vertical boost multiplier */
        public static final double VERTICAL_BOOST_MULTIPLIER = 3.2;
        /** Elytra horizontal adjustment factor */
        public static final double HORIZONTAL_ADJUSTMENT_FACTOR = 0.1;
    }

    /**
     * Climbing constants (ladders, vines).
     */
    public static final class Climbing {
        private Climbing() {}

        /** Climbing velocity in blocks per tick */
        public static final double CLIMB_VELOCITY = 0.2;
        /** Maximum climbing speed */
        public static final double MAX_CLIMB_SPEED = 0.15000000596046448;
        /** Maximum ladder/vine movement distance */
        public static final double MAX_LADDER_DISTANCE = 6.3;
        /** Ladder horizontal movement limit */
        public static final double LADDER_HORIZONTAL_LIMIT = 2.3;
    }

    /**
     * Friction and slipperiness values.
     */
    public static final class Friction {
        private Friction() {}

        /** Default ground friction multiplier */
        public static final float GROUND_FRICTION = 0.91f;
    }

    /**
     * Fluid movement constants.
     */
    public static final class Fluid {
        private Fluid() {}

        /** Lava fluid multiplier in ultrawarm dimensions */
        public static final double LAVA_ULTRAWARM_MULTIPLIER = 0.007;
        /** Lava fluid multiplier in normal dimensions */
        public static final double LAVA_NORMAL_MULTIPLIER = 0.0023333333333333335;
        /** Lava movement speed */
        public static final float LAVA_MOVEMENT_SPEED = 0.02f;
        /** Lava velocity multiplier X/Z */
        public static final double LAVA_VELOCITY_MULT_XZ = 0.5;
        /** Lava velocity multiplier Y */
        public static final float LAVA_VELOCITY_MULT_Y = 0.8f;
        /** Submerged lava velocity multiplier */
        public static final double SUBMERGED_LAVA_VELOCITY_MULT = 0.5;
        /** Lava fall speed divisor */
        public static final double LAVA_FALL_SPEED_DIVISOR = 4.0;
        /** Fluid velocity threshold */
        public static final double FLUID_VELOCITY_THRESHOLD = 0.014;
    }

    /**
     * Special movement constants.
     */
    public static final class Special {
        private Special() {}

        /** Neo movement check distance */
        public static final double NEO_MOVEMENT_CHECK_DISTANCE = 4.2;
        /** Honey block slide threshold */
        public static final double HONEY_BLOCK_SLIDE_THRESHOLD = 0.9375;
        /** Honey block velocity cap */
        public static final double HONEY_BLOCK_VELOCITY_CAP = -0.05;
        /** Honey block slow threshold */
        public static final double HONEY_BLOCK_SLOW_THRESHOLD = -0.13;
        /** Honey block slide velocity threshold */
        public static final double HONEY_BLOCK_SLIDE_VELOCITY = -0.08;
        /** Cobweb movement multiplier X/Z */
        public static final double COBWEB_MOVEMENT_MULTIPLIER = 0.25;
        /** Cobweb movement multiplier Y */
        public static final float COBWEB_MOVEMENT_MULTIPLIER_Y = 0.05f;
        /** Sweet berry bush movement multiplier */
        public static final float SWEET_BERRY_MOVEMENT_MULTIPLIER = 0.8f;
        /** Sweet berry bush horizontal multiplier */
        public static final double SWEET_BERRY_HORIZONTAL_MULTIPLIER = 0.75;
        /** Bubble column drag down */
        public static final double BUBBLE_COLUMN_DRAG_DOWN = -0.9;
        /** Bubble column acceleration down */
        public static final double BUBBLE_COLUMN_ACCEL_DOWN = 0.03;
        /** Bubble column max speed down */
        public static final double BUBBLE_COLUMN_MAX_SPEED_DOWN = 1.8;
        /** Bubble column speed multiplier down */
        public static final double BUBBLE_COLUMN_SPEED_MULT_DOWN = 0.1;
        /** Bubble column drag up */
        public static final double BUBBLE_COLUMN_DRAG_UP = -0.3;
        /** Bubble column acceleration up */
        public static final double BUBBLE_COLUMN_ACCEL_UP = 0.03;
        /** Bubble column max speed-up */
        public static final double BUBBLE_COLUMN_MAX_SPEED_UP = 0.7;
        /** Bubble column speed multiplier up */
        public static final double BUBBLE_COLUMN_SPEED_MULT_UP = 0.06;
        /** Honey block position offset */
        public static final double HONEY_BLOCK_POSITION_OFFSET = 0.5;
        /** Honey block edge distance */
        public static final double HONEY_BLOCK_EDGE_DISTANCE = 0.4375;
    }
}