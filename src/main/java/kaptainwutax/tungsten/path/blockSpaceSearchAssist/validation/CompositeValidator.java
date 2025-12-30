package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

import kaptainwutax.tungsten.TungstenMod;
import java.util.Arrays;
import java.util.List;

/**
 * Composite validator that combines multiple validators.
 * Ensures all validation rules are checked in sequence.
 */
public class CompositeValidator implements NodeValidator {

    private final List<NodeValidator> validators;

    /**
     * Creates a composite validator with the standard set of validators.
     */
    public CompositeValidator() {
        this.validators = Arrays.asList(
            new WaterMovementValidator(),
            new LadderMovementValidator(),
            new SlabMovementValidator(),
            new BlockHeightValidator(),
            new JumpDistanceValidator(),
            new CollisionValidator()
        );
    }

    /**
     * Creates a composite validator with custom validators.
     *
     * @param validators The validators to use
     */
    public CompositeValidator(NodeValidator... validators) {
        this.validators = Arrays.asList(validators);
    }

    @Override
    public boolean isValid(ValidationContext context) {
        // Check if pathfinding should stop
        if (TungstenMod.PATHFINDER.stop.get()) {
            return false;
        }

        // Run all validators
        for (NodeValidator validator : validators) {
            if (!validator.isValid(context)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the list of validators being used.
     *
     * @return The validators
     */
    public List<NodeValidator> getValidators() {
        return validators;
    }
}