package kaptainwutax.tungsten.path;

import java.util.List;

import kaptainwutax.tungsten.TungstenMod;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;

public class PathExecutor {

    protected List<Node> path;
    protected int tick = 0;
    protected boolean allowedFlying = false;
    public boolean stop = false;
    public Runnable cb = null;

    public PathExecutor() {
    	try {
        	this.allowedFlying = TungstenMod.mc.player.getAbilities().allowFlying;
		} catch (Exception e) {
			this.allowedFlying = true;
		}
	}

	public void setPath(List<Node> path) {
    	this.allowedFlying = TungstenMod.mc.player.getAbilities().allowFlying;
	    stop = false;
    	this.path = path;
    	this.tick = 0;
	}
	
	public List<Node> getPath() {
		return this.path;
	}

	public boolean isRunning() {
        return this.path != null && this.tick <= this.path.size();
    }

    public void tick(ClientPlayerEntity player, GameOptions options) {
    	player.getAbilities().allowFlying = false;
    	if(TungstenMod.pauseKeyBinding.isPressed() || stop) {
    		this.tick = this.path.size();
		    options.forwardKey.setPressed(false);
		    options.backKey.setPressed(false);
		    options.leftKey.setPressed(false);
		    options.rightKey.setPressed(false);
		    options.jumpKey.setPressed(false);
		    options.sneakKey.setPressed(false);
		    options.sprintKey.setPressed(false);
		    player.getAbilities().allowFlying = allowedFlying;
		    this.path = null;
		    stop = false;
		    TungstenMod.RUNNING_PATH_RENDERER.clear();
		    TungstenMod.BLOCK_PATH_RENDERER.clear();
    		return;
    	}
    	if(this.tick == this.path.size()) {
		    options.forwardKey.setPressed(false);
		    options.backKey.setPressed(false);
		    options.leftKey.setPressed(false);
		    options.rightKey.setPressed(false);
		    options.jumpKey.setPressed(false);
		    options.sneakKey.setPressed(false);
		    options.sprintKey.setPressed(false);
		    player.getAbilities().allowFlying = allowedFlying;
		    this.path = null;
		    stop = false;
		    TungstenMod.RUNNING_PATH_RENDERER.clear();
		    TungstenMod.BLOCK_PATH_RENDERER.clear();
			player.setVelocity(0, 0, 0);
		    if (cb != null) {
		    	cb.run();
		    	cb = null;
		    }
	    } else {
		    Node node = this.path.get(this.tick);
		    if(this.tick != 0) {
			    this.path.get(this.tick - 1).agent.compare(player, true);
		    }

		    if(node.input != null) {
			    player.setYaw(node.input.yaw);
			    player.setPitch(node.input.pitch);
			    if (player.isCreative()) player.stopFallFlying();
			    options.forwardKey.setPressed(node.input.forward);
			    options.backKey.setPressed(node.input.back);
			    options.leftKey.setPressed(node.input.left);
			    options.rightKey.setPressed(node.input.right);
			    options.jumpKey.setPressed(node.input.jump);
			    options.sneakKey.setPressed(node.input.sneak);
			    options.sprintKey.setPressed(node.input.sprint);
		    }
	    }

	    this.tick++;
    }

}
