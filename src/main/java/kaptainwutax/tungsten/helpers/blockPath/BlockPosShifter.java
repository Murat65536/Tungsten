package kaptainwutax.tungsten.helpers.blockPath;

import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class BlockPosShifter {
	
	public static Vec3d getPosOnLadder(WorldView world, BlockNode blockNode) {
		BlockState blockState = world.getBlockState(blockNode.getBlockPos());
		BlockState blockBelowState = world.getBlockState(blockNode.getBlockPos().down());
		Vec3d currPos = blockNode.getPos(true);
		if (!(blockState.getBlock() instanceof LadderBlock) && !(blockBelowState.getBlock() instanceof LadderBlock)) {
			return currPos;
		}
		
		if (blockBelowState.getBlock() instanceof LadderBlock) {
			Direction ladderFacingDir = blockBelowState.get(Properties.HORIZONTAL_FACING);
			
			currPos = currPos.offset(ladderFacingDir.getOpposite(), 0.2).add(0, 0.6, 0);
			
			return currPos;
		}
		
		Direction ladderFacingDir = blockState.get(Properties.HORIZONTAL_FACING);
		
		currPos = currPos.offset(ladderFacingDir.getOpposite(), 0.2).add(0, 0.6, 0);
		
		return currPos;
	}
	
	public static Vec3d shiftForStraightNeo(BlockNode blockNode, Direction dir) {
		if (dir == Direction.DOWN || dir == Direction.UP) {
			throw new IllegalArgumentException("Only horizontal directions may be passed!");
		}
		Vec3d currPos = new Vec3d(blockNode.getBlockPos().getX() + 0.5, blockNode.getBlockPos().getY(), blockNode.getBlockPos().getZ() + 0.5).offset(dir, 0.4);
		
		return currPos;
	}

}
