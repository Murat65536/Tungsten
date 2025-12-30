package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import kaptainwutax.tungsten.concurrent.PathfindingExecutor;
import java.util.stream.IntStream;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.constants.pathfinding.CostConstants;
import kaptainwutax.tungsten.constants.physics.MovementConstants;
import kaptainwutax.tungsten.constants.physics.GravityConstants;
import kaptainwutax.tungsten.path.common.HeapNode;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.MovementHelper;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.movement.CornerJumpMovementHelper;
import kaptainwutax.tungsten.helpers.movement.NeoMovementHelper;
import kaptainwutax.tungsten.helpers.movement.StraightMovementHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.movement.MovementState;
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

public class BlockNode implements HeapNode {

	// Movement distance constants
	private static final class MovementLimits {
		static final double MAX_HORIZONTAL_DISTANCE = 7.0;
		static final double SPRINT_JUMP_DISTANCE = 6.0;
		static final double STANDARD_JUMP_DISTANCE = 5.3;
		static final double LADDER_MAX_DISTANCE = 5.3;
		static final double SLAB_JUMP_LIMIT = 4.5;
		static final double FENCE_DISTANCE = 4.0;
		static final double NEO_CHECK_DISTANCE = 4.0;
		static final double WATER_MAX_DISTANCE = 1.0;
		static final double WATER_HEIGHT_JUMP_LIMIT = 2.0;

		// Height-based jump distances
		static final double HEIGHT_0_MAX_DISTANCE = 5.3;
		static final double HEIGHT_1_MAX_DISTANCE_NORMAL = 4.5;
		static final double HEIGHT_1_MAX_DISTANCE_SPRINT = 6.0;
		static final double HEIGHT_NEG1_MAX_DISTANCE = 5.4;
		static final double HEIGHT_NEG2_MAX_DISTANCE = 7.0;
		static final double HEIGHT_NEG3_MAX_DISTANCE = 6.1;
		static final double HEIGHT_NEG3_EXACT_DISTANCE = 6.3;
		static final double TRAPDOOR_HEIGHT_1_DISTANCE = 5.0;
		static final double OPEN_TRAPDOOR_HEIGHT_NEG2_DISTANCE = 6.0;
		static final double BOTTOM_TRAPDOOR_DISTANCE = 6.4;
		static final double CLOSED_TRAPDOOR_HEIGHT_2_DISTANCE = 4.4;
		static final double SPECIAL_BLOCK_HEIGHT_DISTANCE = 7.4;
	}

	private static final class HeightLimits {
		static final int MAX_JUMP_HEIGHT = 2;
		static final int MAX_FALL_HEIGHT = 2;
		static final int SLIME_BOUNCE_CHECK_HEIGHT = 4;
		static final int DEEP_GENERATION_HEIGHT = 64;
		static final int DEEP_GENERATION_MIN = -64;
		static final int SHALLOW_GENERATION_MIN = -4;
		static final double TALL_BLOCK_THRESHOLD = 1.3;
		static final double SLAB_HEIGHT = 0.5;
		static final double FULL_BLOCK_HEIGHT = 1.0;
		static final double TOP_SLAB_HEIGHT = 1.5;
	}

	private static final class Debug {
		static final long SLEEP_MILLISECONDS = 250;
		static final long PATHFINDING_TIMEOUT_MS = 2000;
		static final int NODE_GENERATION_RADIUS = 8;
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
		return wasCleared(world, start, end, null, null);
	}

