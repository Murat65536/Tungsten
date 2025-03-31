package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.helpers.AgentChecker;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.movement.StreightMovementHelper;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.path.calculators.BinaryHeapOpenSet;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Colors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class PathFinder {

	public boolean active = false;
	public boolean stop = false;
	public Thread thread = null;
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final Node[] bestSoFar = new Node[COEFFICIENTS.length];
	private static final double minimumImprovement = 0.21;
	private static Optional<List<BlockNode>> blockPath = Optional.empty();
	protected static final double MIN_DIST_PATH = 5;
	protected static int NEXT_CLOSEST_BLOCKNODE_IDX = 1;
	
	
	public void find(WorldView world, Vec3d target) {
		if(active)return;
		active = true;
		NEXT_CLOSEST_BLOCKNODE_IDX = 1;

		thread = new Thread(() -> {
			try {
				NEXT_CLOSEST_BLOCKNODE_IDX = 1;
				search(world, target);
			} catch(Exception e) {
				e.printStackTrace();
			}

			active = false;
		});
		thread.setName("PathFinder");
		thread.start();
	}

	private void search(WorldView world, Vec3d target) {
	    boolean failing = true;
	    TungstenMod.RENDERERS.clear();
	    NEXT_CLOSEST_BLOCKNODE_IDX = 1;
	
	    long startTime = System.currentTimeMillis();
	    long primaryTimeoutTime = startTime + 250L;
	    int numNodesConsidered = 1;
	    int timeCheckInterval = 1 << 3;
	    double minVelocity = 0.07;
	
	    ClientPlayerEntity player = Objects.requireNonNull(TungstenMod.mc.player);
	    if (player.getPos().distanceTo(target) < 1.0) {
	        Debug.logMessage("Already at target location!");
	        return;
	    }
	
	    Node start = initializeStartNode(player, target);
	    Optional<List<BlockNode>> blockPath = findBlockPath(world, target);
	    if (blockPath.isPresent()) {
        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX);
        	this.blockPath = blockPath;
        }
//	    if (blockPath.isEmpty()) {
//	    	Debug.logWarning("Failed!");
//	    	return;
//	    }
	
	    double[] bestHeuristicSoFar = initializeBestHeuristics(start);
	    BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
	    Set<Vec3d> closed = new HashSet<>();
	    openSet.insert(start);
	
	    while (!openSet.isEmpty()) {
	        if (stop) {
	        	RenderHelper.clearRenderers();
	            break;
	        }
	
	        if (blockPath.isPresent() && TungstenMod.BLOCK_PATH_RENDERER.isEmpty()) {
	        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX);
	        }
	
	        Node next = openSet.removeLowest();
	
	        if (shouldSkipNode(next, target, closed, blockPath)) {
	            continue;
	        }
	
	        if (isPathComplete(next, target, failing)) {
	            if (tryExecutePath(next, target, minVelocity)) {
	        		TungstenMod.RENDERERS.clear();
	        		TungstenMod.TEST.clear();
	                return;
	            }
	        }
	
	        if (shouldResetSearch(numNodesConsidered, blockPath, next, target)) {
	        	blockPath = resetSearch(next, world, blockPath, target, bestHeuristicSoFar);
	            openSet = new BinaryHeapOpenSet();
	            start = initializeStartNode(next, target);
	            openSet.insert(start);
	            continue;
	        }
	
	        if ((numNodesConsidered & (timeCheckInterval - 1)) == 0) {
	            handleTimeout(startTime, primaryTimeoutTime, next, target, start, bestHeuristicSoFar, player, closed);
	        }
	        
	        if (numNodesConsidered % 20 == 0) {	        	
	        	RenderHelper.renderPathSoFar(next);
	        }
	
	        failing = processNodeChildren(world, next, target, blockPath, openSet, closed, bestHeuristicSoFar);
	        numNodesConsidered++;
	        if (numNodesConsidered % 150 == 0) {
		        RenderHelper.renderNode(next);
	        }
//	        try {
//				Thread.sleep(250);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
	    }
	
	    if (stop) {
	        stop = false;
	    } else if (openSet.isEmpty()) {
	        Debug.logMessage("Ran out of nodes!");
	    }
	    RenderHelper.clearRenderers();
	}
	protected static Optional<List<Node>> bestSoFar(boolean logInfo, int numNodes, Node startNode) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = 0;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            if (bestSoFar[i] == null) {
                continue;
            }
            double dist = computeHeuristic(startNode.agent.getPos(), startNode.agent.onGround || startNode.agent.slimeBounce, bestSoFar[i].agent.getPos());
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
                Node n = bestSoFar[i];
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
			Node n = bestSoFar[i];
			if (n.equals(node)) {
				n.parent = null;
				bestSoFar[i] = n;
			} else {				
				bestSoFar[i] = null;
			}
		}
	}

	private boolean shouldSkipChild(Node child, Vec3d target, Set<Vec3d> closed, Optional<List<BlockNode>> blockPath) {
	    return child.agent.touchingWater && shouldSkipNode(child, target, closed, blockPath);
	}
	
	private boolean shouldSkipNode(Node node, Vec3d target, Set<Vec3d> closed, Optional<List<BlockNode>> blockPath) {
	    BlockNode bN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX);
	    BlockNode lBN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX-1);
	    boolean isBottomSlab = BlockStateChecker.isBottomSlab(TungstenMod.mc.world.getBlockState(bN.getBlockPos().down()));
	    Vec3d agentPos = node.agent.getPos();
	    Vec3d parentAgentPos = node.parent == null ? null : node.parent.agent.getPos();
	    if (!isBottomSlab && !node.agent.onGround && agentPos.y < bN.y && lBN != null && lBN.y <= bN.y && parentAgentPos != null && parentAgentPos.y > agentPos.y) {
	    	return true;
	    }
	    return shouldNodeBeSkipped(node, target, closed, true, 
	        blockPath.isPresent() && (
	            blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX).isDoingLongJump() ||
	            blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX).isDoingNeo() ||
	            blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX - 1).isDoingCornerJump()
	        ),
	        blockPath.isPresent() && !blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX).isDoingNeo()
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
	    return (Math.sqrt(dx * dx + dy * dy + dz * dz) + (((blockPath.isPresent() ? blockPath.get().size() : 0) - NEXT_CLOSEST_BLOCKNODE_IDX) * 40));
	}
	
	private static void updateNode(WorldView world, Node current, Node child, Vec3d target, List<BlockNode> blockPath) {
	    Vec3d childPos = child.agent.getPos();

	    double collisionScore = 0;
	    double tentativeCost = child.cost + 1; // Assuming uniform cost for each step
	    if (child.agent.horizontalCollision) {
	        collisionScore += 2500 + (Math.abs(current.agent.velZ - child.agent.velZ) + Math.abs(current.agent.velX - child.agent.velX)) * 120;
	    }
//	    if (child.agent.touchingWater) {
////	    	collisionScore = 20000^20;
//	    	collisionScore += 2000;
//	    }
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
	    		updateNextClosestBlockNodeIDX(blockPath, child);
		    	Vec3d posToGetTo = BlockPosShifter.getPosOnLadder(blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX));
		    	
		    	if (child.agent.getPos().squaredDistanceTo(target) <= 2.0D) {
		    		posToGetTo = target;
		    	}
		    	
	    	estimatedCostToGoal +=  computeHeuristic(childPos, child.agent.onGround || child.agent.slimeBounce, posToGetTo) * 600.5;
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

        int closestIDX = NEXT_CLOSEST_BLOCKNODE_IDX;
        BlockNode closest = positions.get(closestIDX);
        double minDistance = current.getSquaredDistance(closest.getPos(true))/* + Math.abs(closest.y - current.getY()) * 160*/;
        int maxLoop = Math.min(closestIDX+10, positions.size());
        for (int i = closestIDX+1; i < maxLoop; i++) {
        	BlockNode position = positions.get(i);
//			if (i % 5 != 0) {
//        		continue;
//        	}
            double distance = current.getSquaredDistance(position.getPos(true))/* + Math.abs(position.y - current.getY()) * 160*/;
            if ( distance < 1) continue;
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
	
	private static boolean updateBestSoFar(Node child, double[] bestHeuristicSoFar, Vec3d target) {
		boolean failing = false;
	    for (int i = 0; i < COEFFICIENTS.length; i++) {
	        double heuristic = child.estimatedCostToGoal + child.cost / COEFFICIENTS[i];
	        if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
	            bestHeuristicSoFar[i] = heuristic;
	            bestSoFar[i] = child;
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
        Node start = new Node(null, node.agent, null, 0);
        start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target);
        return start;
    }
	
	private Node initializeStartNode(ClientPlayerEntity player, Vec3d target) {
        Node start = new Node(null, Agent.of(player), null, 0);
        start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target);
        return start;
    }

    private Optional<List<BlockNode>> findBlockPath(WorldView world, Vec3d target) {
        return kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, target);
    }
    
    private Optional<List<BlockNode>> findBlockPath(WorldView world, BlockNode start, Vec3d target) {
        return kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, start, target);
    }

    private double[] initializeBestHeuristics(Node start) {
        double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];
        for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.heuristic;
            bestSoFar[i] = start;
        }
        return bestHeuristicSoFar;
    }
    
    private boolean isPathComplete(Node node, Vec3d target, boolean failing) {
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
        if (AgentChecker.isAgentStationary(node.agent, minVelocity)) {
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
        stop = true;
    }

    private boolean shouldResetSearch(int numNodesConsidered, Optional<List<BlockNode>> blockPath, Node next, Vec3d target) {
        return (numNodesConsidered & (8 - 1)) == 0 &&
               NEXT_CLOSEST_BLOCKNODE_IDX > blockPath.get().size() - 10 &&
               !TungstenMod.EXECUTOR.isRunning() &&
               blockPath.get().get(blockPath.get().size() - 1).getPos().squaredDistanceTo(next.agent.getPos()) < 3.0D &&
               blockPath.get().get(blockPath.get().size() - 1).getPos().squaredDistanceTo(target) > 1.0D &&
               AgentChecker.isAgentStationary(next.agent, 0.08);
    }

    private Optional<List<BlockNode>> resetSearch(Node next, WorldView world, Optional<List<BlockNode>> blockPath, Vec3d target, double[] bestHeuristicSoFar) {
    	BlockNode lastNode = blockPath.get().getLast();
    	lastNode.previous = null;
        blockPath = findBlockPath(world, lastNode, target);
        if (blockPath.isPresent()) {
            List<Node> path = constructPath(next);
            TungstenMod.EXECUTOR.setPath(path);
            NEXT_CLOSEST_BLOCKNODE_IDX = 1;
        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX);
        	return blockPath;
        }
        Debug.logWarning("Failed!");
        stop = true;
        return Optional.empty();
    }

    private void handleTimeout(long startTime, long primaryTimeoutTime, Node next, Vec3d target, Node start, double[] bestHeuristicSoFar, ClientPlayerEntity player, Set<Vec3d> closed) {
        long now = System.currentTimeMillis();
        Optional<List<Node>> result = bestSoFar(true, 0, start);

        if (!result.isPresent() || now < primaryTimeoutTime) {
            return;
        }

        if (player.getPos().distanceTo(result.get().get(0).agent.getPos()) < 1 && player.getPos().distanceTo(result.get().getLast().agent.getPos()) > 3 && next.agent.getPos().distanceTo(target) > 1) {
            Debug.logMessage("Time ran out");
            TungstenMod.EXECUTOR.setPath(result.get());
            RenderHelper.renderPathCurrentlyExecuted();
            clearParentsForBestSoFar(result.get().getLast());
            closed = new HashSet<Vec3d>();
        }
    }

    private boolean processNodeChildren(WorldView world, Node parent, Vec3d target, Optional<List<BlockNode>> blockPath, BinaryHeapOpenSet openSet, Set<Vec3d> closed, double[] bestHeuristicSoFar) {
        boolean failing = true;
//        TungstenMod.RENDERERS.clear();
        Node lastChild = null;
    	for (Node child : parent.getChildren(world, target, blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX))) {
            if (stop) break;
            if (shouldSkipChild(child, target, closed, blockPath) 
            		|| (lastChild != null 
	            		&& lastChild.agent.getPos().distanceTo(child.agent.getPos()) < 0.07)
            		|| blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX).isDoingNeo() 
            			&& !child.agent.onGround
            		|| blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX).isDoingLongJump() 
            			&& !child.agent.onGround
            		) continue;

            updateNode(world, parent, child, target, blockPath.get());
            if (child.isOpen()) {
                openSet.update(child);
            } else {
                openSet.insert(child);
            }
            failing = updateBestSoFar(child, bestHeuristicSoFar, target);
