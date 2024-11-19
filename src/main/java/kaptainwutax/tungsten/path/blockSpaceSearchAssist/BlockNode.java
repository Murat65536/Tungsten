package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.path.calculators.ActionCosts;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.world.BetterBlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SlimeBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
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
     * Total cost of getting from start to here
     * Mutable and changed by PathFinder
     */
    public double cost;

    /**
     * Should always be equal to estimatedCosttoGoal + cost
     * Mutable and changed by PathFinder
     */
    public double combinedCost;

    /**
     * In the graph search, what previous node contributed to the cost
     * Mutable and changed by PathFinder
     */
    public BlockNode previous;
    
    private boolean wasOnSlime;
    private boolean wasOnLadder;

    /**
     * Where is this node in the array flattenization of the binary heap? Needed for decrease-key operations.
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
        this.wasOnSlime = MinecraftClient.getInstance().world.getBlockState(pos.down()).getBlock() instanceof SlimeBlock;
        this.wasOnLadder = MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof LadderBlock;
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
        this.wasOnSlime = MinecraftClient.getInstance().world.getBlockState(new BlockPos(x, y-1, z)).getBlock() instanceof SlimeBlock;
        this.wasOnLadder = MinecraftClient.getInstance().world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
    }
    
    public BlockNode(int x, int y, int z, Goal goal, BlockNode parent, double cost) {
        this.previous = parent;
        this.wasOnSlime = MinecraftClient.getInstance().world.getBlockState(new BlockPos(x, y-1, z)).getBlock() instanceof SlimeBlock;
        this.wasOnLadder = MinecraftClient.getInstance().world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
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
     * TODO: Possibly reimplement hashCode and equals. They are necessary for this class to function but they could be done better
     *
     * @return The hash code value for this {@link PathNode}
     */
    @Override
    public int hashCode() {
        return (int) BetterBlockPos.longHash(x, y, z);
    }
    
    public Vec3d getPos() {
    	return new Vec3d(x, y, z);
    }
    
    public BlockPos getBlockPos() {
    	return new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        // GOTTA GO FAST
        // ALL THESE CHECKS ARE FOR PEOPLE WHO WANT SLOW CODE
        // SKRT SKRT
        //if (obj == null || !(obj instanceof PathNode)) {
        //    return false;
        //}

        final BlockNode other = (BlockNode) obj;
        //return Objects.equals(this.pos, other.pos) && Objects.equals(this.goal, other.goal);

        return x == other.x && y == other.y && z == other.z;
    }
    
    public List<BlockNode> getChildren(WorldView world, Goal goal) {
		
		List<BlockNode> nodes = getNodesIn2DCircule(6, this, goal);
		nodes.removeIf((child) -> {
			return shouldRemoveNode(world, child);
		});
		
//		for (BlockNode blockNode : nodes) {
//			TungstenMod.TEST.add(new Cuboid(new Vec3d(blockNode.x, blockNode.y, blockNode.z), new Vec3d(1.0D, 1.0D, 1.0D), blockNode.wasOnLadder ? Color.GREEN : Color.WHITE));
//		}
//
//		if (wasOnLadder) {
//				try {
//					Thread.sleep(200);
//				} catch (InterruptedException ignored) {}
//		}
		
		TungstenMod.TEST.clear();
		
		return nodes;
		
    }
    
    public boolean wasCleared(WorldView world, BlockPos start, BlockPos end) { 
		int x1 = start.getX();
	    int y1 = start.getY();
	    int z1 = start.getZ();
	
	    int x2 = end.getX();
	    int y2 = end.getY();
	    int z2 = end.getZ();
	    
	    boolean isJumpingOneBlock = y2-y1 == 1;
		TungstenMod.TEST.clear();
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(x1, y1, z1), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(x2, y2, z2), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(x1, y1, z1), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(x2, y2, z2),
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(x1, y1, z1), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(x2, y2, z2), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
		BlockPos.Mutable currPos = new BlockPos.Mutable();
		int x = x1;
        int y = isJumpingOneBlock ? y1+1 : y1;
        int z = z1;

        while (x != x2 || y != y2 || z != z2) {
        	if (TungstenMod.PATHFINDER.stop) return false;
            currPos.set(x, y, z);
            if (isObscured(world, currPos)) {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				try {
//					Thread.sleep(450);
//				} catch (InterruptedException ignored) {}
				return false;
			} else {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
			}
            if (z < z2) {
                z++;
            } else if (z > z2) {
                z--;
            }
//    		TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));

            currPos.set(x, y, z);
            if (isObscured(world, currPos)) {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				try {
//					Thread.sleep(450);
//				} catch (InterruptedException ignored) {}
				return false;
			} else {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
			}
//        }
            if (x < x2) {
                x++;
            } else if (x > x2) {
                x--;
            }
//    		TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
            currPos.set(x, y, z);

            if (isObscured(world, currPos)) {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				try {
//					Thread.sleep(450);
//				} catch (InterruptedException ignored) {}
				return false;
			} else {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
			}

            if (y < y2) {
                y++;
            } else if (y > y2) {
                y--;
            }
//    		TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
            currPos.set(x, y, z);
            
            if (isObscured(world, currPos)) {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//				try {
//					Thread.sleep(450);
//				} catch (InterruptedException ignored) {}
				return false;
			} else {
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//				TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
			}
        }
//		try {
//		Thread.sleep(250);
//	} catch (InterruptedException ignored) {}

		return true;
	}
    
    private static boolean isObscured(WorldView world, BlockPos pos) {
    	return ((world.getBlockState(pos).isFullCube(world, pos)
    			|| world.getBlockState(pos).getBlock() instanceof LeavesBlock) && !hasBiggerCollisionShapeThanAbove(world, pos) || 
				hasBiggerCollisionShapeThanAbove(world, pos) ) 
		&& !(world.getBlockState(pos).getBlock() instanceof SlabBlock)
		&& !(world.getBlockState(pos).getBlock() instanceof CarpetBlock)
		&& !(world.getBlockState(pos).getBlock() instanceof DaylightDetectorBlock)
		|| (world.getBlockState(pos.up()).isFullCube(world, pos.up())
				|| (world.getBlockState(pos.up()).getBlock() instanceof LeavesBlock)) 
		&& !(world.getBlockState(pos.up()).getBlock() instanceof SlabBlock)
		&& !(world.getBlockState(pos.up()).getBlock() instanceof CarpetBlock)
		&& !(world.getBlockState(pos.up()).getBlock() instanceof DaylightDetectorBlock);
    }
    
    public static boolean hasBiggerCollisionShapeThanAbove(WorldView world, BlockPos pos) {
        // Get the block states of the block at pos and the two blocks above it
        BlockState blockState = world.getBlockState(pos);
        if (blockState.getBlock() instanceof LadderBlock) return false;
        BlockState aboveBlockState1 = world.getBlockState(pos.up(1));
        BlockState aboveBlockState2 = world.getBlockState(pos.up(2));

        // Get the collision shapes of these blocks
        VoxelShape blockShape = blockState.getCollisionShape(world, pos);
        VoxelShape aboveBlockShape1 = aboveBlockState1.getCollisionShape(world, pos.up(1));
        VoxelShape aboveBlockShape2 = aboveBlockState2.getCollisionShape(world, pos.up(2));
        
        // Calculate the volume of the collision shapes
        double blockVolume = getShapeVolume(blockShape);
        double aboveBlockVolume1 = getShapeVolume(aboveBlockShape1);
        double aboveBlockVolume2 = getShapeVolume(aboveBlockShape2);

        // Compare the volumes
        return blockVolume > aboveBlockVolume1 && blockVolume > aboveBlockVolume2;
    }
    
    private static double getShapeVolume(VoxelShape shape) {
        // Iterate over the shape's bounding boxes and sum their volumes
        return shape.getBoundingBoxes().stream()
                .mapToDouble(box -> (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ))
                .sum();
    }
    
    private List<BlockNode> getNodesIn2DCircule(int d, BlockNode parent, Goal goal) {
    	List<BlockNode> nodes = new ArrayList<>();
//        for( int py = -3; py < 3; py++ ) {
//	    	for (int id = 1; id <= (py < 0 ? d+(py*-1) : d); id++) {
//		        int px = id;
//		        int pz = 0;
//		        int dx = -1, dz = 1;
//		        int n = id * 4;
//    	        for( int i = 0; i < n; i++ ) {
//		            if( px == id && dx > 0 ) dx = -1;
//		            else if( px == -id && dx < 0 ) dx = 1;
//		            if( pz == id && dz > 0 ) dz = -1;
//		            else if( pz == -id && dz < 0 ) dz = 1;
//		            px += dx;
//		            pz += dz;
//		            BlockNode newNode = new BlockNode(this.x + px, this.y + py, this.z + pz, goal, this, ActionCosts.WALK_ONE_BLOCK_COST);
//		            nodes.add(newNode);
//	            }
//	        }
//		}
		double g = 32.656;  // Acceleration due to gravity in m/s^2
		double v_sprint = 5.8;  // Sprinting speed in m/s 5.8 based on meteor player.speed var
		double yMax = (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y != 0 ? getSlimeBounceHeight(parent.previous.y - parent.y)-0.5 :  2);
//		if (parent.wasOnLadder) {
//			yMax += 1;
//		}
//		if (yMax != 2.0)System.out.println(yMax);
		int distanceWanted = d;
		for( int py = -14; py < yMax; py++ ) {
		
			if (py < 0 && py < -5) {
				double t = Math.sqrt((2 * py*-1) / g);
				d = (int) Math.ceil(v_sprint * t);
		//	    		if (py == -1)
		//	    		System.out.println(d);
			}
			else
				d = distanceWanted+1;
            nodes.add(new BlockNode(this.x, this.y + py, this.z, goal, this, ActionCosts.WALK_ONE_BLOCK_COST));
			for (int id = 1; id <= d; id++) {
		        int px = id;
		        int pz = 0;
		        int dx = -1, dz = 1;
		        int n = id * 4;
		        for( int i = 0; i < n; i++ ) {
		            if( px == id && dx > 0 ) dx = -1;
		            else if( px == -id && dx < 0 ) dx = 1;
		            if( pz == id && dz > 0 ) dz = -1;
		            else if( pz == -id && dz < 0 ) dz = 1;
		            px += dx;
		            pz += dz;
//					TungstenMod.TEST.add(new Cuboid(new Vec3d(this.x + px, this.y + py, this.z + pz), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
		            BlockNode newNode = new BlockNode(this.x + px, this.y + py, this.z + pz, goal, this, ActionCosts.WALK_ONE_BLOCK_COST);
		            nodes.add(newNode);
		        }
		    }
		}
//		try {
//			Thread.sleep(250);
//		} catch (InterruptedException ignored) {}
//		TungstenMod.TEST.clear();
        return nodes;
    }
    
    private double getSlimeBounceHeight(double startHeight) {
    	return -0.0011 * Math.pow(startHeight, 2) + 0.43529 * startHeight + 1.7323;
    }
    
    private boolean shouldRemoveNode(WorldView world, BlockNode child) {

    	double heightDiff = this.y - child.y;
////		System.out.println(heightDiff);
		if ((world.getBlockState(child.getBlockPos()).getFluidState().isOf(Fluids.WATER) 
				|| world.getBlockState(child.getBlockPos()).getFluidState().isOf(Fluids.FLOWING_WATER))
				&& wasCleared(world, getBlockPos(), child.getBlockPos())) return false;
//		if(previous != null && previous.y-y < 1 && wasOnSlime || !wasOnSlime && child.y - y > 1) return true;
		if (!wasOnSlime && heightDiff > 0 && getPos().distanceTo(child.getPos()) >= 5) return true;
		if (world.getBlockState(child.getBlockPos().down()).getBlock() instanceof LadderBlock && wasCleared(world, getBlockPos(), child.getBlockPos())) return false;
		if (world.getBlockState(child.getBlockPos()).getBlock() instanceof LadderBlock && wasCleared(world, getBlockPos(), child.getBlockPos())) return false;
		
		if(world.getBlockState(child.getBlockPos().down()).isAir()
				&& !(world.getBlockState(child.getBlockPos()).getBlock() instanceof SlabBlock)) return true;
		if (world.getBlockState(child.getBlockPos().down()).getBlock() instanceof SlabBlock
				&& world.getBlockState(child.getBlockPos().down()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM) return true;
		if (world.getBlockState(child.getBlockPos().down()).getBlock() instanceof SeaPickleBlock) return true;
		if (world.getBlockState(child.getBlockPos()).getBlock() instanceof SlabBlock
				&& world.getBlockState(child.getBlockPos()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM) {
			if(!hasBiggerCollisionShapeThanAbove(world, child.getBlockPos())) return true;
		}
		else 
			if(!hasBiggerCollisionShapeThanAbove(world, child.getBlockPos().down()) 
				&& !(world.getBlockState(child.getBlockPos()).getBlock() instanceof CarpetBlock)) return true;
		if(world.getBlockState(child.getBlockPos()).isOf(Blocks.LAVA)) return true;
////		if(world.getBlockState(child.getBlockPos()).getBlock() instanceof SlabBlock) return true;
////		if(world.getBlockState(child.getBlockPos()).getBlock() instanceof CarpetBlock) return false;
		if(world.getBlockState(child.getBlockPos().down()).getBlock() instanceof LilyPadBlock) return true;
		if(world.getBlockState(child.getBlockPos().down()).getBlock() instanceof CarpetBlock) return true;
		if(world.getBlockState(child.getBlockPos().down()).getBlock() instanceof DaylightDetectorBlock) return true;
		if(world.getBlockState(child.getBlockPos()).getBlock() instanceof StairsBlock) return true;
		
		VoxelShape blockShape = world.getBlockState(child.getBlockPos().down()).getCollisionShape(world, child.getBlockPos().down());
		VoxelShape previousBlockShape = world.getBlockState(getBlockPos().down()).getCollisionShape(world, getBlockPos().down());
		
		if (!blockShape.isEmpty() && 
				blockShape.getBoundingBox().maxY > 1.3
				&& !previousBlockShape.isEmpty()
				&& previousBlockShape.getBoundingBox().maxY < 1.3
				&& previousBlockShape.getBoundingBox().maxY > 0.5
				&& (heightDiff > 0
				&& getPos().distanceTo(child.getPos()) > 4 )) return true;
		
		
		if(world.getBlockState(child.getBlockPos()).getBlock() instanceof SlabBlock
				&& world.getBlockState(child.getBlockPos()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM
				&& heightDiff > 0
				&& (world.getBlockState(getBlockPos().down()).getBlock() instanceof SlabBlock
				&& world.getBlockState(getBlockPos().down()).get(Properties.SLAB_TYPE) != SlabType.BOTTOM
				|| world.getBlockState(getBlockPos()).getBlock() instanceof SlabBlock
				&& world.getBlockState(getBlockPos()).get(Properties.SLAB_TYPE) != SlabType.BOTTOM
				)
				) return true;


		if(!blockShape.isEmpty() && 
				blockShape.getBoundingBox().maxY > 1.3
				&& y - child.y < 0
				&& !(world.getBlockState(getBlockPos().down()).getBlock() instanceof SlabBlock
				&& world.getBlockState(getBlockPos().down()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM)
				&& !previousBlockShape.isEmpty() && 
				previousBlockShape.getBoundingBox().maxY < 1.3
				) return true;
		if((world.getBlockState(child.getBlockPos()).getBlock() instanceof SlabBlock
				&& world.getBlockState(child.getBlockPos()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM
				||  !blockShape.isEmpty() && 
				blockShape.getBoundingBox().maxY > 1.3)
				&& !(world.getBlockState(getBlockPos()).getBlock() instanceof SlabBlock
						&& world.getBlockState(getBlockPos()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM)
				&& y - child.y < 0
				&& !previousBlockShape.isEmpty() && 
				previousBlockShape.getBoundingBox().maxY < 1.3
				) return true;

		
		if (!wasCleared(world, getBlockPos(), child.getBlockPos())) {
            return true;
        }

		return false;
    }

}
