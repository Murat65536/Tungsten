package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.constants.physics.MovementConstants;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import net.minecraft.block.Block;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.VineBlock;

/**
 * Validates movement on ladders and vines.
 * Handles climbing mechanics and distance restrictions.
 */
public class LadderMovementValidator implements NodeValidator {

    private static final double MAX_LADDER_DISTANCE = 5.3;
    private static final double LADDER_HORIZONTAL_LIMIT = 1.0;

    @Override
    public boolean isValid(ValidationContext context) {
        Block fromBlock = context.fromState().getBlock();
        Block toBlock = context.toState().getBlock();
        Block toBelowBlock = context.toBelowState().getBlock();

        boolean isFromLadder = fromBlock instanceof LadderBlock;
        boolean isToLadder = toBlock instanceof LadderBlock;
        boolean isToBelowLadder = toBelowBlock instanceof LadderBlock;
        boolean isToVine = toBlock instanceof VineBlock;
        boolean isToBelowVine = toBelowBlock instanceof VineBlock;

        // Vine movement validation
        if ((isToBelowVine || isToVine) && context.heightDiff() >= -1) {
            if (context.distance() < MovementConstants.Climbing.LADDER_HORIZONTAL_LIMIT && context.heightDiff() == 0) {
                return true;
            }
            if (context.distance() < MAX_LADDER_DISTANCE) {
                return BlockNode.wasCleared(context.world(),
                    context.from().getBlockPos(),
                    context.to().getBlockPos(),
                    null,
                    null,
                    false,  // shouldRender
                    false   // shouldSlow
                );
            }
            return false;
        }

        // Ladder specific checks
        if (isToLadder || isToBelowLadder) {
            // Can't climb more than 1 block up on ladder
            if (context.heightDiff() > 1) {
                return false;
            }

            // Special case for bottom slabs
            if (BlockStateChecker.isBottomSlab(context.fromBelowState()) && context.heightDiff() > 0) {
                return false;
            }

            // Distance check from ladder
            if (isFromLadder && context.distance() > MovementConstants.Climbing.LADDER_HORIZONTAL_LIMIT) {
                return false;
            }

            // Max ladder distance check
            if (context.distance() > MAX_LADDER_DISTANCE) {
                return false;
            }

            // Ladder below must have air or ladder above
            if (isToBelowLadder && !(context.toState().isAir() || isToLadder)) {
                return false;
            }

            // Close ladder movement
            if (context.distance() < 1 && context.heightDiff() >= -1) {
                return true;
            }

            // Normal ladder movement with path check
            if (context.distance() < MAX_LADDER_DISTANCE && context.heightDiff() >= -1) {
                return BlockNode.wasCleared(context.world(),
                    context.from().getBlockPos(),
                    context.to().getBlockPos(),
                    context.from(),
                    context.to(),
                    false,  // shouldRender
                    false   // shouldSlow
                );
            }
        }

        return true;
    }
}