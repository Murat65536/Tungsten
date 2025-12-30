package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.helpers.BlockStateChecker;
import net.minecraft.block.SlabBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.SlabType;

/**
 * Validates movement involving slabs.
 * Handles special slab physics and height differences.
 */
public class SlabMovementValidator implements NodeValidator {

    private static final double SLAB_HEIGHT = 0.5;
    private static final double TOP_SLAB_HEIGHT = 1.5;
    private static final double SLAB_JUMP_LIMIT = 4.5;

    @Override
    public boolean isValid(ValidationContext context) {
        // Check if destination is a bottom slab
        if (BlockStateChecker.isBottomSlab(context.toState())) {
            return false; // Can't land on bottom slabs directly
        }

        // Check for double slabs
        if (BlockStateChecker.isDoubleSlab(context.world(), context.from().getBlockPos())) {
            return false;
        }

        // Check if standing on bottom slab trying to move
        if (BlockStateChecker.isBottomSlab(context.fromBelowState())) {
            // Limited jump height from bottom slab
            if (context.toBlockHeight() == 1.0 && context.heightDiff() > 0) {
                return false;
            }
        }

        // Check top slab restrictions
        if (context.toState().getBlock() instanceof SlabBlock &&
            context.toState().get(Properties.SLAB_TYPE) == SlabType.TOP &&
            !context.world().getBlockState(context.to().getBlockPos()).isAir()) {
            return false;
        }

        // Slab-to-slab movement
        if (context.toBlockHeight() == TOP_SLAB_HEIGHT &&
            context.fromBlockHeight() == TOP_SLAB_HEIGHT &&
            context.heightDiff() <= 1) {
            return context.distance() <= 4.0; // Use fence distance constant
        }

        // Moving onto slab with height difference
        if (BlockStateChecker.isBottomSlab(context.toBelowState())) {
            // Can't land on bottom slab if it's not air above
            if (!context.toState().isAir()) {
                return false;
            }

            // Distance check for slab landings
            if (context.heightDiff() <= 0 &&
                (BlockStateChecker.isBottomSlab(context.toBelowState()) ||
                 (!context.from().wasOnLadder && context.toState().getBlock() instanceof net.minecraft.block.LadderBlock)) &&
                context.distance() >= SLAB_JUMP_LIMIT) {
                return false;
            }
        }

        // Bottom slab height checks
        if (context.fromBlockHeight() <= SLAB_HEIGHT &&
            context.heightDiff() > 0 &&
            context.toBlockHeight() > SLAB_HEIGHT) {
            return false;
        }

        return true;
    }
}