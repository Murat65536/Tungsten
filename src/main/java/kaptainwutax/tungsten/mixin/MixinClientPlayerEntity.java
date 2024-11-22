package kaptainwutax.tungsten.mixin;

import com.mojang.authlib.GameProfile;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.path.PathFinder;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayerEntity {

	private Thread patfinderThread = null;

	public MixinClientPlayerEntity(ClientWorld world, GameProfile profile, @Nullable PlayerPublicKey publicKey) {
		super(world, profile);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	public void start(CallbackInfo ci) {
		if(TungstenMod.EXECUTOR.isRunning()) {
			TungstenMod.EXECUTOR.tick((ClientPlayerEntity)(Object)this, MinecraftClient.getInstance().options);
		}

		if(!this.getAbilities().flying) {
			Agent.INSTANCE = Agent.of((ClientPlayerEntity)(Object)this);
			Agent.INSTANCE.tick(this.getWorld());
		}

		if(TungstenMod.runKeyBinding.isPressed() && !TungstenMod.PATHFINDER.active) {
			TungstenMod.PATHFINDER.find(this.getWorld(), TungstenMod.TARGET);
		}
		if(TungstenMod.runBlockSearchKeyBinding.isPressed() && !TungstenMod.PATHFINDER.active) {
			BlockSpacePathFinder.find(getWorld(), TungstenMod.TARGET);
		}
		if (TungstenMod.pauseKeyBinding.isPressed()) {
			try {
				
	        	if((TungstenMod.PATHFINDER.active || TungstenMod.EXECUTOR.isRunning())) {
	        		TungstenMod.PATHFINDER.stop = true;
	        		TungstenMod.EXECUTOR.stop = true;
	        		if (TungstenMod.PATHFINDER.thread != null && TungstenMod.PATHFINDER.thread.isAlive()) {
	        			TungstenMod.PATHFINDER.thread.interrupt();
	        			TungstenMod.RENDERERS.clear();
	        			TungstenMod.TEST.clear();
	        		}
					Debug.logMessage("Stopped!");
	    		} else {
					Debug.logMessage("Nothing to stop.");
	    		}

	
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
		
		if (TungstenMod.createGoalKeyBinding.isPressed()) {
			BlockPos cameraBlockPos = MinecraftClient.getInstance().gameRenderer.getCamera().getBlockPos();
			TungstenMod.TARGET = new Vec3d(cameraBlockPos.getX() + 0.5, cameraBlockPos.getY() - 1, cameraBlockPos.getZ() + 0.5);
		}
	}

	@Inject(method = "tick", at = @At(value = "RETURN"))
	public void end(CallbackInfo ci) {
		if(!this.getAbilities().flying && Agent.INSTANCE != null) {
			Agent.INSTANCE.compare((ClientPlayerEntity)(Object)this, false);
		}
	}

	@Inject(method="getPitch", at=@At("RETURN"), cancellable = true)
	public void getPitch(float tickDelta, CallbackInfoReturnable<Float> ci) {
		if(TungstenMod.EXECUTOR.isRunning()) {
			ci.setReturnValue(super.getPitch(tickDelta));
		}
	}

	@Inject(method="getYaw", at=@At("RETURN"), cancellable = true)
	public void getYaw(float tickDelta, CallbackInfoReturnable<Float> ci) {
		if(TungstenMod.EXECUTOR.isRunning()) {
			ci.setReturnValue(super.getYaw(tickDelta));
		}
	}

}