//            RenderHelper.renderNode(child);
            lastChild = child;
        }
    	return failing;
    }
    
    private static void updateNextClosestBlockNodeIDX(List<BlockNode> blockPath, Node node) {
    	if (blockPath == null) return;
    	WorldView world = TungstenMod.mc.world;
    	BlockPos nodeBlockPos = new BlockPos(node.agent.blockX, node.agent.blockY, node.agent.blockZ);
    	Vec3d nodePos = node.agent.getPos();
    	int closestPosIDX = findClosestPositionIDX(world, nodeBlockPos, blockPath);
    	BlockNode closestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX);
        BlockState state = world.getBlockState(closestPos.getBlockPos());
        BlockState stateBelow = world.getBlockState(closestPos.getBlockPos().down());
        double closestBlockBelowHeight = BlockShapeChecker.getBlockHeight(closestPos.getBlockPos().down());
        double closestBlockVolume = BlockShapeChecker.getShapeVolume(closestPos.getBlockPos());
        double distanceToClosestPos = nodePos.distanceTo(closestPos.getPos(true));
        int heightDiff = closestPos.getJumpHeight((int) Math.ceil(nodePos.y), closestPos.y);
        
        boolean isLadder = state.getBlock() instanceof LadderBlock;
        boolean isConnected = BlockStateChecker.isConnected(nodeBlockPos);
        boolean isBelowLadder = stateBelow.getBlock() instanceof LadderBlock;
        boolean isBelowBottomSlab = BlockStateChecker.isBottomSlab(stateBelow);
        boolean isBelowGlassPane = (stateBelow.getBlock() instanceof PaneBlock) || (stateBelow.getBlock() instanceof StainedGlassPaneBlock);
        boolean isBlockBelowTall = closestBlockBelowHeight > 1.3;
        
        // Agent state conditions
        boolean agentOnGroundOrClimbingOrOnTallBlock = node.agent.onGround || node.agent.isClimbing(world) || isBelowLadder || isBlockBelowTall;

        // Ladder-specific conditions
        boolean validLadderProximity = (isLadder || isBelowLadder) 
            && nodePos.isWithinRangeOf(BlockPosShifter.getPosOnLadder(closestPos), 0.5, 0.6);

        // Tall block position conditions. Things like fences and walls
        boolean validTallBlockProximity = isBlockBelowTall 
            && nodePos.isWithinRangeOf(closestPos.getPos(true), 0.4, 0.58);

        boolean validBottomSlabProximity = isBelowBottomSlab && distanceToClosestPos < 0.90
                && heightDiff < 2;
        
        // General position conditions
        boolean validStandardProximity = !isLadder && !isBelowLadder && !isBelowGlassPane 
            && !isBlockBelowTall
            && distanceToClosestPos < 0.90
            && heightDiff < 1;

        // Glass pane conditions
        boolean validGlassPaneProximity = isBelowGlassPane && distanceToClosestPos < 0.1;
        
        // Block volume conditions
        boolean validSmallBlockProximity = !isBelowGlassPane && closestBlockVolume > 0 && closestBlockVolume < 1 && distanceToClosestPos < 0.7;
        
        
    	if (closestPosIDX+1 > NEXT_CLOSEST_BLOCKNODE_IDX && closestPosIDX +1 < blockPath.size()
    			&& !isConnected
//    			&& BlockNode.wasCleared(world, nodeBlockPos, blockPath.get(closestPosIDX+1).getBlockPos())
    			&& agentOnGroundOrClimbingOrOnTallBlock
    			&& (
	    			validLadderProximity 
		    		|| validTallBlockProximity
		    		|| validStandardProximity
		    		|| validGlassPaneProximity
		    		|| validSmallBlockProximity
		    		|| validBottomSlabProximity
	    		)
//			    && (child.agent.getBlockPos().getY() == blockPath.get(closestPosIDX).getBlockPos().getY())
    			) {
	    		NEXT_CLOSEST_BLOCKNODE_IDX = closestPosIDX+1;
	        	RenderHelper.renderBlockPath(blockPath, NEXT_CLOSEST_BLOCKNODE_IDX);
    	}
    }
	
}
