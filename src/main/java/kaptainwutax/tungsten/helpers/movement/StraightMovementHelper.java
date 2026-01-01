package kaptainwutax.tungsten.helpers.movement;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.MovementHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

public class StraightMovementHelper {

    public static boolean isPossible(WorldView world, BlockPos startPos, BlockPos endPos) {
        return isPossible(world, startPos, endPos, false, false);
    }

    public static boolean isPossible(WorldView world, BlockPos startPos, BlockPos endPos, boolean shouldRender, boolean shouldSlow) {

        boolean isJumpingUp = endPos.getY() - startPos.getY() == 1;

        int dx = startPos.getX() - endPos.getX();
        int dz = startPos.getZ() - endPos.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        boolean isJumpingOneBlock = distance == 1;
        PathNavigator navigator = new PathNavigator(world, isJumpingUp, isJumpingOneBlock, shouldRender, shouldSlow);

        return navigator.traversePath(startPos, endPos);
    }

    private record PathNavigator(WorldView world, boolean isJumpingUp, boolean isJumpingOneBlock, boolean shouldRender,
                                 boolean shouldSlow) {

        public boolean traversePath(BlockPos startPos, BlockPos endPos) {
            int x = startPos.getX();
            int y = startPos.getY();
            int z = startPos.getZ();
            int endX = endPos.getX();
            int endY = endPos.getY();
            int endZ = endPos.getZ();

            BlockPos.Mutable currPos = new BlockPos.Mutable();

            boolean isOneBlockAway = DistanceCalculator.getHorizontalEuclideanDistance(startPos, endPos) <= 1;

            if (isOneBlockAway) {
                Direction dir = DirectionHelper.getHorizontalDirectionFromPos(startPos, endPos);
                int offsetX = dir.getOffsetX();
                int offsetZ = dir.getOffsetZ();

                currPos.set(x, y + 2, z);

                if (!processStep(currPos)) {
                    currPos.set(x + offsetX, y, z + offsetZ);
                    if (!processStep(currPos)) {
                        return false; // Path obstructed
                    }
                }

            }

            while (x != endX || y != endY || z != endZ) {
                if (TungstenMod.PATHFINDER.stop.get()) return false;

                currPos.set(x, y, z);

                if (!processStep(currPos)) {
                    return false; // Path obstructed
                }

                z = moveCoordinate(z, endZ);


                currPos.set(x, y, z);

                if (!processStep(currPos)) {
                    return false; // Path obstructed
                }

                x = moveCoordinate(x, endX);

                currPos.set(x, y, z);

                if (!processStep(currPos)) {
                    return false; // Path obstructed
                }

                y = moveCoordinate(y, endY);

            }
            if (shouldSlow) {
                try {
                    Thread.sleep(450);
                } catch (InterruptedException ignored) {}
            }
            return true; // Successfully navigated the path
        }

        private boolean processStep(BlockPos.Mutable position) {
            if (MovementHelper.isObscured(world, position, isJumpingUp, isJumpingOneBlock)) {
                if (shouldSlow) {
                    try {
                        Thread.sleep(450);
                    } catch (InterruptedException ignored) {}
                }
                return false;
            } else {
                return true;
            }
        }

        private int moveCoordinate(int current, int target) {
            if (current < target) return current + 1;
            if (current > target) return current - 1;
            return current;
        }
    }
}
