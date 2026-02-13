package kaptainwutax.tungsten.path.blockSpaceSearchAssist.generation;

import java.util.ArrayList;
import java.util.List;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.constants.pathfinding.CostConstants;
import kaptainwutax.tungsten.constants.physics.GravityConstants;
import kaptainwutax.tungsten.constants.physics.MovementConstants;
import kaptainwutax.tungsten.helpers.MovementHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.Goal;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation.CompositeValidator;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation.ValidationContext;
import net.minecraft.world.WorldView;

/**
 * Generates child nodes for pathfinding.
 * Handles the complex logic of determining possible movements from a given node.
 */
public class BlockNodeGenerator {

    private static final int NODE_GENERATION_RADIUS = 5;
    private static final int DEEP_GENERATION_MIN = -64;
    private static final int SHALLOW_GENERATION_MIN = -4;
    private static final int SLIME_BOUNCE_CHECK_HEIGHT = 4;
    private static final int MAX_JUMP_HEIGHT = 2;
    private static final double SLAB_HEIGHT = 0.5;

    private final CompositeValidator validator;

    public BlockNodeGenerator() {
        this.validator = new CompositeValidator();
    }

    /**
     * Generates child nodes for the given parent node.
     *
     * @param parent The parent node
     * @param world The world view
     * @param goal The pathfinding goal
     * @param generateDeep Whether to generate nodes for deep falls
     * @return List of valid child nodes
     */
    public List<BlockNode> generateChildren(BlockNode parent, WorldView world, Goal goal, boolean generateDeep) {
        // Optimize generation radius based on distance to goal
        int effectiveRadius = calculateEffectiveRadius(parent, goal, NODE_GENERATION_RADIUS);

        // Generate all possible nodes in a 3D circle
        List<BlockNode> nodes = generateNodesIn3DCircle(effectiveRadius, parent, goal, generateDeep);

        // Filter nodes using validators
        List<BlockNode> validNodes = new ArrayList<>(nodes.size());
        for (BlockNode node : nodes) {
            if (isValidNode(world, parent, node)) {
                validNodes.add(node);
            }
        }
        
        return validNodes;
    }

    /**
     * Calculates an optimized generation radius based on distance to goal.
     * Reduces unnecessary node generation when close to goal.
     */
    private int calculateEffectiveRadius(BlockNode parent, Goal goal, int maxRadius) {
        double distanceToGoal = parent.estimatedCostToGoal;

        // When very close to goal, reduce radius significantly
        if (distanceToGoal < 5.0) {
            return Math.min(3, maxRadius);
        }

        // Use full radius when far from goal
        return maxRadius;
    }

    /**
     * Validates if a child node is reachable from the parent.
     *
     * @param world The world view
     * @param parent The parent node
     * @param child The child node to validate
     * @return true if the node is valid
     */
    private boolean isValidNode(WorldView world, BlockNode parent, BlockNode child) {
        ValidationContext context = ValidationContext.from(world, parent, child,
            TungstenMod.ignoreFallDamage, TungstenMod.PATHFINDER.stop);
        return validator.isValid(context);
    }

    /**
     * Generates nodes in a 3D circle pattern around the parent.
     *
     * @param radius The generation radius
     * @param parent The parent node
     * @param goal The pathfinding goal
     * @param generateDeep Whether to generate deep nodes
     * @return List of generated nodes
     */
    private List<BlockNode> generateNodesIn3DCircle(int radius, BlockNode parent, Goal goal, boolean generateDeep) {
        List<BlockNode> nodes = new ArrayList<>();

        // Calculate maximum Y based on slime bounce or normal jump
        double yMax = calculateMaxY(parent, generateDeep);
        int finalYMax = (int) Math.ceil(yMax);
        
        int minY = generateDeep ? DEEP_GENERATION_MIN : SHALLOW_GENERATION_MIN;

        for (int py = minY; py < finalYMax; py++) {
            generateNodesAtHeight(nodes, parent, goal, radius, py, generateDeep);
        }

        return nodes;
    }

    /**
     * Calculates the maximum Y height for node generation.
     *
     * @param parent The parent node
     * @param generateDeep Whether generating deep nodes
     * @return The maximum Y height
     */
    private double calculateMaxY(BlockNode parent, boolean generateDeep) {
        boolean isSlimeBounce = parent.wasOnSlime &&
                              parent.previous != null &&
                              parent.previous.y - parent.y < 0;

        if (isSlimeBounce) {
            return MovementHelper.getSlimeBounceHeight(parent.previous.y - parent.y) - SLAB_HEIGHT;
        }

        return generateDeep ? SLIME_BOUNCE_CHECK_HEIGHT : MAX_JUMP_HEIGHT;
    }

    /**
     * Generates nodes at a specific height level.
     *
     * @param nodes The collection to add nodes to
     * @param parent The parent node
     * @param goal The pathfinding goal
     * @param radius The generation radius
     * @param py The Y offset from parent
     * @param generateDeep Whether generating deep nodes
     */
    private void generateNodesAtHeight(List<BlockNode> nodes,
                                      BlockNode parent,
                                      Goal goal,
                                      int radius,
                                      int py,
                                      boolean generateDeep) {
        int localRadius;

        // Calculate radius based on fall distance for deep generation
        if (py < -5) {
            double t = Math.sqrt((2 * py * -1) / GravityConstants.Gravity.GRAVITY_ACCELERATION);
            localRadius = (int) Math.ceil(MovementConstants.Speed.SPRINT_VELOCITY * t);
        } else {
            localRadius = radius + 1;
        }

        // Generate center node at this height
        nodes.add(new BlockNode(
            parent.x, parent.y + py, parent.z,
            goal, parent,
            CostConstants.BaseCosts.WALK_ONE_BLOCK_COST
        ));

        // Generate nodes in a diamond pattern at this height
        generateDiamondPattern(nodes, parent, goal, localRadius, py);
    }

    /**
     * Generates nodes in a diamond (square rotated 45°) pattern.
     *
     * @param nodes The collection to add nodes to
     * @param parent The parent node
     * @param goal The pathfinding goal
     * @param radius The pattern radius
     * @param py The Y offset
     */
    private void generateDiamondPattern(List<BlockNode> nodes,
                                       BlockNode parent,
                                       Goal goal,
                                       int radius,
                                       int py) {
        for (int id = 1; id <= radius; id++) {
            int px = id, pz = 0;
            int dx = -1, dz = 1;
            int n = id * 4;

            for (int i = 0; i < n; i++) {
                // Update direction at corners
                if (px == id && dx > 0) dx = -1;
                else if (px == -id && dx < 0) dx = 1;

                if (pz == id && dz > 0) dz = -1;
                else if (pz == -id && dz < 0) dz = 1;

                px += dx;
                pz += dz;

                BlockNode newNode = new BlockNode(
                    parent.x + px,
                    parent.y + py,
                    parent.z + pz,
                    goal,
                    parent,
                    CostConstants.BaseCosts.WALK_ONE_BLOCK_COST
                );
                nodes.add(newNode);
            }
        }
    }
}