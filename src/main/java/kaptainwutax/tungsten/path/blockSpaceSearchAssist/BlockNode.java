package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.List;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.MovementHelper;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.movement.CornerJumpMovementHelper;
import kaptainwutax.tungsten.helpers.movement.NeoMovementHelper;
import kaptainwutax.tungsten.helpers.movement.StreightMovementHelper;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.calculators.ActionCosts;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.world.BetterBlockPos;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SlimeBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;

public class BlockNode {

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

	private boolean wasOnSlime;
	private boolean wasOnLadder;
	private boolean isDoingNeo = false;
	private Direction neoSide;
	private boolean isDoingCornerJump = false;

	/**
	 * Where is this node in the array flattenization of the binary heap? Needed for
	 * decrease-key operations.
	 */
	public int heapPosition;

	public BlockNode(BlockPos pos, Goal goal) {
		this.previous = null;
		this.cost = ActionCosts.COST_INF;
		this.estimatedCostToGoal = goal.heuristic(pos.getX(), pos.getY(), pos.getZ());
		if (Double.isNaN(estimatedCostToGoal)) {
			throw new IllegalStateException(goal + " calculated implausible heuristic");
		}
		this.heapPosition = -1;
		this.x = pos.getX();
		this.y = pos.getY();
		this.z = pos.getZ();
		this.wasOnSlime = TungstenMod.mc.world.getBlockState(pos.down()).getBlock() instanceof SlimeBlock;
		this.wasOnLadder = TungstenMod.mc.world.getBlockState(pos).getBlock() instanceof LadderBlock;
	}

	public BlockNode(int x, int y, int z, Goal goal) {
		this.previous = null;
		this.cost = ActionCosts.COST_INF;
		this.estimatedCostToGoal = goal.heuristic(x, y, z);
		if (Double.isNaN(estimatedCostToGoal)) {
			throw new IllegalStateException(goal + " calculated implausible heuristic");
		}
		this.heapPosition = -1;
		this.x = x;
		this.y = y;
		this.z = z;
		this.wasOnSlime = TungstenMod.mc.world.getBlockState(new BlockPos(x, y - 1, z))
				.getBlock() instanceof SlimeBlock;
		this.wasOnLadder = TungstenMod.mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
	}

