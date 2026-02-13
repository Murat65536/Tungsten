package kaptainwutax.tungsten.path;

import com.google.common.util.concurrent.AtomicDoubleArray;
import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.simulation.SimulatedPlayer;
import kaptainwutax.tungsten.concurrent.TaskManager;
import kaptainwutax.tungsten.constants.pathfinding.CostConstants;
import kaptainwutax.tungsten.constants.pathfinding.PathfindingConstants;
import kaptainwutax.tungsten.helpers.AgentChecker;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.movement.StraightMovementHelper;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathfinder;
import kaptainwutax.tungsten.path.common.BinaryHeapOpenSet;
import kaptainwutax.tungsten.path.common.IOpenSet;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

public class Pathfinder {

    protected final AtomicReferenceArray<Node> bestSoFar = new AtomicReferenceArray<>(PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length);
    protected AtomicInteger NEXT_CLOSEST_BLOCKNODE_IDX = new AtomicInteger(1);
    private Optional<List<BlockNode>> blockPath = Optional.empty();
    private final Set<Long> closed = Collections.synchronizedSet(new HashSet<>());
    public AtomicBoolean active = new AtomicBoolean(false);
    public AtomicBoolean stop = new AtomicBoolean(false);
    public volatile Thread thread = null;
    private AtomicDoubleArray bestHeuristicSoFar;
    private IOpenSet<Node> openSet = new BinaryHeapOpenSet<>();

    protected Optional<List<Node>> bestSoFar(Node startNode) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = -1.0D;
        Node bestNode = null;

        for (int i = 0; i < PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length; i++) {
            Node n = bestSoFar.get(i);
            if (n == null) {
                continue;
            }
            if (!n.agent.onGround) continue;

            double dist = computeHeuristic(startNode.agent.getPos(), n.agent.getPos());
            if (dist > bestDist) {
                bestDist = dist;
                bestNode = n;
            }
        }

