package kaptainwutax.tungsten.agent;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

public class AgentInput extends Input {

	private final Agent agent;

	public AgentInput(Agent agent) {
		this.agent = agent;
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
		this.playerInput = new PlayerInput(
				this.agent.keyForward,
				this.agent.keyBack,
				this.agent.keyLeft,
				this.agent.keyRight,
				this.agent.keyJump,
				this.agent.keySneak,
				this.agent.sprinting
		);
		float f = getMovementMultiplier(this.playerInput.forward(), this.playerInput.backward());
		float g = getMovementMultiplier(this.playerInput.left(), this.playerInput.right());
		this.movementVector = new Vec2f(g, f).normalize();
	}

}
