package kaptainwutax.tungsten.path;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.simulation.SimulatedPlayer;
import kaptainwutax.tungsten.constants.pathfinding.CostConstants;
import kaptainwutax.tungsten.constants.pathfinding.PathfindingConstants;
import kaptainwutax.tungsten.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.movement.StraightMovementHelper;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathfinder;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Pathfinder {

    protected AtomicInteger NEXT_CLOSEST_BLOCKNODE_IDX = new AtomicInteger(1);
    private Optional<List<BlockNode>> blockPath = Optional.empty();
    public AtomicBoolean active = new AtomicBoolean(false);
    public AtomicBoolean stop = new AtomicBoolean(false);
    public volatile Thread thread = null;

    /**
     * A decision point in the greedy search tree. Stores the sorted list of
     * valid children and which index to try next upon backtracking.
     */
    private static class DecisionPoint {
        final List<Node> children;
        int nextIndex;

        DecisionPoint(List<Node> children, int nextIndex) {
            this.children = children;
            this.nextIndex = nextIndex;
        }
    }

    private double computeHeuristic(Vec3d position, Vec3d target) {
        double dx = position.x - target.x;
        double dy = target.y != Double.MIN_VALUE ? position.y - target.y : 0;
        double dz = position.z - target.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
                + ((blockPath.map(List::size).orElse(0) - NEXT_CLOSEST_BLOCKNODE_IDX.get()) * CostConstants.Heuristics.BLOCK_PATH_DISTANCE_WEIGHT);
    }

    private void updateNode(WorldView world, Node child, Vec3d target, List<BlockNode> blockPath) {
        Vec3d childPos = child.agent.getPos();

        double collisionScore = 0;
        double tentativeCost = child.cost + 1; // Assuming uniform cost for each step
        if (child.agent.horizontalCollision && child.agent.getPos().distanceTo(target) > 3) {
            collisionScore += CostConstants.Penalties.HORIZONTAL_COLLISION_PENALTY + (Math.abs(0.3 - child.agent.velZ) + Math.abs(0.3 - child.agent.velX));
        }

        if (child.agent.touchingWater) {
            if (BlockStateChecker.isAnyWater(world.getBlockState(blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getBlockPos())))
                collisionScore += CostConstants.Bonuses.WATER_BONUS;
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

    private void updateNextClosestBlockNodeIDX(WorldView world, List<BlockNode> blockPath, Node node) {
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
            blockPath = Optional.empty();
            NEXT_CLOSEST_BLOCKNODE_IDX.set(1);

        });
        thread.setName("PathFinder");
        thread.setPriority(PathfindingConstants.NodeEvaluation.THREAD_PRIORITY);
        thread.start();
    }

    private void search(WorldView world, Vec3d target) {
        TungstenMod.RENDERERS.clear();
        NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
        stop.set(false);

        long startTime = System.currentTimeMillis();
        int totalNodesEvaluated = 0;
        int totalBacktracks = 0;

        ClientPlayerEntity player = Objects.requireNonNull(TungstenMod.mc.player);
        if (player.getPos().distanceTo(target) < 1.0) {
            Debug.logMessage("Already at target location!");
            return;
        }

        Node start = initializeStartNode(player, target);
        if (blockPath.isEmpty()) {
            Optional<List<BlockNode>> bp = findBlockPath(world, target);
            if (bp.isPresent()) {
                RenderHelper.renderBlockPath(bp.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
                this.blockPath = bp;
            }
        }

        if (blockPath.isEmpty()) {
            Debug.logWarning("No block path found!");
            return;
        }

        Deque<DecisionPoint> decisions = new ArrayDeque<>();
        Set<Long> blacklisted = new HashSet<>();
        Node current = start;

        while (totalNodesEvaluated < PathfindingConstants.Greedy.MAX_DEPTH
                && totalBacktracks < PathfindingConstants.Greedy.MAX_BACKTRACKS) {
            if (stop.get()) {
                RenderHelper.clearRenderers();
                break;
            }

            if (blockPath.isPresent() && TungstenMod.BLOCK_PATH_RENDERER.isEmpty()) {
                RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
            }

            // Void check — fell below minimum build height
            if (current.agent.getPos().y < world.getBottomY()) {
                blacklisted.add(current.closedSetHashCode());
                boolean found = false;
                while (!decisions.isEmpty()) {
                    DecisionPoint dp = decisions.peek();
                    while (dp.nextIndex < dp.children.size()
                            && blacklisted.contains(dp.children.get(dp.nextIndex).closedSetHashCode())) {
                        dp.nextIndex++;
                    }
                    if (dp.nextIndex < dp.children.size()) {
                        current = dp.children.get(dp.nextIndex);
                        dp.nextIndex++;
                        totalBacktracks++;
                        found = true;
                        break;
                    } else {
                        decisions.pop();
                        if (!decisions.isEmpty()) {
                            Node exhaustedParent = dp.children.get(0).parent;
                            if (exhaustedParent != null) {
                                blacklisted.add(exhaustedParent.closedSetHashCode());
                            }
                        }
                    }
                }
                if (!found) {
                    Debug.logMessage("No path found - fell into void and exhausted all options!");
                    break;
                }
                continue;
            }

            // Goal check
            if (isPathComplete(world, current, target)) {
                if (tryExecutePath(current, target)) {
                    TungstenMod.RENDERERS.clear();
                    TungstenMod.TEST.clear();
                    this.blockPath = Optional.empty();
                    return;
                }
            }

            // Generate, filter, score, and sort children (excluding blacklisted states)
            List<Node> validChildren = generateAndFilterChildren(world, current, target, blacklisted);

            if (!validChildren.isEmpty()) {
                // Pick the best child, push remaining as backtrack options
                Node bestChild = validChildren.get(0);
                decisions.push(new DecisionPoint(validChildren, 1));
                current = bestChild;
                totalNodesEvaluated++;
                updateNextClosestBlockNodeIDX(world, blockPath.get(), current);
                RenderHelper.renderExploredNode(current);
            } else {
                // Dead end — blacklist this state and backtrack up the tree
                blacklisted.add(current.closedSetHashCode());
                boolean found = false;
                while (!decisions.isEmpty()) {
                    DecisionPoint dp = decisions.peek();
                    // Skip any siblings that have since been blacklisted
                    while (dp.nextIndex < dp.children.size()
                            && blacklisted.contains(dp.children.get(dp.nextIndex).closedSetHashCode())) {
                        dp.nextIndex++;
                    }
                    if (dp.nextIndex < dp.children.size()) {
                        current = dp.children.get(dp.nextIndex);
                        dp.nextIndex++;
                        totalBacktracks++;
                        found = true;
                        RenderHelper.renderExploredNode(current);
                        break;
                    } else {
                        // All children at this level exhausted — blacklist the parent too
                        decisions.pop();
                        if (!decisions.isEmpty()) {
                            // The node that owned this DecisionPoint is the parent;
                            // find it from the chosen child's parent reference
                            Node exhaustedParent = dp.children.get(0).parent;
                            if (exhaustedParent != null) {
                                blacklisted.add(exhaustedParent.closedSetHashCode());
                            }
                        }
                    }
                }

                if (!found) {
                    Debug.logMessage("No path found - exhausted all options!");
                    break;
                }
            }
        }

        // Performance metrics
        long totalTime = System.currentTimeMillis() - startTime;
        Debug.logMessage("=== PathFinder Performance Metrics ===");
        Debug.logMessage("Total pathfinding time: " + totalTime + "ms");
        Debug.logMessage("Total nodes evaluated: " + totalNodesEvaluated);
        Debug.logMessage("Total backtracks: " + totalBacktracks);
        Debug.logMessage("Nodes per second: " + (totalNodesEvaluated * 1000L / Math.max(1, totalTime)));
        Debug.logMessage("====================================");

        if (stop.get()) {
            stop.set(false);
        }

        this.blockPath = Optional.empty();
    }

    private List<Node> generateAndFilterChildren(WorldView world, Node parent, Vec3d target, Set<Long> blacklisted) {
        List<Node> children = parent.getChildren(world, target, blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()));

        Vec3d waypointPos = BlockPosShifter.getPosOnLadder(blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()));
        double desiredYaw = DirectionHelper.calcYawFromVec3d(parent.agent.getPos(), waypointPos);
        double parentDist = parent.agent.getPos().squaredDistanceTo(waypointPos);

        List<Node> valid = new ArrayList<>();

        for (Node child : children) {
            if (stop.get()) break;
            if (blacklisted.contains(child.closedSetHashCode())) continue;

            updateNode(world, child, target, blockPath.get());

            if (child.agent.getPos().squaredDistanceTo(waypointPos) <= parentDist) {
                valid.add(child);
            } else {
                blacklisted.add(child.closedSetHashCode());
            }
        }

        // Sort by combinedCost, breaking ties with yaw proximity to waypoint
        valid.sort(Comparator.comparingDouble((Node n) -> n.combinedCost)
                .thenComparingDouble(n -> Math.abs(n.agent.yaw - desiredYaw)));
        return valid;
    }

    private Node initializeStartNode(SimulatedPlayer agent, Vec3d target) {
        Node start = new Node(null, agent, Color.GREEN, 0);
        start.combinedCost = computeHeuristic(start.agent.getPos(), target);
        return start;
    }

    private Node initializeStartNode(ClientPlayerEntity player, Vec3d target) {
        return initializeStartNode(new SimulatedPlayer(player), target);
    }

    private Optional<List<BlockNode>> findBlockPath(WorldView world, Vec3d target) {
        return BlockSpacePathfinder.search(world, target);
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

}
