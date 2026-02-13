package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import net.minecraft.block.BlockState;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Immutable context object containing all information needed for node validation.
 * This record encapsulates the state needed by validators to make decisions
 * about movement validity.
 */
public record ValidationContext(
    WorldView world,
    BlockNode from,
    BlockNode to,
    BlockState fromState,
    BlockState fromBelowState,
    BlockState toState,
    BlockState toBelowState,
    BlockState toAboveState,
    double heightDiff,
    double distance,
    VoxelShape fromBelowShape,
    VoxelShape toBelowShape,
    double fromBlockHeight,
    double toBlockHeight,
    boolean ignoreFallDamage,
    AtomicBoolean stop
) {
    /**
     * Factory method to create a validation context from two nodes.
     *
     * @param world The world view
     * @param from The starting node
     * @param to The destination node
     * @param ignoreFallDamage Whether fall damage checks should be skipped
     * @param stop Shared stop signal for cooperative cancellation
     * @return A new validation context
     */
    public static ValidationContext from(WorldView world, BlockNode from, BlockNode to,
                                         boolean ignoreFallDamage, AtomicBoolean stop) {
        BlockState fromState = world.getBlockState(from.getBlockPos());
        BlockState fromBelowState = world.getBlockState(from.getBlockPos().down());
        BlockState toState = world.getBlockState(to.getBlockPos());
        BlockState toBelowState = world.getBlockState(to.getBlockPos().down());
        BlockState toAboveState = world.getBlockState(to.getBlockPos().up());

        VoxelShape fromBelowShape = fromBelowState.getCollisionShape(world, from.getBlockPos().down());
        VoxelShape toBelowShape = toBelowState.getCollisionShape(world, to.getBlockPos().down());

        double fromBlockHeight = BlockShapeChecker.getBlockHeight(fromBelowShape);
        double toBlockHeight = BlockShapeChecker.getBlockHeight(toBelowShape);

        // Calculate height difference (positive = going up, negative = going down)
        int heightDiff = to.y - from.y;

        // Calculate horizontal distance
        double distance = DistanceCalculator.getHorizontalEuclideanDistance(
            from.getPos(true),
            to.getPos(true)
        );

        return new ValidationContext(
            world,
            from,
            to,
            fromState,
            fromBelowState,
            toState,
            toBelowState,
            toAboveState,
            heightDiff,
            distance,
            fromBelowShape,
            toBelowShape,
            fromBlockHeight,
            toBlockHeight,
            ignoreFallDamage,
            stop
        );
    }

    /**
     * Gets the block height difference between from and to positions.
     *
     * @return The block height difference (negative means fromBlockHeight is lower)
     */
    public double getBlockHeightDiff() {
        return fromBlockHeight - toBlockHeight;
    }

    /**
     * Checks if the destination block is tall (above threshold).
     *
     * @param threshold The height threshold
     * @return true if the block is taller than the threshold
     */
    public boolean isToBlockTall(double threshold) {
        double blockBelowHeight = BlockShapeChecker.getBlockHeight(to.getBlockPos().down());
        return blockBelowHeight > threshold;
    }
}