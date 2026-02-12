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
     * Pathfinding coefficients for heuristic calculations.
     */
    public static final class Coefficients {
        private Coefficients() {}

        /** Array of pathfinding coefficients for different scenarios */
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
}
