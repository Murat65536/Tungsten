package kaptainwutax.tungsten.simulation;

import kaptainwutax.tungsten.path.PathInput;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

public class SimulatedInput extends Input {

    private PlayerInput nextInput = new PlayerInput(false, false, false, false, false, false, false);

    public void setInput(PathInput input) {
        this.nextInput = new PlayerInput(
                input.forward(),
                input.back(),
                input.left(),
                input.right(),
                input.jump(),
                input.sneak(),
                input.sprint()
        );
    }

    private static float getMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        } else {
            return positive ? 1.0F : -1.0F;
        }
    }

    @Override
    public void tick() {
        this.playerInput = this.nextInput;
        float f = getMovementMultiplier(this.playerInput.forward(), this.playerInput.backward());
        float g = getMovementMultiplier(this.playerInput.left(), this.playerInput.right());
        this.movementVector = new Vec2f(g, f).normalize();
    }
}
