package kaptainwutax.tungsten.agent;

import com.google.common.collect.AbstractIterator;
import kaptainwutax.tungsten.constants.CollisionConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.CollisionView;
import org.jetbrains.annotations.Nullable;

public class AgentBlockCollisions extends AbstractIterator<VoxelShape> {

    private final Box box;
    private final ShapeContext context;
    private final CuboidBlockIterator blockIterator;
    private final BlockPos.Mutable pos;
    private final VoxelShape boxShape;
    private final CollisionView world;
    private final boolean forEntity;
    private BlockView chunk;
    private long chunkPos;
    public int scannedBlocks;

    public AgentBlockCollisions(CollisionView world, Agent agent, Box box) {
        this(world, agent, box, false);
    }

    public AgentBlockCollisions(CollisionView world, Agent agent, Box box, boolean forEntity) {
        this.context = new AgentShapeContext(agent);
        this.pos = new BlockPos.Mutable();
        this.boxShape = VoxelShapes.cuboid(box);
        this.world = world;
        this.box = box;
        this.forEntity = forEntity;
        int i = MathHelper.floor(box.minX - CollisionConstants.BOUNDING_BOX_EPSILON) - 1;
        int j = MathHelper.floor(box.maxX + CollisionConstants.BOUNDING_BOX_EPSILON) + 1;
        int k = MathHelper.floor(box.minY - CollisionConstants.BOUNDING_BOX_EPSILON) - 1;
        int l = MathHelper.floor(box.maxY + CollisionConstants.BOUNDING_BOX_EPSILON) + 1;
        int m = MathHelper.floor(box.minZ - CollisionConstants.BOUNDING_BOX_EPSILON) - 1;
        int n = MathHelper.floor(box.maxZ + CollisionConstants.BOUNDING_BOX_EPSILON) + 1;
        this.blockIterator = new CuboidBlockIterator(i, k, m, j, l, n);
    }

    @Nullable
    private BlockView getChunk(int x, int z) {
        int i = ChunkSectionPos.getSectionCoord(x);
        int j = ChunkSectionPos.getSectionCoord(z);
        long l = ChunkPos.toLong(i, j);

        if(this.chunk != null && this.chunkPos == l) {
            return this.chunk;
        } else {
            BlockView blockView = this.world.getChunkAsView(i, j);
            this.chunk = blockView;
            this.chunkPos = l;
            return blockView;
        }
    }

    protected VoxelShape computeNext() {
        while(true) {
            if (this.blockIterator.step()) {
                int i = this.blockIterator.getX();
                int j = this.blockIterator.getY();
                int k = this.blockIterator.getZ();
                int l = this.blockIterator.getEdgeCoordinatesCount();

                if(l == CollisionConstants.COLLISION_TYPE_OTHER) {
                    continue;
                }

                /*
                BlockView blockView = this.getChunk(i, k);

                if(blockView == null) {
                    continue;
                }*/

                BlockView blockView = this.world;

                this.pos.set(i, j, k);
                BlockState blockState = blockView.getBlockState(this.pos);
                this.scannedBlocks++;

                if(this.forEntity && !blockState.shouldSuffocate(blockView, this.pos) || l == CollisionConstants.COLLISION_TYPE_MOVEMENT && !blockState.exceedsCube()
                    || l == CollisionConstants.COLLISION_TYPE_STEP && !blockState.isOf(Blocks.MOVING_PISTON)) {
                    continue;
                }

                VoxelShape voxelShape = blockState.getCollisionShape(this.world, this.pos, this.context);

                if(voxelShape == VoxelShapes.fullCube()) {
                    if(!this.box.intersects(i, j, k, (double)i + CollisionConstants.COLLISION_BOX_FULL_BLOCK, (double)j + CollisionConstants.COLLISION_BOX_FULL_BLOCK, (double)k + CollisionConstants.COLLISION_BOX_FULL_BLOCK)) {
                        continue;
                    }

                    return voxelShape.offset(i, j, k);
                }

                VoxelShape voxelShape2 = voxelShape.offset(i, j, k);

                if(!VoxelShapes.matchesAnywhere(voxelShape2, this.boxShape, BooleanBiFunction.AND)) {
                    continue;
                }

                return voxelShape2;
            }

            return this.endOfData();
        }
    }

}

