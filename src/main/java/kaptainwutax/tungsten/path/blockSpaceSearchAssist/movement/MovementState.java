package kaptainwutax.tungsten.path.blockSpaceSearchAssist.movement;

import kaptainwutax.tungsten.TungstenMod;
import net.minecraft.block.SlimeBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Manages the movement state of a block node.
 * Tracks special movement conditions and types.
 */
public class MovementState {
    public final boolean wasOnSlime;
    public final boolean wasOnLadder;
    private MovementType currentMovement;
    private Direction neoSide;

    /**
     * Creates a movement state from a block position.
     *
     * @param pos The block position
     */
    public MovementState(BlockPos pos) {
        this.wasOnSlime = TungstenMod.mc.world.getBlockState(pos.down()).getBlock() instanceof SlimeBlock;
        this.wasOnLadder = TungstenMod.mc.world.getBlockState(pos).getBlock() instanceof LadderBlock;
        this.currentMovement = MovementType.NORMAL;
        this.neoSide = null;
    }

    /**
     * Creates a movement state from coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public MovementState(int x, int y, int z) {
        this(new BlockPos(x, y, z));
    }

    /**
     * Gets the current movement type.
     *
     * @return The movement type
     */
    public MovementType getCurrentMovement() {
        return currentMovement;
    }

    /**
     * Sets the current movement type.
     *
     * @param type The movement type
     */
    public void setCurrentMovement(MovementType type) {
        this.currentMovement = type;
    }

    /**
     * Checks if performing a neo movement.
     *
     * @return true if doing neo
     */
    public boolean isDoingNeo() {
        return currentMovement == MovementType.NEO;
    }

    /**
     * Sets neo movement with the wall side.
     *
     * @param side The wall side for neo
     */
    public void setNeoMovement(Direction side) {
        this.currentMovement = MovementType.NEO;
        this.neoSide = side;
    }

    /**
     * Gets the neo wall side.
     *
     * @return The neo side direction
     */
    public Direction getNeoSide() {
        return neoSide;
    }

    /**
     * Checks if performing a corner jump.
     *
     * @return true if doing corner jump
     */
    public boolean isDoingCornerJump() {
        return currentMovement == MovementType.CORNER_JUMP;
    }

    /**
     * Sets corner jump movement.
     */
    public void setCornerJump() {
        this.currentMovement = MovementType.CORNER_JUMP;
        this.neoSide = null;
    }

    /**
     * Checks if performing a long jump.
     *
     * @return true if doing long jump
     */
    public boolean isDoingLongJump() {
        return currentMovement == MovementType.LONG_JUMP;
    }

    /**
     * Sets long jump movement.
     */
    public void setLongJump() {
        this.currentMovement = MovementType.LONG_JUMP;
        this.neoSide = null;
    }

    /**
     * Resets movement to normal.
     */
    public void resetMovement() {
        this.currentMovement = MovementType.NORMAL;
        this.neoSide = null;
    }

    /**
     * Creates a copy of this movement state.
     *
     * @return A new movement state with the same values
     */
    public MovementState copy() {
        MovementState copy = new MovementState(0, 0, 0);
        copy.currentMovement = this.currentMovement;
        copy.neoSide = this.neoSide;
        return copy;
    }
}