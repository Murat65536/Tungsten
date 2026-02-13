package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.simulation.SimulatedPlayer;
import kaptainwutax.tungsten.constants.pathfinding.PathfindingConstants;
import kaptainwutax.tungsten.constants.physics.PlayerConstants;
import kaptainwutax.tungsten.path.common.HeapNode;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.MathHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class Node implements HeapNode {
    public Node parent;
    public SimulatedPlayer agent;
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

    public Node(Node parent, SimulatedPlayer agent, Color color, double pathCost) {
        this.parent = parent;
        this.agent = agent;
        this.color = color;
        this.cost = pathCost;
        this.combinedCost = 0;
        this.heapPosition = -1;
    }

    public Node(Node parent, WorldView world, PathInput input, Color color, double pathCost) {
        this.parent = parent;
        this.agent = new SimulatedPlayer(parent.agent, input).tick(world);
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
            // Quantize velocities to 0.1 blocks/tick (use rounding for consistency with closedSetHashCode)
            quantizedVelX = (int) Math.round(this.agent.velX * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelY = (int) Math.round(this.agent.velY * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelZ = (int) Math.round(this.agent.velZ * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);

            Vec3d pos = this.agent.getPos();
            if (pos != null) {
                // Quantize positions to 0.01 blocks
                quantizedPosX = (int) Math.round(pos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosY = (int) Math.round(pos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosZ = (int) Math.round(pos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
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

    /**
     * Hash based only on physical state (position + velocity) for the closed set.
     * Input state is excluded because the same position+velocity leads to the same
     * future possibilities regardless of which inputs were used to reach it.
     * Returns a 64-bit hash to minimize collision probability in the closed set.
     */
    public long closedSetHashCode() {
        int quantizedPosX = 0, quantizedPosY = 0, quantizedPosZ = 0;
        int quantizedVelX = 0, quantizedVelY = 0, quantizedVelZ = 0;
        int flags = 0;

        if (this.agent != null) {
            // Use rounding (not truncation) to avoid collapsing distinct motion states into the same closed-set bucket.
            quantizedVelX = (int) Math.round(this.agent.velX * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelY = (int) Math.round(this.agent.velY * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelZ = (int) Math.round(this.agent.velZ * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);

            Vec3d pos = this.agent.getPos();
            if (pos != null) {
                quantizedPosX = (int) Math.round(pos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosY = (int) Math.round(pos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosZ = (int) Math.round(pos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
            }

            // Include movement-relevant discrete state to prevent over-deduplication (which can starve the open set).
            if (this.agent.onGround) flags |= 1;
            if (this.agent.touchingWater) flags |= 2;
            if (this.agent.isSubmergedInWater) flags |= 4;
            if (this.agent.swimming) flags |= 8;
            if (this.agent.sprinting) flags |= 16;
        }

        // 64-bit hash: pack position into upper bits, velocity+flags into lower bits
        long h = ((long) quantizedPosX * 73856093L) ^ ((long) quantizedPosY * 19349669L) ^ ((long) quantizedPosZ * 83492791L);
        h = h * 31L + ((long) quantizedVelX * 48611L) ^ ((long) quantizedVelY * 96857L) ^ ((long) quantizedVelZ * 27644437L);
        h = h * 31L + flags;
        return h;
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

        if (!((int) Math.round(thisPos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int) Math.round(otherPos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int) Math.round(thisPos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int) Math.round(otherPos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int) Math.round(thisPos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int) Math.round(otherPos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int) Math.round(this.agent.velX * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int) Math.round(other.agent.velX * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) &&
                (int) Math.round(this.agent.velY * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int) Math.round(other.agent.velY * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) &&
                (int) Math.round(this.agent.velZ * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int) Math.round(other.agent.velZ * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING))) {
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
        BlockState state = world.getBlockState(nextBlockNode.getBlockPos());

        if (agent.isClimbing(world)
                && state.getBlock() instanceof LadderBlock
                && nextBlockNode.getBlockPos().getX() == agent.blockX
                && nextBlockNode.getBlockPos().getZ() == agent.blockZ) {
            Direction dir = state.get(Properties.HORIZONTAL_FACING);
            double desiredYaw = DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true).offset(dir.getOpposite(), 1)) + MathHelper.roundToPrecision(Math.random(), 2) / 1000000;
            if (nextBlockNode.getBlockPos().getY() > agent.blockY) {
                createAndAddNode(world, nextBlockNode, nodes, true, false, false, false, false, true, (float) desiredYaw, isDoingLongJump);
                return;
            }
            if (nextBlockNode.getBlockPos().getY() < agent.blockY) {
                createAndAddNode(world, nextBlockNode, nodes, true, false, false, false, false, false, (float) desiredYaw, isDoingLongJump);
                return;
            }
        }

        // Try the direct yaw first (most likely to succeed), then offset yaws
        float increment = PlayerConstants.Inputs.YAW_RANGE * 2 / (PlayerConstants.Inputs.YAW_PRECISION - 1);
        float directYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
        int inputCount = agent.onGround ? PlayerConstants.Inputs.ALL_INPUTS.length : PlayerConstants.Inputs.NO_JUMP_INPUT_LENGTH;

        // Generate direct yaw first, then offsets, to avoid left/right bias from deduplication
        for (int yi = 0; yi < PlayerConstants.Inputs.YAW_PRECISION; yi++) {
            float yaw;
            if (yi == 0) {
                yaw = directYaw;
            } else if (yi % 2 == 1) {
                yaw = directYaw - increment * ((yi + 1) / 2);
            } else {
                yaw = directYaw + increment * (yi / 2);
            }
            for (int i = 0; i < inputCount; i++) {
                KeyboardInput input = PlayerConstants.Inputs.ALL_INPUTS[i];
                createAndAddNode(world, nextBlockNode, nodes, input.forward(), input.right(), input.left(), input.sneak(), input.sprint(), input.jump(), yaw, isDoingLongJump);
            }
        }
    }

    private void createAndAddNode(WorldView world, BlockNode nextBlockNode, List<Node> nodes,
                                  boolean forward, boolean right, boolean left, boolean sneak, boolean sprint, boolean jump,
                                  float yaw, boolean isDoingLongJump) {
        try {
            if (jump && sneak) return;
            // Pre-filter impossible input combinations using parent state to avoid expensive simulation
            if (!agent.touchingWater && (sneak && sprint)) return;
            if (!agent.touchingWater && sneak && (right || left) && forward) return;
            if (!agent.touchingWater && !agent.onGround && sneak) return;
            if (!agent.touchingWater && sneak && jump) return;

            Node newNode = new Node(this, world, new PathInput(forward, false, left, right, jump, sneak, sprint, agent.pitch, yaw),
                    new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost);
            if (newNode.agent.isClimbing(world))
                jump = this.agent.getBlockPos().getY() < nextBlockNode.getBlockPos().getY();

            if (newNode.agent.touchingWater && (sneak || jump) && newNode.agent.getBlockPos().getY() == nextBlockNode.getBlockPos().getY())
                return;
            double addNodeCost = calculateNodeCost(forward, sprint, jump, sneak, newNode.agent);
            newNode.cost = this.cost + addNodeCost;
            nodes.add(newNode);
        } catch (ConcurrentModificationException e) {
            try {
                Thread.sleep(2);
                System.out.println("Thread interrupted");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Debug.logWarning("Thread interrupted: " + ie.getMessage());
            }
        }
    }

    private double calculateNodeCost(boolean forward, boolean sprint, boolean jump, boolean sneak, SimulatedPlayer agent) {
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
