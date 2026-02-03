package kaptainwutax.tungsten.mixin;

import java.util.ArrayList;
import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.commandsystem.Command;
import kaptainwutax.tungsten.commandsystem.CommandExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler extends ClientCommonNetworkHandler {
	
	@Shadow
    private ClientWorld world;

    @Shadow
    public abstract void sendChatMessage(String content);

    @Unique
    private boolean ignoreChatMessage;

    @Unique
    private boolean worldNotNull;
    
    protected MixinClientPlayNetworkHandler(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
        super(client, connection, connectionState);
    }
    
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (ignoreChatMessage) return;
		String prefix = TungstenMod.getCommandPrefix();
        if (message.startsWith(prefix)) {
            try {
            	if (message.contains("|")) {
                	Collection<Command> commands = new ArrayList<>(TungstenMod.getCommandExecutor().allCommands());
                	commands.removeIf((command) -> !message.contains(command.getName()));
                	TungstenMod.getCommandExecutor().executeRecursive(commands.toArray(new Command[0]), message.split("|"), 0, () -> {
                    }, ex -> Debug.logWarning(ex.getMessage()));
            	} else CommandExecutor.dispatch(message.substring(prefix.length()));
            } catch (CommandSyntaxException | IllegalArgumentException e) {
                Debug.logWarning(e.getMessage());
            }

            client.inGameHud.getChatHud().addToMessageHistory(message);
            ci.cancel();
        }
    }

    @Inject(method = "onEntityTrackerUpdate", at = @At("HEAD"), cancellable = true)
    public void onEntityTrackerUpdate(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        if(TungstenMod.EXECUTOR.isRunning()) {
            ClientPlayerEntity player = TungstenMod.mc.player;

            if(player != null && packet.id() == player.getId()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"), cancellable = true)
    public void onPlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if(TungstenMod.EXECUTOR.isRunning()) {
            ci.cancel();
        }
    }

}
