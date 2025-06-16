package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

import com.google.common.util.concurrent.AtomicDoubleArray;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.helpers.AgentChecker;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.movement.StreightMovementHelper;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.path.calculators.BinaryHeapOpenSet;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class PathFinder {

	public AtomicBoolean active = new AtomicBoolean(false);
	public AtomicBoolean stop = new AtomicBoolean(false);
	public Thread thread = null;
	private Set<Vec3d> closed = Collections.synchronizedSet(new HashSet<>());
	private AtomicDoubleArray bestHeuristicSoFar;
	private BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final AtomicReferenceArray<Node> bestSoFar = new AtomicReferenceArray<Node>(COEFFICIENTS.length);
	private static final double minimumImprovement = 0.21;
	private static Optional<List<BlockNode>> blockPath = Optional.empty();
	protected static final double MIN_DIST_PATH = 5;
	protected static AtomicInteger NEXT_CLOSEST_BLOCKNODE_IDX = new AtomicInteger(1);
	
	
	synchronized public void find(WorldView world, Vec3d target) {
		if(active.get() || thread != null)return;
		active.set(true);
		NEXT_CLOSEST_BLOCKNODE_IDX.set(1);

		thread = new Thread(() -> {
			try {
				NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
				search(world, target);
			} catch(Exception e) {
				e.printStackTrace();
			}

			active.set(false);
			this.thread = null;
			closed.clear();
			blockPath = Optional.empty();
			NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
			
		});
		thread.setName("PathFinder");
		thread.setPriority(4);
		thread.start();
//		try {
//			thread.join();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	private boolean checkForFallDamage(Node n) {
		if (TungstenMod.ignoreFallDamage) return false;
		if (BlockStateChecker.isAnyWater(TungstenMod.mc.world.getBlockState(n.agent.getBlockPos()))) return false;
		if (n.parent == null) return false;
		if (Thread.currentThread().isInterrupted()) return false;
		Node prev = null;
		do {
			if (Thread.currentThread().isInterrupted()) return false;
			if (stop.get()) break;
			if (prev == null) {
				prev = n.parent;
			} else {
				prev = prev.parent;
			}
			double currFallDist = DistanceCalculator.getJumpHeight(prev.agent.getPos().y, n.agent.getPos().y);
			if (currFallDist < -3) {
				return true;
			}
		} while (!prev.agent.onGround);

		if (DistanceCalculator.getJumpHeight(prev.agent.getPos().y, n.agent.getPos().y) < -3) {
//			RenderHelper.clearRenderers();
//        	RenderHelper.renderNode(prev);
//        	TungstenMod.RENDERERS.add(new Cuboid(prev.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.3D, 0.8D, 0.3D), prev.color));
//        	RenderHelper.renderNode(n);
//        	try {
// 				Thread.sleep(150);
// 			} catch (InterruptedException e) {
// 				// TODO Auto-generated catch block
// 				e.printStackTrace();
// 			}
			return true;
		}
		return false;
	}

	private void search(WorldView world, Vec3d target) {
		search(world, null, target);
	}
	
	private void search(WorldView world, Node start, Vec3d target) {
	    boolean failing = true;
	    TungstenMod.RENDERERS.clear();
	    NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
	
	    long startTime = System.currentTimeMillis();
	    long primaryTimeoutTime = startTime + 250L;
	    int numNodesConsidered = 1;
	    int timeCheckInterval = 1 << 3;
	    double minVelocity = BlockStateChecker.isAnyWater(world.getBlockState(new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ()))) ? 0.2 :  0.07;
	
	    ClientPlayerEntity player = Objects.requireNonNull(TungstenMod.mc.player);
	    if (player.getPos().distanceTo(target) < 1.0) {
	        Debug.logMessage("Already at target location!");
	        return;
	    }
	    if (start == null) start = initializeStartNode(player, target);
	    if (blockPath.isEmpty()) {
		    Optional<List<BlockNode>> blockPath = findBlockPath(world, target);
		    if (blockPath.isPresent()) {
	        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
	        	PathFinder.blockPath = blockPath;
	        }
	    }
//	    if (blockPath.isEmpty()) {
//	    	Debug.logWarning("Failed!");
//	    	return;
//	    }
	
	    bestHeuristicSoFar = initializeBestHeuristics(start);
	    openSet = new BinaryHeapOpenSet();
	    openSet.insert(start);
	
	    while (!openSet.isEmpty()) {
	        if (stop.get()) {
	        	RenderHelper.clearRenderers();
	            break;
	        }
	
	        if (blockPath.isPresent() && TungstenMod.BLOCK_PATH_RENDERER.isEmpty()) {
	        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
	        }
	
	        Node next = openSet.removeLowest();
            // Search for a path without fall damage
            if (checkForFallDamage(next)) {
            	continue;
            }
	
	        if (shouldSkipNode(next, target, closed, blockPath)) {
	            continue;
	        }

	
	        if (isPathComplete(next, target, failing)) {
	            if (tryExecutePath(next, target, minVelocity)) {
	        		TungstenMod.RENDERERS.clear();
	        		TungstenMod.TEST.clear();
	    			closed.clear();
	    			PathFinder.blockPath = Optional.empty();
	                return;
	            }
	        } else if (NEXT_CLOSEST_BLOCKNODE_IDX.get() == (blockPath.get().size()-1) && blockPath.get().getLast().getPos(true).distanceTo(target) > 5) {
	        	if (tryExecutePath(next, blockPath.get().getLast().getPos(true), 5)) {
	        		TungstenMod.RENDERERS.clear();
	        		TungstenMod.TEST.clear();
	    			closed.clear();
	    			PathFinder.blockPath = findBlockPath(world, blockPath.get().getLast(), target);
	    		    if (blockPath.isPresent()) {
	    		    	NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
	    	        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
	    	        }
	            }
	        }
	
	        if (shouldResetSearch(numNodesConsidered, blockPath, next, target)) {
	        	blockPath = resetSearch(next, world, blockPath, target);
	            openSet = new BinaryHeapOpenSet();
	            start = initializeStartNode(next, target);
	            openSet.insert(start);
	            continue;
	        }
	
	        if ((numNodesConsidered & (timeCheckInterval - 1)) == 0) {
	            if (handleTimeout(startTime, primaryTimeoutTime, next, target, start, player, closed)) {
	                return;
	            }
	        }
        	updateNextClosestBlockNodeIDX(blockPath.get(), next, closed);
	        
	        if (numNodesConsidered % 20 == 0) {
	        	RenderHelper.renderPathSoFar(next);
	        }
	
	        failing = processNodeChildren(world, next, target, blockPath, openSet, closed);
	        numNodesConsidered++;
//	        try {
//				Thread.sleep(250);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
	    }
	
	    if (stop.get()) {
	        stop.set(false);
	    } else if (openSet.isEmpty()) {
	        Debug.logMessage("Ran out of nodes!");
	    }
	    RenderHelper.clearRenderers();
		closed.clear();
		PathFinder.blockPath = Optional.empty();
	}
	protected static Optional<List<Node>> bestSoFar(boolean logInfo, int numNodes, Node startNode) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = 0;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            if (bestSoFar.get(i) == null) {
                continue;
            }
            double dist = computeHeuristic(startNode.agent.getPos(), startNode.agent.onGround || startNode.agent.slimeBounce, bestSoFar.get(i).agent.getPos());
            if (dist > bestDist) {
                bestDist = dist;
            }