	public BlockNode(int x, int y, int z, Goal goal, BlockNode parent, double cost) {
		this.previous = parent;
		this.wasOnSlime = TungstenMod.mc.world.getBlockState(new BlockPos(x, y - 1, z))
				.getBlock() instanceof SlimeBlock;
		this.wasOnLadder = TungstenMod.mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
		this.cost = parent != null ? 0 : ActionCosts.COST_INF;
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
			if (isDoingNeo)
				return BlockPosShifter.shiftForStraightNeo(this, neoSide);
			return BlockPosShifter.getPosOnLadder(this);
		}
		return new Vec3d(x, y, z);
	}

	public boolean isDoingLongJump() {
		if (this.previous != null) {
			double distance = DistanceCalculator.getHorizontalEuclideanDistance(this.previous.getPos(), this.getPos());
			if (distance >= 4 && distance < 6) {
				return true;
			}
		}
		return false;
	}

	public boolean isDoingNeo() {
		return this.isDoingNeo;
	}
	
	public Direction getNeoSide() {
		return this.neoSide;
	}

	public boolean isDoingCornerJump() {
		return this.isDoingCornerJump;
	}

	public BlockPos getBlockPos() {
		return new BlockPos(x, y, z);
	}

	@Override
	public boolean equals(Object obj) {

		final BlockNode other = (BlockNode) obj;

		return x == other.x && y == other.y && z == other.z;
	}

	public List<BlockNode> getChildren(WorldView world, Goal goal) {

		List<BlockNode> nodes = getNodesIn3DCircule(8, this, goal);
		nodes.removeIf((child) -> {
			return shouldRemoveNode(world, child);
		});

		TungstenMod.TEST.clear();

		return nodes;

	}

	public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end) {
		return wasCleared(world, start, end, null, null);
	}

	public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end, BlockNode startNode,
			BlockNode endNode) {

		TungstenMod.TEST.clear();
		boolean shouldRender = false;
		boolean shouldSlow = false;
		

		boolean isStreightPossible = StreightMovementHelper.isPossible(world, start, end, shouldRender, shouldSlow);
		
		if (isStreightPossible) return true;
		if (endNode == null) return false;
		
		boolean shouldCheckNeo = start.isWithinDistance(end, 4.2) && true;
		if (shouldCheckNeo) {
			Direction neoDirection = NeoMovementHelper.getNeoDirection(world, start, end, shouldRender, shouldSlow);
			if (neoDirection != null) {
				endNode.isDoingNeo = true;
				endNode.neoSide = neoDirection;
				endNode.isDoingCornerJump = false;
				return true;
			}
		}
		boolean isCornerJumpPossible = CornerJumpMovementHelper.isPossible(world, start, end, shouldRender, shouldSlow);
		if (isCornerJumpPossible) {
			endNode.isDoingNeo = false;
			endNode.isDoingCornerJump = true;
			return true;
		}

		return false;
	}

	private List<BlockNode> getNodesIn3DCircule(int d, BlockNode parent, Goal goal) {
		List<BlockNode> nodes = new ArrayList<>();
		
		double g = 32.656; // Acceleration due to gravity in m/s^2
		double v_sprint = 5.8; // Sprinting speed in m/s 5.8 based on meteor player.speed var
		
		if (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y < 0) {
			TungstenMod.RENDERERS.clear();
//	    	TungstenMod.RENDERERS.add(new Cuboid(node.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), Color.GREEN));
	    	 TungstenMod.BLOCK_PATH_RENDERER.add(new Cuboid(new Vec3d((double)parent.getBlockPos().getX(), (double)parent.getBlockPos().getY(), (double)parent.getBlockPos().getZ()), new Vec3d(0.2D, 0.2D, 0.2D), Color.GREEN));
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		double yMax = (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y < 0
				? MovementHelper.getSlimeBounceHeight(parent.previous.y - parent.y) - 0.5
				: 4);
//		if (parent.wasOnLadder) {
//			yMax += 1;
//		}

		int distanceWanted = d;
		for (int py = -64; py < yMax; py++) {

			if (py < 0 && py < -5) {
				double t = Math.sqrt((2 * py * -1) / g);
				d = (int) Math.ceil(v_sprint * t);
			} else
				d = distanceWanted + 1;
			nodes.add(new BlockNode(this.x, this.y + py, this.z, goal, this, ActionCosts.WALK_ONE_BLOCK_COST));
			for (int id = 1; id <= d; id++) {
				int px = id;
				int pz = 0;
				int dx = -1, dz = 1;
				int n = id * 4;
				for (int i = 0; i < n; i++) {
					if (px == id && dx > 0)
						dx = -1;
					else if (px == -id && dx < 0)
						dx = 1;
					if (pz == id && dz > 0)
						dz = -1;
					else if (pz == -id && dz < 0)
						dz = 1;
					px += dx;
					pz += dz;
					BlockNode newNode = new BlockNode(this.x + px, this.y + py, this.z + pz, goal, this,
							ActionCosts.WALK_ONE_BLOCK_COST);
					nodes.add(newNode);
				}
			}
		}
		return nodes;
	}

	private boolean shouldRemoveNode(WorldView world, BlockNode child) {
		if (TungstenMod.PATHFINDER.stop)
			return true;

		BlockState currentBlockState = world.getBlockState(getBlockPos());
		BlockState childAboveState = world.getBlockState(child.getBlockPos().up());
		BlockState childState = world.getBlockState(child.getBlockPos());
		BlockState childBelowState = world.getBlockState(child.getBlockPos().down());
		Block currentBlock = currentBlockState.getBlock();
		Block childBlock = childState.getBlock();
		Block childBelowBlock = childBelowState.getBlock();
		double heightDiff = this.y - child.y; // -1 is going up and +1 is going down, in negative y levels its reversed
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(getPos(true), child.getPos(true));


//		if (BlockStateChecker.isAnyWater(currentBlockState)) {
//			if (distance > 1) return true;
//			if (!wasCleared(world, getBlockPos(), child.getBlockPos())) return true;
//			return false;
//		}
		if (BlockStateChecker.isAnyWater(childState)) {
			if (distance > 1 || heightDiff > 1) return true;
			if (!wasCleared(world, getBlockPos(), child.getBlockPos())) return true;
			return false;
		}
		if (BlockStateChecker.isAnyWater(childState) && !childAboveState.isAir()) return true;
		
		if (BlockStateChecker.isDoubleSlab(world, getBlockPos()) || childBelowBlock instanceof SnowBlock)
			return true;

		// Check for air below
		if (childBelowState.isAir() && !(childBlock instanceof LadderBlock)) {
			if (!(childBlock instanceof SlabBlock))
				return true;
		}

//        if (BlockStateChecker.isConnected(child.getBlockPos())) {
//    		TungstenMod.TEST.add(new Cuboid(new Vec3d(child.getBlockPos().getX(), child.getBlockPos().getY(), child.getBlockPos().getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//    		try {
//    			Thread.sleep(250);
//    		} catch (InterruptedException ignored) {}
//        }

		// Check for water
//        if (isWater(childState) && wasCleared(world, getBlockPos(), child.getBlockPos())) return false;

		// Vine checks
		if ((childBelowBlock instanceof VineBlock || childBlock instanceof VineBlock)
				&& wasCleared(world, getBlockPos(), child.getBlockPos()) && distance < 6.3 && heightDiff >= -1) {
			return false;
		}
		if ((childBelowBlock instanceof VineBlock || childBlock instanceof VineBlock)
				&& distance < 2.3 && heightDiff == 0) {
			return false;
		}

		// Ladder checks
		if ((currentBlock instanceof LadderBlock) && distance > 2.3) {
			return true;
		}
		if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock) && distance > 6.3) {
			return true;
		}
		if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock) && distance < 1 && heightDiff >= -1) {
			return false;
		}
		if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock)
				&& wasCleared(world, getBlockPos(), child.getBlockPos(), this, child) && distance < 5.3 && heightDiff >= -1) {
			return false;
		}

		// General height and distance checks