        if (bestNode != null) {
            List<Node> path = new ArrayList<>();
            Node n = bestNode;
            while (n.parent != null) {
                path.add(n);
                n = n.parent;
            }

            path.add(n);
            Collections.reverse(path);
            return Optional.of(path);
        }
        return Optional.empty();
    }

    private boolean shouldNodeBeSkipped(Node n, Vec3d target, Set<Long> closed, boolean addToClosed) {
        long hashCode = n.closedSetHashCode();

        if (addToClosed) {
            // add() returns false if already present — atomic check-then-act for synchronized set
            return !closed.add(hashCode);
        }

        return closed.contains(hashCode);
    }

    private double computeHeuristic(Vec3d position, Vec3d target) {
        double dx = position.x - target.x;
        double dy = target.y != Double.MIN_VALUE ? position.y - target.y : 0;
        double dz = position.z - target.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
                + ((blockPath.map(List::size).orElse(0) - NEXT_CLOSEST_BLOCKNODE_IDX.get()) * CostConstants.Heuristics.BLOCK_PATH_DISTANCE_WEIGHT);
    }

    private void updateNode(WorldView world, Node current, Node child, Vec3d target, List<BlockNode> blockPath, Set<Long> closed) {
        Vec3d childPos = child.agent.getPos();

        double collisionScore = 0;
        double tentativeCost = child.cost + 1; // Assuming uniform cost for each step
        if (child.agent.horizontalCollision && child.agent.getPos().distanceTo(target) > 3) {
            collisionScore += CostConstants.Penalties.HORIZONTAL_COLLISION_PENALTY + (Math.abs(0.3 - child.agent.velZ) + Math.abs(0.3 - child.agent.velX));
        }

        if (child.agent.touchingWater) {
            if (BlockStateChecker.isAnyWater(world.getBlockState(blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getBlockPos())))
                collisionScore += CostConstants.Bonuses.WATER_BONUS;

        } else {
            collisionScore += (Math.abs(0.3 - child.agent.velZ) + Math.abs(0.3 - child.agent.velX));
        }
        if (child.agent.isClimbing(world)) {
            collisionScore += CostConstants.Bonuses.CLIMBING_BONUS;
        }
        if (world.getBlockState(child.agent.getBlockPos()).getBlock() instanceof CobwebBlock) {
            collisionScore += CostConstants.Penalties.COBWEB_PENALTY;
        }

        double estimatedCostToGoal = collisionScore;
        if (blockPath != null) {
            Vec3d posToGetTo = BlockPosShifter.getPosOnLadder(blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()));

            if (child.agent.getPos().squaredDistanceTo(target) <= 2.0D) {
                posToGetTo = target;
            }

            estimatedCostToGoal += computeHeuristic(childPos, posToGetTo);
        }

        child.cost = tentativeCost;
        child.estimatedCostToGoal = estimatedCostToGoal;
        child.combinedCost = tentativeCost + estimatedCostToGoal;
    }

    private int findClosestPositionIDX(WorldView world, BlockPos current, List<BlockNode> positions) {
        if (positions == null || positions.isEmpty()) {
            throw new IllegalArgumentException("The list of positions must not be null or empty.");
        }

        int closestIDX = NEXT_CLOSEST_BLOCKNODE_IDX.get();
        BlockNode closest = positions.get(closestIDX);
        double minDistance = current.getSquaredDistance(closest.getPos(true));
        int maxLoop = Math.min(closestIDX + 10, positions.size());
        for (int i = closestIDX + 1; i < maxLoop; i++) {
            BlockNode position = positions.get(i);
            double distance = current.getSquaredDistance(position.getPos(true));
            if (distance < 1 && closestIDX < i - 1) continue;
            if (distance < minDistance
                    && StraightMovementHelper.isPossible(world, position.getBlockPos(), current)
            ) {
                minDistance = distance;
                closest = position;
                closestIDX = i;
            }
        }
        return closestIDX;
    }

    private boolean updateBestSoFar(Node child, Vec3d target, AtomicDoubleArray bestHeuristicSoFar) {
        boolean failing = true;
        for (int i = 0; i < PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length; i++) {
            double heuristic = child.combinedCost / PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS[i];
            if (bestHeuristicSoFar.get(i) - heuristic > PathfindingConstants.NodeEvaluation.MINIMUM_IMPROVEMENT) {
                bestHeuristicSoFar.set(i, heuristic);
                bestSoFar.set(i, child);
                failing = false;
            }
        }
        return failing;
    }

    private void updateNextClosestBlockNodeIDX(WorldView world, List<BlockNode> blockPath, Node node, Set<Long> closed) {
        if (blockPath == null) return;

        BlockNode lastClosestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get() - 1);
        BlockNode closestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
        if (NEXT_CLOSEST_BLOCKNODE_IDX.get() + 1 >= blockPath.size()) return;
        BlockNode nextNodePos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get() + 1);

        boolean isLongDist = lastClosestPos.getPos(true).distanceTo(closestPos.getPos(true)) > PathfindingConstants.WaypointAdvance.LONG_DISTANCE_THRESHOLD;
        double xzRange = isLongDist ? PathfindingConstants.WaypointAdvance.LONG_DIST_XZ_RANGE : PathfindingConstants.WaypointAdvance.SHORT_DIST_XZ_RANGE;
        double yRange = isLongDist ? PathfindingConstants.WaypointAdvance.LONG_DIST_Y_RANGE : PathfindingConstants.WaypointAdvance.SHORT_DIST_Y_RANGE;

        Vec3d nodePos = node.agent.getPos();
        if (!nodePos.isWithinRangeOf(closestPos.getPos(true), xzRange, yRange)) return;
        if (!areParentsWithinRange(node, closestPos.getPos(true), xzRange, yRange)) return;

        BlockPos nodeBlockPos = new BlockPos(node.agent.blockX, node.agent.blockY, node.agent.blockZ);
        int closestPosIDX = findClosestPositionIDX(world, nodeBlockPos, blockPath);

        if (closestPosIDX + 1 <= NEXT_CLOSEST_BLOCKNODE_IDX.get() || closestPosIDX + 1 >= blockPath.size()) return;

        if (isValidWaypointAdvance(world, node, closestPos, nextNodePos, nodePos, nodeBlockPos, isLongDist)) {
            NEXT_CLOSEST_BLOCKNODE_IDX.set(closestPosIDX + 1);
            RenderHelper.renderBlockPath(blockPath, NEXT_CLOSEST_BLOCKNODE_IDX.get());
            closed.clear();
        }
    }

    private boolean areParentsWithinRange(Node node, Vec3d target, double xzRange, double yRange) {
        Node p = node.parent;
        for (int i = 0; i < PathfindingConstants.WaypointAdvance.PARENT_CHECK_DEPTH; i++) {
            if (p != null && !p.agent.getPos().isWithinRangeOf(target, xzRange, yRange)) return false;
            if (p != null) p = p.parent;
        }
        return true;
    }

    private boolean isValidWaypointAdvance(WorldView world, Node node, BlockNode closestPos, BlockNode nextNodePos,
                                           Vec3d nodePos, BlockPos nodeBlockPos, boolean isLongDist) {
        BlockState state = world.getBlockState(closestPos.getBlockPos());
        BlockState stateBelow = world.getBlockState(closestPos.getBlockPos().down());
        double closestBlockBelowHeight = BlockShapeChecker.getBlockHeight(closestPos.getBlockPos().down());
        double closestBlockVolume = BlockShapeChecker.getShapeVolume(closestPos.getBlockPos());
        double distToClosest = nodePos.distanceTo(closestPos.getPos(true));
        int heightDiff = DistanceCalculator.getJumpHeight((int) Math.ceil(nodePos.y), closestPos.y);

        boolean isWater = BlockStateChecker.isAnyWater(state);
        boolean isLadder = state.getBlock() instanceof LadderBlock;
        boolean isVine = state.getBlock() instanceof VineBlock;
        boolean isConnected = BlockStateChecker.isConnected(nodeBlockPos);
        boolean isBelowLadder = stateBelow.getBlock() instanceof LadderBlock;
        boolean isBelowBottomSlab = BlockStateChecker.isBottomSlab(stateBelow);
        boolean isBelowClosedTrapDoor = BlockStateChecker.isClosedBottomTrapdoor(stateBelow);
        boolean isBelowGlassPane = (stateBelow.getBlock() instanceof PaneBlock) || (stateBelow.getBlock() instanceof StainedGlassPaneBlock);
        boolean isBlockBelowTall = closestBlockBelowHeight > PathfindingConstants.WaypointAdvance.TALL_BLOCK_HEIGHT;

        // Water always valid if in range
        if (isWater && nodePos.isWithinRangeOf(BlockPosShifter.getPosOnLadder(closestPos),
                PathfindingConstants.WaypointAdvance.WATER_XZ_RANGE, PathfindingConstants.WaypointAdvance.WATER_Y_RANGE)) {
            return true;
        }

        // All remaining checks require: not connected, and on ground/climbing/tall block
        if (isConnected) return false;
        boolean grounded = node.agent.onGround || node.agent.isClimbing(world) || isBelowLadder || isBlockBelowTall;
        if (!grounded) return false;

        boolean isNextAbove = nextNodePos.getBlockPos().getY() > closestPos.getBlockPos().getY();
        boolean isNextBelow = nextNodePos.getBlockPos().getY() < closestPos.getBlockPos().getY();

        return isValidLadderAdvance(node, closestPos, nodePos, isLadder, isBelowLadder, isVine, isNextAbove, isNextBelow, world)
            || (isBlockBelowTall && nodePos.isWithinRangeOf(closestPos.getPos(true), PathfindingConstants.WaypointAdvance.TALL_BLOCK_XZ, PathfindingConstants.WaypointAdvance.TALL_BLOCK_Y))
            || (!isLadder && !isBelowLadder && !isBelowGlassPane && !isBlockBelowTall
                && distToClosest < (isLongDist ? PathfindingConstants.WaypointAdvance.STANDARD_LONG_DIST : PathfindingConstants.WaypointAdvance.STANDARD_SHORT_DIST)
                && heightDiff < PathfindingConstants.WaypointAdvance.STANDARD_HEIGHT_DIFF)
            || (isBelowGlassPane && distToClosest < PathfindingConstants.WaypointAdvance.GLASS_PANE_DIST)
            || (!isBelowGlassPane && closestBlockVolume > 0 && closestBlockVolume < 1 && distToClosest < PathfindingConstants.WaypointAdvance.SMALL_BLOCK_DIST)
            || (isBelowBottomSlab && distToClosest < PathfindingConstants.WaypointAdvance.BOTTOM_SLAB_DIST && heightDiff < PathfindingConstants.WaypointAdvance.BOTTOM_SLAB_HEIGHT_DIFF)
            || (isBelowClosedTrapDoor && nodePos.isWithinRangeOf(closestPos.getPos(true), PathfindingConstants.WaypointAdvance.TRAPDOOR_XZ, PathfindingConstants.WaypointAdvance.TRAPDOOR_Y));
    }

    private boolean isValidLadderAdvance(Node node, BlockNode closestPos, Vec3d nodePos,
                                         boolean isLadder, boolean isBelowLadder, boolean isVine,
                                         boolean isNextAbove, boolean isNextBelow, WorldView world) {
        if (!isLadder && !isBelowLadder && !isVine) return false;
        if (isLadder && !isBelowLadder && !node.agent.isClimbing(world)) return false;

        Vec3d ladderPos = BlockPosShifter.getPosOnLadder(closestPos);
        if (nodePos.isWithinRangeOf(ladderPos, PathfindingConstants.WaypointAdvance.LADDER_CLOSE_XZ, PathfindingConstants.WaypointAdvance.LADDER_CLOSE_Y)) {
            return true;
        }
        return node.agent.isClimbing(world)
            && (isNextAbove && nodePos.getY() > closestPos.getBlockPos().getY() || !isNextBelow && nodePos.getY() < closestPos.getBlockPos().getY())
            && nodePos.isWithinRangeOf(ladderPos, PathfindingConstants.WaypointAdvance.LADDER_CLIMB_XZ, PathfindingConstants.WaypointAdvance.LADDER_CLIMB_Y);
    }

    public synchronized void find(WorldView world, Vec3d target) {
        if (active.get() || thread != null) return;
        active.set(true);
        NEXT_CLOSEST_BLOCKNODE_IDX.set(1);

        thread = new Thread(() -> {
            try {
                NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
                search(world, target);
            } catch (Exception e) {
                e.printStackTrace();
            }

            active.set(false);
            this.thread = null;
            closed.clear();
            blockPath = Optional.empty();
            NEXT_CLOSEST_BLOCKNODE_IDX.set(1);

        });
        thread.setName("PathFinder");
        thread.setPriority(PathfindingConstants.NodeEvaluation.THREAD_PRIORITY);
        thread.start();
    }

    private boolean checkForFallDamage(WorldView world, Node n) {
        if (TungstenMod.ignoreFallDamage) return false;
        if (BlockStateChecker.isAnyWater(world.getBlockState(n.agent.getBlockPos()))) return false;
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

        return DistanceCalculator.getJumpHeight(prev.agent.getPos().y, n.agent.getPos().y) < -3;
    }

    private void search(WorldView world, Vec3d target) {
        search(world, null, target);
    }

    private void search(WorldView world, Node start, Vec3d target) {
        TungstenMod.RENDERERS.clear();
        NEXT_CLOSEST_BLOCKNODE_IDX.set(1);

        // Initialize task manager for this pathfinding session
        TaskManager taskManager = new TaskManager();

        try {
            // Performance profiling variables
            long startTime = System.currentTimeMillis();
            long primaryTimeoutTime = startTime + PathfindingConstants.Timeouts.PRIMARY_TIMEOUT_MS;
            int numNodesConsidered = 1;
            int totalNodesEvaluated = 0;
            long nodeGenerationTime = 0;
            int timeCheckInterval = PathfindingConstants.NodeEvaluation.TIME_CHECK_INTERVAL;

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
                    this.blockPath = blockPath;
                }
            }

            bestHeuristicSoFar = initializeBestHeuristics(start);
            openSet = new BinaryHeapOpenSet<>();
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
                if (checkForFallDamage(world, next)) {
                    continue;
                }

                if (shouldSkipNode(world, next, target, closed, blockPath)) {
                    continue;
                }


                if (isPathComplete(world, next, target)) {
                    if (tryExecutePath(next, target)) {
                        TungstenMod.RENDERERS.clear();
                        TungstenMod.TEST.clear();
                        closed.clear();
                        this.blockPath = Optional.empty();
                        return;
                    }
                } else if (NEXT_CLOSEST_BLOCKNODE_IDX.get() == (blockPath.get().size() - 1) && blockPath.get().getLast().getPos(true).distanceTo(target) > 5) {
                    if (tryExecutePath(next, blockPath.get().getLast().getPos(true))) {
                        TungstenMod.RENDERERS.clear();
                        TungstenMod.TEST.clear();
                        closed.clear();
                        this.blockPath = findBlockPath(world, blockPath.get().getLast(), target);
                        if (blockPath.isPresent()) {
                            NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
                            RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
                        }
                    }
                }

                if (shouldResetSearch(numNodesConsidered, blockPath, next, target)) {
                    blockPath = resetSearch(next, world, blockPath, target);
                    openSet = new BinaryHeapOpenSet<>();
                    start = initializeStartNode(next, target);
                    openSet.insert(start);
                    continue;
                }

                if ((numNodesConsidered & (timeCheckInterval - 1)) == 0) {
                    if (handleTimeout(world, startTime, primaryTimeoutTime, next, target, start, player, closed)) {
                        return;
                    }
                }
                updateNextClosestBlockNodeIDX(world, blockPath.get(), next, closed);

                RenderHelper.renderExploredNode(next);

                // Profile node generation
                long nodeGenStart = System.currentTimeMillis();
                processNodeChildren(world, next, target, blockPath, openSet, closed, taskManager);
                nodeGenerationTime += (System.currentTimeMillis() - nodeGenStart);
                numNodesConsidered++;
                totalNodesEvaluated++;

            }

            // Print performance metrics
            long totalTime = System.currentTimeMillis() - startTime;
            Debug.logMessage("=== PathFinder Performance Metrics ===");
            Debug.logMessage("Total pathfinding time: " + totalTime + "ms");
            Debug.logMessage("Total nodes evaluated: " + totalNodesEvaluated);
            Debug.logMessage("Nodes per second: " + (totalNodesEvaluated * 1000L / Math.max(1, totalTime)));
            Debug.logMessage("Average node generation time: " + (nodeGenerationTime / Math.max(1, totalNodesEvaluated)) + "ms");
            Debug.logMessage("Node generation total time: " + nodeGenerationTime + "ms");
            Debug.logMessage("====================================");

            if (stop.get()) {
                stop.set(false);
            } else if (openSet.isEmpty()) {
                Debug.logMessage("Ran out of nodes!");
            }
        } finally {
            // Clean up task manager before exiting
            taskManager.cancelAll();
        }

        closed.clear();
        this.blockPath = Optional.empty();
    }

    private void clearBestSoFar() {
        for (int i = 0; i < PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length; i++) {
            bestSoFar.set(i, null);
        }
    }

    private boolean shouldSkipNode(WorldView world, Node node, Vec3d target, Set<Long> closed, Optional<List<BlockNode>> blockPath) {
        BlockNode bN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
        BlockNode lBN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get() - 1);
        boolean isBottomSlab = BlockStateChecker.isBottomSlab(world.getBlockState(bN.getBlockPos().down()));
        Vec3d agentPos = node.agent.getPos();
        Vec3d parentAgentPos = node.parent == null ? null : node.parent.agent.getPos();
        if (!isBottomSlab && !node.agent.onGround && agentPos.y < bN.y && lBN != null && lBN.y <= bN.y && parentAgentPos != null && parentAgentPos.y > agentPos.y) {
            return true;
        }
        return shouldNodeBeSkipped(node, target, closed, true);
    }

    private Node initializeStartNode(SimulatedPlayer agent, Vec3d target) {
        Node start = new Node(null, agent, Color.GREEN, 0);
        start.combinedCost = computeHeuristic(start.agent.getPos(), target);
        return start;
    }

    private Node initializeStartNode(Node node, Vec3d target) {
        return initializeStartNode(node.agent, target);
    }

    private Node initializeStartNode(ClientPlayerEntity player, Vec3d target) {
        return initializeStartNode(new SimulatedPlayer(player), target);
    }

    private Optional<List<BlockNode>> findBlockPath(WorldView world, Vec3d target) {
        return BlockSpacePathfinder.search(world, target);
    }

    private Optional<List<BlockNode>> findBlockPath(WorldView world, BlockNode start, Vec3d target) {
        return BlockSpacePathfinder.search(world, start, target);
    }

    private AtomicDoubleArray initializeBestHeuristics(Node start) {
        AtomicDoubleArray bestHeuristicSoFar = new AtomicDoubleArray(PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS.length);
        for (int i = 0; i < bestHeuristicSoFar.length(); i++) {
            bestHeuristicSoFar.set(i, start.combinedCost / PathfindingConstants.Coefficients.PATHFINDING_COEFFICIENTS[i]);
            bestSoFar.set(i, start);
        }
        return bestHeuristicSoFar;
    }

    private boolean isPathComplete(WorldView world, Node node, Vec3d target) {
        BlockPos targetPos = new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ());
        if (BlockStateChecker.isAnyWater(world.getBlockState(targetPos)))
            return node.agent.getPos().squaredDistanceTo(target) <= 0.9D;
        if (world.getBlockState(targetPos).getBlock() instanceof LadderBlock)
            return node.agent.getPos().squaredDistanceTo(target) <= 0.9D;
        return node.agent.getPos().squaredDistanceTo(target) <= 0.2D;
    }

    private boolean tryExecutePath(Node node, Vec3d target) {
        TungstenMod.TEST.clear();
        RenderHelper.renderPathSoFar(node);
        while (TungstenMod.EXECUTOR.isRunning()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Debug.logWarning("Interrupted while waiting for executor: " + e.getMessage());
            }
        }
        List<Node> path = constructPath(node);
        executePath(path);
        return true;
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

    private boolean handleTimeout(WorldView world, long startTime, long primaryTimeoutTime, Node next, Vec3d target, Node start, ClientPlayerEntity player, Set<Long> closed) {
        long now = System.currentTimeMillis();
        Optional<List<Node>> result = bestSoFar(start);

        if (!result.isPresent() || now < primaryTimeoutTime) {
            return false;
        }
        if (player.getPos().distanceTo(result.get().get(0).agent.getPos()) < 1 && player.getPos().distanceTo(result.get().getLast().agent.getPos()) > 3 && next.agent.getPos().distanceTo(target) > 1) {
            Debug.logMessage("Time ran out");
            TungstenMod.EXECUTOR.setPath(result.get());
            RenderHelper.renderPathCurrentlyExecuted();
            Node newStart = initializeStartNode(result.get().getLast(), target);
            clearBestSoFar();
            closed.clear();
            bestHeuristicSoFar = initializeBestHeuristics(newStart);
            openSet = new BinaryHeapOpenSet<>();
            openSet.insert(newStart);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Debug.logWarning("Interrupted during timeout handler: " + e.getMessage());
            }
            RenderHelper.clearRenderers();
            search(world, newStart, target);
            return true;
        }
        return false;
    }

    private boolean filterChildren(WorldView world, Node child, BlockNode lastBlockNode, BlockNode nextBlockNode, boolean isSmallBlock) {
        boolean isLadder = world.getBlockState(nextBlockNode.getBlockPos()).getBlock() instanceof LadderBlock;
        boolean isLadderBelow = world.getBlockState(nextBlockNode.getBlockPos().down()).getBlock() instanceof LadderBlock;
        if (isLadder || isLadderBelow) return false;
        double distB = DistanceCalculator.getHorizontalEuclideanDistance(lastBlockNode.getPos(true), nextBlockNode.getPos(true));

        if (distB > 6 || child.agent.isClimbing(world))
            return child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY() - 0.8);

        if (isSmallBlock) return child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY());

        return !BlockStateChecker.isBottomSlab(world.getBlockState(nextBlockNode.getBlockPos().down())) && child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY() - 2.5);
    }

    private boolean processNodeChildren(WorldView world, Node parent, Vec3d target, Optional<List<BlockNode>> blockPath,
                                        IOpenSet<Node> openSet, Set<Long> closed, TaskManager taskManager) {
        AtomicBoolean failing = new AtomicBoolean(true);
        List<Node> children = parent.getChildren(world, target, blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()));

        Queue<Node> validChildren = new ConcurrentLinkedQueue<>();

        BlockNode lastBlockNode = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get() - 1);
        BlockNode nextBlockNode = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
        double closestBlockVolume = BlockShapeChecker.getShapeVolume(nextBlockNode.getBlockPos().down());
        boolean isSmallBlock = closestBlockVolume > 0 && closestBlockVolume < 1;

        // Use TaskManager instead of creating a new ExecutorService
        // First pass: filter based on individual node criteria (no race condition)
        List<Callable<Node>> filteringTasks = children.stream().map(child -> (Callable<Node>) () -> {
            if (stop.get()) return null;
            if (Thread.currentThread().isInterrupted()) return null;

            boolean skip = filterChildren(world, child, lastBlockNode, nextBlockNode, isSmallBlock);

            if (skip || checkForFallDamage(world, child)) {
                return null;
            }

            return child; // Return the valid child for distance checking
        }).collect(Collectors.toList());

        // Process filtering tasks using TaskManager with proper timeout handling
        List<Node> preliminaryChildren = new ArrayList<>();
        taskManager.processNodeFilteringBatch(
                filteringTasks,
                preliminaryChildren,
                stop,
                PathfindingConstants.Timeouts.NODE_FILTER_TIMEOUT_MS
        );

        // Sort by combined cost so best nodes are kept first during proximity pruning
        preliminaryChildren.sort(Comparator.comparingDouble(n -> n.combinedCost));

        // Second pass: filter by distance (single-threaded to avoid race condition)
        for (Node child : preliminaryChildren) {
            if (child == null) continue;

            boolean tooClose = false;
            for (Node other : validChildren) {
                double distance = other.agent.getPos().distanceTo(child.agent.getPos());
                boolean bothClimbing = other.agent.isClimbing(world) && child.agent.isClimbing(world);
                boolean bothNotClimbing = !other.agent.isClimbing(world) && !child.agent.isClimbing(world);

                if ((bothClimbing && distance < 0.06) || (bothNotClimbing && distance < 0.2) || (isSmallBlock && distance < 0.3)) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                validChildren.add(child);
            }
        }


        // Lock for thread-safe access to the non-thread-safe openSet
        Object openSetLock = new Object();

        List<Runnable> processingTasks = validChildren.stream()
                .filter(Objects::nonNull)  // Filter out null children
                .map(child -> (Runnable) () -> {
                    if (stop.get()) return;
                    if (Thread.currentThread().isInterrupted()) return;
                    // Pre-filter: skip children already in the closed set (don't add yet —
                    // actual insertion happens in shouldNodeBeSkipped when dequeued from open set)
                    if (closed.contains(child.closedSetHashCode())) return;
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
                        if (!updateBestSoFar(child, target, bestHeuristicSoFar)) {
                            failing.set(false);
                        }
                    }
                })
                .collect(Collectors.toList());

        // Process node updates using TaskManager with proper timeout handling
        taskManager.processNodeUpdates(
                processingTasks,
                PathfindingConstants.Timeouts.NODE_UPDATE_TIMEOUT_MS
        );


        return failing.get();
    }

}
