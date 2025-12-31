package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import net.minecraft.block.*;

/**
 * Validates collision and block shape requirements.
 * Ensures players can stand on and move through blocks properly.
 */
public class CollisionValidator implements NodeValidator {

    @Override
    public boolean isValid(ValidationContext context) {
        BlockState toState = context.toState();
        BlockState toBelowState = context.toBelowState();
        Block toBlock = toState.getBlock();
        Block toBelowBlock = toBelowState.getBlock();

        // Check if current position is valid for standing
        if (context.from().previous != null) {
            BlockState fromState = context.fromState();
            if (fromState.isFullCube(context.world(), context.from().getBlockPos()) ||
                (!(fromState.getBlock() instanceof LadderBlock) &&
                 context.world().getBlockState(context.from().getBlockPos().down()).isAir())) {
                return false;
            }
        }

        // Can't move into lava
        if (toState.isOf(Blocks.LAVA)) {
            return false;
        }

        // Check for air below (must have something to stand on unless it's a ladder)
        if (toBelowState.isAir() && !(toBlock instanceof LadderBlock)) {
            if (!(toBlock instanceof SlabBlock)) {
                return false;
            }
        }

        // Check collision shape validity
        boolean canStandOn = BlockShapeChecker.hasBiggerCollisionShapeThanAbove(
            context.world(),
            context.to().getBlockPos().down()
        );

        if (!canStandOn &&
            !(toBlock instanceof DaylightDetectorBlock) &&
            !(toBlock instanceof CarpetBlock) &&
            !(toBelowBlock instanceof SlabBlock) &&
            !(toBelowBlock instanceof LanternBlock) &&
            !(toBelowBlock == Blocks.SNOW)) {
            return false;
        }

        // Specific block type restrictions
        if (toBelowBlock instanceof LilyPadBlock ||
            toBelowBlock instanceof CarpetBlock ||
            toBelowBlock instanceof DaylightDetectorBlock ||
            toBlock instanceof StairsBlock ||
            toBelowBlock instanceof SeaPickleBlock ||
            toBelowBlock instanceof CropBlock) {
            return false;
        }

        // Bottom slab with non-air above
        if (BlockStateChecker.isBottomSlab(toBelowState) && !(toBlock instanceof AirBlock)) {
            return false;
        }

        // Final path clearance check
        return BlockNode.wasCleared(
            context.world(),
            context.from().getBlockPos(),
            context.to().getBlockPos(),
            context.from(),
            context.to()
        );
    }
}