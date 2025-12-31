package kaptainwutax.tungsten.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.commandsystem.Command;
import net.minecraft.command.CommandSource;

public class StopCommand extends Command {
	public StopCommand(TungstenMod mod) {
        super("stop", "Tell bot to stop", mod);
    }

	@Override
	public void build(LiteralArgumentBuilder<CommandSource> builder) {
		
		builder.executes(context -> {
	        try {
				
	        	if(TungstenMod.PATHFINDER.active.get() || TungstenMod.EXECUTOR.isRunning()) {
	        		TungstenMod.PATHFINDER.stop.set(true);
	        		TungstenMod.EXECUTOR.stop = true;
					Debug.logMessage("Stopped!");
	    		} else {
					Debug.logMessage("Nothing to stop.");
	    		}

			} catch (Exception e) {
				// TODO: handle exception
			}
			
			return SINGLE_SUCCESS;
		});
	}
}
