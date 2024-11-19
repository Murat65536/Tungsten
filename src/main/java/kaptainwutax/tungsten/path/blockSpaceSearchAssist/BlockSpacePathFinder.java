package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.path.PathFinder;
import kaptainwutax.tungsten.path.calculators.ActionCosts;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Line;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class BlockSpacePathFinder {
	
	public static boolean active = false;
	public static Thread thread = null;
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final BlockNode[] bestSoFar = new BlockNode[COEFFICIENTS.length];
	private static final double minimumImprovement = 0.21;
	protected static final double MIN_DIST_PATH = 5;
	private BlockNode startNode = null;
	
	
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
		thread.start();
	}
	
	public static Optional<List<BlockNode>> search(WorldView world, Vec3d target) {
		ClientPlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);
		return search(world, new BlockNode(player.getBlockPos(), new Goal((int) target.x, (int) target.y, (int) target.z)), target);
	}
	
	public static Optional<List<BlockNode>> search(WorldView world, BlockNode start, Vec3d target) {
		Goal goal = new Goal((int) target.x, (int) target.y, (int) target.z);
		boolean failing = true;
        int numNodes = 0;
        int timeCheckInterval = 1 << 6;
        long startTime = System.currentTimeMillis();
        long primaryTimeoutTime = startTime + 5000020L;
		
		TungstenMod.RENDERERS.clear();
		Debug.logMessage("Searchin...");
		
		double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];//keep track of the best node by the metric of (estimatedCostToGoal + cost / COEFFICIENTS[i])
		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.cost;
            bestSoFar[i] = start;
        }

		BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
		Set<BlockNode> closed = new HashSet<>();
		openSet.insert(start);
		target = target.subtract(0.5, 0, 0.5);
		while(!openSet.isEmpty()) {
			if (TungstenMod.PATHFINDER.stop) break;
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
			if(next.getPos().squaredDistanceTo(target) <= 0.4D && !failing && next.wasCleared(world, next.previous.getBlockPos(), next.getBlockPos())) {
				TungstenMod.RENDERERS.clear();
//				TungstenMod.TEST.clear();
				BlockNode n = next;
				List<BlockNode> path = new ArrayList<>();

				Debug.logMessage("FOUND IT");
				while(n.previous != null) {
					path.add(n);
					TungstenMod.RENDERERS.add(new Line(new Vec3d(n.previous.x + 0.5, n.previous.y + 0.1, n.previous.z + 0.5), new Vec3d(n.x + 0.5, n.y + 0.1, n.z + 0.5), Color.RED));
	                TungstenMod.RENDERERS.add(new Cuboid(n.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.BLUE));
					n = n.previous;
				}

				path.add(n);
				Collections.reverse(path);
				return Optional.of(path);
			}
			
			if(TungstenMod.RENDERERS.size() > 9000) {
				TungstenMod.RENDERERS.clear();
			}
			 renderPathSoFar(next);
			 PathFinder.renderPathCurrentlyExecuted();

			 TungstenMod.RENDERERS.add(new Cuboid(next.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));

			
			for(BlockNode child : next.getChildren(world, goal)) {
				if (TungstenMod.PATHFINDER.stop) return Optional.empty();
//				if (closed.contains(child)) continue;
				

				updateNode(next, child, target);
				
                if (child.isOpen()) {
                    openSet.update(child);
                } else {
                    openSet.insert(child);//dont double count, dont insert into open set if it's already there
                }
                
                
                failing = updateBestSoFar(child, bestHeuristicSoFar, target);
                TungstenMod.RENDERERS.add(new Cuboid(child.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.BLUE));
			}
//			try {
//                Thread.sleep(150);
//            } catch (InterruptedException ignored) {}
		}
//		TungstenMod.TEST.clear();
		if (openSet.isEmpty()) {
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
                if (logInfo) {
                    if (COEFFICIENTS[i] >= 3) {
                        System.out.println("Warning: cost coefficient is greater than three! Probably means that");
                        System.out.println("the path I found is pretty terrible (like sneak-bridging for dozens of blocks)");
                        System.out.println("But I'm going to do it anyway, because yolo");
                    }
                    System.out.println("Path goes for " + Math.sqrt(dist) + " blocks");
                }
                BlockNode n = bestSoFar[i];
				List<BlockNode> path = new ArrayList<>();
				while(n.previous != null) {
					path.add(n);
					TungstenMod.RENDERERS.add(new Line(new Vec3d(n.previous.x + 0.5, n.previous.y + 0.1, n.previous.z + 0.5), new Vec3d(n.x + 0.5, n.y + 0.1, n.z + 0.5), Color.RED));
	                TungstenMod.RENDERERS.add(new Cuboid(n.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.BLUE));
					n = n.previous;
				}
				path.add(n);
				Collections.reverse(path);
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }
	
	private static double computeHeuristic(Vec3d position, Vec3d target) {
	    double dx = position.x - target.x;
	    double dy = (position.y - target.y)*5;
	    double dz = position.z - target.z;
	    return (Math.sqrt(dx * dx + dy * dy + dz * dz)) * 80;
	}
	
	private static void updateNode(BlockNode current, BlockNode child, Vec3d target) {
	    Vec3d childPos = child.getPos();
	    double tentativeCost = child.cost + ActionCosts.WALK_ONE_BLOCK_COST + (childPos.distanceTo(current.getPos()) > 4 ? 4000 : 0); // Assuming uniform cost for each step

	    double estimatedCostToGoal = computeHeuristic(childPos, target);

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
	
	protected static double getDistFromStartSq(BlockNode n, Vec3d target) {
        double xDiff = n.getPos().x - target.x;
        double yDiff = n.getPos().y - target.y;
        double zDiff = n.getPos().z - target.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }
	
	private static void renderPathSoFar(BlockNode n) {
		int i = 0;
		while(n.previous != null) {
			TungstenMod.RENDERERS.add(new Line(new Vec3d(n.previous.x + 0.5, n.previous.y + 0.1, n.previous.z + 0.5), new Vec3d(n.x + 0.5, n.y + 0.1, n.z + 0.5), Color.WHITE));
			i++;
			n = n.previous;
		}
//		for (int i = 0; i < COEFFICIENTS.length; i++) {
//			if (bestSoFar[i] == null) {
//                continue;
//            }
//			BlockNode a = bestSoFar[i];
//			while(a.previous != null) {
//				TungstenMod.RENDERERS.add(new Line(new Vec3d(a.previous.x + 0.5, a.previous.y + 0.1, a.previous.z + 0.5), new Vec3d(a.x + 0.5, a.y + 0.1, a.z + 0.5), Color.WHITE));
//				i++;
//				a = a.previous;
//			}
//		}
	}

}
