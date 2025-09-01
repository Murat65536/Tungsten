package kaptainwutax.tungsten.agent;

import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

public class AgentInput {
	

	public PlayerInput playerInput = PlayerInput.DEFAULT;
	protected Vec2f movementVector = Vec2f.ZERO;

	public Vec2f getMovementInput() {
		return this.movementVector;
	}

	public boolean hasForwardMovement() {
		return this.movementVector.y > 1.0E-5F;
	}

	public void jump() {
		this.playerInput = new PlayerInput(
			this.playerInput.forward(),
			this.playerInput.backward(),
			this.playerInput.left(),
			this.playerInput.right(),
			true,
			this.playerInput.sneak(),
			this.playerInput.sprint()
		);
	}

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