//            if (dist < MIN_DIST_PATH * MIN_DIST_PATH) { // square the comparison since distFromStartSq is squared
//                if (logInfo) {
//                    if (COEFFICIENTS[i] >= 3) {
//                        System.out.println("Warning: cost coefficient is greater than three! Probably means that");
//                        System.out.println("the path I found is pretty terrible (like sneak-bridging for dozens of blocks)");
//                        System.out.println("But I'm going to do it anyway, because yolo");
//                    }
//                    System.out.println("Path goes for " + Math.sqrt(dist) + " blocks");
//                }
                Node n = bestSoFar.get(i);
                if (!n.agent.onGround) continue;
                List<Node> path = new ArrayList<>();
				while(n.parent != null) {
					path.add(n);
					n = n.parent;
				}

				path.add(n);
				Collections.reverse(path);
                return Optional.of(path);
            }
//        }
        return Optional.empty();
    }
	
	private void clearParentsForBestSoFar(Node node) {
		for (int i = 0; i < COEFFICIENTS.length; i++) {
			bestSoFar.set(i, null);
		}
	}

	private boolean shouldSkipChild(Node child, Vec3d target, Set<Vec3d> closed, Optional<List<BlockNode>> blockPath) {
	    return child.agent.touchingWater && shouldSkipNode(child, target, closed, blockPath);
	}
	
	private boolean shouldSkipNode(Node node, Vec3d target, Set<Vec3d> closed, Optional<List<BlockNode>> blockPath) {
	    BlockNode bN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
	    BlockNode lBN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
	    boolean isBottomSlab = BlockStateChecker.isBottomSlab(TungstenMod.mc.world.getBlockState(bN.getBlockPos().down()));
	    Vec3d agentPos = node.agent.getPos();
	    Vec3d parentAgentPos = node.parent == null ? null : node.parent.agent.getPos();
	    if (!isBottomSlab && !node.agent.onGround && agentPos.y < bN.y && lBN != null && lBN.y <= bN.y && parentAgentPos != null && parentAgentPos.y > agentPos.y) {
	    	return true;
	    }
	    return shouldNodeBeSkipped(node, target, closed, true, 
	        blockPath.isPresent() && (
	            blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingLongJump() ||
	            blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingNeo() ||
	            blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get() - 1).isDoingCornerJump()
	        ),
	        blockPath.isPresent() && !blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingNeo()
	    );
	}
	
	private static boolean shouldNodeBeSkipped(Node n, Vec3d target, Set<Vec3d> closed, boolean addToClosed, boolean isDoingLongJump, boolean shouldAddYaw) {

		int hashCode = n.hashCode(1, shouldAddYaw);
	    Vec3d agentPos = n.agent.getPos();
	    double distanceToTarget = agentPos.distanceTo(target);

	    // Determine scaling factors based on conditions
	    double xScale, yScale, zScale;
	    if (distanceToTarget < 1.0 /* || n.agent.isSubmergedInWater || n.agent.isClimbing(MinecraftClient.getInstance().world) */) {
	        xScale = 1000;
	        yScale = 1000;
	        zScale = 1000;
	    } else if (isDoingLongJump) {
	        xScale = 10;
	        yScale = 100;
	        zScale = 10;
	    } else if (n.agent.isClimbing(TungstenMod.mc.world)) {
	        xScale = 1;
	        yScale = 10000;
	        zScale = 1;
	    } else if (n.agent.touchingWater) {
	        xScale = 1000;
	        yScale = 100;
	        zScale = 1000;
	    } else {
	        xScale = 100;
	        yScale = 10;
	        zScale = 100;
	    }

	    // Compute scaled position with hashCode offset
	    Vec3d scaledPos = computeScaledPosition(agentPos, hashCode, xScale, yScale, zScale);

	    // Check if the position is in the closed set
	    if (closed.contains(scaledPos)) {
	        return true;
	    }

	    // Optionally add the position to the closed set
	    if (addToClosed) {
	        closed.add(scaledPos);
	    }

	    return false;
	}
	
	private static Vec3d computeScaledPosition(Vec3d pos, int hashCode, double xScale, double yScale, double zScale) {
	    return new Vec3d(
	        Math.round(pos.x * xScale + hashCode),
	        Math.round(pos.y * yScale),
	        Math.round(pos.z * zScale)
	    );
	}
	
	private static double computeHeuristic(Vec3d position, boolean onGround, Vec3d target) {
		double xzMultiplier = 1.3;
	    double dx = (position.x - target.x)*xzMultiplier;
	    double dy = 0;
	    if (target.y != Double.MIN_VALUE) {
		    dy = (position.y - target.y) * 1.6;//*16;
		    if (!onGround || dy < 1.6 && dy > -1.6) dy = 0;
	    }
	    double dz = (position.z - target.z)*xzMultiplier;
	    return (Math.sqrt(dx * dx + dy * dy + dz * dz) + (((blockPath.isPresent() ? blockPath.get().size() : 0) - NEXT_CLOSEST_BLOCKNODE_IDX.get()) * 40));
	}
	
	private static void updateNode(WorldView world, Node current, Node child, Vec3d target, List<BlockNode> blockPath, Set<Vec3d> closed) {
	    Vec3d childPos = child.agent.getPos();

	    double collisionScore = 0;
	    double tentativeCost = child.cost + 1; // Assuming uniform cost for each step
	    if (child.agent.horizontalCollision && child.agent.getPos().distanceTo(target) > 3) {
	        collisionScore += 25 + (Math.abs(0.3 - child.agent.velZ) + Math.abs(0.3 - child.agent.velX));
	    }
	    
	    if (child.agent.touchingWater) {
//	    	collisionScore = 20000^20;
	    	if (BlockStateChecker.isAnyWater(world.getBlockState(blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getBlockPos()))) collisionScore -= 20;
//	    	else collisionScore += 2000;
	    	
	    } else {
	        collisionScore += (Math.abs(0.3 - child.agent.velZ) + Math.abs(0.3 - child.agent.velX));
	    }
	    if (child.agent.isClimbing(TungstenMod.mc.world)) {
//	    	collisionScore *= 20000;
	    	collisionScore -= 2;
	    }
	    if (world.getBlockState(child.agent.getBlockPos()).getBlock() instanceof CobwebBlock) {
	    	collisionScore += 20000;
	    }
//	    if (child.agent.slimeBounce) {
//	    	collisionScore -= 20000;
//	    }

	    double estimatedCostToGoal = /*computeHeuristic(childPos, child.agent.onGround, target) - 200 +*/ collisionScore;
	    if (blockPath != null) {
//	    		updateNextClosestBlockNodeIDX(blockPath, child, closed);
		    	Vec3d posToGetTo = BlockPosShifter.getPosOnLadder(blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()));
		    	
		    	if (child.agent.getPos().squaredDistanceTo(target) <= 2.0D) {
		    		posToGetTo = target;
		    	}
		    	
	    	estimatedCostToGoal +=  computeHeuristic(childPos, child.agent.onGround || child.agent.slimeBounce, posToGetTo);
	    }

