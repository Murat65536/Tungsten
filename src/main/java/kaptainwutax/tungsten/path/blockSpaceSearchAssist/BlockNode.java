package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.List;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.constants.pathfinding.CostConstants;
import kaptainwutax.tungsten.path.common.HeapNode;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.movement.CornerJumpMovementHelper;
import kaptainwutax.tungsten.helpers.movement.NeoMovementHelper;
import kaptainwutax.tungsten.helpers.movement.StraightMovementHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.movement.MovementState;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.world.BetterBlockPos;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class BlockNode implements HeapNode {

	// Movement distance constants for internal checks if needed, but mostly moved to Validators
	private static final class MovementLimits {
		static final double SPRINT_JUMP_DISTANCE = 6.0;
		static final double FENCE_DISTANCE = 4.0;
		static final double NEO_CHECK_DISTANCE = 4.0;
	}

	/**
	 * The position of this node
	 */
	public final int x;
	public final int y;
	public final int z;

	/**
	 * Cached, should always be equal to goal.heuristic(pos)
	 */
	public double estimatedCostToGoal;

	/**
	 * Total cost of getting from start to here Mutable and changed by PathFinder
	 */
	public double cost;

	/**
	 * Should always be equal to estimatedCosttoGoal + cost Mutable and changed by
	 * PathFinder
	 */
	public double combinedCost;

	/**
	 * In the graph search, what previous node contributed to the cost Mutable and
	 * changed by PathFinder
	 */
	public BlockNode previous;

	// Movement state management
	private final MovementState movementState;

	// Compatibility accessors for existing code
	public final boolean wasOnSlime;
	public final boolean wasOnLadder;

	/**
	 * Where is this node in the array flattenization of the binary heap? Needed for
	 * decrease-key operations.
	 */
	public int heapPosition;

	public BlockNode(BlockPos pos, Goal goal) {
		this(pos.getX(), pos.getY(), pos.getZ(), goal);
	}

	public BlockNode(int x, int y, int z, Goal goal) {
		this(x, y, z, goal, null, 0);
	}

	public BlockNode(int x, int y, int z, Goal goal, BlockNode parent, double cost) {
		this.previous = parent;
		this.movementState = new MovementState(x, y, z);
		this.wasOnSlime = movementState.wasOnSlime;
		this.wasOnLadder = movementState.wasOnLadder;
		this.cost = parent != null ? 0 : CostConstants.BaseCosts.COST_INFINITY;
		this.estimatedCostToGoal = goal.heuristic(x, y, z);
		if (Double.isNaN(estimatedCostToGoal)) {
			throw new IllegalStateException(goal + " calculated implausible heuristic");
		}
		this.heapPosition = -1;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public boolean isOpen() {
		return heapPosition != -1;
	}

	// HeapNode interface implementation
	@Override
	public int getHeapPosition() {
		return heapPosition;
	}

	@Override
	public void setHeapPosition(int position) {
		this.heapPosition = position;
	}

	@Override
	public double getCombinedCost() {
		return combinedCost;
	}

	/**
	 * TODO: Possibly reimplement hashCode and equals. They are necessary for this
	 * class to function but they could be done better
	 *
	 * @return The hash code value for this {@link PathNode}
	 */
	@Override
	public int hashCode() {
		return (int) BetterBlockPos.longHash(x, y, z);
	}

	public Vec3d getPos() {
		return getPos(false);
	}

	public Vec3d getPos(boolean shift) {
		if (shift) {
			if (movementState.isDoingNeo())
				return BlockPosShifter.shiftForStraightNeo(this, movementState.getNeoSide());
			return BlockPosShifter.getPosOnLadder(this);
		}
		return new Vec3d(x, y, z);
	}

	public boolean isDoingLongJump() {
		if (this.previous != null) {
			double distance = DistanceCalculator.getHorizontalEuclideanDistance(this.previous.getPos(), this.getPos());
            return distance >= MovementLimits.FENCE_DISTANCE && distance < MovementLimits.SPRINT_JUMP_DISTANCE;
		}
		return false;
	}

	public boolean isDoingNeo() {
		return movementState.isDoingNeo();
	}

	public Direction getNeoSide() {
		return movementState.getNeoSide();
	}

	public boolean isDoingCornerJump() {
		return movementState.isDoingCornerJump();
	}

	// Methods to update movement state
	public void setNeoMovement(Direction side) {
		movementState.setNeoMovement(side);
	}

	public void setCornerJump() {
		movementState.setCornerJump();
	}

	public BlockPos getBlockPos() {
		return new BlockPos(x, y, z);
	}

	@Override
	public boolean equals(Object obj) {

		final BlockNode other = (BlockNode) obj;

		return x == other.x && y == other.y && z == other.z;
	}

	// Static processor instance for node operations
	private static final BlockNodeProcessor processor = new BlockNodeProcessor();

	public List<BlockNode> getChildren(WorldView world, Goal goal, boolean generateDeep) {
		// Delegate to the processor for clean separation of concerns
		return processor.processNode(this, world, goal, generateDeep);
	}

	public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end) {
		return wasCleared(world, start, end, null, null, false, false);
	}

	public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end, BlockNode startNode,
			BlockNode endNode) {
		return wasCleared(world, start, end, startNode, endNode, false, false);
	}

	public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end, BlockNode startNode,
			BlockNode endNode, boolean shouldRender, boolean shouldSlow) {

		boolean isStraightPossible = StraightMovementHelper.isPossible(world, start, end, false, shouldSlow); // Don't render in helper

		if (isStraightPossible) {
			// Render successful path if visualization is enabled
			if (shouldRender) {
				renderPathVisualization(start, end, Color.GREEN);
			}
			return true;
		}
		if (endNode == null) return false;

		// When running bot in a normal environment instead of parkour, you need to turn on Neo and Corner jump checks to avoid cases where it can get stuck
		boolean shouldCheckNeo = start.isWithinDistance(end, MovementLimits.NEO_CHECK_DISTANCE);
		if (shouldCheckNeo) {
			Direction neoDirection = NeoMovementHelper.getNeoDirection(world, start, end, false, shouldSlow); // Don't render in helper
			if (neoDirection != null) {
				endNode.setNeoMovement(neoDirection);
				// Render successful neo path if visualization is enabled
				if (shouldRender) {
					renderPathVisualization(start, end, Color.BLUE);
				}
				return true;
			}
		}
		boolean isCornerJumpPossible = CornerJumpMovementHelper.isPossible(world, start, end, false, shouldSlow); // Don't render in helper
		if (isCornerJumpPossible) {
			endNode.setCornerJump();
			// Render successful corner jump if visualization is enabled
			if (shouldRender) {
				renderPathVisualization(start, end, new Color(128, 0, 128)); // Purple
			}
			return true;
		}

		// Render failed path if visualization is enabled
		if (shouldRender) {
			renderPathVisualization(start, end, Color.RED);
		}

		return false;
	}

	/**
	 * Renders path visualization for movement between two block positions.
	 * This method handles the rendering that was previously in the movement helpers.
	 *
	 * @param start The starting block position
	 * @param end The ending block position
	 * @param color The color to use for rendering
	 */
	private static void renderPathVisualization(BlockPos start, BlockPos end, Color color) {
		// Clear previous visualization
		TungstenMod.TEST.clear();

		// Render the end position
		TungstenMod.TEST.add(new Cuboid(new Vec3d(end.getX(), end.getY(), end.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), color));
		TungstenMod.TEST.add(new Cuboid(new Vec3d(end.getX(), end.getY() + 1, end.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), color));

		// For now, just render the start and end positions
		// In a more complex implementation, you could render the full path
		TungstenMod.TEST.add(new Cuboid(new Vec3d(start.getX(), start.getY(), start.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), color));
		TungstenMod.TEST.add(new Cuboid(new Vec3d(start.getX(), start.getY() + 1, start.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), color));
	}
}
