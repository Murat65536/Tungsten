package kaptainwutax.tungsten.simulation;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.Node;
import kaptainwutax.tungsten.path.PathInput;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;

public class SimulatedPlayer {

    public static SimulatedPlayer INSTANCE;

    private static final ThreadLocal<BlockPos.Mutable> threadLocalBlockPos =
            ThreadLocal.withInitial(BlockPos.Mutable::new);

    private final SimulatedPlayerFactory.SimulatedPlayerHandle handle;
    public final SimulatedInput input = new SimulatedInput();

    public double posX, posY, posZ;
    public int blockX, blockY, blockZ;
    public double velX, velY, velZ;
    public float yaw, pitch;
    public boolean onGround;
    public boolean touchingWater;
    public boolean isSubmergedInWater;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean collidedSoftly;
    public boolean slimeBounce;
    public float stepHeight;
    public double fallDistance;
    public Box box;
    public EntityDimensions dimensions;
    public float standingEyeHeight;
    public boolean sprinting;
    public boolean swimming;
    public boolean fallFlying;
    public boolean jumping;

    public SimulatedPlayer(ClientPlayerEntity player) {
        this.handle = SimulatedPlayerFactory.createFrom(player);
        SimulatedPlayerFactory.attachInput(this.handle, this.input);
        if (player.input != null) {
            this.input.playerInput = player.input.playerInput;
            this.input.setMovementVector(player.input.getMovementInput());
        }
        this.syncFromHandle();
    }

    public SimulatedPlayer(SimulatedPlayer parent, PathInput input) {
        this.handle = SimulatedPlayerFactory.copyFrom(parent.handle);
        SimulatedPlayerFactory.attachInput(this.handle, this.input);
        this.input.playerInput = parent.input.playerInput;
        this.input.setMovementVector(parent.input.getMovementInput());
        this.syncFromHandle();
        this.applyInput(input);
    }

    public SimulatedPlayer tick(WorldView world) {
        this.handle.tickMovement();
        this.syncFromHandle();
        return this;
    }

    public SimulatedPlayer tick(WorldView world, PathInput input) {
        this.applyInput(input);
        return this.tick(world);
    }

    public void applyInput(PathInput input) {
        this.input.setInput(input);
        this.handle.setYaw(input.yaw());
        this.handle.setPitch(input.pitch());
        this.yaw = input.yaw();
        this.pitch = input.pitch();
    }

    public Vec3d getPos() {
        return new Vec3d(this.posX, this.posY, this.posZ);
    }

    public BlockPos getBlockPos() {
        return new BlockPos(this.blockX, this.blockY, this.blockZ);
    }

    public Iterable<VoxelShape> getBlockCollisions(WorldView world, Box box) {
        return () -> new SimulatedBlockCollisions(world, this, box);
    }

    public boolean isClimbing(WorldView world) {
        BlockPos.Mutable mutable = threadLocalBlockPos.get();
        mutable.set(this.blockX, this.blockY, this.blockZ);
        BlockState state = world.getBlockState(mutable);
        if (state.isIn(BlockTags.CLIMBABLE)) return true;
        return state.getBlock() instanceof TrapdoorBlock && this.canEnterTrapdoor(world, state);
    }

    private boolean canEnterTrapdoor(WorldView world, BlockState trapdoor) {
        if (!trapdoor.get(TrapdoorBlock.OPEN)) return false;

        BlockPos.Mutable mutable = threadLocalBlockPos.get();
        mutable.set(this.blockX, this.blockY - 1, this.blockZ);
        BlockState ladder = world.getBlockState(mutable);

        return ladder.isOf(Blocks.LADDER) && ladder.get(LadderBlock.FACING) == trapdoor.get(TrapdoorBlock.FACING);
    }

    public boolean isInLava() {
        return this.handle.isInLava();
    }

    public void compare(ClientPlayerEntity player, boolean executor) {
        if (this.posX != player.getX() || this.posY != player.getY() || this.posZ != player.getZ()) {
            if (TungstenMod.EXECUTOR.isRunning()) {
                Node node = TungstenMod.EXECUTOR.getCurrentNode();
                if (node != null) {
                    RenderHelper.renderNode(node, TungstenMod.ERROR);
                }
                TungstenMod.ERROR.add(new Cuboid(player.getPos(), new Vec3d(0.1, 0.5, 0.1), Color.RED));
            }
        }

        if (this.velX != player.getVelocity().x || this.velY != player.getVelocity().y || this.velZ != player.getVelocity().z) {
            if (TungstenMod.EXECUTOR.isRunning()) {
                Node node = TungstenMod.EXECUTOR.getCurrentNode();
                if (node != null) {
                    RenderHelper.renderNode(node, TungstenMod.ERROR);
                }
                TungstenMod.ERROR.add(new Cuboid(player.getPos(), new Vec3d(0.1, 0.5, 0.1), Color.RED));
            }
        }
    }

    private void syncFromHandle() {
        Vec3d pos = this.handle.getPos();
        Vec3d velocity = this.handle.getVelocity();
        BlockPos blockPos = this.handle.getBlockPos();
        this.posX = pos.x;
        this.posY = pos.y;
        this.posZ = pos.z;
        this.velX = velocity.x;
        this.velY = velocity.y;
        this.velZ = velocity.z;
        this.blockX = blockPos.getX();
        this.blockY = blockPos.getY();
        this.blockZ = blockPos.getZ();
        this.onGround = this.handle.isOnGround();
        this.touchingWater = this.handle.isTouchingWater();
        this.isSubmergedInWater = this.handle.isSubmergedInWater();
        this.sprinting = this.handle.isSprinting();
        this.swimming = this.handle.isSwimming();
        this.fallFlying = this.handle.isFallFlying();
        this.box = this.handle.getBoundingBox();
        if (this.box == null) {
            this.box = new Box(this.posX, this.posY, this.posZ, this.posX, this.posY, this.posZ);
        }
        this.stepHeight = this.handle.getStepHeight();
        this.standingEyeHeight = this.handle.getStandingEyeHeight();
        this.yaw = this.handle.getYaw();
        this.pitch = this.handle.getPitch();
        this.horizontalCollision = this.handle.getHorizontalCollision();
        this.verticalCollision = this.handle.getVerticalCollision();
        this.collidedSoftly = this.handle.getCollidedSoftly();
        this.fallDistance = this.handle.getFallDistance();
        this.slimeBounce = this.handle.getSlimeBounce();
    }
}