	public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end, BlockNode startNode,
			BlockNode endNode) {

		TungstenMod.TEST.clear();
		boolean shouldRender = false;
		boolean shouldSlow = false;
		

		boolean isStraightPossible = StraightMovementHelper.isPossible(world, start, end, shouldRender, shouldSlow);
		
		if (isStraightPossible) return true;
		if (endNode == null) return false;
		
		// When running bot in a normal environment instead of parkour, you need to turn on Neo and Corner jump checks to avoid cases where it can get stuck
		boolean shouldCheckNeo = start.isWithinDistance(end, MovementLimits.NEO_CHECK_DISTANCE);
		if (shouldCheckNeo) {
			Direction neoDirection = NeoMovementHelper.getNeoDirection(world, start, end, shouldRender, shouldSlow);
			if (neoDirection != null) {
				endNode.setNeoMovement(neoDirection);
				return true;
			}
		}
		boolean isCornerJumpPossible = CornerJumpMovementHelper.isPossible(world, start, end, shouldRender, shouldSlow);
		if (isCornerJumpPossible) {
			endNode.setCornerJump();
			return true;
		}

		return false;
	}

	// Large complex methods moved to separate classes for better maintainability
	// See BlockNodeGenerator for node generation logic
	// See validation package for validation logic
	// See movement package for movement detection

	// Keeping only this simplified helper method:
	private List<BlockNode> getNodesIn3DCircle(int d, BlockNode parent, Goal goal, boolean generateDeep) {
		ConcurrentLinkedQueue<BlockNode> nodes = new ConcurrentLinkedQueue<>();

	    double yMax = (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y < 0)
	        ? MovementHelper.getSlimeBounceHeight(parent.previous.y - parent.y) - HeightLimits.SLAB_HEIGHT
	        : generateDeep ? HeightLimits.SLIME_BOUNCE_CHECK_HEIGHT : HeightLimits.MAX_JUMP_HEIGHT;

	    if (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y < 0) {
	        TungstenMod.BLOCK_PATH_RENDERER.add(new Cuboid(
	                new Vec3d(parent.getBlockPos().getX(), parent.getBlockPos().getY(), parent.getBlockPos().getZ()),
	                new Vec3d(0.2D, 0.2D, 0.2D), Color.GREEN));
	        try {
	            Thread.sleep(Debug.SLEEP_MILLISECONDS); // Optional debug delay
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
	    }

	    int distanceWanted = d;
	    int finalYMax = (int) Math.ceil(yMax);

	    PathfindingExecutor pathfindingExecutor = PathfindingExecutor.getInstance();

	    Future<Void> future = pathfindingExecutor.submitTask(() -> {
	        IntStream.range(generateDeep ? HeightLimits.DEEP_GENERATION_MIN : HeightLimits.SHALLOW_GENERATION_MIN, finalYMax).parallel().forEach(py -> {
	                int localD;

	                if (py < -5) {
	                    double t = Math.sqrt((2 * py * -1) / GravityConstants.Gravity.GRAVITY_ACCELERATION);
	                    localD = (int) Math.ceil(MovementConstants.Speed.SPRINT_VELOCITY * t);
	                } else {
	                    localD = distanceWanted + 1;
	                }

	                // Center node
	                nodes.add(new BlockNode(this.x, this.y + py, this.z, goal, this, CostConstants.BaseCosts.WALK_ONE_BLOCK_COST));

	                for (int id = 1; id <= localD; id++) {
	                    int px = id, pz = 0;
	                    int dx = -1, dz = 1;
	                    int n = id * 4;

	                    for (int i = 0; i < n; i++) {
	                        if (px == id && dx > 0) dx = -1;
	                        else if (px == -id && dx < 0) dx = 1;

	                        if (pz == id && dz > 0) dz = -1;
	                        else if (pz == -id && dz < 0) dz = 1;

	                        px += dx;
	                        pz += dz;

	                        BlockNode newNode = new BlockNode(this.x + px, this.y + py, this.z + pz, goal, this,
			                        CostConstants.BaseCosts.WALK_ONE_BLOCK_COST);
	                        nodes.add(newNode);
	                    }
	                }
	            });
	        return null;
	    }, Debug.PATHFINDING_TIMEOUT_MS); // 2 second timeout

	    try {
	        future.get(); // Wait for completion
	    } catch (InterruptedException | ExecutionException e) {
	        e.printStackTrace();
	    }

	    return new ArrayList<>(nodes);
	}

	private boolean shouldRemoveNode(WorldView world, BlockNode child) {
		if (TungstenMod.PATHFINDER.stop.get())
			return true;

		BlockState currentBlockState = world.getBlockState(getBlockPos());
		
		if (previous != null)
		if (currentBlockState.isFullCube(world, getBlockPos()) || !(world.getBlockState(getBlockPos()).getBlock() instanceof LadderBlock) && world.getBlockState(getBlockPos().down()).isAir()) return true;
		BlockState currentBlockBelowState = world.getBlockState(getBlockPos().down());
		BlockState childAboveState = world.getBlockState(child.getBlockPos().up());
		BlockState childState = world.getBlockState(child.getBlockPos());
		BlockState childBelowState = world.getBlockState(child.getBlockPos().down());
		Block currentBlock = currentBlockState.getBlock();
		Block childBlock = childState.getBlock();
		Block childBelowBlock = childBelowState.getBlock();
		double heightDiff = getJumpHeight(this.y, child.y); // positive is going up and negative is going down
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(getPos(true), child.getPos(true));


//		if (BlockStateChecker.isAnyWater(currentBlockState)) {
//			if (distance > 1) return true;
//			if (!wasCleared(world, getBlockPos(), child.getBlockPos())) return true;
//			return false;
//		}
		// Search for a path without fall damage
		if (!TungstenMod.ignoreFallDamage) {
			if (!BlockStateChecker.isAnyWater(childState)) {
				if (heightDiff < -HeightLimits.MAX_FALL_HEIGHT) return true;
			}
		}
		if (BlockStateChecker.isAnyWater(childState)) {
			if (distance > MovementLimits.WATER_MAX_DISTANCE || heightDiff > MovementLimits.WATER_MAX_DISTANCE) return true;
            return !wasCleared(world, getBlockPos(), child.getBlockPos());
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
				&& wasCleared(world, getBlockPos(), child.getBlockPos()) && distance < MovementLimits.LADDER_MAX_DISTANCE && heightDiff >= -1) {
			return false;
		}
		if ((childBelowBlock instanceof VineBlock || childBlock instanceof VineBlock)
				&& distance < MovementConstants.Climbing.LADDER_HORIZONTAL_LIMIT && heightDiff == 0) {
			return false;
		}

		// Ladder checks
		if ((childBlock instanceof LadderBlock || childBelowBlock instanceof LadderBlock) && heightDiff > 1) {
			return true;
		}
		if (BlockStateChecker.isBottomSlab(currentBlockBelowState) && (childBlock instanceof LadderBlock || childBelowBlock instanceof LadderBlock) && heightDiff > 0) {
			return true;
		}
		if ((currentBlock instanceof LadderBlock) && distance > MovementConstants.Climbing.LADDER_HORIZONTAL_LIMIT) {
			return true;
		}
		if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock) && distance > MovementConstants.Climbing.MAX_LADDER_DISTANCE) {
			return true;
		}
		if ((childBelowBlock instanceof LadderBlock && !(childState.isAir() || childBlock instanceof LadderBlock))) {
			return true;
		}
		if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock) && distance < 1 && heightDiff >= -1) {
			return false;
		}
		if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock)
				&& wasCleared(world, getBlockPos(), child.getBlockPos(), this, child) && distance < MovementLimits.LADDER_MAX_DISTANCE && heightDiff >= -1) {
			return false;
		}

		// General height and distance checks
