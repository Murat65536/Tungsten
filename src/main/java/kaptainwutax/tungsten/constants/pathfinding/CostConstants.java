package kaptainwutax.tungsten.constants.pathfinding;

/**
 * Constants related to action costs and penalties.
 */
public final class CostConstants {

    private CostConstants() {}

    /**
     * Base movement costs.
     */
    public static final class BaseCosts {
        private BaseCosts() {}

        /** Cost representing infinity (impossible action) */
        public static final double COST_INFINITY = 1_000_000.0;
        /** Cost to walk one block horizontally (20 ticks / 4.317 blocks per second) */
        public static final double WALK_ONE_BLOCK_COST = 20.0 / 4.317; // ~4.633
    }

    /**
     * Movement penalties for different conditions.
     */
    public static final class Penalties {
        private Penalties() {}

        /** Penalty for horizontal collision */
        public static final double HORIZONTAL_COLLISION_PENALTY = 25.0;
        /** Penalty for moving through water */
        public static final double COBWEB_PENALTY = 20000.0;
    }

    /**
     * Movement bonuses (negative costs) for favorable conditions.
     */
    public static final class Bonuses {
        private Bonuses() {}

        /** Bonus for climbing (ladders, vines) */
        public static final double CLIMBING_BONUS = -2.0;
        /** Bonus for moving through water when swimming */
        public static final double WATER_BONUS = -20.0;
    }

    /**
     * Heuristic multipliers for pathfinding estimation.
     */
    public static final class Heuristics {
        private Heuristics() {}

        /** Block path distance weight */
        public static final double BLOCK_PATH_DISTANCE_WEIGHT = 40.0;
    }
}