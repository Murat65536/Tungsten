package kaptainwutax.tungsten.world;

import kaptainwutax.tungsten.constants.VoxelConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;

import java.util.Arrays;

public class VoxelChunk {

	public static final VoxelChunk EMPTY = new VoxelChunk() {
		{
			this.blocks = new BlockState[0];
			this.fluids = new FluidState[0];
		}

		@Override
		public BlockState getBlockState(int x, int y, int z) {
			return Blocks.AIR.getDefaultState();
		}

		@Override
		public FluidState getFluidState(int x, int y, int z) {
			return Fluids.EMPTY.getDefaultState();
		}
	};

	public BlockState[] blocks = new BlockState[VoxelConstants.CHUNK_SIZE_CUBED];
	public FluidState[] fluids = new FluidState[VoxelConstants.CHUNK_SIZE_CUBED];

	public VoxelChunk() {
		Arrays.fill(this.blocks, Blocks.AIR.getDefaultState());
		Arrays.fill(this.fluids, Fluids.EMPTY.getDefaultState());
	}

	public BlockState getBlockState(int x, int y, int z) {
		return this.blocks[(y & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_Y | (x & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_X | z & VoxelConstants.CHUNK_MASK];
	}

	public FluidState getFluidState(int x, int y, int z) {
		return this.fluids[(y & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_Y | (x & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_X | z & VoxelConstants.CHUNK_MASK];
	}

	public void setBlockState(int x, int y, int z, BlockState state) {
		this.blocks[(y & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_Y | (x & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_X | z & VoxelConstants.CHUNK_MASK] = state;
	}

	public void setFluidState(int x, int y, int z, FluidState state) {
		this.fluids[(y & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_Y | (x & VoxelConstants.CHUNK_MASK) << VoxelConstants.CHUNK_SHIFT_X | z & VoxelConstants.CHUNK_MASK] = state;
	}

}
