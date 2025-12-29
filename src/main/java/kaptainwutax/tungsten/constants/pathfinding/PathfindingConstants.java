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
    }

    /**
     * Node evaluation and iteration parameters.
     */
    public static final class NodeEvaluation {
        private NodeEvaluation() {}

        /** Interval for time checks (power of 2 for efficient bitwise operations) */
        public static final int TIME_CHECK_INTERVAL = 8;
        /** Interval for rendering node updates */
        public static final int NODE_RENDER_INTERVAL = 20;
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
     */
    public static final class ClosedSetScale {
        private ClosedSetScale() {}

        /** Precise movement scaling */
        public static final class Precise {
            private Precise() {}
            public static final double X = 1000.0;
            public static final double Y = 1000.0;
            public static final double Z = 1000.0;
        }

        /** Standard movement scaling */
        public static final class Standard {
            private Standard() {}
            public static final double X = 100.0;
            public static final double Y = 10.0;
            public static final double Z = 100.0;
        }

        /** Long jump movement scaling */
        public static final class LongJump {
            private LongJump() {}
            public static final double X = 10.0;
            public static final double Y = 100.0;
            public static final double Z = 10.0;
        }

        /** Climbing movement scaling */
        public static final class Climbing {
            private Climbing() {}
            public static final double X = 1.0;
            public static final double Y = 10000.0;
            public static final double Z = 1.0;
        }

        /** Water movement scaling */
        public static final class Water {
            private Water() {}
            public static final double X = 1000.0;
            public static final double Y = 100.0;
            public static final double Z = 1000.0;
        }
    }
}