//        if (previous != null && (previous.y - y < 1 && wasOnSlime || (!wasOnSlime && child.y - y > 1))) return true;
		if (distance >= MovementLimits.MAX_HORIZONTAL_DISTANCE)
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
        return !wasCleared(world, getBlockPos(), child.getBlockPos(), this, child);
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
		boolean isBlockBelowTall = closestBlockBelowHeight > HeightLimits.TALL_BLOCK_THRESHOLD;

//    	if (world.getBlockState(child.getBlockPos().down()).getBlock() instanceof TrapdoorBlock) {
//			System.out.println(!world.getBlockState(child.getBlockPos().down()).get(Properties.OPEN));
//    	}

		VoxelShape blockShape = childBlockState.getCollisionShape(world, child.getBlockPos().down());
		VoxelShape currentBlockShape = currentBlockState.getCollisionShape(world, getBlockPos().down());

		double childBlockHeight = BlockShapeChecker.getBlockHeight(blockShape);
		double currentBlockHeight = BlockShapeChecker.getBlockHeight(currentBlockShape);

		double blockHeightDiff = currentBlockHeight - childBlockHeight; // Negative values means currentBlockHeight is
																		// lower, and positive means currentBlockHeight

		
		if (BlockStateChecker.isBottomSlab(currentBlockState) && childBlockHeight == 1 && heightDiff > 0) {
			return true;
		}

		if (BlockStateChecker.isAnyWater(currentBlockState)) {
            return distance >= MovementLimits.WATER_HEIGHT_JUMP_LIMIT;
        }
		if (childBlockHeight == HeightLimits.TOP_SLAB_HEIGHT && currentBlockHeight == HeightLimits.TOP_SLAB_HEIGHT && heightDiff <= 1) {
			if (distance <= MovementLimits.FENCE_DISTANCE) return false;
		}
		
		if (isBlockBelowTall && heightDiff > 0) return true;
								
		// VoxelShape-based checks
		if (!Double.isInfinite(blockHeightDiff) && !Double.isNaN(blockHeightDiff)) {
			
			// Slab and ladder checks
			if (heightDiff <= 0 && (BlockStateChecker.isBottomSlab(childBlockState)
					|| (!wasOnLadder && childBlock instanceof LadderBlock)) && distance >= MovementLimits.SLAB_JUMP_LIMIT) {
				return true;
			}
			if (childBlock instanceof SlabBlock && childBlockState.get(Properties.SLAB_TYPE) == SlabType.TOP
					&& !world.getBlockState(child.getBlockPos()).isAir()) {
				return true;
			}

			if (BlockStateChecker.isClosedBottomTrapdoor(childBlockState)) {
				if (heightDiff <= 1 && distance <= MovementLimits.BOTTOM_TRAPDOOR_DISTANCE) return false;
				if (heightDiff == 2 && distance <= MovementLimits.CLOSED_TRAPDOOR_HEIGHT_2_DISTANCE) return false;
			}
			
			if (BlockStateChecker.isClosedBottomTrapdoor(currentBlockState)) {
				if (heightDiff <= 0 && distance <= MovementLimits.BOTTOM_TRAPDOOR_DISTANCE) return false;
				if (heightDiff > 0 && BlockStateChecker.isTopSlab(childBlockState)) return true;
			}
			
			if (blockHeightDiff != 0) {
				
				
				if (Math.abs(blockHeightDiff) > HeightLimits.SLAB_HEIGHT && Math.abs(blockHeightDiff) <= HeightLimits.FULL_BLOCK_HEIGHT) {
					if (heightDiff > 0 && (blockShape.getMin(Axis.Y) == 0.0 && currentBlockHeight <= HeightLimits.FULL_BLOCK_HEIGHT))
						return true;
					if (heightDiff == 2 && distance <= MovementLimits.STANDARD_JUMP_DISTANCE)
						return false;
					if (heightDiff >= 0 && distance <= MovementLimits.STANDARD_JUMP_DISTANCE)
						return false;
				}
				
				if (Math.abs(blockHeightDiff) <= HeightLimits.SLAB_HEIGHT && (blockShape.getMin(Axis.Y) == 0.0 && childBlockHeight == HeightLimits.FULL_BLOCK_HEIGHT)) {
					if (heightDiff == 0 && distance <= MovementLimits.HEIGHT_NEG1_MAX_DISTANCE)
						return false;
				}

				if (Math.abs(blockHeightDiff) <= HeightLimits.SLAB_HEIGHT && (blockShape.getMin(Axis.Y) == 0.0 || childBlockHeight > HeightLimits.FULL_BLOCK_HEIGHT)) {
					if (blockHeightDiff == HeightLimits.SLAB_HEIGHT && heightDiff <= -1)
						return true;
					if (heightDiff == 0 && distance >= MovementLimits.CLOSED_TRAPDOOR_HEIGHT_2_DISTANCE)
						return true;
					if (heightDiff <= 1 && distance <= MovementLimits.SPECIAL_BLOCK_HEIGHT_DISTANCE)
						return false;
				}

				if (Math.abs(blockHeightDiff) >= HeightLimits.SLAB_HEIGHT && (blockShape.getMin(Axis.Y) == 0.0 || childBlockHeight > HeightLimits.FULL_BLOCK_HEIGHT))
					return true;
				
			}
		}

		if (!wasOnSlime || this.previous.y - this.y >= 0) {
			// Basic height and distance checks
			if (heightDiff >= HeightLimits.MAX_JUMP_HEIGHT)
				return true;
			if (heightDiff == 1 && distance > MovementLimits.HEIGHT_1_MAX_DISTANCE_SPRINT)
				return true;
			if (heightDiff <= -HeightLimits.MAX_JUMP_HEIGHT && distance > MovementLimits.HEIGHT_NEG2_MAX_DISTANCE)
				return true;
			if (heightDiff == 1 && distance >= MovementLimits.HEIGHT_1_MAX_DISTANCE_NORMAL)
				return true;
			if ((heightDiff == 0) && distance >= MovementLimits.HEIGHT_0_MAX_DISTANCE)
				return true;
			if (heightDiff >= -3 && distance >= MovementLimits.HEIGHT_NEG3_MAX_DISTANCE)
				return true;
			if (heightDiff < -HeightLimits.MAX_JUMP_HEIGHT && distance >= MovementLimits.HEIGHT_NEG3_EXACT_DISTANCE)
				return true;

			// Trapdoor checks
			if (heightDiff == 1 && BlockStateChecker.isOpenTrapdoor(childBlockState) && distance > MovementLimits.TRAPDOOR_HEIGHT_1_DISTANCE)
				return true;
			if (heightDiff <= -HeightLimits.MAX_JUMP_HEIGHT && BlockStateChecker.isOpenTrapdoor(childBlockState) && distance > MovementLimits.OPEN_TRAPDOOR_HEIGHT_NEG2_DISTANCE)
				return true;
		}

		// Large height drop
		if (heightDiff > -1 && distance >= MovementLimits.SPRINT_JUMP_DISTANCE)
			return true;

		// Bottom slab checks
        return currentBlockHeight <= HeightLimits.SLAB_HEIGHT && heightDiff > 0 && childBlockHeight > HeightLimits.SLAB_HEIGHT;
    }

}
