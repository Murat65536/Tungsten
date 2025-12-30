package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;

/**
 * Validates movement through water blocks.
 * Ensures proper water physics and movement restrictions.
 */
public class WaterMovementValidator implements NodeValidator {

    private static final double MAX_WATER_DISTANCE = 1.0;
    private static final double MAX_WATER_HEIGHT_JUMP = 2.0;

    @Override
    public boolean isValid(ValidationContext context) {
        boolean fromInWater = BlockStateChecker.isAnyWater(context.fromState());
        boolean toInWater = BlockStateChecker.isAnyWater(context.toState());

        // Check water entry/exit conditions
        if (toInWater) {
            // Can't move too far horizontally in water
            if (context.distance() > MAX_WATER_DISTANCE) {
                return false;
            }
            // Can't jump too high into water
            if (context.heightDiff() > MAX_WATER_DISTANCE) {
                return false;
            }
            // Can't enter water if block above is solid
            if (!context.toAboveState().isAir()) {
                return false;
            }
            // Must have clear path
            return BlockNode.wasCleared(context.world(),
                context.from().getBlockPos(),
                context.to().getBlockPos());
        }

        // Moving from water
        if (fromInWater) {
            // Limited horizontal movement from water
            return context.distance() < MAX_WATER_HEIGHT_JUMP;
        }

        return true;
    }
}