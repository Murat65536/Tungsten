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
import kaptainwutax.tungsten.simulation.HollowClientPlayerEntity;
import kaptainwutax.tungsten.simulation.SimulatedInput;
import kaptainwutax.tungsten.simulation.SimulatedPlayerState;
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
import net.minecraft.block.ShapeContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;

public class Node implements HeapNode {
    public Node parent;
    public SimulatedPlayerState state;
    public SimulatedInput input;
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

    public Node(Node parent, SimulatedPlayerState state, Color color, double pathCost) {
        this.parent = parent;
        this.state = state;
        this.color = color;
        this.cost = pathCost;
        this.combinedCost = 0;
        this.heapPosition = -1;
        this.input = null;
    }

    public Node(Node parent, WorldView world, SimulatedInput input, Color color, double pathCost) {
        this.parent = parent;

        HollowClientPlayerEntity hollowPlayer = TungstenMod.HOLLOW_PLAYER;

        // Create state with new input to apply before ticking
        SimulatedPlayerState startState = parent.state.withInput(input);

        // Apply state and tick with specific rotation
        hollowPlayer.tickMovement(startState);

        // Capture result state
        this.state = new SimulatedPlayerState(hollowPlayer.getPlayer(), input);
        
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

        if (this.state != null) {
            // Quantize velocities to 0.1 blocks/tick
            quantizedVelX = (int) (this.state.getVelocity().x * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelY = (int) (this.state.getVelocity().y * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
            quantizedVelZ = (int) (this.state.getVelocity().z * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);

            Vec3d pos = this.state.getPos();
            if (pos != null) {
                // Quantize positions to 0.01 blocks
                quantizedPosX = (int) (pos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosY = (int) (pos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
                quantizedPosZ = (int) (pos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
            }


            // Build hash with quantized values
            if (shouldAddYaw && this.input != null) {
                return Objects.hash(
                        this.input.playerInput.forward(), this.input.playerInput.backward(), this.input.playerInput.right(), this.input.playerInput.left(),
                        this.input.playerInput.jump(), this.input.playerInput.sneak(), this.input.playerInput.sprint(),
                        this.state.getPitch(), this.state.getYaw(),
                        quantizedVelX, quantizedVelY, quantizedVelZ,
                        quantizedPosX, quantizedPosY, quantizedPosZ
                );
            } else if (this.input != null) {
                return Objects.hash(
                        this.input.playerInput.forward(), this.input.playerInput.backward(), this.input.playerInput.right(), this.input.playerInput.left(),
                        this.input.playerInput.jump(), this.input.playerInput.sneak(), this.input.playerInput.sprint(),
                        this.state.getPitch(),
                        quantizedVelX, quantizedVelY, quantizedVelZ,
                        quantizedPosX, quantizedPosY, quantizedPosZ
                );
            } else {
                return Objects.hash(quantizedVelX, quantizedVelZ,
                        quantizedPosX, quantizedPosY, quantizedPosZ);
            }
        }
        return Objects.hash(quantizedVelX, quantizedVelZ,
                quantizedPosX, quantizedPosY, quantizedPosZ);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Node other = (Node) obj;

        // Compare quantized values for equality
        if (this.state == null || other.state == null) {
            return this.state == other.state;
        }

        // Quantize positions (0.01 block precision)
        Vec3d thisPos = this.state.getPos();
        Vec3d otherPos = other.state.getPos();
        if (thisPos == null || otherPos == null) {
            return thisPos == otherPos;
        }

        if (!((int)(thisPos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int)(otherPos.x * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int)(thisPos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int)(otherPos.y * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int)(thisPos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) == (int)(otherPos.z * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING) &&
                (int)(this.state.getVelocity().x * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int)(other.state.getVelocity().x * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) &&
                (int)(this.state.getVelocity().y * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int)(other.state.getVelocity().y * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) &&
                (int)(this.state.getVelocity().z * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING) == (int)(other.state.getVelocity().z * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING))) {
            return false;
        }

        // Check input equality if present
        if (this.input == null || other.input == null) {
            return this.input == other.input;
        }

        return this.input.playerInput.forward() == other.input.playerInput.forward() &&
               this.input.playerInput.backward() == other.input.playerInput.backward() &&
               this.input.playerInput.right() == other.input.playerInput.right() &&
               this.input.playerInput.left() == other.input.playerInput.left() &&
               this.input.playerInput.jump() == other.input.playerInput.jump() &&
               this.input.playerInput.sneak() == other.input.playerInput.sneak() &&
               this.input.playerInput.sprint() == other.input.playerInput.sprint() &&
               this.state.getPitch() == other.state.getPitch() &&
               this.state.getYaw() == other.state.getYaw();
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
        return n != null && (n.state.isInLava() || state.isInLava() || (state.getFallDistance() >
                this.state.getPos().y - nextBlockNode.getBlockPos().getY() + 2
                && !state.isTouchingWater()
        ));
    }

    private void generateNodes(WorldView world, Vec3d target, BlockNode nextBlockNode, List<Node> nodes) {
        boolean isDoingLongJump = nextBlockNode.isDoingLongJump() || nextBlockNode.isDoingNeo();
        boolean isCloseToBlockNode = DistanceCalculator.getHorizontalEuclideanDistance(state.getPos(), nextBlockNode.getPos(true)) < 1;
        BlockState blockState = world.getBlockState(nextBlockNode.getBlockPos());

        if (state.isClimbing(world)
                && blockState.getBlock() instanceof LadderBlock
                && nextBlockNode.getBlockPos().getX() == state.getBlockPos().getX()
                && nextBlockNode.getBlockPos().getZ() == state.getBlockPos().getZ()) {
            Direction dir = blockState.get(Properties.HORIZONTAL_FACING);
            double desiredYaw = DirectionHelper.calcYawFromVec3d(state.getPos(), nextBlockNode.getPos(true).offset(dir.getOpposite(), 1)) + MathHelper.roundToPrecision(Math.random(), 2) / 1000000;
            if (nextBlockNode.getBlockPos().getY() > state.getBlockPos().getY()) {
                createAndAddNode(world, nextBlockNode, nodes, true, false, false, false, false, true, (float) desiredYaw, isDoingLongJump, isCloseToBlockNode);
                return;
            }
            if (nextBlockNode.getBlockPos().getY() < state.getBlockPos().getY()) {
                createAndAddNode(world, nextBlockNode, nodes, true, false, false, false, false, false, (float) desiredYaw, isDoingLongJump, isCloseToBlockNode);
                return;
            }
        }


        for (int i = 0; i < (state.isOnGround() ? PlayerConstants.Inputs.ALL_INPUTS.length : PlayerConstants.Inputs.NO_JUMP_INPUT_LENGTH); i++) {
            kaptainwutax.tungsten.path.KeyboardInput input = PlayerConstants.Inputs.ALL_INPUTS[i];
            float increment = PlayerConstants.Inputs.YAW_RANGE * 2 / (PlayerConstants.Inputs.YAW_PRECISION - 1);
            float directYaw = (float) Math.round(DirectionHelper.calcYawFromVec3d(state.getPos(), nextBlockNode.getPos(true)) / increment) * increment;
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
            SimulatedInput playerInput = new SimulatedInput(new PlayerInput(forward, false, left, right, jump, sneak, sprint), yaw, state.getPitch());
            Node newNode = new Node(this, world, playerInput,
                    new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost);
            if (newNode.state.isClimbing(world))
                jump = this.state.getBlockPos().getY() < nextBlockNode.getBlockPos().getY();

            if (!newNode.state.isTouchingWater() && !newNode.state.isOnGround() && sneak) return;
            if (!newNode.state.isTouchingWater() && sneak && jump) return;
            if (!newNode.state.isTouchingWater() && (sneak && sprint)) return;
            if (!newNode.state.isTouchingWater() && sneak && (right || left) && forward) return;
            if (!newNode.state.isTouchingWater() && sneak && Math.abs(newNode.parent.state.getYaw() - newNode.state.getYaw()) > 80)
                return;
            if (newNode.state.isTouchingWater() && (sneak || jump) && newNode.state.getBlockPos().getY() == nextBlockNode.getBlockPos().getY())
                return;
            if (newNode.state.isTouchingWater() && jump && newNode.state.getBlockPos().getY() > nextBlockNode.getBlockPos().getY())
                return;
            double addNodeCost = calculateNodeCost(forward, sprint, jump, sneak, newNode.state);
            if (newNode.state.getPos().isWithinRangeOf(nextBlockNode.getPos(true), 0.1, 0.4)) return;
            double newNodeDistanceToBlockNode = Math.ceil(newNode.state.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e5);
            double parentNodeDistanceToBlockNode = Math.ceil(newNode.parent.state.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e5);

            if (newNodeDistanceToBlockNode >= parentNodeDistanceToBlockNode) return;

            boolean isMoving = (forward || right || left);
            if (!sneak) {
                boolean isBelowClosedTrapDoor = BlockStateChecker.isClosedBottomTrapdoor(world.getBlockState(nextBlockNode.getBlockPos().down()));
                boolean shouldAllowWalkingOnLowerBlock = !world.getBlockState(state.getBlockPos().up(2)).isAir() && nextBlockNode.getPos(true).distanceTo(state.getPos()) < 3;
                double minY = isBelowClosedTrapDoor ? nextBlockNode.getPos(true).y - 1 : nextBlockNode.getBlockPos().getY() - (shouldAllowWalkingOnLowerBlock ? 1.3 : 0.3);
                for (int j = 0; j < ((!jump) && !newNode.state.isClimbing(world) ? 1 : 10); j++) {
                    if (!isMoving) break;
                    if (!(state.isHorizontalCollision() || state.isVerticalCollision()) && isDoingLongJump) jump = true;
                    playerInput = new SimulatedInput(new PlayerInput(forward, false, left, right, jump, sneak, sprint), yaw, state.getPitch());
                    newNode = new Node(newNode, world, playerInput,
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

    private double calculateNodeCost(boolean forward, boolean sprint, boolean jump, boolean sneak, SimulatedPlayerState state) {
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
        double desiredYaw = DirectionHelper.calcYawFromVec3d(state.getPos(), target);
        nodes.sort((n1, n2) -> {
            double diff1 = Math.abs(n1.state.getYaw() - desiredYaw);
            double diff2 = Math.abs(n2.state.getYaw() - desiredYaw);
            return Double.compare(diff1, diff2);
        });
    }
}