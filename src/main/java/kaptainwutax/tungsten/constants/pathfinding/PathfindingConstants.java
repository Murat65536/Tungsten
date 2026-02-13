package kaptainwutax.tungsten.constants.pathfinding;

/**
 * Constants related to pathfinding algorithms, timeouts, thresholds, and node evaluation.
 */
public final class PathfindingConstants {

    private PathfindingConstants() {}

    /**
     * Timeout values for pathfinding operations.
     */
    public static final class Timeouts {
        private Timeouts() {}

        /** Primary pathfinding timeout in milliseconds */
        public static final long PRIMARY_TIMEOUT_MS = 250L;

        /** Timeout for node filtering operations in milliseconds */
        public static final long NODE_FILTER_TIMEOUT_MS = 100L;

        /** Timeout for node update operations in milliseconds */
        public static final long NODE_UPDATE_TIMEOUT_MS = 200L;
    }

    /**
     * Thread pool configuration for pathfinding operations.
     */
    public static final class ThreadPool {
        private ThreadPool() {}

        /** Enable work-stealing for better load distribution */
        public static final boolean WORK_STEALING_ENABLED = true;
    }

    /**
     * Node evaluation and iteration parameters.
     */
    public static final class NodeEvaluation {
        private NodeEvaluation() {}

        /** Interval for time checks (power of 2 for efficient bitwise operations) */
        public static final int TIME_CHECK_INTERVAL = 8;
        /** Minimum improvement required to continue pathfinding */
        public static final double MINIMUM_IMPROVEMENT = 0.21;
        /** Thread priority for pathfinding */
        public static final int THREAD_PRIORITY = 4;
    }

    /**
     * Pathfinding coefficients for Anytime Weighted A* (AWA*).
     * <p>
     * Each coefficient is a weight factor applied to the heuristic during search.
     * Lower coefficients (e.g., 1.5) produce near-optimal paths; higher ones (e.g., 10.0)
     * explore more greedily and find "good enough" paths quickly.
     * <p>
     * The pathfinder tracks the best path found at each weight level via {@code bestSoFar},
     * enabling it to return a usable path on timeout while continuing to search for
     * better solutions at lower weights.
     */
    public static final class Coefficients {
        private Coefficients() {}

        /** Weight factors for AWA* — lower = more optimal, higher = faster convergence */
        public static final double[] PATHFINDING_COEFFICIENTS = {1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0};
    }

    /**
     * Scaling factors for a closed set in different movement types.
     * Unified scaling approach: All movement types use BASE_SCALE for complete consistency.
     */
    public static final class ClosedSetScale {
        private ClosedSetScale() {}

        /** Position rounding factor */
        public static final double POSITION_ROUNDING = 10.0;

        /** Velocity rounding factor */
        public static final double VELOCITY_ROUNDING = 10.0;
    }

    /**
     * Greedy pathfinder with backtracking parameters.
     */
    public static final class Greedy {
        private Greedy() {}

        /** Maximum number of ticks (depth) to simulate before declaring failure */
        public static final int MAX_DEPTH = 50_000;

        /** Maximum number of backtracks before declaring failure */
        public static final int MAX_BACKTRACKS = 100_000;
    }

    /**
     * Proximity thresholds for advancing the block-path waypoint index.
     * Used in updateNextClosestBlockNodeIDX to determine when the agent is close
     * enough to the current waypoint to advance to the next one.
     */
    public static final class WaypointAdvance {
        private WaypointAdvance() {}

        /** Segment length above which "long distance" thresholds apply */
        public static final double LONG_DISTANCE_THRESHOLD = 7.0;

        // Horizontal / vertical range for initial proximity check
        public static final double LONG_DIST_XZ_RANGE = 2.80;
        public static final double LONG_DIST_Y_RANGE = 1.20;
        public static final double SHORT_DIST_XZ_RANGE = 1.10;
        public static final double SHORT_DIST_Y_RANGE = 0.80;

        // Per-block-type proximity thresholds
        public static final double WATER_XZ_RANGE = 0.9;
        public static final double WATER_Y_RANGE = 1.2;
        public static final double LADDER_CLOSE_XZ = 0.4;
        public static final double LADDER_CLOSE_Y = 0.9;
        public static final double LADDER_CLIMB_XZ = 0.7;
        public static final double LADDER_CLIMB_Y = 3.7;
        public static final double TALL_BLOCK_XZ = 0.4;
        public static final double TALL_BLOCK_Y = 0.58;
        public static final double TALL_BLOCK_HEIGHT = 1.3;
        public static final double BOTTOM_SLAB_DIST = 0.90;
        public static final double TRAPDOOR_XZ = 0.88;
        public static final double TRAPDOOR_Y = 2.2;
        public static final double STANDARD_SHORT_DIST = 1.05;
        public static final double STANDARD_LONG_DIST = 1.80;
        public static final double GLASS_PANE_DIST = 0.5;
        public static final double SMALL_BLOCK_DIST = 0.7;
        public static final double STANDARD_HEIGHT_DIFF = 1.6;
        public static final int BOTTOM_SLAB_HEIGHT_DIFF = 2;

        /** Number of parent nodes to check for approach consistency */
        public static final int PARENT_CHECK_DEPTH = 4;
    }
}
