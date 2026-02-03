package kaptainwutax.tungsten.constants.game;

/**
 * Constants related to general game mechanics.
 */
public final class MechanicsConstants {

    private MechanicsConstants() {}

    /**
     * Angle and rotation constants.
     * All angle values are in degrees unless specified otherwise.
     */
    public static final class Angles {
        private Angles() {}

        /** Degrees-to-radians conversion factor */
        public static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    }

    /**
     * Fall damage constants.
     */
    public static final class FallDamage {
        private FallDamage() {}

        /** Fall damage threshold in blocks */
        public static final float FALL_DAMAGE_THRESHOLD = 3.0f;
        /** Hay block fall damage multiplier */
        public static final float HAY_BLOCK_FALL_MULTIPLIER = 0.2f;
        /** Bed fall damage multiplier */
        public static final float BED_FALL_MULTIPLIER = 0.66f;
        /** Default fall damage multiplier */
        public static final float DEFAULT_FALL_MULTIPLIER = 1.0f;
    }

    /**
     * Food and hunger constants.
     */
    public static final class Hunger {
        private Hunger() {}

        /** Hunger level required for sprinting */
        public static final int SPRINT_HUNGER_REQUIREMENT = 6;
    }
}