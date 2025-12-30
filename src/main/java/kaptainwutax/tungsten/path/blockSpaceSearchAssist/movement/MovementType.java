package kaptainwutax.tungsten.path.blockSpaceSearchAssist.movement;

/**
 * Enum representing different types of movement in pathfinding.
 */
public enum MovementType {
    /**
     * Normal walking or jumping movement
     */
    NORMAL,

    /**
     * Neo parkour movement (wall-assisted jump)
     */
    NEO,

    /**
     * Corner jump parkour movement
     */
    CORNER_JUMP,

    /**
     * Long distance jump (4-6 blocks)
     */
    LONG_JUMP,

    /**
     * Ladder or vine climbing movement
     */
    CLIMBING,

    /**
     * Swimming in water
     */
    SWIMMING,

    /**
     * Falling movement
     */
    FALLING
}