package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.constants.pathfinding.PathfindingConstants;
import kaptainwutax.tungsten.constants.physics.PlayerConstants;
import kaptainwutax.tungsten.path.common.HeapNode;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.MathHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;

public class Node implements HeapNode {
    public Node parent;
    public Agent agent;
    public PathInput input;
    public double cost;
    public double estimatedCostToGoal = 0;
    public int heapPosition;
    public double combinedCost;
    public Color color;

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

    public Node(Node parent, Agent agent, Color color, double pathCost) {
        this.parent = parent;
        this.agent = agent;
        this.color = color;
        this.cost = pathCost;
        this.combinedCost = 0;
        this.heapPosition = -1;
    }

    public Node(Node parent, WorldView world, PathInput input, Color color, double pathCost) {
        this.parent = parent;
        this.agent = new Agent(parent.agent, input).tick(world);
        this.input = input;
        this.color = color;
        this.cost = pathCost;
        this.combinedCost = 0;
        this.heapPosition = -1;
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    @Override
    public int hashCode() {
        return hashCode(true);
    }

    public int hashCode(boolean shouldAddYaw) {
        // Initialize quantized values
        int quantizedPosX = 0, quantizedPosY = 0, quantizedPosZ = 0;
        int quantizedVelX = 0, quantizedVelY = 0, quantizedVelZ = 0;

        if (this.agent != null) {
            // Quantize velocities to 0.1 blocks/tick
            quantizedVelX = (int)(this.agent.velX * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelY = (int)(this.agent.velY * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelZ = (int)(this.agent.velZ * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);

            Vec3d pos = this.agent.getPos();
            if (pos != null) {
                // Quantize positions to 0.01 blocks
                quantizedPosX = (int)(pos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosY = (int)(pos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosZ = (int)(pos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
            }
        }

        // Build hash with quantized values
        if (shouldAddYaw && this.input != null) {
            return Objects.hash(
                this.input.forward(), this.input.back(), this.input.right(), this.input.left(),
                this.input.jump(), this.input.sneak(), this.input.sprint(),
                this.input.pitch(), this.input.yaw(),
                quantizedVelX, quantizedVelY, quantizedVelZ,
                quantizedPosX, quantizedPosY, quantizedPosZ
            );
        } else if (this.input != null) {
            return Objects.hash(
                this.input.forward(), this.input.back(), this.input.right(), this.input.left(),
                this.input.jump(), this.input.sneak(), this.input.sprint(),
                this.input.pitch(),
                quantizedVelX, quantizedVelY, quantizedVelZ,
                quantizedPosX, quantizedPosY, quantizedPosZ
            );
        } else {
            return Objects.hash(quantizedVelX, quantizedVelZ,
                              quantizedPosX, quantizedPosY, quantizedPosZ);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Node other = (Node) obj;

        // Compare quantized values for equality
        if (this.agent == null || other.agent == null) {
            return this.agent == other.agent;
        }

        // Quantize positions (0.01 block precision)
        Vec3d thisPos = this.agent.getPos();
        Vec3d otherPos = other.agent.getPos();
        if (thisPos == null || otherPos == null) {
            return thisPos == otherPos;
        }

        if (!((int)(thisPos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int)(otherPos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int)(thisPos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int)(otherPos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int)(thisPos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int)(otherPos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int)(this.agent.velX * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int)(other.agent.velX * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) &&
                (int)(this.agent.velY * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int)(other.agent.velY * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) &&
                (int)(this.agent.velZ * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int)(other.agent.velZ * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING))) {
            return false;
        }

        // Check input equality if present
        if (this.input == null || other.input == null) {
            return this.input == other.input;
        }

        return this.input.forward() == other.input.forward() &&
               this.input.back() == other.input.back() &&
               this.input.right() == other.input.right() &&
               this.input.left() == other.input.left() &&
               this.input.jump() == other.input.jump() &&
               this.input.sneak() == other.input.sneak() &&
               this.input.sprint() == other.input.sprint() &&
               this.input.pitch() == other.input.pitch() &&
               this.input.yaw() == other.input.yaw();
    }

    public List<Node> getChildren(WorldView world, Vec3d target, BlockNode nextBlockNode) {
        if (shouldSkipNodeGeneration(nextBlockNode)) {
            return Collections.emptyList();
        }

        List<Node> nodes = new ArrayList<>();

        generateNodes(world, target, nextBlockNode, nodes);

        sortNodesByYaw(nodes, target);

        return nodes;
    }


    private boolean shouldSkipNodeGeneration(BlockNode nextBlockNode) {
        Node n = this.parent;
        return n != null && (n.agent.isInLava() || agent.isInLava() || (agent.fallDistance >
                this.agent.getPos().y - nextBlockNode.getBlockPos().getY() + 2
                && !agent.slimeBounce
                && !agent.touchingWater
        ));
    }

    private void generateNodes(WorldView world, Vec3d target, BlockNode nextBlockNode, List<Node> nodes) {
        boolean isDoingLongJump = nextBlockNode.isDoingLongJump() || nextBlockNode.isDoingNeo();
        boolean isCloseToBlockNode = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true)) < 1;
        BlockState state = world.getBlockState(nextBlockNode.getBlockPos());

        if (agent.isClimbing(world)
                && state.getBlock() instanceof LadderBlock
                && nextBlockNode.getBlockPos().getX() == agent.blockX
                && nextBlockNode.getBlockPos().getZ() == agent.blockZ) {
            Direction dir = state.get(Properties.HORIZONTAL_FACING);
            double desiredYaw = DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true).offset(dir.getOpposite(), 1)) + MathHelper.roundToPrecision(Math.random(), 2) / 1000000;
            if (nextBlockNode.getBlockPos().getY() > agent.blockY) {
                createAndAddNode(world, nextBlockNode, nodes, true, false, false, false, false, true, (float) desiredYaw, isDoingLongJump, isCloseToBlockNode);
                return;
            }
            if (nextBlockNode.getBlockPos().getY() < agent.blockY) {
                createAndAddNode(world, nextBlockNode, nodes, true, false, false, false, false, false, (float) desiredYaw, isDoingLongJump, isCloseToBlockNode);
                return;
            }
        }


        for (int i = 0; i < (agent.onGround ? PlayerConstants.Inputs.ALL_INPUTS.length : PlayerConstants.Inputs.NO_JUMP_INPUT_LENGTH); i++) {
            KeyboardInput input = PlayerConstants.Inputs.ALL_INPUTS[i];
            float increment = PlayerConstants.Inputs.YAW_RANGE * 2 / (PlayerConstants.Inputs.YAW_PRECISION - 1);
            // TODO Should we be using target instead of the block position?
            float directYaw = (float) Math.round(DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true)) / increment) * increment;
            for (float yaw = directYaw - PlayerConstants.Inputs.YAW_RANGE; yaw <= directYaw + PlayerConstants.Inputs.YAW_RANGE; yaw += increment) {
                createAndAddNode(world, nextBlockNode, nodes, input.forward(), input.right(), input.left(), input.sneak(), input.sprint(), input.jump(), yaw, isDoingLongJump, isCloseToBlockNode);
            }
        }
    }

    private void createAndAddNode(WorldView world, BlockNode nextBlockNode, List<Node> nodes,
                                  boolean forward, boolean right, boolean left, boolean sneak, boolean sprint, boolean jump,
                                  float yaw, boolean isDoingLongJump, boolean isCloseToBlockNode) {
        try {
            if (jump && sneak) return;
            Node newNode = new Node(this, world, new PathInput(forward, false, right, left, jump, sneak, sprint, agent.pitch, yaw),
                    new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost);
            if (newNode.agent.isClimbing(world))
                jump = this.agent.getBlockPos().getY() < nextBlockNode.getBlockPos().getY();

            if (!newNode.agent.touchingWater && !newNode.agent.onGround && sneak) return;
            if (!newNode.agent.touchingWater && sneak && jump) return;
            if (!newNode.agent.touchingWater && (sneak && sprint)) return;
            if (!newNode.agent.touchingWater && sneak && (right || left) && forward) return;
            if (!newNode.agent.touchingWater && sneak && Math.abs(newNode.parent.agent.yaw - newNode.agent.yaw) > 80)
                return;
            if (newNode.agent.touchingWater && (sneak || jump) && newNode.agent.getBlockPos().getY() == nextBlockNode.getBlockPos().getY())
                return;
            if (newNode.agent.touchingWater && jump && newNode.agent.getBlockPos().getY() > nextBlockNode.getBlockPos().getY())
                return;
            double addNodeCost = calculateNodeCost(forward, sprint, jump, sneak, newNode.agent);
            if (newNode.agent.getPos().isWithinRangeOf(nextBlockNode.getPos(true), 0.1, 0.4)) return;
            double newNodeDistanceToBlockNode = Math.ceil(newNode.agent.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e5);
            double parentNodeDistanceToBlockNode = Math.ceil(newNode.parent.agent.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e5);

            if (newNodeDistanceToBlockNode >= parentNodeDistanceToBlockNode) return;

            boolean isMoving = (forward || right || left);
            if (!sneak) {
                boolean isBelowClosedTrapDoor = BlockStateChecker.isClosedBottomTrapdoor(world.getBlockState(nextBlockNode.getBlockPos().down()));
                boolean shouldAllowWalkingOnLowerBlock = !world.getBlockState(agent.getBlockPos().up(2)).isAir() && nextBlockNode.getPos(true).distanceTo(agent.getPos()) < 3;
                double minY = isBelowClosedTrapDoor ? nextBlockNode.getPos(true).y - 1 : nextBlockNode.getBlockPos().getY() - (shouldAllowWalkingOnLowerBlock ? 1.3 : 0.3);
                for (int j = 0; j < ((!jump) && !newNode.agent.isClimbing(world) ? 1 : 10); j++) {
//		                if (newNode.agent.getPos().y <= minY && !newNode.agent.isClimbing(world) || !isMoving) break;
                    if (!isMoving) break;
                    Box adjustedBox = newNode.agent.box.offset(0, -0.5, 0).expand(-0.001, 0, -0.001);
                    Stream<VoxelShape> blockCollisions = Streams.stream(agent.getBlockCollisions(TungstenMod.mc.world, adjustedBox));
                    if (blockCollisions.findAny().isEmpty() && isDoingLongJump) jump = true;
                    newNode = new Node(newNode, world, new PathInput(forward, false, right, left, jump, sneak, sprint, agent.pitch, yaw),
                            jump ? new Color(0, 255, 255) : new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost + addNodeCost);
                    if (!isDoingLongJump && jump && j > 1) break;
                }
            }

            nodes.add(newNode);
        } catch (ConcurrentModificationException e) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Debug.logWarning("Thread interrupted: " + ie.getMessage());
            }
        }
    }

    private double calculateNodeCost(boolean forward, boolean sprint, boolean jump, boolean sneak, Agent agent) {
        double addNodeCost = 1;

        if (forward && sprint && jump && !sneak) {
            addNodeCost -= 0.2;
        }

        if (sneak) {
            addNodeCost += 2;
        }

        return addNodeCost;
    }

    private void sortNodesByYaw(List<Node> nodes, Vec3d target) {
        double desiredYaw = DirectionHelper.calcYawFromVec3d(agent.getPos(), target);
        nodes.sort((n1, n2) -> {
            double diff1 = Math.abs(n1.agent.yaw - desiredYaw);
            double diff2 = Math.abs(n2.agent.yaw - desiredYaw);
            return Double.compare(diff1, diff2);
        });
    }
}
