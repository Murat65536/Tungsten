package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.constants.pathfinding.BlockSpacePathfindingConstants;
import kaptainwutax.tungsten.constants.pathfinding.CostConstants;
import kaptainwutax.tungsten.constants.pathfinding.PathfindingConstants;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.path.common.BinaryHeapOpenSet;
import kaptainwutax.tungsten.path.common.IOpenSet;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class BlockSpacePathfinder {
	
	private static volatile boolean active = false;
	
	public static void find(WorldView world, Vec3d target) {
		if (active) return;
		active = true;

		Thread thread = new Thread(() -> {
			try {
				search(world, target);
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				active = false;
			}
		});
		thread.setName("BlockSpacePathFinder");
		thread.start();
	}
	
	public static Optional<List<BlockNode>> search(WorldView world, Vec3d target) {
		return search(world, target, false);
	}

	public static Optional<List<BlockNode>> search(WorldView world, BlockNode start, Vec3d target) {
		return search(world, start, target, false);
	}
	
	private static Optional<List<BlockNode>> search(WorldView world, Vec3d target, boolean generateDeep) {
		ClientPlayerEntity player = Objects.requireNonNull(TungstenMod.mc.player);
		BlockPos startPos = player.getBlockPos();
		// If standing in a block, try starting from the block above
		if (!world.getBlockState(startPos).isAir() && BlockShapeChecker.getShapeVolume(startPos) != 0) {
			startPos = startPos.up();
		}
		return search(world, new BlockNode(startPos, new Goal((int) target.x, (int) target.y, (int) target.z)), target, generateDeep);
	}
	
	private static Optional<List<BlockNode>> search(WorldView world, BlockNode start, Vec3d target, boolean generateDeep) {
		Goal goal = new Goal((int) target.x, (int) target.y, (int) target.z);
		
        int numNodes = 0;
        long startTime = System.currentTimeMillis();
        long primaryTimeoutTime = startTime + BlockSpacePathfindingConstants.PRIMARY_TIMEOUT_MS;
		
		TungstenMod.RENDERERS.clear();
		Debug.logMessage("Searching...");
		
		BlockNode[] bestSoFar = new BlockNode[PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length];
		double[] bestHeuristicSoFar = new double[PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length];
		
		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.estimatedCostToGoal;
            bestSoFar[i] = start;
        }

		IOpenSet<BlockNode> openSet = new BinaryHeapOpenSet<>();
		Set<BlockNode> closed = new HashSet<>();
		Map<Long, Double> bestCosts = new HashMap<>();
		
		start.cost = 0;
		start.combinedCost = start.estimatedCostToGoal;
		openSet.insert(start);
		bestCosts.put(BlockPos.asLong(start.x, start.y, start.z), 0.0);
		
		while(!openSet.isEmpty()) {
			if (TungstenMod.PATHFINDER.stop.get()) {
				RenderHelper.clearRenderers();
				return Optional.empty();
			}

			// Time check
			if ((numNodes & (BlockSpacePathfindingConstants.TIME_CHECK_INTERVAL - 1)) == 0) { 
                long now = System.currentTimeMillis();
                if (now >= primaryTimeoutTime) {
                	Debug.logWarning("Pathfinding timed out");
                    break;
                }
            }

			BlockNode next = openSet.removeLowest();
			
			if (closed.contains(next)) continue;
			closed.add(next);
			numNodes++;
			
			if(TungstenMod.pauseKeyBinding.isPressed()) break;
			
			if(isPathComplete(next, target)) {
				TungstenMod.RENDERERS.clear();
				List<BlockNode> path = generatePath(next);
				Debug.logMessage("Path found! Length: " + path.size());
				return Optional.of(path);
			}
			
			if(TungstenMod.RENDERERS.size() > 3000) {
				TungstenMod.RENDERERS.clear();
			}
			RenderHelper.renderPathSoFar(next);

			for(BlockNode child : next.getChildren(world, goal, generateDeep)) {
				if (TungstenMod.PATHFINDER.stop.get()) return Optional.empty();
				if (closed.contains(child)) continue;

				updateNode(next, child, target);
				
				long childHash = BlockPos.asLong(child.x, child.y, child.z);
				Double bestCost = bestCosts.get(childHash);
				
				if (bestCost == null || child.cost < bestCost) {
					bestCosts.put(childHash, child.cost);
					openSet.insert(child);
					updateBestSoFar(child, bestSoFar, bestHeuristicSoFar, target);
				}
			}
		}

		if (openSet.isEmpty()) {
			if (!generateDeep) {
				return search(world, start, target, true);
			}
			Debug.logWarning("Ran out of nodes");
		}
		
        return bestSoFar(bestSoFar, start);
	}
	
	private static Optional<List<BlockNode>> bestSoFar(BlockNode[] bestSoFar, BlockNode startNode) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = 0;
        BlockNode bestNode = null;
        
        for (int i = 0; i < PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length; i++) {
            if (bestSoFar[i] == null) {
                continue;
            }
            // Use simple distance heuristic for bestSoFar selection if cost is high
            // Or just pick the one that got closest to the target
            double distSq = getDistFromStartSq(bestSoFar[i], startNode.getPos());
            if (distSq > bestDist) {
                bestDist = distSq;
                bestNode = bestSoFar[i];
            }
        }
        
        if (bestNode != null) {
        	// Only return if it's somewhat far from start, otherwise it's useless
        	if (bestDist > BlockSpacePathfindingConstants.MIN_PATH_LENGTH * BlockSpacePathfindingConstants.MIN_PATH_LENGTH) {
        		return Optional.of(generatePath(bestNode));
        	}
        }
        
        return Optional.empty();
    }
	
	private static double computeHeuristic(Vec3d position, Vec3d target) {
	    double dx = Math.abs(position.x - target.x);
	    double dy = Math.abs(position.y - target.y);
	    double dz = Math.abs(position.z - target.z);
	    
	    double cost = (dx + dz) * BlockSpacePathfindingConstants.Heuristics.XZ_MULTIPLIER;
	    
	    // Add vertical cost
	    if (BlockStateChecker.isAnyWater(TungstenMod.mc.world.getBlockState(new BlockPos((int) position.x, (int) position.y, (int) position.z)))) {
	    	cost += dy * BlockSpacePathfindingConstants.Heuristics.Y_MULTIPLIER_WATER;
	    } else {
	    	cost += dy; 
	    }
	    
	    return cost * CostConstants.BaseCosts.WALK_ONE_BLOCK_COST;
	}
	
	private static void updateNode(BlockNode current, BlockNode child, Vec3d target) {
	    double distance = DistanceCalculator.getHorizontalEuclideanDistance(current.getPos(true), child.getPos(true));
	    // If distance is 0 (e.g. vertical move), treat as 1 block cost for now to avoid zero cost cycles
	    if (distance < 0.1) distance = 1.0;
	    
	    double moveCost = distance * CostConstants.BaseCosts.WALK_ONE_BLOCK_COST;
	    
	    double tentativeCost = current.cost + moveCost;
	    
	    double estimatedCostToGoal = computeHeuristic(child.getPos(), target);

	    child.previous = current;
	    child.cost = tentativeCost;
	    child.estimatedCostToGoal = estimatedCostToGoal;
	    child.combinedCost = tentativeCost + estimatedCostToGoal;
	}
	
	private static void updateBestSoFar(BlockNode child, BlockNode[] bestSoFar, double[] bestHeuristicSoFar, Vec3d target) {
	    for (int i = 0; i < PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length; i++) {
	        double heuristic = child.estimatedCostToGoal + child.cost / PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS[i];
	        if (bestSoFar[i] == null || heuristic < bestHeuristicSoFar[i]) {
	            bestHeuristicSoFar[i] = heuristic;
	            bestSoFar[i] = child;
	        }
	    }
	}
	
	private static double getDistFromStartSq(BlockNode n, Vec3d target) {
        double xDiff = n.getPos().x - target.x;
        double yDiff = n.getPos().y - target.y;
        double zDiff = n.getPos().z - target.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }

	private static boolean isPathComplete(BlockNode node, Vec3d target) {
        return node.getPos().squaredDistanceTo(target) < 1.5D; // Increased slightly tolerance
    }
	
	private static List<BlockNode> generatePath(BlockNode node) {
		List<BlockNode> path = new ArrayList<>();
		BlockNode current = node;
		
		while(current != null) {
			path.add(current);
			current = current.previous;
		}
		
		Collections.reverse(path);
		return path;
	}
}

