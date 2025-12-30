package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.helpers.BlockStateChecker;

/**
 * Validates jump distances based on height differences.
 * Ensures movements respect Minecraft physics for jumping.
 */
public class JumpDistanceValidator implements NodeValidator {

    // Distance constants for different jump heights
    private static final double MAX_HORIZONTAL_DISTANCE = 7.0;
    private static final double SPRINT_JUMP_DISTANCE = 6.0;
    private static final double STANDARD_JUMP_DISTANCE = 5.3;
    private static final double HEIGHT_0_MAX_DISTANCE = 5.3;
    private static final double HEIGHT_1_MAX_DISTANCE_NORMAL = 4.5;
    private static final double HEIGHT_1_MAX_DISTANCE_SPRINT = 6.0;
    private static final double HEIGHT_NEG2_MAX_DISTANCE = 7.0;
    private static final double HEIGHT_NEG3_MAX_DISTANCE = 6.1;
    private static final double HEIGHT_NEG3_EXACT_DISTANCE = 6.3;
    private static final double TRAPDOOR_HEIGHT_1_DISTANCE = 5.0;
    private static final double OPEN_TRAPDOOR_HEIGHT_NEG2_DISTANCE = 6.0;
    private static final int MAX_JUMP_HEIGHT = 2;

    @Override
    public boolean isValid(ValidationContext context) {
        double heightDiff = context.heightDiff();
        double distance = context.distance();

        // Overall distance limit
        if (distance >= MAX_HORIZONTAL_DISTANCE) {
            return false;
        }

        // Can't jump higher than max jump height
        if (heightDiff >= MAX_JUMP_HEIGHT) {
            return false;
        }

        // Check slime bounce conditions
        boolean wasOnSlime = context.from().wasOnSlime;
        boolean wasBouncing = context.from().previous != null &&
                            context.from().previous.y - context.from().y < 0;

        if (!wasOnSlime || !wasBouncing) {
            // Normal jump distance checks

            // Height 1 jump checks
            if (heightDiff == 1) {
                if (distance > HEIGHT_1_MAX_DISTANCE_SPRINT) {
                    return false;
                }
                if (distance >= HEIGHT_1_MAX_DISTANCE_NORMAL) {
                    return false;
                }

                // Special trapdoor check
                if (BlockStateChecker.isOpenTrapdoor(context.toBelowState()) &&
                    distance > TRAPDOOR_HEIGHT_1_DISTANCE) {
                    return false;
                }
            }

            // Level jump check
            if (heightDiff == 0 && distance >= HEIGHT_0_MAX_DISTANCE) {
                return false;
            }

            // Falling checks
            if (heightDiff <= -2) {
                if (distance > HEIGHT_NEG2_MAX_DISTANCE) {
                    return false;
                }

                // Special trapdoor check for falling
                if (BlockStateChecker.isOpenTrapdoor(context.toBelowState()) &&
                    distance > OPEN_TRAPDOOR_HEIGHT_NEG2_DISTANCE) {
                    return false;
                }
            }

            // Height -3 check
            if (heightDiff >= -3 && distance >= HEIGHT_NEG3_MAX_DISTANCE) {
                return false;
            }

            // Exact height check for large falls
            if (heightDiff < -2 && distance >= HEIGHT_NEG3_EXACT_DISTANCE) {
                return false;
            }
        }

        // General sprint jump check
        if (heightDiff > -1 && distance >= SPRINT_JUMP_DISTANCE) {
            return false;
        }

        return true;
    }
}