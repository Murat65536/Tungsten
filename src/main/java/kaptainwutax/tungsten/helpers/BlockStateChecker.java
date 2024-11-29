package kaptainwutax.tungsten.helpers;

import kaptainwutax.tungsten.TungstenMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public class BlockStateChecker {
	
	
	public static boolean isConnected(BlockPos pos) {
		WorldView world = TungstenMod.mc.world;
	    BlockState state = world.getBlockState(pos);
	    Block block = state.getBlock();

	    // Check for Fence connections
	    if (block instanceof FenceBlock) {
	        return isFenceConnected(state, world, pos);
	    }

	    // Check for Wall connections
	    if (block instanceof WallBlock) {
	        return isWallConnected(state);
	    }

	    // Check for Glass Pane connections
	    if (block instanceof PaneBlock) {
	        return isGlassPaneConnected(state, world, pos);
	    }

	    return false;
	}

	public static boolean isFenceConnected(BlockState state, WorldView world, BlockPos pos) {
	    return state.get(Properties.NORTH) && isSameFence(world, pos.north())
	        || state.get(Properties.SOUTH) && isSameFence(world, pos.south())
	        || state.get(Properties.EAST) && isSameFence(world, pos.east())
	        || state.get(Properties.WEST) && isSameFence(world, pos.west());
	}

	public static boolean isSameFence(WorldView world, BlockPos pos) {
	    Block block = world.getBlockState(pos).getBlock();
	    return block instanceof FenceBlock;
	}

	public static boolean isWallConnected(BlockState state) {
	    return state.get(Properties.NORTH_WALL_SHAPE) != WallShape.NONE
	        || state.get(Properties.SOUTH_WALL_SHAPE) != WallShape.NONE
	        || state.get(Properties.EAST_WALL_SHAPE) != WallShape.NONE
	        || state.get(Properties.WEST_WALL_SHAPE) != WallShape.NONE;
	}

	public static boolean isGlassPaneConnected(BlockState state, WorldView world, BlockPos pos) {
	    return state.get(Properties.NORTH) && isSamePane(world, pos.north())
	        || state.get(Properties.SOUTH) && isSamePane(world, pos.south())
	        || state.get(Properties.EAST) && isSamePane(world, pos.east())
	        || state.get(Properties.WEST) && isSamePane(world, pos.west());
	}

	public static boolean isSamePane(WorldView world, BlockPos pos) {
	    Block block = world.getBlockState(pos).getBlock();
	    return block instanceof PaneBlock;
	}


}
