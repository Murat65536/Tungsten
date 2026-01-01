package kaptainwutax.tungsten.constants;

import kaptainwutax.tungsten.path.PathInput;

/**
 * Pathfinding algorithm constants used by the PathFinder and Node classes.
 * These values control heuristics, thresholds, and optimization parameters.
 */
public final class PathfindingConstants {

    private PathfindingConstants() {} // Prevent instantiation

    // Heuristic Coefficients
    public static final double[] HEURISTIC_COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};

    // Path Optimization
    public static final double MINIMUM_IMPROVEMENT = 0.21;
    public static final double MIN_DISTANCE_PATH = 5;
    public static final double MIN_VELOCITY = 0.2;
    public static final double TARGET_REACHED_DISTANCE_SQUARED = 0.4D;

    // Position Rounding
    public static final double POSITION_ROUNDING_FACTOR = 1000;
    public static final double POSITION_COARSE_ROUNDING = 10;

    // Rendering Limits
    public static final int MAX_RENDERERS = 9000;

    // Path Cost Calculations
    public static final double CLOSE_DISTANCE_THRESHOLD = 2.0;
    public static final double HEURISTIC_MULTIPLIER = 33.563;
    public static final int UNIFORM_COST_PER_STEP = 1;


    // Node Generation
    public static final float YAW_INCREMENT = 22.5F;
    public static final float YAW_MIN = -180.0F;
    public static final float YAW_MAX = 180.0F;

    // Rendering
    public static final double RENDER_CUBE_SIZE_SMALL = 0.05D;
    public static final double RENDER_CUBE_SIZE_DEFAULT = 0.1D;

    // Binary Heap
    public static final int INITIAL_HEAP_CAPACITY = 1024;

    // Inputs

    /** Number of yaw directions to consider. Higher values are more accurate but slower. Each increment doubles the number of considered values. */
    public static final int YAW_DIRECTION_MAGNITUDE = 4;
    public static final PathInput[] ALL_INPUTS = new PathInput[42 << YAW_DIRECTION_MAGNITUDE]; // Pre-computed size

    static {
        int index = 0;
        for (boolean jump : new boolean[] {false, true}) {
            for (boolean sprint : new boolean[]{false, true}) {
                for (boolean sneak : new boolean[]{false, true}) {
                    if (!(sprint && sneak)) {
                        for (boolean forward : new boolean[]{false, true}) {
                            if (!(sprint && !forward)) {
                                for (boolean back : new boolean[]{false, true}) {
                                    if (!(forward && back)) {
                                        for (boolean left : new boolean[]{false, true}) {
                                            for (boolean right : new boolean[]{false, true}) {
                                                if (!(left && right)) {
                                                    for (float yaw = -180f; yaw < 180f; yaw += 360f / (1 << YAW_DIRECTION_MAGNITUDE)) {
                                                        ALL_INPUTS[index++] = new PathInput(forward, back, right, left, jump, sneak, sprint, 90, yaw);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}