//	    child.parent = current;
	    child.cost = tentativeCost;
	    child.estimatedCostToGoal = estimatedCostToGoal;
	    child.combinedCost = tentativeCost + estimatedCostToGoal;
	}
	
	private static int findClosestPositionIDX(WorldView world, BlockPos current, List<BlockNode> positions) {
        if (positions == null || positions.isEmpty()) {
            throw new IllegalArgumentException("The list of positions must not be null or empty.");
        }

        int closestIDX = NEXT_CLOSEST_BLOCKNODE_IDX.get();
        BlockNode closest = positions.get(closestIDX);
        double minDistance = current.getSquaredDistance(closest.getPos(true))/* + Math.abs(closest.y - current.getY()) * 160*/;
        int maxLoop = Math.min(closestIDX+10, positions.size());
        for (int i = closestIDX+1; i < maxLoop; i++) {
        	BlockNode position = positions.get(i);
//			if (i % 5 != 0) {
//        		continue;
//        	}
            double distance = current.getSquaredDistance(position.getPos(true))/* + Math.abs(position.y - current.getY()) * 160*/;
            if ( distance < 1 && closestIDX < i-1) continue;
            if (distance < minDistance
            		&& StreightMovementHelper.isPossible(world, position.getBlockPos(), current)
            		) {
                minDistance = distance;
                closest = position;
                closestIDX = i;
            }
		}
        return closestIDX;
    }
	
	private static boolean updateBestSoFar(Node child, Vec3d target, AtomicDoubleArray bestHeuristicSoFar) {
		boolean failing = true;
	    for (int i = 0; i < COEFFICIENTS.length; i++) {
	        double heuristic = child.combinedCost / COEFFICIENTS[i];
//	        Debug.logMessage("" + (bestHeuristicSoFar[i] - heuristic));
	        if (bestHeuristicSoFar.get(i) - heuristic > minimumImprovement) {
	            bestHeuristicSoFar.set(i, heuristic);
	            bestSoFar.set(i, child);
	            if (failing && getDistFromStartSq(child, target) > MIN_DIST_PATH * MIN_DIST_PATH) {
                    failing = false;
                }
	        }
	    }
	    return failing;
	}
	
	protected static double getDistFromStartSq(Node n, Vec3d target) {
        double xDiff = n.agent.getPos().x - target.x;
        double yDiff = n.agent.getPos().y - target.y;
        double zDiff = n.agent.getPos().z - target.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }
	
	private Node initializeStartNode(Node node, Vec3d target) {
        Node start = new Node(null, node.agent, new Color(0, 255, 0), 0);
        start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target);
        return start;
    }
	
	private Node initializeStartNode(ClientPlayerEntity player, Vec3d target) {
        Node start = new Node(null, Agent.of(player), new Color(0, 255, 0), 0);
        start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target);
        return start;
    }

    private Optional<List<BlockNode>> findBlockPath(WorldView world, Vec3d target) {
        return kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, target);
    }
    
    private Optional<List<BlockNode>> findBlockPath(WorldView world, BlockNode start, Vec3d target) {
        return kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, start, target);
    }

    private AtomicDoubleArray initializeBestHeuristics(Node start) {
    	AtomicDoubleArray bestHeuristicSoFar = new AtomicDoubleArray(COEFFICIENTS.length);
        for (int i = 0; i < bestHeuristicSoFar.length(); i++) {
            bestHeuristicSoFar.set(i, start.combinedCost / COEFFICIENTS[i]);
            bestSoFar.set(i, start);
        }
        return bestHeuristicSoFar;
    }
    
    private boolean isPathComplete(Node node, Vec3d target, boolean failing) {
    	if (BlockStateChecker.isAnyWater(TungstenMod.mc.world.getBlockState(new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ()))))
    		return node.agent.getPos().squaredDistanceTo(target) <= 0.9D;
    	if (TungstenMod.mc.world.getBlockState(new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ())).getBlock() instanceof LadderBlock)
    		return node.agent.getPos().squaredDistanceTo(target) <= 0.9D;
        return node.agent.getPos().squaredDistanceTo(target) <= 0.2D && !failing;
    }

    private boolean tryExecutePath(Node node, Vec3d target, double minVelocity) {
		TungstenMod.TEST.clear();
    	RenderHelper.renderPathSoFar(node);
    	while (TungstenMod.EXECUTOR.isRunning()) {
    		try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
        if (AgentChecker.isAgentStationary(node.agent, minVelocity) || 
        		TungstenMod.mc.world.getBlockState(new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ())).getBlock() instanceof LadderBlock) {
            List<Node> path = constructPath(node);
            executePath(path);
            return true;
        }
        return false;
    }

    private List<Node> constructPath(Node node) {
        List<Node> path = new ArrayList<>();
        TungstenMod.RUNNING_PATH_RENDERER.clear();
        while (node.parent != null) {
            path.add(node);
            RenderHelper.renderNodeConnection(node, node.parent);
            node = node.parent;
        }
        path.add(node);
        Collections.reverse(path);
        return path;
    }

    private void executePath(List<Node> path) {
        TungstenMod.EXECUTOR.cb = () -> {
            Debug.logMessage("Finished!");
            RenderHelper.clearRenderers();
        };
        TungstenMod.EXECUTOR.setPath(path);
//        thread.interrupt();
        stop.set(true);
    }

    private boolean shouldResetSearch(int numNodesConsidered, Optional<List<BlockNode>> blockPath, Node next, Vec3d target) {
        return (numNodesConsidered & (8 - 1)) == 0 &&
               NEXT_CLOSEST_BLOCKNODE_IDX.get() > blockPath.get().size() - 10 &&
               !TungstenMod.EXECUTOR.isRunning() &&
               blockPath.get().get(blockPath.get().size() - 1).getPos().squaredDistanceTo(next.agent.getPos()) < 3.0D &&
               blockPath.get().get(blockPath.get().size() - 1).getPos().squaredDistanceTo(target) > 1.0D &&
               AgentChecker.isAgentStationary(next.agent, 0.08);
    }

    private Optional<List<BlockNode>> resetSearch(Node next, WorldView world, Optional<List<BlockNode>> blockPath, Vec3d target) {
    	BlockNode lastNode = blockPath.get().getLast();
    	lastNode.previous = null;
        blockPath = findBlockPath(world, lastNode, target);
        if (blockPath.isPresent()) {
            List<Node> path = constructPath(next);
            TungstenMod.EXECUTOR.setPath(path);
            NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
        	return blockPath;
        }
        Debug.logWarning("Failed!");
        stop.set(true);
        return Optional.empty();
    }

    private boolean handleTimeout(long startTime, long primaryTimeoutTime, Node next, Vec3d target, Node start, ClientPlayerEntity player, Set<Vec3d> closed) {
        long now = System.currentTimeMillis();
        Optional<List<Node>> result = bestSoFar(true, 0, start);

        if (!result.isPresent() || now < primaryTimeoutTime) {
            return false;
        }
        if (player.getPos().distanceTo(result.get().get(0).agent.getPos()) < 1 && player.getPos().distanceTo(result.get().getLast().agent.getPos()) > 3 && next.agent.getPos().distanceTo(target) > 1) {
            Debug.logMessage("Time ran out");
            TungstenMod.EXECUTOR.setPath(result.get());
            RenderHelper.renderPathCurrentlyExecuted();
			Node newStart = initializeStartNode(result.get().getLast(), target);
            clearParentsForBestSoFar(newStart);
			closed.clear();
			bestHeuristicSoFar = initializeBestHeuristics(newStart);
		    openSet = new BinaryHeapOpenSet();
		    openSet.insert(newStart);
	        try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        RenderHelper.clearRenderers();
		    search(TungstenMod.mc.world, newStart, target);
	        return true;
        }
        return false;
    }
    
    private boolean filterChidren(Node child, BlockNode lastBlockNode, BlockNode nextBlockNode, boolean isSmallBlock) {
    	boolean isLadder = TungstenMod.mc.world.getBlockState(nextBlockNode.getBlockPos()).getBlock() instanceof LadderBlock;
    	boolean isLadderBelow = TungstenMod.mc.world.getBlockState(nextBlockNode.getBlockPos().down()).getBlock() instanceof LadderBlock;
    	if (isLadder || isLadderBelow) return false;
    	double distB = DistanceCalculator.getHorizontalEuclideanDistance(lastBlockNode.getPos(true), nextBlockNode.getPos(true));
    	
    	if (distB > 6 || child.agent.isClimbing(TungstenMod.mc.world)) return  child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY() - 0.8);
    	
    	if (nextBlockNode.isDoingNeo())
    		return !child.agent.onGround && child.agent.getBlockPos().getY() == nextBlockNode.getBlockPos().getY();
    	
    	if (nextBlockNode.isDoingLongJump()) return !child.agent.onGround;
    	
    	if (isSmallBlock) return child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY());
    	
    	
    	return !BlockStateChecker.isBottomSlab(TungstenMod.mc.world.getBlockState(nextBlockNode.getBlockPos().down())) && child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY() - 2.5);
