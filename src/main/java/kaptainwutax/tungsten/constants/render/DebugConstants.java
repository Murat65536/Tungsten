package kaptainwutax.tungsten.constants.render;

/**
 * Constants related to debug rendering, visualization, and debugging tools.
 */
public final class DebugConstants {

    // Prevent instantiation
    private DebugConstants() {}

    /**
     * Debug sleep delays in milliseconds.
     * Used for visualization and stepping through algorithms.
     */
    public static final class SleepDelays {
        private SleepDelays() {}

        /** Very short debug delay */
        public static final long DELAY_VERY_SHORT_MS = 50L;
        /** Short debug delay */
        public static final long DELAY_SHORT_MS = 150L;
        /** Medium debug delay */
        public static final long DELAY_MEDIUM_MS = 200L;
        /** Long debug delay */
        public static final long DELAY_LONG_MS = 250L;
        /** Very long debug delay */
        public static final long DELAY_VERY_LONG_MS = 450L;
        /** Maximum debug delay */
        public static final long DELAY_MAX_MS = 500L;
    }

    /**
     * Debug render settings.
     */
    public static final class RenderSettings {
        private RenderSettings() {}

        /** Update interval for debug rendering in ticks */
        public static final int RENDER_UPDATE_INTERVAL = 20;
        /** Maximum nodes to render at once */
        public static final int MAX_RENDERED_NODES = 1000;
        /** Debug line thickness */
        public static final float LINE_THICKNESS = 2.0f;
        /** Debug box alpha transparency */
        public static final float BOX_ALPHA = 0.5f;
    }

    /**
     * Debug visualization dimensions.
     */
    public static final class Dimensions {
        private Dimensions() {}

        /** Default cube size for debug rendering */
        public static final double CUBE_SIZE = 1.0;
        /** Small cube size for node rendering */
        public static final double CUBE_SIZE_SMALL = 0.2;
        /** Large cube size for target rendering */
        public static final double CUBE_SIZE_LARGE = 1.5;
        /** Line offset to prevent z-fighting */
        public static final double LINE_OFFSET = 0.01;
    }

    /**
     * Debug color components (RGB values 0-255).
     * These are commonly used debug colors.
     */
    public static final class Colors {
        private Colors() {}

        /** Path color - green */
        public static final int PATH_R = 0;
        public static final int PATH_G = 255;
        public static final int PATH_B = 0;

        /** Target color - red */
        public static final int TARGET_R = 255;
        public static final int TARGET_G = 0;
        public static final int TARGET_B = 0;

        /** Collision color - yellow */
        public static final int COLLISION_R = 255;
        public static final int COLLISION_G = 255;
        public static final int COLLISION_B = 0;

        /** Water color - blue */
        public static final int WATER_R = 0;
        public static final int WATER_G = 100;
        public static final int WATER_B = 255;
    }

    /**
     * Debug logging and output settings.
     */
    public static final class Logging {
        private Logging() {}

        /** Enable verbose pathfinding logging */
        public static final boolean VERBOSE_PATHFINDING = false;
        /** Enable collision detection logging */
        public static final boolean VERBOSE_COLLISION = false;
        /** Maximum log entries before rotation */
        public static final int MAX_LOG_ENTRIES = 10000;
    }
}