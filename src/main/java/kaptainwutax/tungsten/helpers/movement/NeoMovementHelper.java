package kaptainwutax.tungsten.helpers.movement;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.helpers.MovementHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

public class NeoMovementHelper {

	/**
	 * Checks if neo is possible.
	 * 
	 * @param world
	 * @param startPos
	 * @param endPos
	 * @param shouldRender
	 * @param shouldSlow
	 * @return Horizontal direction in which neo is possible and null otherwise
	 */
	public static Direction getNeoDirection(WorldView world, BlockPos startPos, BlockPos endPos, boolean shouldRender, boolean shouldSlow) {

		boolean isMovingOnXAxis = startPos.getX() - endPos.getX() == 0;
		boolean isMovingOnZAxis = startPos.getZ() - endPos.getZ() == 0;
		boolean isJumpingUp = endPos.getY() - startPos.getY() == 1;
		
		if (isMovingOnXAxis) {
			return getNeoDirection(world, Direction.Axis.X, startPos, endPos, isJumpingUp, false, shouldRender,
					shouldSlow);
		} else if (isMovingOnZAxis) {
			return getNeoDirection(world, Direction.Axis.Z, startPos, endPos, isJumpingUp, false, shouldRender,
					shouldSlow);
		}

		return null;
	}
	
	/**
	 * Checks if neo is possible.
	 * 
	 * @param world
	 * @param isMovingOnXAxis
	 * @param isMovingOnZAxis
	 * @param startPos
	 * @param endPos
	 * @param isJumpingOneBlock
	 * @param shouldRender
	 * @param shouldSlow
	 * @return Horizontal direction in which neo is possible and null otherwise
	 */
	public static Direction getNeoDirection(WorldView world, boolean isMovingOnXAxis, boolean isMovingOnZAxis,
			BlockPos startPos, BlockPos endPos, boolean isJumpingUp, boolean isJumpingOneBlock, boolean shouldRender, boolean shouldSlow) {

		if (isMovingOnXAxis) {
			return getNeoDirection(world, Direction.Axis.X, startPos, endPos, isJumpingUp, isJumpingOneBlock, shouldRender,
					shouldSlow);
		} else if (isMovingOnZAxis) {
			return getNeoDirection(world, Direction.Axis.Z, startPos, endPos, isJumpingUp, isJumpingOneBlock, shouldRender,
					shouldSlow);
		}

		return null;
	}

	/**
	 * Checks if neo is possible.
	 * 
	 * @param world
	 * @param movementDir       Neo direction. Only X or Z are allowed
	 * @param startPos
	 * @param endPos
	 * @param isJumpingOneBlock
	 * @param shouldRender
	 * @param shouldSlow
	 * @return Horizontal direction in which neo is possible and null otherwise
	 */
	public static Direction getNeoDirection(WorldView world, Direction.Axis movementDir, BlockPos startPos,
		BlockPos endPos, boolean isJumpingUp, boolean isJumpingOneBlock, boolean shouldRender, boolean shouldSlow) {
		if (!movementDir.isHorizontal())
			throw new IllegalArgumentException("Only X and Z directions are allowed for movementDir");
		if (Math.abs(startPos.getY() - endPos.getY()) > 1) return null;
			
		int endX = endPos.getX();
		int endZ = endPos.getZ();
		PathState pathState = new PathState(isJumpingUp, isJumpingOneBlock, shouldRender, shouldSlow);
		if (movementDir == Direction.Axis.X) {
			if (startPos.getZ() > endZ) {
				int neoX = startPos.getX() - 1;
				if (pathState.isClear(world, startPos.getZ(), endZ, neoX, false, startPos.getY())) {
					return Direction.WEST;
				}
			}

			if (startPos.getZ() < endZ) {
				int neoX = startPos.getX() + 1;
				if (pathState.isClear(world, startPos.getZ(), endZ, neoX, false, startPos.getY())) {
					return Direction.EAST;
				}
			}
		}

		if (movementDir == Direction.Axis.Z) {
			if (startPos.getX() < endX) {
				int neoZ = startPos.getZ() + 1;
				if (pathState.isClear(world, startPos.getX(), endX, neoZ, true, startPos.getY())) {
					return Direction.SOUTH;
				}
			}

			if (startPos.getX() > endX) {
				int neoZ = startPos.getZ() - 1;
				if (pathState.isClear(world, startPos.getX(), endX, neoZ, true, startPos.getY())) {
					return Direction.NORTH;
				}
			}
		}

		return null;
	}

	// Encapsulates the path traversal state
		private record PathState(boolean isJumpingUp, boolean isJumpingOneBlock, boolean shouldRender, boolean shouldSlow) {

		private boolean isClear(WorldView world, int start, int end, int fixed, boolean isXAxis, int y) {
				int count = 0;
				int increment = start < end ? 1 : -1;
				int curr = start;
				BlockPos.Mutable currPos = new BlockPos.Mutable();

				while (curr != end) {
					if (TungstenMod.PATHFINDER.stop.get())
						return false;
					if (count > 5)
						return false;
					count++;

					if (isXAxis) {
						currPos.set(curr, y - 1, fixed);
					} else {
						currPos.set(fixed, y - 1, curr);
					}

					if (!world.getBlockState(currPos).isAir()) {
						return false;
					}

					if (isXAxis) {
						currPos.set(curr, y, fixed);
					} else {
						currPos.set(fixed, y, curr);
					}

					if (MovementHelper.isObscured(world, currPos, isJumpingUp, isJumpingOneBlock)) {
						if (shouldSlow) {
							try {
								Thread.sleep(450);
							} catch (InterruptedException ignored) {
							}
						}
						return false;
					} else {
						// No rendering here
					}

					curr += increment;
				}
				return true;
			}
		}
}
