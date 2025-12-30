package kaptainwutax.tungsten.path.blockSpaceSearchAssist.validation;

/**
 * Base interface for all node validation logic.
 * Implementations should validate specific movement types or conditions.
 */
public interface NodeValidator {
    /**
     * Validates whether movement from one node to another is valid
     * according to this validator's specific rules.
     *
     * @param context The validation context containing all necessary information
     * @return true if the movement is valid, false otherwise
     */
    boolean isValid(ValidationContext context);

    /**
     * Gets a descriptive name for this validator.
     *
     * @return The validator name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}