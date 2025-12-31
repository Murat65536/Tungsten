package kaptainwutax.tungsten.constants.pathfinding;

public final class BlockSpacePathfindingConstants {
    private BlockSpacePathfindingConstants() {
    }

    /**
     * Primary pathfinding timeout in milliseconds
     */
    public static final long PRIMARY_TIMEOUT_MS = 1800L;

    /**
     * Time between checks to see if a valid path was found in milliseconds
     */
    public static final int TIME_CHECK_INTERVAL = 64;

    // TODO Is this really needed?
    /**
     * Minimum length of a path to be considered
     */
    public static final double MIN_PATH_LENGTH = 5.0;

    public static final class Heuristics {
        private Heuristics() {}

        /**
         * Value to multiply x and z heuristics by
         */
        public static final double XZ_MULTIPLIER = 1.2;

        /**
         * Value to multiply y heuristics by when in water
         */
        public static final double Y_MULTIPLIER_WATER = 3.5;
    }
}
