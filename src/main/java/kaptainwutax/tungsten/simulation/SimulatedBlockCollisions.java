package kaptainwutax.tungsten.simulation;

import com.google.common.collect.AbstractIterator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.CollisionView;
import org.jetbrains.annotations.Nullable;

public class SimulatedBlockCollisions extends AbstractIterator<VoxelShape> {

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

    public SimulatedBlockCollisions(CollisionView world, SimulatedPlayer player, Box box) {
        this(world, player, box, false);
    }

    public SimulatedBlockCollisions(CollisionView world, SimulatedPlayer player, Box box, boolean forEntity) {
        this.context = new SimulatedShapeContext(player);
        this.pos = new BlockPos.Mutable();
        this.boxShape = VoxelShapes.cuboid(box);
        this.world = world;
        this.box = box;
        this.forEntity = forEntity;
        int i = MathHelper.floor(box.minX - 1.0E-7D) - 1;
        int j = MathHelper.floor(box.maxX + 1.0E-7D) + 1;
        int k = MathHelper.floor(box.minY - 1.0E-7D) - 1;
        int l = MathHelper.floor(box.maxY + 1.0E-7D) + 1;
        int m = MathHelper.floor(box.minZ - 1.0E-7D) - 1;
        int n = MathHelper.floor(box.maxZ + 1.0E-7D) + 1;
        this.blockIterator = new CuboidBlockIterator(i, k, m, j, l, n);
    }

    @Nullable
    private BlockView getChunk(int x, int z) {
        int i = ChunkSectionPos.getSectionCoord(x);
        int j = ChunkSectionPos.getSectionCoord(z);
        long l = ChunkPos.toLong(i, j);

        if (this.chunk != null && this.chunkPos == l) {
            return this.chunk;
        } else {
            BlockView blockView = this.world.getChunkAsView(i, j);
            this.chunk = blockView;
            this.chunkPos = l;
            return blockView;
        }
    }

    protected VoxelShape computeNext() {
        while (this.blockIterator.step()) {
            int x = this.blockIterator.getX();
            int y = this.blockIterator.getY();
            int z = this.blockIterator.getZ();
            int edgeCoordinatesCount = this.blockIterator.getEdgeCoordinatesCount();
            if (edgeCoordinatesCount != 3) {
                double blockMaxX = x + 1;
                double blockMaxY = y + 1;
                double blockMaxZ = z + 1;

                if (this.box.maxX <= (double) x || this.box.minX >= blockMaxX ||
                        this.box.maxY <= (double) y || this.box.minY >= blockMaxY ||
                        this.box.maxZ <= (double) z || this.box.minZ >= blockMaxZ) {
                    continue;
                }

                BlockView blockView = this.getChunk(x, z);
                if (blockView != null) {
                    this.pos.set(x, y, z);
                    BlockState blockState = blockView.getBlockState(this.pos);

                    if (blockState.isAir()) {
                        continue;
                    }

                    if ((!this.forEntity || blockState.shouldSuffocate(blockView, this.pos))
                            && (edgeCoordinatesCount != 1 || blockState.exceedsCube())
                            && (edgeCoordinatesCount != 2 || blockState.isOf(Blocks.MOVING_PISTON))) {
                        VoxelShape voxelShape = blockState.getCollisionShape(this.world, this.pos, this.context);

                        if (voxelShape.isEmpty()) {
                            continue;
                        }

                        if (voxelShape == VoxelShapes.fullCube()) {
                            return voxelShape.offset(x, y, z);
                        } else {
                            VoxelShape voxelShape2 = voxelShape.offset(x, y, z);
                            if (VoxelShapes.matchesAnywhere(voxelShape2, this.boxShape, BooleanBiFunction.AND)) {
                                return voxelShape2;
                            }
                        }
                    }
                }
            }
        }

        return this.endOfData();
    }
}
