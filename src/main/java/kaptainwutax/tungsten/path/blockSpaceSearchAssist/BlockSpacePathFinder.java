package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.constants.pathfinding.CostConstants;
import kaptainwutax.tungsten.path.common.BinaryHeapOpenSet;
import kaptainwutax.tungsten.path.common.IOpenSet;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.movement.StraightMovementHelper;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class BlockSpacePathFinder {
	
	public static boolean active = false;
	public static Thread thread = null;
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final BlockNode[] bestSoFar = new BlockNode[COEFFICIENTS.length];
	private static final double minimumImprovement = 0.21;
	protected static final double MIN_DIST_PATH = 5;
	
	
	public static void find(WorldView world, Vec3d target) {
		if(active)return;
		active = true;

		thread = new Thread(() -> {
			try {
				search(world, target);
			} catch(Exception e) {
				e.printStackTrace();
			}

			active = false;
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
		if (!world.getBlockState(player.getBlockPos()).isAir() && BlockShapeChecker.getShapeVolume(player.getBlockPos()) != 0) {
			return search(world, new BlockNode(player.getBlockPos().up(), new Goal((int) target.x, (int) target.y, (int) target.z)), target);
		}
		return search(world, new BlockNode(player.getBlockPos(), new Goal((int) target.x, (int) target.y, (int) target.z)), target);
	}
	
	private static Optional<List<BlockNode>> search(WorldView world, BlockNode start, Vec3d target, boolean generateDeep) {
		Goal goal = new Goal((int) target.x, (int) target.y, (int) target.z);
		boolean failing = true;
        int numNodes = 0;
        int timeCheckInterval = 1 << 6;
        long startTime = System.currentTimeMillis();
        long primaryTimeoutTime = startTime + 1800L;
		
		TungstenMod.RENDERERS.clear();
		Debug.logMessage("Searchin...");
		
		double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];//keep track of the best node by the metric of (estimatedCostToGoal + cost / COEFFICIENTS[i])
		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.cost;
            bestSoFar[i] = start;
        }

		IOpenSet<BlockNode> openSet = new BinaryHeapOpenSet<>();
		Set<BlockNode> closed = new HashSet<>();
		openSet.insert(start);
		target = target.subtract(0.5, 0, 0.5);
		while(!openSet.isEmpty()) {
			if (TungstenMod.PATHFINDER.stop.get()) {
				RenderHelper.clearRenderers();
				break;
			}
			TungstenMod.RENDERERS.clear();
			if ((numNodes & (timeCheckInterval - 1)) == 0) { // only call this once every 64 nodes (about half a millisecond)
                long now = System.currentTimeMillis(); // since nanoTime is slow on windows (takes many microseconds)
                if ((!failing && now - primaryTimeoutTime >= 0)) {
                    break;
                }
            }
			BlockNode next = openSet.removeLowest();
			
			if (closed.contains(next)) continue;
			
			closed.add(next);
			if(TungstenMod.pauseKeyBinding.isPressed()) break;
			if(isPathComplete(next, target, failing)) {
				TungstenMod.RENDERERS.clear();
				List<BlockNode> path = generatePath(next);

				Debug.logMessage("FOUND IT");
				
				return Optional.of(path);
			}
			
			if(TungstenMod.RENDERERS.size() > 3000) {
				TungstenMod.RENDERERS.clear();
			}
			 RenderHelper.renderPathSoFar(next);

			
			for(BlockNode child : next.getChildren(world, goal, generateDeep)) {
				if (TungstenMod.PATHFINDER.stop.get()) return Optional.empty();
//				if (closed.contains(child)) continue;
				

				updateNode(next, child, target);
				
                if (child.isOpen()) {
                    openSet.update(child);
                } else {
                    openSet.insert(child);//dont double count, dont insert into open set if it's already there
                }
                
                
                failing = updateBestSoFar(child, bestHeuristicSoFar, target);
			}
		}

		if (openSet.isEmpty()) {
			if (!generateDeep) {
				return search(world, start, target, true);
			}
			Debug.logWarning("Ran out of nodes");
			return Optional.empty();
		}
        Optional<List<BlockNode>> result = bestSoFar(true, numNodes, start);
		return result;
	}
	
	protected static Optional<List<BlockNode>> bestSoFar(boolean logInfo, int numNodes, BlockNode startNode) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = 0;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            if (bestSoFar[i] == null) {
                continue;
            }
            double dist = getDistFromStartSq(bestSoFar[i], startNode.getPos());
            if (dist > bestDist) {
                bestDist = dist;
                continue;
            }
            if (dist > MIN_DIST_PATH * MIN_DIST_PATH) { // square the comparison since distFromStartSq is squared
                BlockNode n = bestSoFar[i];
				List<BlockNode> path = generatePath(n);
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }
	
	private static double computeHeuristic(Vec3d position, Vec3d target) {
		double xzMultiplier = 1.2;
	    double dx = (position.x - target.x)*xzMultiplier;
	    double dy = 0;
	    double dz = (position.z - target.z)*xzMultiplier;
	    if (BlockStateChecker.isAnyWater(TungstenMod.mc.world.getBlockState(new BlockPos((int) position.x, (int) position.y, (int) position.z)))) {
	    	dy = (position.y - target.y)*3.5;
	    } else if (DistanceCalculator.getHorizontalManhattanDistance(position, target) < 32) {
	    	dy = (position.y - target.y)*1.5;
	    }
	    return (Math.sqrt(dx * dx + dy * dy + dz * dz)) * 3;
	}
	
	private static void updateNode(BlockNode current, BlockNode child, Vec3d target) {
	    Vec3d childPos = child.getPos();
	    double tentativeCost = child.cost + CostConstants.BaseCosts.WALK_ONE_BLOCK_COST; // Assuming uniform cost for each step
//	    tentativeCost += BlockStateChecker.isAnyWater(TungstenMod.mc.world.getBlockState(child.getBlockPos())) ? 50 : 0; // Assuming uniform cost for each step

	    double estimatedCostToGoal = computeHeuristic(childPos, target) + DistanceCalculator.getHorizontalEuclideanDistance(current.getPos(true), child.getPos(true)) * 4;

	    child.previous = current;
	    child.cost = tentativeCost;
	    child.estimatedCostToGoal = estimatedCostToGoal;
	    child.combinedCost = tentativeCost + estimatedCostToGoal;
	}
	
	private static boolean updateBestSoFar(BlockNode child, double[] bestHeuristicSoFar, Vec3d target) {
		boolean failing = false;
	    for (int i = 0; i < COEFFICIENTS.length; i++) {
	        double heuristic = child.estimatedCostToGoal + child.cost / COEFFICIENTS[i];
            bestSoFar[i] = child;
	        if (bestHeuristicSoFar[i] - heuristic < minimumImprovement) {
	            bestHeuristicSoFar[i] = heuristic;
	            if (failing && getDistFromStartSq(child, target) > MIN_DIST_PATH * MIN_DIST_PATH) {
                    failing = false;
                }
	        }
	    }
	    return failing;
	}
	
	private static double getDistFromStartSq(BlockNode n, Vec3d target) {
        double xDiff = n.getPos().x - target.x;
        double yDiff = n.getPos().y - target.y;
        double zDiff = n.getPos().z - target.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }

	private static boolean isPathComplete(BlockNode node, Vec3d target, boolean failing) {
        return node.getPos().squaredDistanceTo(target) < 1.0D && !failing;
    }
	
	private static List<BlockNode> generatePath(BlockNode node) {
		BlockNode n = node;
		List<BlockNode> path = new ArrayList<>();

		Debug.logMessage("FOUND IT");
		path.add(n);
		while(n.previous != null) {
		        BlockState state = TungstenMod.mc.world.getBlockState(n.getBlockPos());
		        boolean isWater = BlockStateChecker.isAnyWater(state);
		        BlockNode lastN = path.getLast();
		        boolean canGetFromLastNToCurrent = StraightMovementHelper.isPossible(TungstenMod.mc.world, lastN.getBlockPos(), n.getBlockPos());
		        double heightDiff = DistanceCalculator.getJumpHeight(lastN.getPos(true).getY(), n.getPos(true).getY());
				if (heightDiff != 0) {
					if (isWater && n.previous.previous != null)
					{
						path.add(n);
						path.add(n.previous.previous);
					} else if (!isWater) {
						path.add(n);
//						path.add(n.previous);
					}
				} else if (
						!isWater &&
						(DistanceCalculator.getHorizontalEuclideanDistance(n.previous.getBlockPos(), n.getBlockPos()) > 1.44 ||
						!canGetFromLastNToCurrent)
						) {
						path.add(n);
//						if (n.previous != null) path.add(n.previous);
				}
			n = n.previous;
		}

		path.add(n);
		
		List<BlockNode> path2 = new ArrayList<>();

    	path2.add(path.get(0));
		for (int i = 1; i < path.size(); i++) {
			BlockNode blockNode = path.get(i);
			BlockNode lastBlockNode = path.get(i-1);
	        boolean canGetFromLastNToCurrent = StraightMovementHelper.isPossible(TungstenMod.mc.world, lastBlockNode.getBlockPos(), blockNode.getBlockPos());
	        if (!canGetFromLastNToCurrent || BlockStateChecker.isAnyWater(TungstenMod.mc.world.getBlockState(lastBlockNode.getBlockPos()))) {
	        	if (!path2.contains(lastBlockNode)) path2.add(lastBlockNode);
	        	path2.add(blockNode);
	        } else if (lastBlockNode.getPos(true).distanceTo(blockNode.getPos(true)) > 1.44 || lastBlockNode.getBlockPos().getY() - blockNode.getBlockPos().getY() != 0) {
	        	path2.add(blockNode);
	        }
		}
		
		Collections.reverse(path2);
		return path2;
	}
	
	
}
