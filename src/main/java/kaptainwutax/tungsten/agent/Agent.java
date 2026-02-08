package kaptainwutax.tungsten.agent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.constants.game.MechanicsConstants;
import kaptainwutax.tungsten.constants.game.TickConstants;
import kaptainwutax.tungsten.constants.physics.CollisionConstants;
import kaptainwutax.tungsten.constants.physics.GravityConstants;
import kaptainwutax.tungsten.constants.physics.MovementConstants;
import kaptainwutax.tungsten.constants.physics.PlayerConstants;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.mixin.AccessorEntity;
import kaptainwutax.tungsten.mixin.AccessorLivingEntity;
import kaptainwutax.tungsten.path.Node;
import kaptainwutax.tungsten.path.PathInput;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.WorldView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Agent {

    public static Agent INSTANCE;

    // Thread-local mutable BlockPos to avoid allocations
    private static final ThreadLocal<BlockPos.Mutable> threadLocalBlockPos =
        ThreadLocal.withInitial(BlockPos.Mutable::new);

    public final ClientPlayerEntity player;
    public final AgentInput input = new AgentInput(this);
    public final Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
    private final Set<TagKey<Fluid>> submergedFluids = new HashSet<>();
    public boolean keyForward;
    public boolean keyBack;
    public boolean keyLeft;
    public boolean keyRight;
    public boolean keyJump;
    public boolean keySneak;
    public boolean keySprint;
    public EntityPose pose;
    public boolean inSneakingPose;
    public boolean isDamaged = false;
    public boolean usingItem;
    public float sidewaysSpeed;
    public float upwardSpeed;
    public float forwardSpeed;
    public float yaw;
    public float pitch;
    public double posX, posY, posZ;
    public int blockX, blockY, blockZ;
    public double velX, velY, velZ;
    public double mulX, mulY, mulZ;
    public boolean firstUpdate = true;

    public EntityDimensions dimensions;
    public Box box;
    public float standingEyeHeight;

    public boolean onGround;
    public boolean sleeping;
    public boolean sneaking; //flag 1
    public boolean sprinting; //flag 3
    public boolean swimming; //flag 4
    public boolean fallFlying; //flag 7
    public float stepHeight = PlayerConstants.StepHeight.DEFAULT;
    public double fallDistance;
    public boolean touchingWater;
    public boolean isSubmergedInWater;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean collidedSoftly;
    public boolean slimeBounce;

    public boolean jumping;

    public int speed = -1;
    public int blindness = -1;
    public int jumpBoost = -1;
    public int slowFalling = -1;
    public int dolphinsGrace = -1;
    public int levitation = -1;

    public int depthStrider;

    public HungerManager hunger = new HungerManager();
    public double health;
    public float movementSpeed;
    public float airStrafingSpeed;
    public int jumpingCooldown;
    public int ticksToNextAutojump;
    private final List<String> extra = new ArrayList<>();
    private int scannedBlocks;

    public Agent(ClientPlayerEntity player) {
        this.keyForward = TungstenMod.mc.options.forwardKey.isPressed();
        this.keyBack = TungstenMod.mc.options.backKey.isPressed();
        this.keyLeft = TungstenMod.mc.options.leftKey.isPressed();
        this.keyRight = TungstenMod.mc.options.rightKey.isPressed();
        this.keyJump = TungstenMod.mc.options.jumpKey.isPressed();
        this.keySneak = TungstenMod.mc.options.sneakKey.isPressed();
        this.keySprint = TungstenMod.mc.options.sprintKey.isPressed();

        this.player = player;
        this.pose = player.getPose();
        this.inSneakingPose = player.isInSneakingPose();
        this.usingItem = player.isUsingItem();
        this.sidewaysSpeed = player.sidewaysSpeed;
        this.upwardSpeed = player.upwardSpeed;
        this.forwardSpeed = player.forwardSpeed;
        this.yaw = player.getYaw();
        this.pitch = player.getPitch();
        this.posX = player.getX();
        this.posY = player.getY();
        this.posZ = player.getZ();
        this.blockX = player.getBlockPos().getX();
        this.blockY = player.getBlockPos().getY();
        this.blockZ = player.getBlockPos().getZ();
        this.velX = player.getVelocity().x;
        this.velY = player.getVelocity().y;
        this.velZ = player.getVelocity().z;
        this.mulX = ((AccessorEntity) player).getMovementMultiplier().x;
        this.mulY = ((AccessorEntity) player).getMovementMultiplier().y;
        this.mulZ = ((AccessorEntity) player).getMovementMultiplier().z;
        this.fluidHeight.put(FluidTags.WATER, player.getFluidHeight(FluidTags.WATER));
        this.fluidHeight.put(FluidTags.LAVA, player.getFluidHeight(FluidTags.LAVA));
        this.submergedFluids.addAll(((AccessorEntity) player).getSubmergedFluidTag());
        this.firstUpdate = ((AccessorEntity) player).getFirstUpdate();
        this.box = player.getBoundingBox();
        this.dimensions = player.getDimensions(player.getPose());
        this.standingEyeHeight = player.getStandingEyeHeight();
        this.onGround = player.isOnGround();
        this.sleeping = player.isSleeping();
        this.sneaking = player.isSneaky();
        this.hunger = player.getHungerManager();
        this.sprinting = player.isSprinting();
        this.swimming = player.isSwimming();
        this.fallFlying = player.getAbilities().flying;
        this.stepHeight = player.getStepHeight(); // TODO Implement proper step height changes when riding different entities
        this.fallDistance = player.fallDistance;
        this.touchingWater = player.isTouchingWater();
        this.isSubmergedInWater = player.isSubmergedInWater();
        this.horizontalCollision = player.horizontalCollision;
        this.verticalCollision = player.verticalCollision;
        this.collidedSoftly = player.collidedSoftly;
        this.jumping = ((AccessorLivingEntity) player).getJumping();
        this.speed = player.hasStatusEffect(StatusEffects.SPEED) ? player.getStatusEffect(StatusEffects.SPEED).getAmplifier() : -1;
        this.blindness = player.hasStatusEffect(StatusEffects.BLINDNESS) ? player.getStatusEffect(StatusEffects.BLINDNESS).getAmplifier() : -1;
        this.jumpBoost = player.hasStatusEffect(StatusEffects.JUMP_BOOST) ? player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() : -1;
        this.slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING) ? player.getStatusEffect(StatusEffects.SLOW_FALLING).getAmplifier() : -1;
        this.dolphinsGrace = player.hasStatusEffect(StatusEffects.DOLPHINS_GRACE) ? player.getStatusEffect(StatusEffects.DOLPHINS_GRACE).getAmplifier() : -1;
        this.levitation = player.hasStatusEffect(StatusEffects.LEVITATION) ? player.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() : -1;
        this.movementSpeed = player.getMovementSpeed();
        this.airStrafingSpeed = 0.06f;
        this.jumpingCooldown = ((AccessorLivingEntity) player).getJumpingCooldown();
        //TODO: frame.ticksToNextAutojump
    }

    public Agent(Agent other, boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint, float pitch, float yaw) {
        this.keyForward = forward;
        this.keyBack = back;
        this.keyLeft = left;
        this.keyRight = right;
        this.keyJump = jump;
        this.keySneak = sneak;
        this.keySprint = sprint;

        this.player = other.player;
        this.pose = other.pose;
        this.inSneakingPose = other.inSneakingPose;
        this.usingItem = other.usingItem;
        this.sidewaysSpeed = other.sidewaysSpeed;
        this.upwardSpeed = other.upwardSpeed;
        this.forwardSpeed = other.forwardSpeed;
        this.yaw = yaw;
        this.pitch = pitch;
        this.posX = other.posX;
        this.posY = other.posY;
        this.posZ = other.posZ;
        this.blockX = other.blockX;
        this.blockY = other.blockY;
        this.blockZ = other.blockZ;
        this.velX = other.velX;
        this.velY = other.velY;
        this.velZ = other.velZ;
        this.mulX = other.mulX;
        this.mulY = other.mulY;
        this.mulZ = other.mulZ;
        this.fluidHeight.put(FluidTags.WATER, other.getFluidHeight(FluidTags.WATER));
        this.fluidHeight.put(FluidTags.LAVA, other.getFluidHeight(FluidTags.LAVA));
        this.submergedFluids.addAll(other.submergedFluids);
        this.firstUpdate = other.firstUpdate;
        this.dimensions = other.dimensions;
        this.box = other.box;
        this.standingEyeHeight = other.standingEyeHeight;
        this.onGround = other.onGround;
        this.sleeping = other.sleeping;
        this.sneaking = other.sneaking;
        this.sprinting = other.sprinting;
        this.swimming = other.swimming;
        this.fallFlying = other.fallFlying;
        this.stepHeight = other.stepHeight;
        this.fallDistance = other.fallDistance;
        this.touchingWater = other.touchingWater;
        this.isSubmergedInWater = other.isSubmergedInWater;
        this.horizontalCollision = other.horizontalCollision;
        this.verticalCollision = other.verticalCollision;
        this.collidedSoftly = other.collidedSoftly;
        this.jumping = other.jumping;
        this.speed = other.speed;
        this.blindness = other.blindness;
        this.jumpBoost = other.jumpBoost;
        this.slowFalling = other.slowFalling;
        this.dolphinsGrace = other.dolphinsGrace;
        this.levitation = other.levitation;
        this.depthStrider = other.depthStrider;
        this.movementSpeed = other.movementSpeed;
        this.airStrafingSpeed = other.airStrafingSpeed;
        this.jumpingCooldown = other.jumpingCooldown;
        //TODO: frame.ticksToNextAutojump
    }

    public Agent(Agent agent, PathInput input) {
        this(agent, input.forward(), input.back(), input.left(), input.right(), input.jump(), input.sneak(), input.sprint(), input.pitch(), input.yaw());
    }

    private static float[] collectStepHeights(Box collisionBox, List<VoxelShape> collisions, float f, float stepHeight) {
        FloatSet floatSet = new FloatArraySet(4);

        for (VoxelShape voxelShape : collisions) {
            for (double d : voxelShape.getPointPositions(Axis.Y)) {
                float g = (float) (d - collisionBox.minY);
                if (!(g < 0.0F) && g != stepHeight) {
                    if (g > f) {
                        break;
                    }

                    floatSet.add(g);
                }
            }
        }

        float[] fs = floatSet.toFloatArray();
        FloatArrays.unstableSort(fs);
        return fs;
    }

    protected static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
            float f = MathHelper.sin(yaw * (float) MechanicsConstants.Angles.DEGREES_TO_RADIANS);
            float g = MathHelper.cos(yaw * (float) MechanicsConstants.Angles.DEGREES_TO_RADIANS);
            return new Vec3d(vec3d.x * g - vec3d.z * f, vec3d.y, vec3d.z * g + vec3d.x * f);
        }
    }

    private static Vec2f applyDirectionalMovementSpeedFactors(Vec2f vec) {
        float f = vec.length();
        if (f <= 0.0F) {
            return vec;
        } else {
            Vec2f vec2f = vec.multiply(1.0F / f);
            float g = getDirectionalMovementSpeedMultiplier(vec2f);
            float h = Math.min(f * g, 1.0F);
            return vec2f.multiply(h);
        }
    }

    private static float getDirectionalMovementSpeedMultiplier(Vec2f vec) {
        float f = Math.abs(vec.x);
        float g = Math.abs(vec.y);
        float h = g > f ? f / g : g / f;
        return MathHelper.sqrt(1.0F + MathHelper.square(h));
    }

    public Vec3d getPos() {
        return new Vec3d(this.posX, this.posY, this.posZ);
    }

    public BlockPos getBlockPos() {
        return new BlockPos(this.blockX, this.blockY, this.blockZ);
    }

    public void setPos(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.blockX = MathHelper.floor(x);
        this.blockY = MathHelper.floor(y);
        this.blockZ = MathHelper.floor(z);
//		this.setBoundingBox(this.calculateBoundingBox());
    }

    public Agent tick(WorldView world) {
        this.tickPlayer(world);
        return this;
    }

    public void tickPlayer(WorldView world) {
        //Sleeping code
        this.updateWaterSubmersionState();
        this.tickLiving(world);
        //Hunger stuff
        //Turtle helmet
        //Item cooldown
        this.updateSize(world);
    }

    private void tickLiving(WorldView world) {
        this.baseTickLiving(world);
        //more sleep stuff
        this.tickMovementClientPlayer(world);

        if (this.sleeping) {
            this.pitch = 0.0F;
        }
    }

    private void baseTickLiving(WorldView world) {
        this.baseTickEntity(world);
        //Suffocate in walls
        //Drown in water
        //Soulspeed and frost walker
        //Update potion effects
    }

    private void baseTickEntity(WorldView world) {
        this.updateWaterState(world);
        this.updateSubmergedInWaterState(world);
        this.updateSwimming(world);

        if (this.isInLava()) {
            this.fallDistance *= 0.5F;
        }

        this.firstUpdate = false;
    }

    public boolean isSubmergedIn(TagKey<Fluid> tag) {
        return this.submergedFluids.contains(tag);
    }

    public boolean updateWaterState(WorldView world) {
        this.fluidHeight.clear();
        this.checkWaterState(world);
        double d = world.getDimension().ultrawarm() ? MovementConstants.Fluid.LAVA_ULTRAWARM_MULTIPLIER : MovementConstants.Fluid.LAVA_NORMAL_MULTIPLIER;
        boolean bl = this.updateMovementInFluid(world, FluidTags.LAVA, d);
        return this.touchingWater || bl;
    }

    private void updateSubmergedInWaterState(WorldView world) {
        this.isSubmergedInWater = this.isSubmergedIn(FluidTags.WATER);
        this.submergedFluids.clear();
        double d = this.getEyeY();

        BlockPos blockPos = new BlockPos((int) this.posX, (int) d, (int) this.posZ);
        FluidState fluidState = world.getFluidState(blockPos);
        double e = (float) blockPos.getY() + fluidState.getHeight(world, blockPos);

        if (e > d) {
            fluidState.streamTags().forEach(this.submergedFluids::add);
        }
    }

    public double getEyeY() {
        return this.posY + (double) this.standingEyeHeight;
    }

    public void updateSwimming(WorldView world) {
        if (this.swimming) {
            this.swimming = this.sprinting && this.touchingWater;
        } else {
            FluidState fluid = world.getFluidState(new BlockPos(this.blockX, this.blockY, this.blockZ));
            this.swimming = this.sprinting && this.isSubmergedInWater && fluid.isIn(FluidTags.WATER);
        }
    }

    public void updateWaterSubmersionState() {
        this.isSubmergedInWater = this.isSubmergedIn(FluidTags.WATER);
    }

    public void updateSize(WorldView world) {
        if (!this.wouldPoseNotCollide(world, EntityPose.SWIMMING)) return;
        EntityPose newPose;

        if (this.fallFlying) {
            newPose = EntityPose.GLIDING;
        } else {
            if (this.sleeping) {
                newPose = EntityPose.SLEEPING;
            } else {
                if (this.swimming) {
                    newPose = EntityPose.SWIMMING;
                } else {
                    if (this.input.playerInput.sneak()) {
                        newPose = EntityPose.CROUCHING;
                    } else {
                        newPose = EntityPose.STANDING;
                    }
                }
            }
        }

        if (!this.wouldPoseNotCollide(world, newPose)) {
            if (this.wouldPoseNotCollide(world, EntityPose.CROUCHING)) {
                newPose = EntityPose.CROUCHING;
            } else {
                newPose = EntityPose.SWIMMING;
            }
        }

        this.setPose(world, newPose);
    }

    public void setPose(WorldView world, EntityPose pose) {
        this.pose = pose;
        this.calculateDimensions(world);
    }

    public void calculateDimensions(WorldView world) {
        EntityDimensions oldDimensions = this.dimensions;
        this.dimensions = player.getBaseDimensions(this.pose);

        this.standingEyeHeight = this.dimensions.eyeHeight();

        if (this.dimensions.width() < oldDimensions.width()) {
            double d = (double) this.dimensions.width() / 2.0; // Width division factor
            this.box = new Box(this.posX - d, this.posY, this.posZ - d, this.posX + d,
                    this.posY + (double) this.dimensions.height(), this.posZ + d);
            return;
        }

        this.box = new Box(this.box.minX, this.box.minY, this.box.minZ, this.box.minX + (double) this.dimensions.width(),
                this.box.minY + (double) this.dimensions.height(), this.box.minZ + (double) this.dimensions.width());

        if (this.dimensions.width() > oldDimensions.width() && !this.firstUpdate) {
            float f = oldDimensions.width() - this.dimensions.width();
            this.move(world, MovementType.SELF, f, 0.0D, f);
        }
    }

    public void tickMovementClientPlayer(WorldView world) {
        boolean prevSneaking = this.input.playerInput.sneak();
        boolean wasWalking = this.isWalking();

        this.inSneakingPose = !this.swimming && this.wouldPoseNotCollide(world, EntityPose.CROUCHING)
                && (this.input.playerInput.sneak() || !this.sleeping && !this.wouldPoseNotCollide(world, EntityPose.STANDING));
        this.input.tick();

        if (this.ticksToNextAutojump > 0) {
            --this.ticksToNextAutojump;
            this.input.playerInput = new PlayerInput(
                    this.input.playerInput.forward(),
                    this.input.playerInput.backward(),
                    this.input.playerInput.left(),
                    this.input.playerInput.right(),
                    true,
                    this.input.playerInput.sneak(),
                    this.input.playerInput.sprint()
            );
        }

        double width = this.dimensions.width();
        this.pushOutOfBlocks(world, this.posX - width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET, this.posZ + width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET);
        this.pushOutOfBlocks(world, this.posX - width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET, this.posZ - width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET);
        this.pushOutOfBlocks(world, this.posX + width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET, this.posZ - width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET);
        this.pushOutOfBlocks(world, this.posX + width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET, this.posZ + width * CollisionConstants.BoxAdjustments.PUSH_OUT_OF_BLOCKS_OFFSET);


        if (this.sprinting) {
            if (this.swimming) {
                if (this.shouldStopSwimSprinting()) {
                    this.setSprinting(false);
                }
            } else if (this.shouldStopSprinting()) {
                this.setSprinting(false);
            }
        }

        if (this.canStartSprinting()) {
            if (this.keySprint) {
                this.setSprinting(true);
            }
        }

        if (this.touchingWater && this.input.playerInput.sneak()) {
            this.velY -= MovementConstants.Water.DIVE_SPEED;
        }

        this.tickMovementPlayer(world);
    }

    public void tickMovementPlayer(WorldView world) {
        this.tickMovementLiving(world);

        this.airStrafingSpeed = MovementConstants.Air.STRAFE_SPEED_BASE;

        if (this.sprinting) {
            this.airStrafingSpeed += MovementConstants.Air.STRAFE_SPEED_SPRINT_BONUS;
        }
    }

    public void tickMovementLiving(WorldView world) {
        if (this.jumpingCooldown > 0) {
            --this.jumpingCooldown;
        }

        Vec2f vec2f = this.applyMovementSpeedFactors(this.input.getMovementInput());
        this.sidewaysSpeed = vec2f.x;
        this.forwardSpeed = vec2f.y;
        this.jumping = this.input.playerInput.jump();


        if (Math.abs(this.velX) < GravityConstants.VelocityDecay.SMALL_VELOCITY_THRESHOLD) this.velX = 0.0;
        if (Math.abs(this.velY) < GravityConstants.VelocityDecay.VELOCITY_EPSILON) this.velY = 0.0;
        if (Math.abs(this.velZ) < GravityConstants.VelocityDecay.SMALL_VELOCITY_THRESHOLD) this.velZ = 0.0;

        if (this.jumping) {
            double k = this.isInLava() ? this.getFluidHeight(FluidTags.LAVA) : this.getFluidHeight(FluidTags.WATER);
            boolean bl = this.touchingWater && k > 0.0;
            double l = (double) this.standingEyeHeight < MovementConstants.Water.SWIMMING_EYE_HEIGHT_THRESHOLD ? 0.0D : MovementConstants.Water.SWIMMING_EYE_HEIGHT_THRESHOLD;

            if (bl && (!this.onGround || k > l)) {
                this.velY += MovementConstants.Water.WATER_JUMP_INCREMENT;
            } else if (!this.isInLava() || this.onGround && !(k > l)) {
                if ((this.onGround || bl) && this.jumpingCooldown == 0) {
                    this.jump(world);
                    this.jumpingCooldown = TickConstants.Cooldowns.JUMPING_COOLDOWN;
                }
            } else {
                this.velY += MovementConstants.Water.WATER_JUMP_INCREMENT;
            }
        } else {
            this.jumpingCooldown = 0;
        }


        this.travelPlayer(world);

        /* Entity pushing
        if(this.riptideTicks > 0) {
            --this.riptideTicks;
            this.tickRiptide(box, this.getBoundingBox());
        }
        this.tickCramming();*/
    }

    public void jump(WorldView world) {
        float newY = this.getJumpVelocity(world);

        if (this.jumpBoost >= 0) {
            newY += MovementConstants.Jump.JUMP_BOOST_PER_LEVEL * (float) (this.jumpBoost + 1);
        }

        this.velY = newY;

        if (this.sprinting) {
            float g = this.yaw * (float) (Math.PI / 180);
            this.velX += (double) (-MathHelper.sin(g)) * MovementConstants.Jump.SPRINT_JUMP_HORIZONTAL_BOOST;
            this.velZ += (double) MathHelper.cos(g) * MovementConstants.Jump.SPRINT_JUMP_HORIZONTAL_BOOST;
        }
    }

    public float getJumpVelocity(WorldView world) {
        return MovementConstants.Jump.BASE_JUMP_VELOCITY * this.getJumpVelocityMultiplier(world);
    }

    public float getJumpVelocityMultiplier(WorldView world) {
        BlockPos pos1 = new BlockPos(this.blockX, this.blockY, this.blockZ);
        BlockPos pos2 = new BlockPos(this.blockX, (int) (this.box.minY - 0.500001D), this.blockZ);
        float f = world.getBlockState(pos1).getBlock().getJumpVelocityMultiplier();
        float g = world.getBlockState(pos2).getBlock().getJumpVelocityMultiplier();
        return (double) f == 1.0D ? g : f;
    }

    public void updateVelocity(float speed) {
        double squaredMagnitude = (double) this.sidewaysSpeed * (double) this.sidewaysSpeed
                + (double) this.upwardSpeed * (double) this.upwardSpeed
                + (double) this.forwardSpeed * (double) this.forwardSpeed;

        if (squaredMagnitude < 1.0E-7) return;

        double sideways = this.sidewaysSpeed, upward = this.upwardSpeed, forward = this.forwardSpeed;

        if (squaredMagnitude > 1.0D) {
            double magnitude = Math.sqrt(squaredMagnitude);
            if (magnitude < 1.0E-4) {
                return;
            } else {
                sideways /= magnitude;
                upward /= magnitude;
                forward /= magnitude;
            }
        }

        sideways *= speed;
        upward *= speed;
        forward *= speed;
        float f = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
        float g = MathHelper.cos(yaw * (float) (Math.PI / 180.0));

        this.velX += sideways * (double) g - forward * (double) f;
        this.velY += upward;
        this.velZ += forward * (double) g + sideways * (double) f;
    }

    public void travelPlayer(WorldView world) {
        if (this.swimming) {
            float g = -MathHelper.sin(this.pitch * ((float) Math.PI / 180));
            double h = g < -0.2 ? 0.085 : 0.06;

            BlockPos pos = new BlockPos(this.blockX, MathHelper.floor(this.posY + 1.0D - 0.1D), this.blockZ);

            if (g <= 0.0D || this.jumping || !world.getBlockState(pos).getFluidState().isEmpty()) {
                this.velY += (g - this.velY) * h;
            }
        }

        this.travelLiving(world);
    }

    public void travelLiving(WorldView world) {
        boolean falling = this.velY <= 0.0D;
        double fallSpeed = GravityConstants.Gravity.STANDARD_GRAVITY;

        if (falling && this.slowFalling >= 0) {
            fallSpeed = GravityConstants.Gravity.SLOW_FALLING_GRAVITY;
            this.fallDistance = 0.0F;
        }

        if (this.touchingWater) {
            double startY = this.posY;
            float swimSpeed = this.sprinting ? MovementConstants.Water.SWIM_SPRINT_DRAG : MovementConstants.Water.SWIM_DRAG;
            float speed = MovementConstants.Water.SWIM_SPEED_BASE;

            float h = this.depthStrider;
            if (h > MovementConstants.Water.MAX_DEPTH_STRIDER_LEVEL)
                h = MovementConstants.Water.MAX_DEPTH_STRIDER_LEVEL;
            if (!this.onGround) h *= MovementConstants.Water.DEPTH_STRIDER_OFF_GROUND_MULT;

            if (h > 0.0F) {
                swimSpeed += (MovementConstants.Water.DEPTH_STRIDER_BONUS - swimSpeed) * h / MovementConstants.Water.DEPTH_STRIDER_DIVISOR;
                speed += (this.movementSpeed - speed) * h / MovementConstants.Water.DEPTH_STRIDER_DIVISOR;
            }

            if (this.dolphinsGrace >= 0) {
                swimSpeed = MovementConstants.Water.DOLPHIN_GRACE_DRAG;
            }

            this.updateVelocity(speed);
            this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

            if (this.horizontalCollision && this.isClimbing(world)) {
                this.velY = MovementConstants.Climbing.CLIMB_VELOCITY;
            }
            this.velX *= swimSpeed;
            this.velY *= MovementConstants.Water.WATER_Y_VELOCITY_DECAY;
            this.velZ *= swimSpeed;

            this.method_26317(fallSpeed, falling);

            boolean moveNoCollisions = this.doesNotCollide(world, this.velX, this.velY + (double) CollisionConstants.BoxAdjustments.COLLISION_CHECK_Y_OFFSET - this.posY + startY, this.velZ);

            if (this.horizontalCollision && moveNoCollisions) {
                this.velY = CollisionConstants.BoxAdjustments.HORIZONTAL_COLLISION_Y_VELOCITY;
            }
        } else if (this.isInLava()) {
            double startY = this.posY;
            this.updateVelocity(MovementConstants.Fluid.LAVA_MOVEMENT_SPEED);

            this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

            if (this.getFluidHeight(FluidTags.LAVA) <= ((double) this.standingEyeHeight < MovementConstants.Water.SWIMMING_EYE_HEIGHT_THRESHOLD ? 0.0D : MovementConstants.Water.SWIMMING_EYE_HEIGHT_THRESHOLD)) {
                this.velX *= MovementConstants.Fluid.LAVA_VELOCITY_MULT_XZ;
                this.velY *= MovementConstants.Fluid.LAVA_VELOCITY_MULT_Y;
                this.velZ *= MovementConstants.Fluid.LAVA_VELOCITY_MULT_XZ;
                this.method_26317(fallSpeed, falling);
            } else {
                this.velX *= MovementConstants.Fluid.SUBMERGED_LAVA_VELOCITY_MULT;
                this.velY *= MovementConstants.Fluid.SUBMERGED_LAVA_VELOCITY_MULT;
                this.velZ *= MovementConstants.Fluid.SUBMERGED_LAVA_VELOCITY_MULT;
            }

            this.velY -= fallSpeed / MovementConstants.Fluid.LAVA_FALL_SPEED_DIVISOR;

            boolean moveNoCollisions = this.doesNotCollide(world, this.velX, this.velY + (double) CollisionConstants.BoxAdjustments.COLLISION_CHECK_Y_OFFSET - this.posY + startY, this.velZ);

            if (this.horizontalCollision && moveNoCollisions) {
                this.velY = CollisionConstants.BoxAdjustments.HORIZONTAL_COLLISION_Y_VELOCITY;
            }
        } else if (this.fallFlying) {
            //No elytra controls
            if (this.velY > MovementConstants.Elytra.FALL_VELOCITY_THRESHOLD) {
                this.fallDistance = 1.0F;
            }

            float cYaw = MathHelper.cos(-this.yaw * (float) MechanicsConstants.Angles.DEGREES_TO_RADIANS);
            float sYaw = MathHelper.sin(-this.yaw * (float) MechanicsConstants.Angles.DEGREES_TO_RADIANS);
            float cPitch = MathHelper.cos(this.pitch * (float) MechanicsConstants.Angles.DEGREES_TO_RADIANS);
            float sPitch = MathHelper.sin(this.pitch * (float) MechanicsConstants.Angles.DEGREES_TO_RADIANS);
            double facingX = sYaw * cPitch;
            double facingY = -sPitch;
            double facingZ = cYaw * cPitch;

            double facingHM = Math.sqrt(facingX * facingX + facingZ * facingZ);
            double velHM = Math.sqrt(this.velX * this.velX + this.velZ * this.velZ);

            float cPitchSq = (float) ((double) cPitch * (double) cPitch);
            this.velY += fallSpeed * (-1.0D + (double) cPitchSq * MovementConstants.Elytra.GRAVITY_FACTOR);

            if (this.velY < 0.0D && facingHM > 0.0D) {
                double q = this.velY * MovementConstants.Elytra.VERTICAL_VELOCITY_FACTOR * (double) cPitchSq;
                this.velX += facingX * q / facingHM;
                this.velY += q;
                this.velZ += facingZ * q / facingHM;
            }

            if (this.pitch < 0.0F && facingHM > 0.0D) {
                double q = velHM * facingY * MovementConstants.Elytra.PITCH_CONTROL_FACTOR;
                this.velX -= facingX * q / facingHM;
                this.velY += q * MovementConstants.Elytra.VERTICAL_BOOST_MULTIPLIER;
                this.velZ -= facingZ * q / facingHM;
            }

            if (facingHM > 0.0D) {
                this.velX += (facingX / facingHM * velHM - this.velX) * MovementConstants.Elytra.HORIZONTAL_ADJUSTMENT_FACTOR;
                this.velZ += (facingZ / facingHM * velHM - this.velZ) * MovementConstants.Elytra.HORIZONTAL_ADJUSTMENT_FACTOR;
            }

            this.velX *= MovementConstants.Elytra.HORIZONTAL_VELOCITY_DECAY;
            this.velY *= MovementConstants.Elytra.VERTICAL_VELOCITY_DECAY;
            this.velZ *= MovementConstants.Elytra.HORIZONTAL_VELOCITY_DECAY;
            this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

            //Mojang why? WHYYYYYYYYYYYYYYY???
            if (this.onGround /*&& !world.isClient*/) {
                this.fallFlying = false;
            }
        } else {
            BlockPos pos = new BlockPos((int) this.posX, (int) (this.box.minY - 0.5000001D), (int) this.posZ);
            float slipperiness = world.getBlockState(pos).getBlock().getSlipperiness();
            float xzDrag = this.onGround ? slipperiness * MovementConstants.Friction.GROUND_FRICTION : MovementConstants.Friction.GROUND_FRICTION;
            double ajuVelY = this.applyMovementInput(world, slipperiness);

            if (this.levitation >= 0) {
                ajuVelY += (GravityConstants.Gravity.LEVITATION_PER_LEVEL * (double) (this.levitation + 1) - ajuVelY) * GravityConstants.VelocityDecay.LEVITATION_FACTOR;
                this.fallDistance = 0.0F;
            } else {
                ajuVelY -= fallSpeed;
            }

            this.velX *= xzDrag;
            this.velY = ajuVelY * (double) GravityConstants.VelocityDecay.VERTICAL_DECAY;
            this.velZ *= xzDrag;
        }
    }

    public double applyMovementInput(WorldView world, float f) {
        this.updateVelocity(this.getMovementSpeed(f));
        this.applyClimbingSpeed(world);

        this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

        if ((this.horizontalCollision || this.jumping) && this.isClimbing(world)) {
            return 0.2D;
        }

        return this.velY;
    }

    private float getMovementSpeed(float slipperiness) {
        if (this.onGround) {
            return this.movementSpeed * (MovementConstants.Speed.GROUND_SPEED_COEFFICIENT / (slipperiness * slipperiness * slipperiness));
        }

        return this.sprinting ? MovementConstants.Air.FLYING_STRAFE_SPEED : MovementConstants.Air.STRAFE_SPEED_BASE;
    }

    private void applyClimbingSpeed(WorldView world) {
        if (this.isClimbing(world)) {
            this.fallDistance = 0.0f;
            this.velX = MathHelper.clamp(this.velX, -MovementConstants.Climbing.MAX_CLIMB_SPEED, MovementConstants.Climbing.MAX_CLIMB_SPEED);
            this.velY = Math.max(this.velY, -MovementConstants.Climbing.MAX_CLIMB_SPEED);
            this.velZ = MathHelper.clamp(this.velZ, -MovementConstants.Climbing.MAX_CLIMB_SPEED, MovementConstants.Climbing.MAX_CLIMB_SPEED);

            BlockState state = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ));

            if (this.velY < 0.0D && !state.isOf(Blocks.SCAFFOLDING) && this.input.playerInput.sneak()) {
                this.velY = 0.0D;
            }
        }
    }

    public Iterable<VoxelShape> getBlockCollisions(WorldView world, Box box) {
        return () -> new AgentBlockCollisions(world, this, box);
    }

    public boolean isSpaceEmpty(WorldView world, Box box) {
        for (VoxelShape voxelShape : this.getBlockCollisions(world, box)) {
            if (!voxelShape.isEmpty()) {
                return false;
            }
        }

        return world.getEntityCollisions(null, box).isEmpty();
    }

    private boolean doesNotCollide(WorldView world, double offsetX, double offsetY, double offsetZ) {
        Box box = this.box.offset(offsetX, offsetY, offsetZ);
        return this.isSpaceEmpty(world, box) && !world.containsFluid(box);
    }

    public void method_26317(double fallSpeed, boolean falling) {
        if (!this.sprinting) {
            boolean b = falling && Math.abs(this.velY - GravityConstants.VelocityDecay.FALL_VELOCITY_PRECISION) >= CollisionConstants.Epsilon.VELOCITY_EPSILON && Math.abs(this.velY - fallSpeed / GravityConstants.VelocityDecay.FALL_SPEED_DIVISOR) < CollisionConstants.Epsilon.VELOCITY_EPSILON;
            this.velY = b ? -CollisionConstants.Epsilon.VELOCITY_EPSILON : this.velY - fallSpeed / GravityConstants.VelocityDecay.FALL_SPEED_DIVISOR;
        }
    }

    public void move(WorldView world, MovementType type, double movX, double movY, double movZ) {
        //if(type == MovementType.PISTON && (movement = this.adjustMovementForPiston(movement)).equals(Vec3d.ZERO)) {
        //    return;
        //}

        if (this.mulX * this.mulX + this.mulY * this.mulY + this.mulZ * this.mulZ > 0.0000001D) {
            movX *= this.mulX;
            movY *= this.mulY;
            movZ *= this.mulZ;
            this.mulX = 0;
            this.mulY = 0;
            this.mulZ = 0;
            this.velX = 0;
            this.velY = 0;
            this.velZ = 0;
        }


        Vec3d vec1 = this.adjustMovementForSneaking(world, type, new Vec3d(movX, movY, movZ));
        movX = vec1.x;
        movY = vec1.y;
        movZ = vec1.z;

        Vec3d vec2 = this.adjustMovementForCollisions(world, new Vec3d(movX, movY, movZ));
        double ajuX = vec2.x, ajuY = vec2.y, ajuZ = vec2.z;

        double magnitudeSq = ajuX * ajuX + ajuY * ajuY + ajuZ * ajuZ;

        if (magnitudeSq > 0.0000001D) {
            if (this.fallDistance != 0.0F && magnitudeSq >= 1.0D) {
                RaycastContext context = new AgentRaycastContext(this.getPos(), this.getPos().add(new Vec3d(ajuX, ajuY, ajuZ)),
                        RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, this);
                BlockHitResult result = world.raycast(context);

                if (result.getType() != HitResult.Type.MISS) {
                    this.fallDistance = 0.0F;
                }
            }

            this.setPos(this.posX + ajuX, this.posY + ajuY, this.posZ + ajuZ);
            this.box = this.dimensions.getBoxAt(this.posX, this.posY, this.posZ);
        }

        boolean xSimilar = !MathHelper.approximatelyEquals(movX, ajuX);
        boolean zSimilar = !MathHelper.approximatelyEquals(movZ, ajuZ);
        this.horizontalCollision = xSimilar || zSimilar;
        this.verticalCollision = movY != ajuY;
        this.collidedSoftly = this.horizontalCollision && this.hasCollidedSoftly(ajuX, ajuY, ajuZ);
        this.onGround = this.verticalCollision && movY < 0.0D;

        BlockPos landingPos = this.getLandingPos(world);
        BlockState landingState = world.getBlockState(landingPos);

        this.fall(world, ajuY, landingState);

        if (this.horizontalCollision) {
            if (xSimilar) this.velX = 0.0D;
            if (zSimilar) this.velZ = 0.0D;
        }

        Block block = landingState.getBlock();

        if (movY != ajuY) {
            if (block instanceof SlimeBlock && !this.input.playerInput.sneak()) {
                if (this.velY < 0.0D) {
                    this.velY *= -1;
                    this.slimeBounce = true;
                }
            } else if (block instanceof BedBlock) {
                if (this.velY < 0.0D) this.velY *= -1 * (double) MechanicsConstants.FallDamage.BED_FALL_MULTIPLIER;
            } else {
                this.velY = 0;
            }
        }

        if (this.onGround && !this.input.playerInput.sneak()) {
            if (block instanceof MagmaBlock) {
                //damage the entity
            } else if (block instanceof SlimeBlock) {
                double d = Math.abs(this.velY);

                if (d < 0.1D) {
                    this.velX *= 0.4D + d * 0.2D;
                    this.velZ *= 0.4D + d * 0.2D;
                    this.slimeBounce = true;
                }
            } else if (block instanceof TurtleEggBlock) {
                //eggs can break (1/100)
            }
        }

        this.checkBlockCollision(world);

        float i = this.getVelocityMultiplier(world);
        this.velX *= i;
        this.velZ *= i;

		/*
		if (this.world.method_29556(this.getBoundingBox().contract(0.001))
		.noneMatch(blockState -> blockState.isIn(BlockTags.FIRE) || blockState.isOf(Blocks.LAVA)) && this.fireTicks <= 0) {
			this.setFireTicks(-this.getBurningDuration());
		}*/
    }

    private boolean hasCollidedSoftly(double ajuX, double ajuY, double ajuZ) {
        float f = this.yaw * ((float) Math.PI / 180);
        double d = MathHelper.sin(f);
        double e = MathHelper.cos(f);
        double g = (double) this.sidewaysSpeed * e - (double) this.forwardSpeed * d;
        double h = (double) this.forwardSpeed * e + (double) this.sidewaysSpeed * d;
        double i = MathHelper.square(g) + MathHelper.square(h);
        double j = MathHelper.square(ajuX) + MathHelper.square(ajuZ);

        if (i < (double) 1.0E-5F || j < (double) 1.0E-5F) {
            return false;
        }

        double k = g * ajuX + h * ajuZ;
        double l = Math.acos(k / Math.sqrt(i * j));
        return l < CollisionConstants.Thresholds.SOFT_COLLISION_ANGLE_THRESHOLD;
    }

    public Vec3d adjustMovementForSneaking(WorldView world, MovementType type, Vec3d movement) {
        if (this.input.playerInput.sneak() && (type == MovementType.SELF || type == MovementType.PLAYER)
                && (this.onGround || this.fallDistance < this.stepHeight
                && !this.isSpaceEmpty(world, this.box.offset(0.0, this.fallDistance - this.stepHeight, 0.0)))) {
            double d = movement.x;
            double e = movement.z;

            while (d != 0.0 && this.isSpaceEmpty(world, this.box.offset(d, -this.stepHeight, 0.0))) {
                if (d < 0.05 && d >= -0.05) {
                    d = 0.0;
                    continue;
                }
                if (d > 0.0) {
                    d -= 0.05;
                    continue;
                }
                d += 0.05;
            }

            while (e != 0.0 && this.isSpaceEmpty(world, this.box.offset(0.0, -this.stepHeight, e))) {
                if (e < 0.05 && e >= -0.05) {
                    e = 0.0;
                    continue;
                }
                if (e > 0.0) {
                    e -= 0.05;
                    continue;
                }
                e += 0.05;
            }

            while (d != 0.0 && e != 0.0 && this.isSpaceEmpty(world, this.box.offset(d, -this.stepHeight, e))) {
                d = d < 0.05 && d >= -0.05 ? 0.0 : (d > 0.0 ? (d -= 0.05) : (d += 0.05));
                if (e < 0.05 && e >= -0.05) {
                    e = 0.0;
                    continue;
                }
                if (e > 0.0) {
                    e -= 0.05;
                    continue;
                }
                e += 0.05;
            }

            movement = new Vec3d(d, movement.y, e);
        }

        return movement;
    }

    private Vec3d adjustMovementForCollisions(WorldView world, Vec3d movement) {
        Box box = this.box;
        List<VoxelShape> list = world.getEntityCollisions(null, box.stretch(movement));
        Vec3d vec3d = movement.lengthSquared() == 0.0 ? movement : this.adjustMovementForCollisions(movement, box, world, list);
        boolean bl = movement.x != vec3d.x;
        boolean bl2 = movement.y != vec3d.y;
        boolean bl3 = movement.z != vec3d.z;
        boolean bl4 = bl2 && movement.y < 0.0;
        boolean bl5 = this.onGround || bl4;

        if (this.stepHeight > 0.0f && bl5 && (bl || bl3)) {
//            Vec3d vec3d2 = this.adjustMovementForCollisions(new Vec3d(movement.x, this.stepHeight, movement.z), box, world, list);
//            Vec3d vec3d3 = this.adjustMovementForCollisions(new Vec3d(0.0, this.stepHeight, 0.0), box.stretch(movement.x, 0.0, movement.z), world, list);
//            Vec3d vec3d4 = this.adjustMovementForCollisions(new Vec3d(movement.x, 0.0, movement.z), box.offset(vec3d3), world, list).add(vec3d3);
//
//            if(vec3d3.y < (double)this.stepHeight && vec3d4.horizontalLengthSquared() > vec3d2.horizontalLengthSquared()) {
//                vec3d2 = vec3d4;
//            }
//
//            if(vec3d2.horizontalLengthSquared() > vec3d.horizontalLengthSquared()) {
//                return vec3d2.add(this.adjustMovementForCollisions(new Vec3d(0.0, -vec3d2.y + movement.y, 0.0), box.offset(vec3d2), world, list));
//            }
            Box box2 = bl4 ? box.offset(0.0, vec3d.y, 0.0) : box;
            Box box3 = box2.stretch(movement.x, this.stepHeight, movement.z);
            if (!bl4) {
                box3 = box3.stretch(0.0, -1.0E-5F, 0.0);
            }

            List<VoxelShape> list2 = this.findCollisionsForMovement(world, list, box3);
            float f = (float) vec3d.y;
            float[] fs = collectStepHeights(box2, list2, this.stepHeight, f);

            for (float g : fs) {
                Vec3d vec3d2 = adjustMovementForCollisions(new Vec3d(movement.x, g, movement.z), box2, list2);
                if (vec3d2.horizontalLengthSquared() > vec3d.horizontalLengthSquared()) {
                    double d = box.minY - box2.minY;
                    return vec3d2.add(0.0, -d, 0.0);
                }
            }
        }

        return vec3d;
    }

    private List<VoxelShape> findCollisionsForMovement(WorldView world, List<VoxelShape> regularCollisions, Box movingEntityBoundingBox
    ) {
        // Use ArrayList with pre-sized capacity for better performance
        List<VoxelShape> result = new ArrayList<>(regularCollisions.size() + 16);
        if (!regularCollisions.isEmpty()) {
            result.addAll(regularCollisions);
        }

        // Add block collisions
        for (VoxelShape shape : this.getBlockCollisions(world, movingEntityBoundingBox)) {
            result.add(shape);
        }
        return result;
    }

    public Vec3d adjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, WorldView world, List<VoxelShape> entityCollisions) {
        // Use ArrayList with pre-sized capacity for better performance
        List<VoxelShape> collisions = new ArrayList<>(entityCollisions.size() + 16);

        if (!entityCollisions.isEmpty()) {
            collisions.addAll(entityCollisions);
        }

        // Add block collisions
        for (VoxelShape shape : this.getBlockCollisions(world, entityBoundingBox.stretch(movement))) {
            collisions.add(shape);
        }
        return this.adjustMovementForCollisions(movement, entityBoundingBox, collisions);
    }

    private Vec3d adjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, List<VoxelShape> collisions) {
        if (collisions.isEmpty()) {
            return movement;
        }

        double d = movement.x;
        double e = movement.y;
        double f = movement.z;

        // Early exit if no movement
        if (d == 0.0D && e == 0.0D && f == 0.0D) {
            return movement;
        }

        boolean bl = Math.abs(d) < Math.abs(f);

        // Y-axis collision (usually most important for gravity)
        if (e != 0.0D) {
            e = VoxelShapes.calculateMaxOffset(Direction.Axis.Y, entityBoundingBox, collisions, e);
            if (e != 0.0D) {
                entityBoundingBox = entityBoundingBox.offset(0.0D, e, 0.0D);
            }
        }

        // Horizontal collision handling with optimized order
        if (bl && f != 0.0D) {
            f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
            if (f != 0.0D) {
                entityBoundingBox = entityBoundingBox.offset(0.0D, 0.0D, f);
            }
        }

        if (d != 0.0D) {
            d = VoxelShapes.calculateMaxOffset(Direction.Axis.X, entityBoundingBox, collisions, d);
            if (!bl && d != 0.0D) {
                entityBoundingBox = entityBoundingBox.offset(d, 0.0D, 0.0D);
            }
        }

        if (!bl && f != 0.0D) {
            f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
        }

        return new Vec3d(d, e, f);
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

    void checkWaterState(WorldView world) {
        if (this.updateMovementInFluid(world, FluidTags.WATER, MovementConstants.Fluid.FLUID_VELOCITY_THRESHOLD)) {
            this.fallDistance = 0.0F;
            this.touchingWater = true;
        } else {
            this.touchingWater = false;
        }
    }

    public boolean updateMovementInFluid(WorldView world, TagKey<Fluid> tag, double d) {
        int n;
        Box box = this.box.contract(0.001D);
        int i = MathHelper.floor(box.minX);
        int j = MathHelper.ceil(box.maxX);
        int k = MathHelper.floor(box.minY);
        int l = MathHelper.ceil(box.maxY);
        int m = MathHelper.floor(box.minZ);

        if (!world.isRegionLoaded(i, k, m, j, l, n = MathHelper.ceil(box.maxZ))) {
            return false;
        }

        double e = 0.0;
        boolean bl2 = false;
        Vec3d vec3d = Vec3d.ZERO;
        int o = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int p = i; p < j; ++p) {
            for (int q = k; q < l; ++q) {
                for (int r = m; r < n; ++r) {
                    double f;
                    mutable.set(p, q, r);
                    FluidState fluidState = world.getFluidState(mutable);
                    if (!fluidState.isIn(tag) || !((f = (float) q + fluidState.getHeight(world, mutable)) >= box.minY))
                        continue;
                    bl2 = true;
                    e = Math.max(f - box.minY, e);

                    Vec3d vec3d2 = fluidState.getVelocity(world, mutable);

                    if (e < 0.4) {
                        vec3d2 = vec3d2.multiply(e);
                    }

                    vec3d = vec3d.add(vec3d2);
                    ++o;
                }
            }
        }

        if (vec3d.length() > 0.0) {
            if (o > 0) {
                vec3d = vec3d.multiply(1.0 / (double) o);
            }

            Vec3d vec3d3 = new Vec3d(this.velX, this.velY, this.velZ);
            vec3d = vec3d.multiply(d);

            if (Math.abs(vec3d3.x) < 0.003 && Math.abs(vec3d3.z) < 0.003 && vec3d.length() < 0.0045000000000000005) {
                vec3d = vec3d.normalize().multiply(0.0045000000000000005);
            }

            this.velX += vec3d.x;
            this.velY += vec3d.y;
            this.velZ += vec3d.z;
        }

        this.fluidHeight.put(tag, e);
        return bl2;
    }

    public void fall(WorldView world, double heightDifference, BlockState landedState) {
        if (!this.touchingWater) {
            this.checkWaterState(world);
        }

        //add soulspeed movement boost

        if (this.onGround) {
            if (this.fallDistance > 0.0F) {
                if (landedState.getBlock() instanceof BedBlock) {
                    this.handleFallDamage(this.fallDistance * MechanicsConstants.FallDamage.BED_FALL_MULTIPLIER, MechanicsConstants.FallDamage.DEFAULT_FALL_MULTIPLIER);
                } else if (landedState.getBlock() instanceof FarmlandBlock) {
                    //grief
                } else if (landedState.getBlock() instanceof HayBlock) {
                    this.handleFallDamage(this.fallDistance, MechanicsConstants.FallDamage.HAY_BLOCK_FALL_MULTIPLIER);
                } else if (landedState.getBlock() instanceof SlimeBlock) {
                    this.handleFallDamage(this.fallDistance, this.input.playerInput.sneak() ? MechanicsConstants.FallDamage.DEFAULT_FALL_MULTIPLIER : 0.0F);
                } else if (landedState.getBlock() instanceof TurtleEggBlock) {
                    //eggs can break (1/3)
                    this.handleFallDamage(this.fallDistance, MechanicsConstants.FallDamage.DEFAULT_FALL_MULTIPLIER);
                } else {
                    this.handleFallDamage(this.fallDistance, MechanicsConstants.FallDamage.DEFAULT_FALL_MULTIPLIER);
                }
            }

            this.fallDistance = 0.0F;
        } else if (heightDifference < 0.0D) {
            this.fallDistance -= (float) heightDifference;
        }

        if (this.touchingWater) {
            this.fallDistance = 0F;
        }
    }

    public boolean handleFallDamage(double fallDistance, float damageMultiplier) {
        int i = this.computeFallDamage(fallDistance, damageMultiplier);

        if (i > 0) {
            //this.damage(DamageSource.FALL, i);
            this.isDamaged = true;
            this.velX = 0;
            this.velZ = 0;
            return true;
        }

        return false;
    }

    public int computeFallDamage(double fallDistance, float damageMultiplier) {
        if (TungstenMod.mc.player.getType().isIn(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            Debug.logInternal("Player has fall damage immunity");
            return 0;
        }
        float f = this.jumpBoost < 0 ? 0.0F : (float) (this.jumpBoost + 1);
        return MathHelper.ceil((fallDistance - MechanicsConstants.FallDamage.FALL_DAMAGE_THRESHOLD - f) * damageMultiplier);
    }

    public void checkBlockCollision(WorldView world) {
        double minOffset = CollisionConstants.Epsilon.COLLISION_EPSILON; // default 0.001
        double maxOffset = CollisionConstants.Epsilon.COLLISION_EPSILON; // default 0.001
        BlockPos blockPos = new BlockPos((int) (this.box.minX + minOffset), (int) (this.box.minY + minOffset), (int) (this.box.minZ + minOffset));
        BlockPos blockPos2 = new BlockPos((int) (this.box.maxX - maxOffset), (int) (this.box.maxY - maxOffset), (int) (this.box.maxZ - maxOffset));
        BlockPos.Mutable pos = new BlockPos.Mutable();

        if (world.isRegionLoaded(blockPos, blockPos2)) {
            for (int i = blockPos.getX(); i <= blockPos2.getX(); ++i) {
                for (int j = blockPos.getY(); j <= blockPos2.getY(); ++j) {
                    for (int k = blockPos.getZ(); k <= blockPos2.getZ(); ++k) {
                        pos.set(i, j, k);
                        BlockState state = world.getBlockState(pos);

                        if (state.getBlock() instanceof AbstractFireBlock) {
                            //damage the entity
                        } else if (state.getBlock() instanceof AbstractPressurePlateBlock) {
                            //change block state
                        } else if (state.getBlock() instanceof BubbleColumnBlock) {
                            BlockState surface = world.getBlockState(pos.up());
                            boolean drag = surface.contains(BubbleColumnBlock.DRAG) && surface.get(BubbleColumnBlock.DRAG);

                            if (surface.isAir()) {
                                this.velY = drag ? Math.max(MovementConstants.Special.BUBBLE_COLUMN_DRAG_DOWN, this.velY - MovementConstants.Special.BUBBLE_COLUMN_ACCEL_DOWN) : Math.min(MovementConstants.Special.BUBBLE_COLUMN_MAX_SPEED_DOWN, this.velY + MovementConstants.Special.BUBBLE_COLUMN_SPEED_MULT_DOWN);
                            } else {
                                this.velY = drag ? Math.max(MovementConstants.Special.BUBBLE_COLUMN_DRAG_UP, this.velY - MovementConstants.Special.BUBBLE_COLUMN_ACCEL_UP) : Math.min(MovementConstants.Special.BUBBLE_COLUMN_MAX_SPEED_UP, this.velY + MovementConstants.Special.BUBBLE_COLUMN_SPEED_MULT_UP);
                                this.fallDistance = 0.0F;
                            }
                        } else if (state.getBlock() instanceof CactusBlock) {
                            //damage the entity
                        } else if (state.getBlock() instanceof CampfireBlock) {
                            //damage the entity
                        } else if (state.getBlock() instanceof CampfireBlock) {
                            //damage the entity
                        } else if (state.getBlock() instanceof CauldronBlock) {
                            //extinguish the entity
                        } else if (state.getBlock() instanceof CobwebBlock) {
                            this.fallDistance = 0.0F;
                            this.mulX = MovementConstants.Special.COBWEB_MOVEMENT_MULTIPLIER;
                            this.mulY = MovementConstants.Special.COBWEB_MOVEMENT_MULTIPLIER_Y;
                            this.mulZ = MovementConstants.Special.COBWEB_MOVEMENT_MULTIPLIER;
                        } else if (state.getBlock() instanceof EndPortalBlock) {
                            //fuck
                        } else if (state.getBlock() instanceof HoneyBlock) {
                            if (this.isSliding(pos)) {
                                if (this.velY < MovementConstants.Special.HONEY_BLOCK_SLOW_THRESHOLD) {
                                    double m = MovementConstants.Special.HONEY_BLOCK_VELOCITY_CAP / this.velY;
                                    this.velX *= m;
                                    this.velY = MovementConstants.Special.HONEY_BLOCK_VELOCITY_CAP;
                                    this.velZ *= m;
                                } else {
                                    this.velY = MovementConstants.Special.HONEY_BLOCK_VELOCITY_CAP;
                                }

                                this.fallDistance = 0.0F;
                            }
                        } else if (state.getBlock() instanceof NetherPortalBlock) {
                            //eh?
                        } else if (state.getBlock() instanceof SweetBerryBushBlock) {
                            this.mulX = MovementConstants.Special.SWEET_BERRY_MOVEMENT_MULTIPLIER;
                            this.mulY = MovementConstants.Special.SWEET_BERRY_HORIZONTAL_MULTIPLIER;
                            this.mulZ = MovementConstants.Special.SWEET_BERRY_MOVEMENT_MULTIPLIER;
                            //damage the entity
                        } else if (state.getBlock() instanceof TripwireBlock) {
                            //change block state
                        } else if (state.getBlock() instanceof WitherRoseBlock) {
                            //damage the entity
                        }
                    }
                }
            }
        }
    }

    private boolean isSliding(BlockPos pos) {
        if (this.onGround) return false;
        if (this.posY > (double) pos.getY() + MovementConstants.Special.HONEY_BLOCK_SLIDE_THRESHOLD - CollisionConstants.Epsilon.COLLISION_EPSILON)
            return false;
        if (this.velY >= MovementConstants.Special.HONEY_BLOCK_SLIDE_VELOCITY) return false;

        double d = Math.abs((double) pos.getX() + MovementConstants.Special.HONEY_BLOCK_POSITION_OFFSET - this.posX);
        double e = Math.abs((double) pos.getZ() + MovementConstants.Special.HONEY_BLOCK_POSITION_OFFSET - this.posZ);
        double f = MovementConstants.Special.HONEY_BLOCK_EDGE_DISTANCE + (double) (this.dimensions.width() / 2.0F);
        return d + CollisionConstants.Epsilon.COLLISION_EPSILON > f || e + CollisionConstants.Epsilon.COLLISION_EPSILON > f;
    }

    public float getVelocityMultiplier(WorldView world) {
        BlockState blockState = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ));
        float f = blockState.getBlock().getVelocityMultiplier();
        if (!blockState.isOf(Blocks.WATER) && !blockState.isOf(Blocks.BUBBLE_COLUMN)) {
            return (double) f == 1.0 ? world.getBlockState(this.getLandingPos(world)).getBlock().getVelocityMultiplier() : f;
        } else {
            return f;
        }
    }

    public BlockPos getLandingPos(WorldView world) {
        BlockPos pos = new BlockPos(this.blockX, MathHelper.floor(this.posY - (double) 0.2F), this.blockZ);

        if (!world.getBlockState(pos).isAir()) {
            return pos;
        }

        BlockState state = world.getBlockState(pos.down());

        if (state.getBlock() instanceof FenceGateBlock || state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS)) {
            return pos.down();
        }

        return pos;
    }

    private boolean canSprint() {
        return this.hunger.getFoodLevel() > MechanicsConstants.Hunger.SPRINT_HUNGER_REQUIREMENT;
    }

    public boolean shouldSlowDown() {
        return this.sneaking || this.keySneak;
    }

    private Vec2f applyMovementSpeedFactors(Vec2f input) {
        if (input.lengthSquared() == 0.0F) {
            return input;
        } else {
            Vec2f vec2f = input.multiply(MovementConstants.Speed.MOVEMENT_INPUT_MULTIPLIER);
            if (this.usingItem) {
                vec2f = vec2f.multiply(MovementConstants.Speed.USING_ITEM_MULTIPLIER);
            }

            if (this.shouldSlowDown()) {
                float f = MovementConstants.Speed.SNEAK_MULTIPLIER;
                vec2f = vec2f.multiply(f);
            }

            vec2f = applyDirectionalMovementSpeedFactors(vec2f);


            return vec2f;
        }
    }

    private boolean canStartSprinting() {
        return Math.abs(this.forwardSpeed) > -0.1
                && this.canSprint()
                && this.keyForward
                && !this.horizontalCollision
                && !this.usingItem
                && this.blindness < 0
                && (!this.shouldSlowDown() || this.isSubmergedInWater)
                && (!this.touchingWater || this.isSubmergedInWater);
    }

    private boolean shouldStopSprinting() {

        return this.forwardSpeed == 0
                || !this.keyForward
                || !this.canSprint()
                || this.horizontalCollision && !this.collidedSoftly
                || this.touchingWater && !this.isSubmergedInWater;
    }

    private boolean shouldStopSwimSprinting() {
        return !this.touchingWater
                || !this.input.hasForwardMovement() && !this.onGround && !this.input.playerInput.sneak()
                || !this.canSprint();
    }

    private boolean isWalking() {
        return this.input.hasForwardMovement();
    }

    public Box calculateBoundsForPose(EntityPose pose) {
        EntityDimensions size = player.getBaseDimensions(pose);
        float f = size.width() / 2.0F;
        Vec3d min = new Vec3d(this.posX - (double) f, this.posY, this.posZ - (double) f);
        Vec3d max = new Vec3d(this.posX + (double) f, this.posY + (double) size.height(), this.posZ + (double) f);
        return new Box(min, max);
    }

    public boolean wouldPoseNotCollide(WorldView world, EntityPose pose) {
        return this.isSpaceEmpty(world, this.calculateBoundsForPose(pose).contract(1.0E-7));
    }

    private void pushOutOfBlocks(WorldView world, double x, double d) {
        Direction[] directions = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
        BlockPos blockPos = new BlockPos((int) x, (int) this.posY, (int) d);
        if (!this.wouldCollideAt(world, blockPos)) {
            return;
        }
        double e = x - (double) blockPos.getX();
        double f = d - (double) blockPos.getZ();
        Direction direction = null;
        double g = Double.MAX_VALUE;

        for (Direction direction2 : directions) {
            double i;
            double h = direction2.getAxis().choose(e, 0.0D, f);
            double d2 = i = direction2.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - h : h;
            if (!(i < g) || this.wouldCollideAt(world, blockPos.offset(direction2))) continue;
            g = i;
            direction = direction2;
        }

        if (direction != null) {
            if (direction.getAxis() == Direction.Axis.X) {
                this.velX = CollisionConstants.BoxAdjustments.PUSH_VELOCITY * (double) direction.getOffsetX();
            } else {
                this.velZ = CollisionConstants.BoxAdjustments.PUSH_VELOCITY * (double) direction.getOffsetZ();
            }
        }
    }

    public boolean canCollide(WorldView world, Box box) {
        AgentBlockCollisions collisions = new AgentBlockCollisions(world, this, box, true);

        while (collisions.hasNext()) {
            if (!collisions.next().isEmpty()) {
                return true;
            }
        }

        return false;

//        if(!collisions.hasNext()) {
//            this.scannedBlocks += collisions.scannedBlocks;
//            return false;
//        }
//
//        while(collisions.next().isEmpty()) {
//            if(!collisions.hasNext()) {
//                this.scannedBlocks += collisions.scannedBlocks;
//                return false;
//            }
//        }
//
//        this.scannedBlocks += collisions.scannedBlocks;
//        return true;
    }

    private boolean wouldCollideAt(WorldView world, BlockPos pos) {
        Box box = this.box;
//		Box box2 = new Box((double)pos.getX(), box.minY, (double)pos.getZ(), (double)pos.getX() + 1.0, box.maxY, (double)pos.getZ() + 1.0).contract(1.0E-7);
        return this.canCollide(world, box);
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
        this.movementSpeed = MovementConstants.Speed.BASE_MOVEMENT_SPEED;

        if (sprinting) {
            this.movementSpeed *= (1.0D + (double) MovementConstants.Speed.SPRINT_MULTIPLIER);
        }

        if (this.speed >= 0) {
            double amplifier = MovementConstants.Speed.SPEED_EFFECT_AMPLIFIER * (double) (this.speed + 1);
            this.movementSpeed *= (1.0D + amplifier);
        }
    }

    public double getFluidHeight(TagKey<Fluid> fluid) {
        return this.fluidHeight.getDouble(fluid);
    }

    public boolean isInLava() {
        return !this.firstUpdate && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0D;
    }

    /*

    public void tickCramming(WorldView world) {
        List<Entity> list = world.getOtherEntities(this, this.box, EntityPredicates.canBePushedBy(this));

        if(!list.isEmpty()) {
            int j;
            int i = this.world.getGameRules().getInt(GameRules.MAX_ENTITY_CRAMMING);
            if (i > 0 && list.size() > i - 1 && this.random.nextInt(MechanicsConstants.HealthDamage.CRAMMING_RANDOM_CHANCE) == 0) {
                j = 0;
                for (int k = 0; k < list.size(); ++k) {
                    if (list.get(k).hasVehicle()) continue;
                    ++j;
                }
                if (j > i - 1) {
                    this.damage(DamageSource.CRAMMING, MechanicsConstants.HealthDamage.CRAMMING_DAMAGE);
                }
            }
            for (j = 0; j < list.size(); ++j) {
                Entity entity = list.get(j);
                this.pushAway(entity);
            }
        }
    }

    public void pushAway(Entity entity) {
        double e;
        double d = this.posX - entity.getX();
        double f = MathHelper.absMax(d, e = this.posZ - entity.getZ());

        if (f >= CollisionConstants.EntityCollision.MIN_PUSH_DISTANCE) {
            f = MathHelper.sqrt(f);
            d /= f;
            e /= f;

            double g = 1.0 / f;
            if(g > CollisionConstants.EntityCollision.PUSH_FORCE_CAP) g = CollisionConstants.EntityCollision.PUSH_FORCE_CAP;

            d *= g;
            e *= g;
            d *= CollisionConstants.EntityCollision.PUSH_FORCE_MULTIPLIER;
            e *= CollisionConstants.EntityCollision.PUSH_FORCE_MULTIPLIER;
            d *= 1.0F - entity.pushSpeedReduction;
            e *= 1.0F - entity.pushSpeedReduction;

            entity.addVelocity(-d, 0.0, -e);
            this.velX += d;
            this.velZ += e;
        }
    }

    public void tickRiptide(Box a, Box b) {
        Box box = a.union(b);
        List<Entity> list = this.world.getOtherEntities(this, box);
        if (!list.isEmpty()) {
            for (int i = 0; i < list.size(); ++i) {
                Entity entity = list.get(i);
                if (!(entity instanceof LivingEntity)) continue;
                this.attackLivingEntity((LivingEntity)entity);
                this.riptideTicks = 0;
                this.setVelocity(this.getVelocity().multiply(-0.2));
                break;
            }
        } else if (this.horizontalCollision) {
            this.riptideTicks = 0;
        }
        if (!this.world.isClient && this.riptideTicks <= 0) {
            this.setLivingFlag(4, false);
        }
    }
    */

    public void compare(ClientPlayerEntity player, boolean executor) {
        List<String> values = new ArrayList<>();

        if (this.posX != player.getX() || this.posY != player.getY() || this.posZ != player.getZ()) {
            values.add(String.format("Position mismatch (%s, %s, %s) vs (%s, %s, %s)",
                    player.getPos().x == this.posX ? "x" : player.getPos().x,
                    player.getPos().y == this.posY ? "y" : player.getPos().y,
                    player.getPos().z == this.posZ ? "z" : player.getPos().z,
                    player.getPos().x == this.posX ? "x" : this.posX,
                    player.getPos().y == this.posY ? "y" : this.posY,
                    player.getPos().z == this.posZ ? "z" : this.posZ));
            if (TungstenMod.EXECUTOR.isRunning()) {
//            	TungstenMod.EXECUTOR.stop = true;
//            	TungstenMod.PATHFINDER.stop.set(true);

                Node node = TungstenMod.EXECUTOR.getCurrentNode();
                if (node != null) RenderHelper.renderNode(node, TungstenMod.ERROR);
                TungstenMod.ERROR.add(new Cuboid(player.getPos(), new Vec3d(0.1, 0.5, 0.1), Color.RED));
            }
        }

        if (this.velX != player.getVelocity().x || this.velY != player.getVelocity().y || this.velZ != player.getVelocity().z) {
            values.add(String.format("Velocity mismatch (%s, %s, %s) vs (%s, %s, %s)",
                    player.getVelocity().x,
                    player.getVelocity().y,
                    player.getVelocity().z,
                    player.getVelocity().x == this.velX ? "x" : this.velX,
                    player.getVelocity().y == this.velY ? "y" : this.velY,
                    player.getVelocity().z == this.velZ ? "z" : this.velZ));
            // I know this is probably a really stupid way to fix a mismatch but server doesnt seem to care so I'm doing it anyway!
            if (TungstenMod.EXECUTOR.isRunning()) {

                values.add(String.format("Velocity mismatch by (%s, %s, %s)",
                        player.getVelocity().x - this.velX,
                        player.getVelocity().y - this.velY,
                        player.getVelocity().z - this.velZ));
                player.setVelocity(this.velX, this.velY, this.velZ);
                Node node = TungstenMod.EXECUTOR.getCurrentNode();
                if (node != null) RenderHelper.renderNode(node, TungstenMod.ERROR);
                TungstenMod.ERROR.add(new Cuboid(player.getPos(), new Vec3d(0.1, 0.5, 0.1), Color.RED));
            }
        }

        if (this.mulX != ((AccessorEntity) player).getMovementMultiplier().x
                || this.mulY != ((AccessorEntity) player).getMovementMultiplier().y
                || this.mulZ != ((AccessorEntity) player).getMovementMultiplier().z) {
            values.add(String.format("Movement Multiplier mismatch (%s, %s, %s) vs (%s, %s, %s)",
                    ((AccessorEntity) player).getMovementMultiplier().x,
                    ((AccessorEntity) player).getMovementMultiplier().y,
                    ((AccessorEntity) player).getMovementMultiplier().z,
                    ((AccessorEntity) player).getMovementMultiplier().x == this.mulX ? "x" : this.mulX,
                    ((AccessorEntity) player).getMovementMultiplier().y == this.mulY ? "y" : this.mulY,
                    ((AccessorEntity) player).getMovementMultiplier().z == this.mulZ ? "z" : this.mulZ));
        }

        if (this.forwardSpeed != player.forwardSpeed || this.sidewaysSpeed != player.sidewaysSpeed || this.upwardSpeed != player.upwardSpeed) {
            values.add(String.format("Input Speed mismatch (%s, %s, %s) vs (%s, %s, %s)",
                    player.forwardSpeed == this.forwardSpeed ? "f" : player.forwardSpeed,
                    player.upwardSpeed == this.upwardSpeed ? "u" : player.upwardSpeed,
                    player.sidewaysSpeed == this.sidewaysSpeed ? "s" : player.sidewaysSpeed,
                    player.forwardSpeed == this.forwardSpeed ? "f" : this.forwardSpeed,
                    player.upwardSpeed == this.upwardSpeed ? "u" : this.upwardSpeed,
                    player.sidewaysSpeed == this.sidewaysSpeed ? "s" : this.sidewaysSpeed));
        }

        if (this.movementSpeed != player.getMovementSpeed()) {

            Node node = TungstenMod.EXECUTOR.getCurrentNode();
            if (node != null) {
                StringBuilder string = new StringBuilder();

                string.append("{\n");
                if (node.input.forward() != player.input.playerInput.forward()) {
                    string.append("forward: ");
                    string.append(player.input.playerInput.forward());
                    string.append(" vs ");
                    string.append(node.input.forward());
                    string.append("\n");
                }
                if (node.input.back() != player.input.playerInput.backward()) {
                    string.append("back: ");
                    string.append(player.input.playerInput.backward());
                    string.append(" vs ");
                    string.append(node.input.back());
                    string.append("\n");
                }
                if (node.input.right() != player.input.playerInput.right()) {
                    string.append("right: ");
                    string.append(player.input.playerInput.right());
                    string.append(" vs ");
                    string.append(node.input.right());
                    string.append("\n");
                }
                if (node.input.left() != player.input.playerInput.left()) {
                    string.append("left: ");
                    string.append(player.input.playerInput.left());
                    string.append(" vs ");
                    string.append(node.input.left());
                    string.append("\n");
                }
                if (node.input.jump() != player.input.playerInput.jump()) {
                    string.append("jump: ");
                    string.append(player.input.playerInput.jump());
                    string.append(" vs ");
                    string.append(node.input.jump());
                    string.append("\n");
                }
                if (node.input.sneak() != player.input.playerInput.sneak()) {
                    string.append("sneak: ");
                    string.append(player.input.playerInput.sneak());
                    string.append(" vs ");
                    string.append(node.input.sneak());
                    string.append("\n");
                }
                if (node.input.sprint() != player.input.playerInput.sprint()) {
                    string.append("sprint: ");
                    string.append(player.input.playerInput.sprint());
                    string.append(" vs ");
                    string.append(node.input.sprint());
                    string.append("\n");
                }
                if (node.input.pitch() != player.getPitch()) {
                    string.append("pitch: ");
                    string.append(player.getPitch());
                    string.append(" vs ");
                    string.append(node.input.pitch());
                    string.append("\n");
                }
                if (node.input.yaw() != player.getYaw()) {
                    string.append("yaw: ");
                    string.append(player.getYaw());
                    string.append(" vs ");
                    string.append(node.input.yaw());
                    string.append("\n");
                }
                if (node.agent.onGround != player.isOnGround()) {
                    string.append("isOnGround: ");
                    string.append(player.isOnGround());
                    string.append(" vs ");
                    string.append(node.agent.onGround);
                    string.append("\n");
                }
                string.append("\n");
                string.append("}");

                Debug.logMessage("------------");
                Debug.logMessage(string.toString());
                Debug.logMessage(player.getMovementSpeed() + " " + this.movementSpeed);
                Debug.logMessage("------------");
            }
            values.add(String.format("Movement Speed mismatch %f vs %f", player.getMovementSpeed(), this.movementSpeed));
        }

        if (this.pose != player.getPose()) {
            values.add(String.format("Pose mismatch %s vs %s", player.getPose(), this.pose));
        }

        if (this.isSubmergedInWater != player.isSubmergedInWater()) {
            values.add(String.format("Sprinting mismatch %s vs %s", player.isSprinting(), this.sprinting));
        }

        if (this.touchingWater != player.isTouchingWater()) {
            values.add(String.format("Touching water mismatch %s vs %s", player.isTouchingWater(), this.touchingWater));
        }

        if (this.isSubmergedInWater != player.isSubmergedInWater()) {
            values.add(String.format("Submerged in water mismatch %s vs %s", player.isSubmergedInWater(), this.isSubmergedInWater));
        }

        if (this.input.playerInput.sneak() != player.input.playerInput.sneak()) {
            values.add(String.format("Sneaking mismatch %s vs %s", player.input.playerInput.sneak(), this.input.playerInput.sneak()));
        }

        if (this.swimming != player.isSwimming()) {
            values.add(String.format("Swimming mismatch %s vs %s", player.isSwimming(), this.swimming));
        }

        if (this.standingEyeHeight != player.getStandingEyeHeight()) {
            values.add(String.format("Eye height mismatch %s vs %s", player.getStandingEyeHeight(), this.standingEyeHeight));
        }

        if (this.fallDistance != player.fallDistance) {
            values.add(String.format("Fall distance mismatch %s vs %s", player.fallDistance, this.fallDistance));
        }

        if (this.horizontalCollision != player.horizontalCollision) {
            values.add(String.format("Horizontal Collision mismatch %s vs %s", player.horizontalCollision, this.horizontalCollision));
        }

        if (this.verticalCollision != player.verticalCollision) {
            values.add(String.format("Vertical Collision mismatch %s vs %s", player.verticalCollision, this.verticalCollision));
        }

        if (this.collidedSoftly != player.collidedSoftly) {
            values.add(String.format("Soft Collision mismatch %s vs %s", player.collidedSoftly, this.collidedSoftly));
        }

        if (this.jumping != ((AccessorLivingEntity) player).getJumping()) {
            values.add(String.format("Jumping mismatch %s vs %s", ((AccessorLivingEntity) player).getJumping(), this.jumping));
        }

        if (this.jumpingCooldown != ((AccessorLivingEntity) player).getJumpingCooldown()) {
            values.add(String.format("Jumping Cooldown mismatch %s vs %s", ((AccessorLivingEntity) player).getJumpingCooldown(), this.jumpingCooldown));
        }

//        if(this.airStrafingSpeed != player.airStrafingSpeed) {
//            values.add(String.format("Air Strafe Speed mismatch %s vs %s", player.airStrafingSpeed, this.airStrafingSpeed));
//        }

//        if(!this.box.equals(player.getBoundingBox())) {
//            values.add(String.format("Bounding box mismatch %s vs %s", player.getBoundingBox(), this.box));
//            this.box = player.getBoundingBox();
//        }


        if (this.firstUpdate != ((AccessorEntity) player).getFirstUpdate()) {
            values.add(String.format("First Update mismatch %s vs %s", ((AccessorEntity) player).getFirstUpdate(), this.firstUpdate));
        }

        if (!this.submergedFluids.equals(((AccessorEntity) player).getSubmergedFluidTag())) {
            values.add(String.format("Submerged Fluids mismatch %s vs %s", ((AccessorEntity) player).getSubmergedFluidTag(), this.submergedFluids));
        }

        if (!values.isEmpty()) {
            Debug.logInternal("Tick %d ===========================================", player.age);
            values.forEach(value -> Debug.logInternal(value));
        }
    }
}
