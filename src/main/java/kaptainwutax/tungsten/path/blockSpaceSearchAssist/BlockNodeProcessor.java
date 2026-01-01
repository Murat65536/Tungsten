package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.List;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.generation.BlockNodeGenerator;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.movement.MovementTypeDetector;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation.CompositeValidator;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation.ValidationContext;
import net.minecraft.world.WorldView;

/**
 * Orchestrator class that coordinates all BlockNode processing components.
 * This facade provides a clean interface for pathfinding operations.
 */
public class BlockNodeProcessor {

    private final BlockNodeGenerator generator;
    private final CompositeValidator validator;

    /**
     * Creates a new BlockNodeProcessor with default components.
     */
    public BlockNodeProcessor() {
        this.generator = new BlockNodeGenerator();
        this.validator = new CompositeValidator();
    }

    /**
     * Processes a node to get its valid children for pathfinding.
     *
     * @param node The node to process
     * @param world The world view
     * @param goal The pathfinding goal
     * @param generateDeep Whether to generate deep nodes (for falls)
     * @return List of valid child nodes
     */
    public List<BlockNode> processNode(BlockNode node, WorldView world, Goal goal, boolean generateDeep) {
        // Clear any debug rendering from previous operations
        if (TungstenMod.TEST != null) {
            TungstenMod.TEST.clear();
        }

        // Generate child nodes
        List<BlockNode> children = generator.generateChildren(node, world, goal, generateDeep);

        // Apply movement detection to each valid child
        for (BlockNode child : children) {
            detectAndApplyMovement(world, node, child);
        }

        return children;
    }

    /**
     * Validates if movement from one node to another is valid.
     *
     * @param world The world view
     * @param from The starting node
     * @param to The destination node
     * @return true if the movement is valid
     */
    public boolean isValidMovement(WorldView world, BlockNode from, BlockNode to) {
        ValidationContext context = ValidationContext.from(world, from, to);
        return validator.isValid(context);
    }

    /**
     * Detects and applies the movement type for a node transition.
     *
     * @param world The world view
     * @param from The starting node
     * @param to The destination node
     */
    private void detectAndApplyMovement(WorldView world, BlockNode from, BlockNode to) {
        MovementTypeDetector.DetectedMovement movement = MovementTypeDetector.detect(world, from, to);
        movement.applyTo(to);
    }

    /**
     * Checks if a path is clear between two nodes.
     * This is a convenience method that delegates to BlockNode's static method.
     *
     * @param world The world view
     * @param from The starting node
     * @param to The destination node
     * @return true if the path is clear
     */
    public boolean isPathClear(WorldView world, BlockNode from, BlockNode to) {
        return BlockNode.wasCleared(
            world,
            from.getBlockPos(),
            to.getBlockPos(),
            from,
            to
        );
    }

    /**
     * Gets the generator used by this processor.
     *
     * @return The block node generator
     */
    public BlockNodeGenerator getGenerator() {
        return generator;
    }

    /**
     * Gets the validator used by this processor.
     *
     * @return The composite validator
     */
    public CompositeValidator getValidator() {
        return validator;
    }
}