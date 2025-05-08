package kaptainwutax.tungsten;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kaptainwutax.tungsten.commandsystem.CommandExecutor;
import kaptainwutax.tungsten.path.PathExecutor;
import kaptainwutax.tungsten.path.PathFinder;
import kaptainwutax.tungsten.render.Renderer;
import kaptainwutax.tungsten.world.VoxelWorld;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

public class TungstenMod implements ClientModInitializer {

	public static final String MOD_ID = "tungsten";
//    public static final ModMetadata MOD_META;
    public static final String NAME;

    public static MinecraftClient mc;
	public static Collection<Renderer> BLOCK_PATH_RENDERER = Collections.synchronizedCollection(new ArrayList<>());
	public static Collection<Renderer> RUNNING_PATH_RENDERER = Collections.synchronizedCollection(new ArrayList<>());
	public static Collection<Renderer> RENDERERS = Collections.synchronizedCollection(new ArrayList<>());
	public static Collection<Renderer> ERROR = Collections.synchronizedCollection(new ArrayList<>());
	public static Collection<Renderer> TEST = Collections.synchronizedCollection(new ArrayList<>());
	public static Vec3d TARGET = new Vec3d(0.5D, 10.0D, 0.5D);
	public static clickModeEnum clickMode = clickModeEnum.OFF;
	public static PathExecutor EXECUTOR = new PathExecutor();
	public static PathFinder PATHFINDER = new PathFinder();
	public static final Logger LOG;
	public static VoxelWorld WORLD;
	public static KeyBinding pauseKeyBinding;
	public static KeyBinding runKeyBinding;
	public static KeyBinding runBlockSearchKeyBinding;
	public static KeyBinding createGoalKeyBinding;
    private static CommandExecutor _commandExecutor;
    public static boolean renderPositonBoxes = true;
	
	
	static {
		// MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();
        NAME = "Tungsten";
        // DEV_BUILD = MOD_META.getCustomValue(TungstenMod.MOD_ID + ":devbuild").getAsString();
        LOG = LoggerFactory.getLogger(NAME);
		
	}

	@Override
	public void onInitializeClient() {
		pauseKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
	            "key.tungsten.pause", // The translation key of the keybinding's name
	            InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
	            GLFW.GLFW_KEY_P, // The keycode of the key
	            "key.category.tungsten.test" // The translation key of the keybinding's category.
        ));
		runKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
	            "key.tungsten.run", // The translation key of the keybinding's name
	            InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
	            GLFW.GLFW_KEY_G, // The keycode of the key
	            "key.category.tungsten.test" // The translation key of the keybinding's category.
        ));
		runBlockSearchKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
	            "key.tungsten.run_block_search", // The translation key of the keybinding's name
	            InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
	            GLFW.GLFW_KEY_J, // The keycode of the key
	            "key.category.tungsten.test.development" // The translation key of the keybinding's category.
        ));
		createGoalKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
	            "key.tungsten.create_goal", // The translation key of the keybinding's name
	            InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
	            GLFW.GLFW_KEY_H, // The keycode of the key
	            "key.category.tungsten.test" // The translation key of the keybinding's category.
        ));
        _commandExecutor = new CommandExecutor(this);

        // Global minecraft client accessor
        mc = MinecraftClient.getInstance();

        initializeCommands();
        
        ClientTickEvents.START_CLIENT_TICK.register((a) -> {
        	
        	boolean isRunning = TungstenMod.PATHFINDER.active || TungstenMod.EXECUTOR.isRunning();
        	if (!isRunning) {
	        	if (!TungstenMod.BLOCK_PATH_RENDERER.isEmpty()) {
	        		TungstenMod.BLOCK_PATH_RENDERER.clear();
	        	}
	        	if (!TungstenMod.RUNNING_PATH_RENDERER.isEmpty()) {
					TungstenMod.RUNNING_PATH_RENDERER.clear();
	        	}
	        	if (!TungstenMod.RENDERERS.isEmpty()) {
					TungstenMod.RENDERERS.clear();
	        	}
	        	if (!TungstenMod.TEST.isEmpty()) {
					TungstenMod.TEST.clear();
	        	}
//	        	if (!TungstenMod.ERROR.isEmpty()) {
//	        		TungstenMod.ERROR.clear();
//	        	}
        	}
        	if (clickMode != clickModeEnum.OFF && mc.options.useKey.isPressed() && !isRunning) {
        		
        		 Camera camera = mc.gameRenderer.getCamera();
                 Vec3d cameraPos = camera.getPos();

                 // Calculate the direction the camera is looking based on its pitch and yaw, and extend this direction 210 units away from the camera position
                 // 210 is used here as the maximum distance of 200 blocks
                 // This is done to be able to set target while in freecam
                 Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(210);
                 Vec3d targetPos = cameraPos.add(direction);
                 
                 RaycastContext context = new RaycastContext(
                         cameraPos,   // start position of the ray
                         targetPos,   // end position of the ray
                         RaycastContext.ShapeType.OUTLINE,
                         RaycastContext.FluidHandling.NONE,
                         mc.player
                 );
                 
                 HitResult hitResult = mc.world.raycast(context);
                 
                 if (hitResult.getType() == HitResult.Type.BLOCK) {
                     BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
	                 if (mc.world.getBlockState(pos).onUse(mc.world, mc.player, (BlockHitResult) hitResult) != ActionResult.PASS) return;
	
		                 BlockState state = mc.world.getBlockState(pos);
		
		                 VoxelShape shape = state.getCollisionShape(mc.world, pos);
//		                 if (shape.isEmpty()) shape = state.getOutlineShape(mc.world, pos);
		
		                 double height = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
		
		                 Vec3d newPos = new Vec3d(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
		         		TungstenMod.TARGET = newPos;
		         		

		        		if (clickMode == clickModeEnum.GOTO && !TungstenMod.PATHFINDER.active) {
		        			PATHFINDER.find(TungstenMod.mc.world, TARGET);
		        		}
	        		}
        		}
        		
        		
        });
	}
	
	 public static String getCommandPrefix() {
		 return ";";
	 }
	 
	// List all command sources here.
    private void initializeCommands() {
        try {
            // This creates the commands. If you want any more commands feel free to initialize new command lists.
            new TungstenCommands(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	 
	 /**
     * Executes commands
     */
    public static CommandExecutor getCommandExecutor() {
        return _commandExecutor;
    }
    
    public enum clickModeEnum {
    	OFF,
    	PLACE_GOAL,
    	GOTO
    }

}
