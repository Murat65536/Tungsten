package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.helpers.BlockStateChecker;
import net.minecraft.util.math.Direction.Axis;

/**
 * Validates movement based on block height differences.
 * Handles complex height-based movement restrictions.
 */
public class BlockHeightValidator implements NodeValidator {

    private static final double TALL_BLOCK_THRESHOLD = 1.3;
    private static final double SLAB_HEIGHT = 0.5;
    private static final double FULL_BLOCK_HEIGHT = 1.0;
    private static final double TOP_SLAB_HEIGHT = 1.5;
    private static final int MAX_FALL_HEIGHT = 2;
    private static final double CLOSED_TRAPDOOR_HEIGHT_2_DISTANCE = 4.4;
    private static final double SPECIAL_BLOCK_HEIGHT_DISTANCE = 7.4;
    private static final double HEIGHT_NEG1_MAX_DISTANCE = 5.4;
    private static final double BOTTOM_TRAPDOOR_DISTANCE = 6.4;

    @Override
    public boolean isValid(ValidationContext context) {
        double heightDiff = context.heightDiff();
        double distance = context.distance();
        double blockHeightDiff = context.getBlockHeightDiff();
        double fromBlockHeight = context.fromBlockHeight();
        double toBlockHeight = context.toBlockHeight();

        // Check for fall damage if enabled
        if (!context.ignoreFallDamage() &&
            !BlockStateChecker.isAnyWater(context.toState()) &&
            heightDiff < -MAX_FALL_HEIGHT) {
            return false;
        }

        // Check if destination block is too tall
        if (context.isToBlockTall(TALL_BLOCK_THRESHOLD) && heightDiff > 0) {
            return false;
        }

        // Bottom slab source with full block destination
        if (BlockStateChecker.isBottomSlab(context.fromBelowState()) &&
            toBlockHeight == FULL_BLOCK_HEIGHT &&
            heightDiff > 0) {
            return false;
        }

        // Handle trapdoor special cases
        if (BlockStateChecker.isClosedBottomTrapdoor(context.toBelowState())) {
            if (heightDiff <= 1 && distance <= BOTTOM_TRAPDOOR_DISTANCE) {
                return true;
            }
            if (heightDiff == 2 && distance <= CLOSED_TRAPDOOR_HEIGHT_2_DISTANCE) {
                return true;
            }
        }

        if (BlockStateChecker.isClosedBottomTrapdoor(context.fromBelowState())) {
            if (heightDiff <= 0 && distance <= BOTTOM_TRAPDOOR_DISTANCE) {
                return true;
            }
            if (heightDiff > 0 && BlockStateChecker.isTopSlab(context.toBelowState())) {
                return false;
            }
        }

        // Complex height difference validations
        if (!Double.isInfinite(blockHeightDiff) && !Double.isNaN(blockHeightDiff) && blockHeightDiff != 0) {

            var toShape = context.toBelowShape();

            // Medium height difference checks
            if (Math.abs(blockHeightDiff) > SLAB_HEIGHT &&
                Math.abs(blockHeightDiff) <= FULL_BLOCK_HEIGHT) {
                if (heightDiff > 0 &&
                    toShape.getMin(Axis.Y) == 0.0 &&
                    fromBlockHeight <= FULL_BLOCK_HEIGHT) {
                    return false;
                }
                if (heightDiff == 2 && distance <= 5.3) {
                    return true;
                }
                if (heightDiff >= 0 && distance <= 5.3) {
                    return true;
                }
            }

            // Small height difference with full block
            if (Math.abs(blockHeightDiff) <= SLAB_HEIGHT &&
                toShape.getMin(Axis.Y) == 0.0 &&
                toBlockHeight == FULL_BLOCK_HEIGHT) {
                if (heightDiff == 0 && distance <= HEIGHT_NEG1_MAX_DISTANCE) {
                    return true;
                }
            }

            // Small height difference with tall block
            if (Math.abs(blockHeightDiff) <= SLAB_HEIGHT &&
                (toShape.getMin(Axis.Y) == 0.0 || toBlockHeight > FULL_BLOCK_HEIGHT)) {
                if (blockHeightDiff == SLAB_HEIGHT && heightDiff <= -1) {
                    return false;
                }
                if (heightDiff == 0 && distance >= CLOSED_TRAPDOOR_HEIGHT_2_DISTANCE) {
                    return false;
                }
                if (heightDiff <= 1 && distance <= SPECIAL_BLOCK_HEIGHT_DISTANCE) {
                    return true;
                }
            }

            // Large height difference with special blocks
            if (Math.abs(blockHeightDiff) >= SLAB_HEIGHT &&
                (toShape.getMin(Axis.Y) == 0.0 || toBlockHeight > FULL_BLOCK_HEIGHT)) {
                return false;
            }
        }

        return true;
    }
}