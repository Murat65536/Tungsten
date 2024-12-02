package kaptainwutax.tungsten.helpers.blockPath;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class BlockPosShifter {
	
	public static Vec3d getPosOnLadder(BlockNode blockNode) {
		WorldView world = TungstenMod.mc.world;
		BlockState blockState = world.getBlockState(blockNode.getBlockPos());
		BlockState blockBelowState = world.getBlockState(blockNode.getBlockPos().down());
		Vec3d currPos = blockNode.getPos().add(0.5, 0, 0.5);
		if (!(blockState.getBlock() instanceof LadderBlock) && !(blockBelowState.getBlock() instanceof LadderBlock)) {
			return currPos;
		}
		
		double insetAmount = 0.4;
		
		if (blockBelowState.getBlock() instanceof LadderBlock) {
			Direction ladderFacingDir = blockBelowState.get(Properties.HORIZONTAL_FACING);
			
			currPos = currPos.offset(ladderFacingDir.getOpposite(), insetAmount).add(0, 0.6, 0);
			
			return currPos;
		}
		
		Direction ladderFacingDir = blockState.get(Properties.HORIZONTAL_FACING);
		
		currPos = currPos.offset(ladderFacingDir.getOpposite(), insetAmount).add(0, 0.6, 0);
		
		return currPos;
	}
	
	public static Vec3d shiftForStraightNeo(BlockNode blockNode, Direction dir) {
		if (dir == Direction.DOWN || dir == Direction.UP) {
			throw new IllegalArgumentException("Only horizontal directions may be passed!");
		}
		Vec3d currPos = new Vec3d(blockNode.getBlockPos().getX() + 0.5, blockNode.getBlockPos().getY(), blockNode.getBlockPos().getZ() + 0.5).offset(dir, 0.55);
		
		return currPos;
	}
	
	public static Vec3d shiftForLongJump(BlockNode blockNode, Direction dir) {
		if (dir == Direction.DOWN || dir == Direction.UP) {
			throw new IllegalArgumentException("Only horizontal directions may be passed!");
		}
		Vec3d currPos = new Vec3d(blockNode.getBlockPos().getX() + 0.5, blockNode.getBlockPos().getY(), blockNode.getBlockPos().getZ() + 0.5);//.offset(dir.getOpposite(), 0.4);

//		TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()).subtract(0.1D, 0, 0.1D).offset(dir.getOpposite(), 0.4), new Vec3d(0.2D, 0.2D, 0.2D), Color.RED));
//		System.out.println(dir);
		
		return currPos;
	}

}