//    	return false;
    }

    private boolean processNodeChildren(WorldView world, Node parent, Vec3d target, Optional<List<BlockNode>> blockPath,
            BinaryHeapOpenSet openSet, Set<Vec3d> closed) {
			AtomicBoolean failing = new AtomicBoolean(true);
			List<Node> children = parent.getChildren(world, target, blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()));
			
			Queue<Node> validChildren = new ConcurrentLinkedQueue<>();

			BlockNode lastBlockNode = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
			BlockNode nextBlockNode = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
	        double closestBlockVolume = BlockShapeChecker.getShapeVolume(nextBlockNode.getBlockPos().down());
	        boolean isSmallBlock = closestBlockVolume > 0 && closestBlockVolume < 1;
			
			ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			
			List<Callable<Void>> tasks = children.stream().map(child -> (Callable<Void>) () -> {
				if (stop.get()) return null;
		    	if (Thread.currentThread().isInterrupted()) return null;
				
				// Check if this child is too close to any already accepted child
			    for (Node other : validChildren) {
			    	if (Thread.currentThread().isInterrupted()) return null;
			        double distance = other.agent.getPos().distanceTo(child.agent.getPos());
	
			        boolean bothClimbing = other.agent.isClimbing(world) && child.agent.isClimbing(world);
			        boolean bothNotClimbing = !other.agent.isClimbing(world) && !child.agent.isClimbing(world);
	
			        if ((bothClimbing && distance < 0.03) || (bothNotClimbing && distance < 0.094) || (isSmallBlock && distance < 0.2)) {
			            return null; // too close to existing child
			        }
			    }
				
				boolean skip = filterChidren(child, lastBlockNode, nextBlockNode, isSmallBlock);
				
				if (skip || checkForFallDamage(child)) {
					return null;
				}
				
				validChildren.add(child);
				return null;
			}).collect(Collectors.toList());
			
			for (Iterator iterator = tasks.iterator(); iterator.hasNext();) {
				Callable<Void> callable = (Callable<Void>) iterator.next();
				try {
					callable.call();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
//			try {
//				executor.invokeAll(tasks);
//				executor.shutdown();
//				if (!executor.awaitTermination(5, TimeUnit.MILLISECONDS)) {
//					executor.shutdownNow();
//		        }
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			} finally {
//				executor.shutdown();
//			}
			
			
			executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			Object openSetLock = new Object();  // if openSet is not thread-safe
		
			List<Callable<Void>> processingTasks = validChildren.stream()
			    .map(child -> (Callable<Void>) () -> {
					if (stop.get()) return null;
			    	if (Thread.currentThread().isInterrupted()) return null;
			        updateNode(world, parent, child, target, blockPath.get(), closed);

			        synchronized (openSetLock) {
			            if (child.isOpen()) {
			                openSet.update(child);
			            } else {
			                openSet.insert(child);
			            }
			        }

			        // Update best heuristic safely
			        synchronized (bestHeuristicSoFar) {
			            if (updateBestSoFar(child, target, bestHeuristicSoFar)) {
			                failing.set(false);
			            }
			        }

			        // Optional: render node if you're using thread-safe rendering
			        // RenderHelper.renderNode(child);

			        return null;
			    })
			    .collect(Collectors.toList());

			

			for (Iterator iterator = processingTasks.iterator(); iterator.hasNext();) {
				Callable<Void> callable = (Callable<Void>) iterator.next();
				try {
					callable.call();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
//			try {
//			    executor.invokeAll(processingTasks);
//		    	executor.shutdown();
//				if (!executor.awaitTermination(5, TimeUnit.MILLISECONDS)) {
//					executor.shutdownNow();
//		        }
//			} catch (InterruptedException e) {
//			    e.printStackTrace();
//			} finally {
//			    executor.shutdown();
//			}
				
//			for (Node child : validChildren) {
//				updateNode(world, parent, child, target, blockPath.get(), closed);
//				
//				if (child.isOpen()) {
//					openSet.update(child);
//				} else {
//					openSet.insert(child);
//				}
//				
//				// Update best so far
//				if (updateBestSoFar(child, bestHeuristicSoFar, target)) {
//					failing.set(false);
//				}
//				
//				// Optionally render or handle visual updates here
//				// RenderHelper.renderNode(child);
//			}
			
			return failing.get();
		}
    
    private static void updateNextClosestBlockNodeIDX(List<BlockNode> blockPath, Node node, Set<Vec3d> closed) {
    	if (blockPath == null) return;

    	BlockNode lastClosestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
    	BlockNode closestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
    	if (NEXT_CLOSEST_BLOCKNODE_IDX.get()+1 >= blockPath.size()) return;
    	BlockNode nextNodePos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()+1);
    	
    	boolean isRunningLongDist = lastClosestPos.getPos(true).distanceTo(closestPos.getPos(true)) > 7;
    	
    	Vec3d nodePos = node.agent.getPos();
    	
    	if (closestPos.getPos(true).y <= nodePos.y && !nodePos.isWithinRangeOf(closestPos.getPos(true), (isRunningLongDist ? 2.80 : 3.10), (isRunningLongDist ? 1.20 : 0.80))) return;
    	
    	Node p = node.parent;
    	for (int i = 0; i < 4; i++) {
    		if (p != null && closestPos.getPos(true).y <= p.agent.getPos().y &&  !p.agent.getPos().isWithinRangeOf(closestPos.getPos(true), (isRunningLongDist ? 2.80 : 3.10), (isRunningLongDist ? 1.20 : 0.80))) return;
		}
    	
    	boolean isNextNodeAbove = nextNodePos.getBlockPos().getY() > closestPos.getBlockPos().getY();
    	boolean isNextNodeBelow = nextNodePos.getBlockPos().getY() < closestPos.getBlockPos().getY();
    	
    	WorldView world = TungstenMod.mc.world;
    	BlockPos nodeBlockPos = new BlockPos(node.agent.blockX, node.agent.blockY, node.agent.blockZ);
    	int closestPosIDX = findClosestPositionIDX(world, nodeBlockPos, blockPath);
        BlockState state = world.getBlockState(closestPos.getBlockPos());
        BlockState stateBelow = world.getBlockState(closestPos.getBlockPos().down());
        double closestBlockBelowHeight = BlockShapeChecker.getBlockHeight(closestPos.getBlockPos().down());
        double closestBlockVolume = BlockShapeChecker.getShapeVolume(closestPos.getBlockPos());
        double distanceToClosestPos = nodePos.distanceTo(closestPos.getPos(true));
        int heightDiff = closestPos.getJumpHeight((int) Math.ceil(nodePos.y), closestPos.y);

        boolean isWater = BlockStateChecker.isAnyWater(state);
        boolean isLadder = state.getBlock() instanceof LadderBlock;
        boolean isVine = state.getBlock() instanceof VineBlock;
        boolean isConnected = BlockStateChecker.isConnected(nodeBlockPos);
        boolean isBelowLadder = stateBelow.getBlock() instanceof LadderBlock;
        boolean isBelowBottomSlab = BlockStateChecker.isBottomSlab(stateBelow);
        boolean isBelowClosedTrapDoor= BlockStateChecker.isClosedBottomTrapdoor(stateBelow);
        boolean isBelowGlassPane = (stateBelow.getBlock() instanceof PaneBlock) || (stateBelow.getBlock() instanceof StainedGlassPaneBlock);
        boolean isBlockBelowTall = closestBlockBelowHeight > 1.3;
        
        boolean validWaterProximity = isWater && nodePos.isWithinRangeOf(BlockPosShifter.getPosOnLadder(closestPos), 0.9, 1.2);
        // Agent state conditions
        boolean agentOnGroundOrClimbingOrOnTallBlock = node.agent.onGround || node.agent.isClimbing(world) || isBelowLadder || isBlockBelowTall;

        // Ladder-specific conditions
        boolean validLadderProximity = (isLadder || isBelowLadder || isVine) 
    		&& (!isLadder && isBelowLadder || node.agent.isClimbing(world))
            && (nodePos.isWithinRangeOf(BlockPosShifter.getPosOnLadder(closestPos), 0.4, 0.9) || 
            		node.agent.isClimbing(world) &&
            		(isNextNodeAbove && nodePos.getY() > closestPos.getBlockPos().getY() || !isNextNodeBelow && nodePos.getY() < closestPos.getBlockPos().getY()) 
            		&& nodePos.isWithinRangeOf(BlockPosShifter.getPosOnLadder(closestPos), 0.7, 3.7));

        // Tall block position conditions. Things like fences and walls
        boolean validTallBlockProximity = isBlockBelowTall 
            && nodePos.isWithinRangeOf(closestPos.getPos(true), 0.4, 0.58);

        boolean validBottomSlabProximity = isBelowBottomSlab && distanceToClosestPos < 0.90
                && heightDiff < 2;
        
        boolean validClosedTrapDoorProximity = isBelowClosedTrapDoor && nodePos.isWithinRangeOf(closestPos.getPos(true), 0.88, 2.2);
        
        // General position conditions
        boolean validStandardProximity = !isLadder && !isBelowLadder && !isBelowGlassPane 
            && !isBlockBelowTall
            && distanceToClosestPos < (isRunningLongDist ? 1.80 : 0.35)
            && heightDiff < 1.8;

        // Glass pane conditions
        boolean validGlassPaneProximity = isBelowGlassPane && distanceToClosestPos < 0.5;
        
        // Block volume conditions
        boolean validSmallBlockProximity = !isBelowGlassPane && closestBlockVolume > 0 && closestBlockVolume < 1 && distanceToClosestPos < 0.7;
        
//        for (int j = 0; j < blockPath.size(); j++) {
//			if (j >= closestPosIDX) {
//	        	RenderHelper.renderBlockPath(blockPath, j);
//				try {
//					Thread.sleep(200);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//		}
        
    	if (closestPosIDX+1 > NEXT_CLOSEST_BLOCKNODE_IDX.get() && closestPosIDX +1 < blockPath.size()
    			&& ( validWaterProximity || !isConnected
//    			&& BlockNode.wasCleared(world, nodeBlockPos, blockPath.get(closestPosIDX+1).getBlockPos())
    			&& agentOnGroundOrClimbingOrOnTallBlock
    			&& (
	    			validLadderProximity 
		    		|| validTallBlockProximity
		    		|| validStandardProximity
		    		|| validGlassPaneProximity
		    		|| validSmallBlockProximity
		    		|| validBottomSlabProximity
		    		|| validClosedTrapDoorProximity
	    		)
//			    && (child.agent.getBlockPos().getY() == blockPath.get(closestPosIDX).getBlockPos().getY())
    			)
    			) {
	    		NEXT_CLOSEST_BLOCKNODE_IDX.set(closestPosIDX+1);
	        	RenderHelper.renderBlockPath(blockPath, NEXT_CLOSEST_BLOCKNODE_IDX.get());
				closed.clear();
    	}
    }
	
}
