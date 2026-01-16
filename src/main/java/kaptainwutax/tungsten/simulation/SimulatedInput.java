package kaptainwutax.tungsten.simulation;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

public final class SimulatedInput extends Input {
    public final float yaw;
    public final float pitch;

    public SimulatedInput(PlayerInput playerInput, float yaw, float pitch) {
        this.playerInput = playerInput;
        this.movementVector = new Vec2f(
                KeyboardInput.getMovementMultiplier(playerInput.forward(), playerInput.backward()),
                KeyboardInput.getMovementMultiplier(playerInput.left(), playerInput.right())
        ).normalize();
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void tick() {
        // TODO Maybe I need this?
    }
}
