package kaptainwutax.tungsten.path.blockSpaceSearchAssist.movement;

import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.movement.NeoMovementHelper;
import kaptainwutax.tungsten.helpers.movement.CornerJumpMovementHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

/**
 * Detects the type of movement between two nodes.
 * Analyzes node positions and world state to determine movement type.
 */
public class MovementTypeDetector {

    private static final double NEO_CHECK_DISTANCE = 4.0;
    private static final double LONG_JUMP_MIN_DISTANCE = 4.0;
    private static final double LONG_JUMP_MAX_DISTANCE = 6.0;

    /**
     * Detects the movement type between two nodes.
     *
     * @param world The world view
     * @param from The starting node
     * @param to The destination node
     * @return The detected movement type and associated data
     */
    public static DetectedMovement detect(WorldView world, BlockNode from, BlockNode to) {
        double distance = DistanceCalculator.getHorizontalEuclideanDistance(
            from.getPos(),
            to.getPos()
        );

        // Check for long jump
        if (isLongJump(from, to, distance)) {
            return new DetectedMovement(MovementType.LONG_JUMP, null);
        }

        // Check for neo movement
        if (shouldCheckNeo(from, to, distance)) {
            Direction neoDirection = detectNeoDirection(world, from, to);
            if (neoDirection != null) {
                return new DetectedMovement(MovementType.NEO, neoDirection);
            }
        }

        // Check for corner jump
        if (isCornerJump(world, from, to)) {
            return new DetectedMovement(MovementType.CORNER_JUMP, null);
        }

        // Check for climbing
        if (isClimbing(from, to)) {
            return new DetectedMovement(MovementType.CLIMBING, null);
        }

        // Check for falling
        if (isFalling(from, to)) {
            return new DetectedMovement(MovementType.FALLING, null);
        }

        // Default to normal movement
        return new DetectedMovement(MovementType.NORMAL, null);
    }

    /**
     * Checks if the movement is a long jump.
     *
     * @param from The starting node
     * @param to The destination node
     * @param distance The horizontal distance
     * @return true if it's a long jump
     */
    private static boolean isLongJump(BlockNode from, BlockNode to, double distance) {
        if (from.previous == null) {
            return false;
        }
        return distance >= LONG_JUMP_MIN_DISTANCE && distance < LONG_JUMP_MAX_DISTANCE;
    }

    /**
     * Checks if neo movement should be checked.
     *
     * @param from The starting node
     * @param to The destination node
     * @param distance The horizontal distance
     * @return true if neo should be checked
     */
    private static boolean shouldCheckNeo(BlockNode from, BlockNode to, double distance) {
        return from.getBlockPos().isWithinDistance(to.getBlockPos(), NEO_CHECK_DISTANCE);
    }

    /**
     * Detects the neo movement direction.
     *
     * @param world The world view
     * @param from The starting node
     * @param to The destination node
     * @return The neo direction or null if not neo
     */
    private static Direction detectNeoDirection(WorldView world, BlockNode from, BlockNode to) {
        return NeoMovementHelper.getNeoDirection(
            world,
            from.getBlockPos(),
            to.getBlockPos(),
            false, // Don't render
            false  // Don't slow down
        );
    }

    /**
     * Checks if the movement is a corner jump.
     *
     * @param world The world view
     * @param from The starting node
     * @param to The destination node
     * @return true if it's a corner jump
     */
    private static boolean isCornerJump(WorldView world, BlockNode from, BlockNode to) {
        return CornerJumpMovementHelper.isPossible(
            world,
            from.getBlockPos(),
            to.getBlockPos(),
            false, // Don't render
            false  // Don't slow down
        );
    }

    /**
     * Checks if the movement is climbing.
     *
     * @param from The starting node
     * @param to The destination node
     * @return true if climbing
     */
    private static boolean isClimbing(BlockNode from, BlockNode to) {
        // Check if on ladder and moving up
        return from.wasOnLadder && to.y > from.y;
    }

    /**
     * Checks if the movement is falling.
     *
     * @param from The starting node
     * @param to The destination node
     * @return true if falling
     */
    private static boolean isFalling(BlockNode from, BlockNode to) {
        // Falling more than 2 blocks
        return to.y - from.y < -2;
    }

    /**
     * Result of movement detection.
     */
    public static class DetectedMovement {
        public final MovementType type;
        public final Direction neoSide;

        public DetectedMovement(MovementType type, Direction neoSide) {
            this.type = type;
            this.neoSide = neoSide;
        }

        /**
         * Applies the detected movement to a node's movement state.
         *
         * @param node The node to update
         */
        public void applyTo(BlockNode node) {
            if (type == MovementType.NEO) {
                node.setNeoMovement(neoSide);
            } else if (type == MovementType.CORNER_JUMP) {
                node.setCornerJump();
            } else if (type == MovementType.LONG_JUMP) {
                // Long jump is detected based on distance, no special state needed
            }
        }
    }
}