//        if (previous != null && (previous.y - y < 1 && wasOnSlime || (!wasOnSlime && child.y - y > 1))) return true;
		if (distance >= 7)
			return true;

		// Slab checks
		if (BlockStateChecker.isBottomSlab(childState))
			return true;
//        if (isBottomSlab(childState) && !hasBiggerCollisionShapeThanAbove(world, child.getBlockPos())) return true;

		boolean canStandOn = BlockShapeChecker.hasBiggerCollisionShapeThanAbove(world, child.getBlockPos().down());

		// Collision shape and block exceptions
		if (!canStandOn && (!(childBlock instanceof DaylightDetectorBlock) && !(childBlock instanceof CarpetBlock)
				&& !(childBelowBlock instanceof SlabBlock) && !(childBelowBlock instanceof LanternBlock)
				&& !(childBelowBlock == Blocks.SNOW))
//                || (childBelowBlock instanceof StairsBlock)
//                && !(childBelowBlock instanceof LanternBlock)
//                && !(childBelowBlock == Blocks.SNOW)
//                && getShapeVolume(childState.getCollisionShape(world, child.getBlockPos())) >= 1
		) {
			return true;
		}

		// Specific block checks
		if (childState.isOf(Blocks.LAVA))
			return true;
		if (childBelowBlock instanceof LilyPadBlock)
			return true;
		if (childBelowBlock instanceof CarpetBlock)
			return true;
		if (childBelowBlock instanceof DaylightDetectorBlock)
			return true;
		if (childBlock instanceof StairsBlock)
			return true;
		if (childBelowBlock instanceof SeaPickleBlock)
			return true;
		if (childBelowBlock instanceof CropBlock)
			return true;
		if (BlockStateChecker.isBottomSlab(childBelowState) && !(childBlock instanceof AirBlock))
			return true;

		if (isJumpImpossible(world, child))
			return true;

		// TODO: Fix bottom slab under fence thing
		if (!wasCleared(world, getBlockPos(), child.getBlockPos(), this, child)) {
			return true;
		}

		return false;
	}
	
	/**
	 * Returns jump height.
	 * 
	 * @param from
	 * @param to
	 * @return positive is going up and negative is going down
	 */
	public int getJumpHeight(int from, int to) {
		
		int diff = to - from;
		
		// if `to` is higher then `from` return value should be positive
		if (to > from) {
			return diff > 0 ? diff : diff * -1;
		}
		return diff > 0 ? diff * -1 : diff;
	}

	private boolean isJumpImpossible(WorldView world, BlockNode child) {
		double heightDiff = getJumpHeight(this.y, child.y); // positive is going up and negative is going down
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(getPos(true), child.getPos(true));

		BlockState childBlockState = world.getBlockState(child.getBlockPos().down());
		BlockState currentBlockState = world.getBlockState(getBlockPos().down());
		Block childBlock = childBlockState.getBlock();
        double closestBlockBelowHeight = BlockShapeChecker.getBlockHeight(child.getBlockPos().down());
		boolean isBlockBelowTall = closestBlockBelowHeight > 1.3;

//    	if (world.getBlockState(child.getBlockPos().down()).getBlock() instanceof TrapdoorBlock) {
//			System.out.println(!world.getBlockState(child.getBlockPos().down()).get(Properties.OPEN));
//    	}

		VoxelShape blockShape = childBlockState.getCollisionShape(world, child.getBlockPos().down());
		VoxelShape currentBlockShape = currentBlockState.getCollisionShape(world, getBlockPos().down());

		double childBlockHeight = BlockShapeChecker.getBlockHeight(blockShape);
		double currentBlockHeight = BlockShapeChecker.getBlockHeight(currentBlockShape);

		double blockHeightDiff = currentBlockHeight - childBlockHeight; // Negative values means currentBlockHeight is
																		// lower, and positive means currentBlockHeight


		if (BlockStateChecker.isAnyWater(currentBlockState)) {
			if (distance >= 2) return true;
			return false;
		}
		
		if (isBlockBelowTall && heightDiff > 0) return true;
								
		// VoxelShape-based checks
		if (!Double.isInfinite(blockHeightDiff) && !Double.isNaN(blockHeightDiff)) {
			
			// Slab and ladder checks
			if (heightDiff <= 0 && (BlockStateChecker.isBottomSlab(childBlockState)
					|| (!wasOnLadder && childBlock instanceof LadderBlock)) && distance >= 4.5) {
				return true;
			}
			if (childBlock instanceof SlabBlock && childBlockState.get(Properties.SLAB_TYPE) == SlabType.TOP
					&& !world.getBlockState(child.getBlockPos()).isAir()) {
				return true;
			}

			if (BlockStateChecker.isClosedBottomTrapdoor(childBlockState)) {
				if (heightDiff <= 1 && distance <= 6.4) return false;
				if (heightDiff == 2 && distance <= 4.4) return false;
			}
			
			if (BlockStateChecker.isClosedBottomTrapdoor(currentBlockState)) {
				if (heightDiff <= 0 && distance <= 6.4) return false;
				if (heightDiff > 0 && BlockStateChecker.isTopSlab(childBlockState)) return true;
			}
			
			if (blockHeightDiff != 0) {
				
				if (Math.abs(blockHeightDiff) > 0.5 && Math.abs(blockHeightDiff) <= 1.0) {
					if (heightDiff > 0 && (blockShape.getMin(Axis.Y) == 0.0 && currentBlockHeight <= 1.0))
						return true;
					if (heightDiff == 2 && distance <= 5.3)
						return false;
					if (heightDiff >= 0 && distance <= 5.3)
						return false;
				}
				
				if (Math.abs(blockHeightDiff) <= 0.5 && (blockShape.getMin(Axis.Y) == 0.0 && childBlockHeight == 1.0)) {
					if (heightDiff == 0 && distance <= 5.4)
						return false;
				}

				if (Math.abs(blockHeightDiff) <= 0.5 && (blockShape.getMin(Axis.Y) == 0.0 || childBlockHeight > 1.0)) {
					if (blockHeightDiff == 0.5 && heightDiff <= -1)
						return true;
					if (heightDiff == 0 && distance >= 4.4)
						return true;
					if (heightDiff <= 1 && distance <= 7.4)
						return false;
				}

				if (Math.abs(blockHeightDiff) >= 0.5 && (blockShape.getMin(Axis.Y) == 0.0 || childBlockHeight > 1.0))
					return true;
			}
		}

		if (!wasOnSlime || this.previous.y - this.y >= 0) {
			// Basic height and distance checks
			if (heightDiff >= 2)
				return true;
			if (heightDiff == 1 && distance > 6)
				return true;
			if (heightDiff <= -2 && distance > 7)
				return true;
			if (heightDiff == 1 && distance >= 4.5)
				return true;
			if ((heightDiff == 0) && distance >= 6.3)
				return true;
			if ((heightDiff == -1) && distance >= 6.3)
				return true;
			if (heightDiff == -1 && distance >= 5.5)
				return true;
			if (heightDiff < -2 && distance >= 6.5)
				return true;

			// Trapdoor checks
			if (heightDiff == 1 && BlockStateChecker.isOpenTrapdoor(childBlockState) && distance > 5)
				return true;
			if (heightDiff <= -2 && BlockStateChecker.isOpenTrapdoor(childBlockState) && distance > 6)
				return true;
		}

		// Large height drop
		if (heightDiff > -1 && distance >= 6)
			return true;

		// Bottom slab checks
		if (currentBlockHeight <= 0.5 && heightDiff > 0 && childBlockHeight > 0.5) {
			return true;
		}
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(child.x, child.x, child.x), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//		try {
//			Thread.sleep(350);
//		} catch (InterruptedException ignored) {}
//		TungstenMod.TEST.clear();

		return false;
	}

}
