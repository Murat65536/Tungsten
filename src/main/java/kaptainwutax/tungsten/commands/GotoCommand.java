package kaptainwutax.tungsten.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.commands.arguments.GotoTargetArgumentType;
import kaptainwutax.tungsten.commandsystem.Command;
import kaptainwutax.tungsten.commandsystem.CommandException;
import kaptainwutax.tungsten.path.targets.BlockTarget;
import net.minecraft.command.CommandSource;

public class GotoCommand extends Command {
	
	public GotoCommand(TungstenMod mod) throws CommandException {
        // x z
        // x y z
        // x y z dimension
        // (dimension)
        // (x z dimension)
        super("goto", "Tell bot to travel to a set of coordinates", mod
                /*new Arg(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")*/
        );
    }

	@Override
	public void build(LiteralArgumentBuilder<CommandSource> builder) {
		
		builder.then(argument("gotoTarget", GotoTargetArgumentType.create()).executes(context -> {
	        try {
				
	        	BlockTarget target = GotoTargetArgumentType.get(context);
	        	if(!TungstenMod.PATHFINDER.active.get() && !TungstenMod.EXECUTOR.isRunning()) {
	        		TungstenMod.TARGET = target.getVec3d().add(0.5, 0, 0.5);
	        		TungstenMod.PATHFINDER.find(TungstenMod.mc.world, target.getVec3d().add(0.5, 0, 0.5));
	    		} else {
	    			Debug.logWarning("Already running!");
	    		}

			} catch (Exception e) {
				// TODO: handle exception
			}
			
			return SINGLE_SUCCESS;
		}));
	}

}
