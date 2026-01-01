package kaptainwutax.tungsten.agent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import kaptainwutax.tungsten.constants.CollisionConstants;
import kaptainwutax.tungsten.constants.PhysicsConstants;
import kaptainwutax.tungsten.mixin.AccessorEntity;
import kaptainwutax.tungsten.mixin.AccessorLivingEntity;
import kaptainwutax.tungsten.path.PathInput;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.TagKey;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.WorldView;

import java.util.*;

public class Agent {

    public static Agent INSTANCE;

    public static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.changing(0.6F, 1.8F);
    public static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F);

    // Eye height constants for different poses
    public static final float EYE_HEIGHT_STANDING = 1.62F;
    public static final float EYE_HEIGHT_CROUCHING = 1.27F;
    public static final float EYE_HEIGHT_SWIMMING = 0.4F;
    public static final float EYE_HEIGHT_SLEEPING = 0.2F;

    public static final Map<EntityPose, EntityDimensions> POSE_DIMENSIONS =
        ImmutableMap.<EntityPose, EntityDimensions>builder()
        .put(EntityPose.STANDING, STANDING_DIMENSIONS)
        .put(EntityPose.SLEEPING, SLEEPING_DIMENSIONS)
        .put(EntityPose.FALL_FLYING, EntityDimensions.changing(0.6F, 0.6F))
        .put(EntityPose.SWIMMING, EntityDimensions.changing(0.6F, 0.6F))
        .put(EntityPose.SPIN_ATTACK, EntityDimensions.changing(0.6F, 0.6F))
        .put(EntityPose.CROUCHING, EntityDimensions.changing(0.6F, 1.5F))
        .put(EntityPose.DYING, EntityDimensions.fixed(0.2F, 0.2F)).build();

    public boolean keyForward;
    public boolean keyBack;
    public boolean keyLeft;
    public boolean keyRight;
    public boolean keyJump;
    public boolean keySneak;
    public boolean keySprint;
    public AgentInput input = new AgentInput(this);

    public EntityPose pose;
    public boolean inSneakingPose;
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

    public Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
    private final Set<TagKey<Fluid>> submergedFluids = new HashSet<>();
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
    public float stepHeight = PhysicsConstants.STEP_HEIGHT_DEFAULT;
    public float fallDistance;
    public boolean touchingWater;
    public boolean isSubmergedInWater;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean collidedSoftly;

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
    private List<String> extra = new ArrayList<>();
    private int scannedBlocks;

    public Vec3d getPos() {
        return new Vec3d(this.posX, this.posY, this.posZ);
    }

    public void setPos(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.blockX = MathHelper.floor(x);
        this.blockY = MathHelper.floor(y);
        this.blockZ = MathHelper.floor(z);
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

        if(this.sleeping) {
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

        if(this.isInLava()) {
            this.fallDistance *= PhysicsConstants.FALL_DISTANCE_LAVA_MULTIPLIER;
        }

        this.firstUpdate = false;
    }

    public boolean isSubmergedIn(TagKey<Fluid> tag) {
        return this.submergedFluids.contains(tag);
    }

    public boolean updateWaterState(WorldView world) {
        this.fluidHeight.clear();
        this.checkWaterState(world);
        double d = world.getDimension().ultrawarm() ? PhysicsConstants.FLUID_VELOCITY_MULTIPLIER : PhysicsConstants.FLUID_VELOCITY_LAVA_MULTIPLIER;
        boolean bl = this.updateMovementInFluid(world, FluidTags.LAVA, d);
        return this.touchingWater || bl;
    }

    private void updateSubmergedInWaterState(WorldView world) {
        this.isSubmergedInWater = this.isSubmergedIn(FluidTags.WATER);
        this.submergedFluids.clear();
        double d = this.getEyeY() - PhysicsConstants.EYE_HEIGHT_FLUID_OFFSET;

        BlockPos blockPos = new BlockPos(this.posX, d, this.posZ);
        FluidState fluidState = world.getFluidState(blockPos);
        double e = (float)blockPos.getY() + fluidState.getHeight(world, blockPos);

        if(e > d) {
            fluidState.streamTags().forEach(this.submergedFluids::add);
        }
    }

    public double getEyeY() {
        return this.posY + (double)this.standingEyeHeight;
    }

    public void updateSwimming(WorldView world) {
        if(this.swimming) {
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
        if(!this.wouldPoseNotCollide(world, EntityPose.SWIMMING)) return;
        EntityPose newPose;

        if(this.fallFlying) {
            newPose = EntityPose.FALL_FLYING;
        } else {
            if(this.sleeping) {
                newPose = EntityPose.SLEEPING;
            } else {
                if(this.swimming) {
                    newPose = EntityPose.SWIMMING;
                } else {
                    if(this.input.sneaking) {
                        newPose = EntityPose.CROUCHING;
                    } else {
                        newPose = EntityPose.STANDING;
                    }
                }
            }
        }

        if(!this.wouldPoseNotCollide(world, newPose)) {
            if(this.wouldPoseNotCollide(world, EntityPose.CROUCHING)) {
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
        this.dimensions = POSE_DIMENSIONS.getOrDefault(this.pose, STANDING_DIMENSIONS);

        this.standingEyeHeight = this.getEyeHeight(this.pose, this.dimensions);

        if(this.dimensions.width < oldDimensions.width) {
            double d = (double)this.dimensions.width / 2.0;
            this.box = new Box(this.posX - d, this.posY, this.posZ - d, this.posX + d,
                this.posY + (double)this.dimensions.height, this.posZ + d);
            return;
        }

        this.box = new Box(this.box.minX, this.box.minY, this.box.minZ, this.box.minX + (double)this.dimensions.width,
            this.box.minY + (double)this.dimensions.height, this.box.minZ + (double)this.dimensions.width);

        if(this.dimensions.width > oldDimensions.width && !this.firstUpdate) {
            float f = oldDimensions.width - this.dimensions.width;
            this.move(world, MovementType.SELF, f, 0.0D, f);
        }
    }

    public final float getEyeHeight(EntityPose pose, EntityDimensions dimensions) {
         return switch(pose) {
            case SWIMMING, FALL_FLYING, SPIN_ATTACK -> EYE_HEIGHT_SWIMMING;
            case CROUCHING -> EYE_HEIGHT_CROUCHING;
            case SLEEPING -> EYE_HEIGHT_SLEEPING;
            default -> EYE_HEIGHT_STANDING;
        };
    }

    public void tickMovementClientPlayer(WorldView world) {
        boolean prevSneaking = this.input.sneaking;
        boolean wasWalking = this.isWalking();

        this.inSneakingPose = !this.swimming && this.wouldPoseNotCollide(world, EntityPose.CROUCHING)
            && (this.input.sneaking || !this.sleeping && !this.wouldPoseNotCollide(world, EntityPose.STANDING));
        this.input.tick(this.inSneakingPose || (this.pose == EntityPose.SWIMMING && !this.touchingWater), PhysicsConstants.MOVEMENT_INPUT_MULTIPLIER);

        if(this.usingItem) {
            this.input.movementSideways *= PhysicsConstants.MOVEMENT_USING_ITEM_MULTIPLIER;
            this.input.movementForward *= PhysicsConstants.MOVEMENT_USING_ITEM_MULTIPLIER;
        }

        if(this.ticksToNextAutojump > 0) {
            --this.ticksToNextAutojump;
            this.input.jumping = true;
        }

        double width = this.dimensions.width;
        this.pushOutOfBlocks(world, this.posX - width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER, this.posZ + width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER);
        this.pushOutOfBlocks(world, this.posX - width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER, this.posZ - width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER);
        this.pushOutOfBlocks(world, this.posX + width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER, this.posZ - width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER);
        this.pushOutOfBlocks(world, this.posX + width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER, this.posZ + width * PhysicsConstants.PUSH_OUT_BLOCKS_MULTIPLIER);

        boolean canSprint = (float)this.hunger.getFoodLevel() > PhysicsConstants.HUNGER_SPRINT_THRESHOLD;

        if((this.onGround || this.isSubmergedInWater) && !prevSneaking && !wasWalking && this.isWalking()
            && !this.sprinting && canSprint && !this.usingItem && this.blindness < 0) {
            if(this.keySprint) {
                this.setSprinting(true);
            }
        }

        if(!this.sprinting && (!this.touchingWater || this.isSubmergedInWater) && this.isWalking() && canSprint
            && !this.usingItem && this.blindness < 0 && this.keySprint) {
            this.setSprinting(true);
        }

        if(this.sprinting) {
            boolean slow = !this.input.hasForwardMovement() || !canSprint;
            boolean slowed = this.horizontalCollision && !this.collidedSoftly || this.touchingWater && !this.isSubmergedInWater;

            if(this.swimming) {
                if(!this.onGround && !this.input.sneaking && slow || !this.touchingWater) {
                    this.setSprinting(false);
                }
            } else if(slow || slowed) {
                this.setSprinting(false);
            }
        }

        if(this.touchingWater && this.input.sneaking) {
            this.velY -= PhysicsConstants.WATER_DESCENT_SPEED_SNEAKING;
        }

        this.tickMovementPlayer(world);
    }

    public void tickMovementPlayer(WorldView world) {
        this.tickMovementLiving(world);

        this.airStrafingSpeed = PhysicsConstants.AIR_STRAFING_SPEED_BASE;

        if(this.sprinting) {
            this.airStrafingSpeed += PhysicsConstants.AIR_STRAFING_SPEED_SLOW;
        }
    }

    public void tickMovementLiving(WorldView world) {
        if(this.jumpingCooldown > 0) {
            --this.jumpingCooldown;
        }

        if(Math.abs(this.velX) < PhysicsConstants.VELOCITY_EPSILON) this.velX = 0.0;
        if(Math.abs(this.velY) < PhysicsConstants.VELOCITY_EPSILON) this.velY = 0.0;
        if(Math.abs(this.velZ) < PhysicsConstants.VELOCITY_EPSILON) this.velZ = 0.0;

        this.sidewaysSpeed = this.input.movementSideways;
        this.forwardSpeed = this.input.movementForward;
        this.jumping = this.input.jumping;

        if(this.jumping) {
            double k = this.isInLava() ? this.getFluidHeight(FluidTags.LAVA) : this.getFluidHeight(FluidTags.WATER);
            boolean bl = this.touchingWater && k > 0.0;
            double l = (double)this.standingEyeHeight < PhysicsConstants.JUMP_VERTICAL_INERTIA ? 0.0D : PhysicsConstants.JUMP_VERTICAL_INERTIA;

            if(bl && (!this.onGround || k > l)) {
                this.velY += PhysicsConstants.JUMP_HORIZONTAL_INERTIA;
            } else if(!this.isInLava() || this.onGround && !(k > l)) {
                if((this.onGround || bl) && this.jumpingCooldown == 0) {
                    this.jump(world);
                    this.jumpingCooldown = 10;
                }
            } else {
                this.velY += PhysicsConstants.JUMP_HORIZONTAL_INERTIA;
            }
        } else {
            this.jumpingCooldown = 0;
        }

        this.sidewaysSpeed *= PhysicsConstants.MOVEMENT_DECAY;
        this.forwardSpeed *= PhysicsConstants.MOVEMENT_DECAY;

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

        if(this.jumpBoost >= 0) {
            newY += PhysicsConstants.JUMP_BOOST_MULTIPLIER * (float)(this.jumpBoost + 1);
        }

        this.velY = newY;

        if(this.sprinting) {
            float g = this.yaw * PhysicsConstants.DEGREES_TO_RADIANS;
            this.velX += -MathHelper.sin(g) * PhysicsConstants.JUMP_HORIZONTAL_BOOST_SPRINTING;
            this.velZ +=  MathHelper.cos(g) * PhysicsConstants.JUMP_HORIZONTAL_BOOST_SPRINTING;
        }
    }

    public float getJumpVelocity(WorldView world) {
        return PhysicsConstants.JUMP_VELOCITY_BASE * this.getJumpVelocityMultiplier(world);
    }

    public float getJumpVelocityMultiplier(WorldView world) {
        BlockPos pos1 = new BlockPos(this.blockX, this.blockY, this.blockZ);
        BlockPos pos2 = new BlockPos(this.blockX, this.box.minY - PhysicsConstants.JUMP_HEIGHT_OFFSET, this.blockZ);
        float f = world.getBlockState(pos1).getBlock().getJumpVelocityMultiplier();
        float g = world.getBlockState(pos2).getBlock().getJumpVelocityMultiplier();
        return (double)f == 1.0D ? g : f;
    }

    public void updateVelocity(float speed) {
        double squaredMagnitude = (double)this.sidewaysSpeed * (double)this.sidewaysSpeed
                                + (double)this.upwardSpeed * (double)this.upwardSpeed
                                + (double)this.forwardSpeed * (double)this.forwardSpeed;

        if (squaredMagnitude < PhysicsConstants.VELOCITY_MIN_SQUARED) return;

        double sideways = this.sidewaysSpeed, upward = this.upwardSpeed, forward = this.forwardSpeed;

        if(squaredMagnitude > 1.0D) {
            double magnitude = Math.sqrt(squaredMagnitude);
            if (magnitude < PhysicsConstants.VELOCITY_SMALL_THRESHOLD_1) { return; }
            else { sideways /= magnitude; upward /= magnitude; forward /= magnitude; }
        }

        sideways *= speed; upward *= speed; forward *= speed;
        float f = MathHelper.sin(this.yaw * PhysicsConstants.DEGREES_TO_RADIANS);
        float g = MathHelper.cos(this.yaw * PhysicsConstants.DEGREES_TO_RADIANS);

        this.velX += sideways * (double)g - forward * (double)f;
        this.velY += upward;
        this.velZ += forward * (double)g + sideways * (double)f;
    }

    public void travelPlayer(WorldView world) {
        if(this.swimming) {
            float g = -MathHelper.sin(this.pitch * PhysicsConstants.DEGREES_TO_RADIANS);
            double h = g < -0.2 ? PhysicsConstants.SWIMMING_SPEED_UNDERWATER : PhysicsConstants.SWIMMING_SPEED_SURFACE;

            BlockPos pos = new BlockPos(this.blockX, MathHelper.floor(this.posY + 1.0D - PhysicsConstants.SWIMMING_SPEED_SURFACE_UNDER), this.blockZ);

            if(g <= 0.0D || this.jumping || !world.getBlockState(pos).getFluidState().isEmpty()) {
                this.velY += (g - this.velY) * h;
            }
        }

        this.travelLiving(world);
    }

    public void travelLiving(WorldView world) {
        boolean falling = this.velY <= 0.0D;
        double fallSpeed = PhysicsConstants.GRAVITY_DEFAULT;

        if(falling && this.slowFalling >= 0) {
            fallSpeed = PhysicsConstants.GRAVITY_SLOW_FALLING;
            this.fallDistance = 0.0F;
        }

        if(this.touchingWater) {
            double startY = this.posY;
            float swimSpeed = this.sprinting ? 0.9F : (float)PhysicsConstants.WATER_PUSH_UP_SPEED;
            float speed = (float)PhysicsConstants.SWIMMING_SPEED_MULTIPLIER;

            float h = this.depthStrider;
            if(h > 3.0F) h = 3.0F;
            if(!this.onGround) h *= 0.5F;

            if(h > 0.0F) {
                swimSpeed += (0.54600006F - swimSpeed) * h / 3.0F;
                speed += (this.movementSpeed - speed) * h / 3.0F;
            }

            if(this.dolphinsGrace >= 0) {
                swimSpeed = 0.96F;
            }

            this.updateVelocity(speed);
            this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

            if(this.horizontalCollision && this.isClimbing(world)) { this.velY = PhysicsConstants.CLIMBING_SPEED_BASE; }
            this.velX *= swimSpeed; this.velY *= (float)PhysicsConstants.WATER_PUSH_UP_SPEED; this.velZ *= swimSpeed;

            this.method_26317(fallSpeed, falling);

            boolean moveNoCollisions = this.doesNotCollide(world, this.velX, this.velY + PhysicsConstants.SLIPPERINESS_MULTIPLIER - this.posY + startY, this.velZ);

            if(this.horizontalCollision && moveNoCollisions) {
                this.velY = PhysicsConstants.COLLISION_VERTICAL_VELOCITY;
            }
        } else if(this.isInLava()) {
            double startY = this.posY;
            this.updateVelocity((float)PhysicsConstants.SWIMMING_SPEED_MULTIPLIER);

            this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

            if(this.getFluidHeight(FluidTags.LAVA) <= ((double)this.standingEyeHeight < PhysicsConstants.JUMP_VERTICAL_INERTIA ? 0.0D : PhysicsConstants.JUMP_VERTICAL_INERTIA)) {
                this.velX *= PhysicsConstants.LAVA_VELOCITY_MULTIPLIER_XZ; this.velY *= (float)PhysicsConstants.WATER_PUSH_UP_SPEED; this.velZ *= PhysicsConstants.LAVA_VELOCITY_MULTIPLIER_XZ;
                this.method_26317(fallSpeed, falling);
            } else {
                this.velX *= PhysicsConstants.LAVA_VELOCITY_MULTIPLIER_XZ; this.velY *= PhysicsConstants.LAVA_VELOCITY_MULTIPLIER_Y; this.velZ *= PhysicsConstants.LAVA_VELOCITY_MULTIPLIER_XZ;
            }

            this.velY -= fallSpeed / 4.0D;

            boolean moveNoCollisions = this.doesNotCollide(world, this.velX, this.velY + PhysicsConstants.SLIPPERINESS_MULTIPLIER - this.posY + startY, this.velZ);

            if(this.horizontalCollision && moveNoCollisions) {
                this.velY = PhysicsConstants.COLLISION_VERTICAL_VELOCITY;
            }
        } else if(this.fallFlying) {
            //No elytra controls
            if(this.velY > PhysicsConstants.ELYTRA_PITCH_MIN) {
                this.fallDistance = 1.0F;
            }

            float cYaw = MathHelper.cos(-this.yaw * PhysicsConstants.DEGREES_TO_RADIANS);
            float sYaw = MathHelper.sin(-this.yaw * PhysicsConstants.DEGREES_TO_RADIANS);
            float cPitch = MathHelper.cos(this.pitch * PhysicsConstants.DEGREES_TO_RADIANS);
            float sPitch = MathHelper.sin(this.pitch * PhysicsConstants.DEGREES_TO_RADIANS);
            double facingX = sYaw * cPitch;
            double facingY = -sPitch;
            double facingZ = cYaw * cPitch;

            double facingHM = Math.sqrt(facingX * facingX + facingZ * facingZ);
            double velHM = Math.sqrt(this.velX * this.velX + this.velZ * this.velZ);

            float cPitchSq = (float)((double)cPitch * (double)cPitch);
            this.velY += fallSpeed * (-1.0D + (double)cPitchSq * 0.75D);

            if(this.velY < 0.0D && facingHM > 0.0D) {
                double q = this.velY * -0.1D * (double)cPitchSq;
                this.velX += facingX * q / facingHM;
                this.velY += q;
                this.velZ += facingZ * q / facingHM;
            }

            if(this.pitch < 0.0F && facingHM > 0.0D) {
                double q = velHM * facingY * 0.04D;
                this.velX -= facingX * q / facingHM;
                this.velY += q * 3.2D;
                this.velZ -= facingZ * q / facingHM;
            }

            if(facingHM > 0.0D) {
                this.velX += (facingX / facingHM * velHM - this.velX) * 0.1D;
                this.velZ += (facingZ / facingHM * velHM - this.velZ) * 0.1D;
            }

            this.velX *= PhysicsConstants.ELYTRA_DRAG_X;
            this.velY *= PhysicsConstants.ELYTRA_DRAG_Y;
            this.velZ *= PhysicsConstants.ELYTRA_DRAG_Z;
            this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

            //Mojang why? WHYYYYYYYYYYYYYYY???
            if(this.onGround /*&& !world.isClient*/) {
                this.fallFlying = false;
            }
        } else {
            BlockPos pos = new BlockPos(this.posX, this.box.minY - PhysicsConstants.BLOCK_SLIPPERINESS_OFFSET, this.posZ);
            float slipperiness = world.getBlockState(pos).getBlock().getSlipperiness();
            float xzDrag = this.onGround ? slipperiness * PhysicsConstants.GROUND_DRAG_DEFAULT : PhysicsConstants.GROUND_DRAG_DEFAULT;
            double ajuVelY = this.applyMovementInput(world, slipperiness);

            if (this.levitation >= 0) {
                ajuVelY += (PhysicsConstants.LEVITATION_SPEED * (double)(this.levitation + 1) - ajuVelY) * PhysicsConstants.CLIMBING_SPEED_BASE;
                this.fallDistance = 0.0F;
            } else {
                ajuVelY -= fallSpeed;
            }

            this.velX *= xzDrag;
            this.velY = ajuVelY * PhysicsConstants.GROUND_DRAG_AIR;
            this.velZ *= xzDrag;
        }
    }

    public double applyMovementInput(WorldView world, float f) {
        this.updateVelocity(this.getMovementSpeed(f));
        this.applyClimbingSpeed(world);

        this.move(world, MovementType.SELF, this.velX, this.velY, this.velZ);

        if((this.horizontalCollision || this.jumping) && this.isClimbing(world)) {
            return PhysicsConstants.CLIMBING_SPEED_BASE;
        }

        return this.velY;
    }

    private float getMovementSpeed(float slipperiness) {
        if(this.onGround) {
            return this.movementSpeed * ((float)PhysicsConstants.MOVEMENT_SPEED_COEFFICIENT / (slipperiness * slipperiness * slipperiness));
        }

        return this.airStrafingSpeed;
    }

    private void applyClimbingSpeed(WorldView world) {
        if(this.isClimbing(world)) {
            this.fallDistance = 0.0f;
            this.velX = MathHelper.clamp(this.velX, -PhysicsConstants.CLIMBING_SPEED_MAX, PhysicsConstants.CLIMBING_SPEED_MAX);
            this.velY = Math.max(this.velY, -PhysicsConstants.CLIMBING_SPEED_MAX);
            this.velZ = MathHelper.clamp(this.velZ, -PhysicsConstants.CLIMBING_SPEED_MAX, PhysicsConstants.CLIMBING_SPEED_MAX);

            BlockState state = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ));

            if(this.velY < 0.0D && !state.isOf(Blocks.SCAFFOLDING) && this.input.sneaking) {
                this.velY = 0.0D;
            }
        }
    }

    public Iterable<VoxelShape> getBlockCollisions(WorldView world, Box box) {
        return () -> new AgentBlockCollisions(world, this, box);
    }

    public boolean isSpaceEmpty(WorldView world, Box box) {
        for(VoxelShape voxelShape : this.getBlockCollisions(world, box)) {
            if(!voxelShape.isEmpty()) {
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
        if(!this.sprinting) {
            boolean b = falling && Math.abs(this.velY - PhysicsConstants.VELOCITY_COLLISION_THRESHOLD) >= PhysicsConstants.VELOCITY_EPSILON && Math.abs(this.velY - fallSpeed / 16.0D) < PhysicsConstants.VELOCITY_EPSILON;
            this.velY = b ? -PhysicsConstants.VELOCITY_EPSILON : this.velY - fallSpeed / 16.0D;
        }
    }

    public void move(WorldView world, MovementType type, double movX, double movY, double movZ) {
        //if(type == MovementType.PISTON && (movement = this.adjustMovementForPiston(movement)).equals(Vec3d.ZERO)) {
        //    return;
        //}

        if(this.mulX * this.mulX + this.mulY * this.mulY + this.mulZ * this.mulZ > 0.0000001D) {
            movX *= this.mulX; movY *= this.mulY; movZ *= this.mulZ;
            this.mulX = 0; this.mulY = 0; this.mulZ = 0;
            this.velX = 0; this.velY = 0; this.velZ = 0;
        }


        Vec3d vec1 = this.adjustMovementForSneaking(world, type, new Vec3d(movX, movY, movZ));
        movX = vec1.x; movY = vec1.y; movZ = vec1.z;

        Vec3d vec2 = this.adjustMovementForCollisions(world, new Vec3d(movX, movY, movZ));
        double ajuX = vec2.x, ajuY = vec2.y, ajuZ = vec2.z;

        double magnitudeSq = ajuX * ajuX + ajuY * ajuY + ajuZ * ajuZ;

		if(magnitudeSq > 0.0000001D) {
            if(this.fallDistance != 0.0F && magnitudeSq >= 1.0D) {
                RaycastContext context = new AgentRaycastContext(this.getPos(), this.getPos().add(new Vec3d(ajuX, ajuY, ajuZ)),
                    RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, this);
                BlockHitResult result = world.raycast(context);

                if(result.getType() != HitResult.Type.MISS) {
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

        if(this.horizontalCollision) {
            if(xSimilar) this.velX = 0.0D;
            if(zSimilar) this.velZ = 0.0D;
        }

        Block block = landingState.getBlock();

        if(movY != ajuY) {
            if(block instanceof SlimeBlock && !this.input.sneaking) {
                if(this.velY < 0.0D) this.velY *= PhysicsConstants.BOUNCE_VELOCITY_INVERT;
            } else if(block instanceof BedBlock) {
                if(this.velY < 0.0D) this.velY *= PhysicsConstants.BOUNCE_VELOCITY_INVERT * PhysicsConstants.BOUNCE_VELOCITY_MULTIPLIER_BED;
            } else {
                this.velY = 0;
            }
        }

        if(this.onGround && !this.input.sneaking) {
            if(block instanceof MagmaBlock) {
                //damage the entity
            } else if(block instanceof SlimeBlock) {
                double d = Math.abs(this.velY);

                if(d < PhysicsConstants.SLIME_VELOCITY_CLAMP_MIN) {
                    this.velX *= PhysicsConstants.SLIME_VELOCITY_BOOST + d * PhysicsConstants.SLIME_HORIZONTAL_MULTIPLIER;
                    this.velZ *= PhysicsConstants.SLIME_VELOCITY_BOOST + d * PhysicsConstants.SLIME_HORIZONTAL_MULTIPLIER;
                }
            } else if(block instanceof TurtleEggBlock) {
                //eggs can break (1/100)
            }
        }

        this.checkBlockCollision(world);

        float i = this.getVelocityMultiplier(world);
        this.velX *= i; this.velZ *= i;

		/*
		if (this.world.method_29556(this.getBoundingBox().contract(0.001))
		.noneMatch(blockState -> blockState.isIn(BlockTags.FIRE) || blockState.isOf(Blocks.LAVA)) && this.fireTicks <= 0) {
			this.setFireTicks(-this.getBurningDuration());
		}*/
    }

    private boolean hasCollidedSoftly(double ajuX, double ajuY, double ajuZ) {
        float f = this.yaw * PhysicsConstants.DEGREES_TO_RADIANS;
        double d = MathHelper.sin(f);
        double e = MathHelper.cos(f);
        double g = (double)this.sidewaysSpeed * e - (double)this.forwardSpeed * d;
        double h = (double)this.forwardSpeed * e + (double)this.sidewaysSpeed * d;
        double i = MathHelper.square(g) + MathHelper.square(h);
        double j = MathHelper.square(ajuX) + MathHelper.square(ajuZ);

        if(i < PhysicsConstants.VELOCITY_SMALL_THRESHOLD_2 || j < PhysicsConstants.VELOCITY_SMALL_THRESHOLD_2) {
            return false;
        }

        double k = g * ajuX + h * ajuZ;
        double l = Math.acos(k / Math.sqrt(i * j));
        return l < PhysicsConstants.ELYTRA_PITCH_FACTOR;
    }

    public Vec3d adjustMovementForSneaking(WorldView world, MovementType type, Vec3d movement) {
        if(this.input.sneaking && (type == MovementType.SELF || type == MovementType.PLAYER)
            && (this.onGround || this.fallDistance < this.stepHeight
            && !this.isSpaceEmpty(world, this.box.offset(0.0, this.fallDistance - this.stepHeight, 0.0)))) {
            double d = movement.x;
            double e = movement.z;

            while(d != 0.0 && this.isSpaceEmpty(world, this.box.offset(d, -this.stepHeight, 0.0))) {
                if(d < PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT && d >= -PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT) { d = 0.0; continue; }
                if(d > 0.0) { d -= PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT; continue; }
                d += PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT;
            }

            while(e != 0.0 && this.isSpaceEmpty(world, this.box.offset(0.0, -this.stepHeight, e))) {
                if(e < PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT && e >= -PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT) { e = 0.0; continue; }
                if(e > 0.0) { e -= PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT; continue; }
                e += PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT;
            }

            while(d != 0.0 && e != 0.0 && this.isSpaceEmpty(world, this.box.offset(d, -this.stepHeight, e))) {
                d = d < 0.05 && d >= -0.05 ? 0.0 : (d > 0.0 ? (d -= 0.05) : (d += 0.05));
                if(e < PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT && e >= -PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT) { e = 0.0; continue; }
                if(e > 0.0) { e -= PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT; continue; }
                e += PhysicsConstants.SNEAK_EDGE_CHECK_INCREMENT;
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
        boolean bl5 = this.onGround || bl2 && movement.y < 0.0;

        if(this.stepHeight > 0.0f && bl5 && (bl || bl3)) {
            Vec3d vec3d2 = this.adjustMovementForCollisions(new Vec3d(movement.x, this.stepHeight, movement.z), box, world, list);
            Vec3d vec3d3 = this.adjustMovementForCollisions(new Vec3d(0.0, this.stepHeight, 0.0), box.stretch(movement.x, 0.0, movement.z), world, list);
            Vec3d vec3d4 = this.adjustMovementForCollisions(new Vec3d(movement.x, 0.0, movement.z), box.offset(vec3d3), world, list).add(vec3d3);

            if(vec3d3.y < (double)this.stepHeight && vec3d4.horizontalLengthSquared() > vec3d2.horizontalLengthSquared()) {
                vec3d2 = vec3d4;
            }

            if(vec3d2.horizontalLengthSquared() > vec3d.horizontalLengthSquared()) {
                return vec3d2.add(this.adjustMovementForCollisions(new Vec3d(0.0, -vec3d2.y + movement.y, 0.0), box.offset(vec3d2), world, list));
            }
        }

        return vec3d;
    }

    public Vec3d adjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, WorldView world, List<VoxelShape> entityCollisions) {
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(entityCollisions.size() + 1);

        if(!entityCollisions.isEmpty()) {
            builder.addAll(entityCollisions);
        }

        builder.addAll(this.getBlockCollisions(world, entityBoundingBox.stretch(movement)));
        return this.adjustMovementForCollisions(movement, entityBoundingBox, builder.build());
    }

    private Vec3d adjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, List<VoxelShape> collisions) {
        if(collisions.isEmpty()) {
            return movement;
        }

        double d = movement.x;
        double e = movement.y;
        double f = movement.z;
        boolean bl = Math.abs(d) < Math.abs(f);

        if(e != 0.0D) {
            e = VoxelShapes.calculateMaxOffset(Direction.Axis.Y, entityBoundingBox, collisions, e);
            if (e != 0.0D) entityBoundingBox = entityBoundingBox.offset(0.0D, e, 0.0D);
        }

        if(bl && f != 0.0D) {
            f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
            if(f != 0.0D) entityBoundingBox = entityBoundingBox.offset(0.0D, 0.0D, f);
        }

        if(d != 0.0D) {
            d = VoxelShapes.calculateMaxOffset(Direction.Axis.X, entityBoundingBox, collisions, d);
            if(!bl && d != 0.0D) entityBoundingBox = entityBoundingBox.offset(d, 0.0D, 0.0D);
        }

        if (!bl && f != 0.0D) {
            f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
        }

        return new Vec3d(d, e, f);
    }

    public boolean isClimbing(WorldView world) {
        BlockState state = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ));
        if(state.isIn(BlockTags.CLIMBABLE)) return true;
        if(state.getBlock() instanceof TrapdoorBlock && this.canEnterTrapdoor(world, state)) return true;
        return false;
    }

    private boolean canEnterTrapdoor(WorldView world, BlockState trapdoor) {
        if(!trapdoor.get(TrapdoorBlock.OPEN)) return false;

        BlockState ladder = world.getBlockState(new BlockPos(this.blockX, this.blockY - 1, this.blockZ));

        if(ladder.isOf(Blocks.LADDER) && ladder.get(LadderBlock.FACING) == trapdoor.get(TrapdoorBlock.FACING)) {
            return true;
        }

        return false;
    }

    void checkWaterState(WorldView world) {
        if(this.updateMovementInFluid(world, FluidTags.WATER, PhysicsConstants.FLUID_FLOW_SCALE)) {
            this.fallDistance = 0.0F;
            this.touchingWater = true;
        } else {
            this.touchingWater = false;
        }
    }

    public boolean updateMovementInFluid(WorldView world, TagKey<Fluid> tag, double d) {
        int n;
        Box box = this.box.contract(CollisionConstants.COLLISION_BOX_CONTRACT);
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

        for(int p = i; p < j; ++p) {
            for(int q = k; q < l; ++q) {
                for(int r = m; r < n; ++r) {
                    double f;
                    mutable.set(p, q, r);
                    FluidState fluidState = world.getFluidState(mutable);
                    if (!fluidState.isIn(tag) || !((f = (float)q + fluidState.getHeight(world, mutable)) >= box.minY)) continue;
                    bl2 = true;
                    e = Math.max(f - box.minY, e);

                    Vec3d vec3d2 = fluidState.getVelocity(world, mutable);

                    if(e < 0.4) {
                        vec3d2 = vec3d2.multiply(e);
                    }

                    vec3d = vec3d.add(vec3d2);
                    ++o;
                }
            }
        }

        if(vec3d.length() > 0.0) {
            if(o > 0) {
                vec3d = vec3d.multiply(1.0 / (double)o);
            }

            Vec3d vec3d3 = new Vec3d(this.velX, this.velY, this.velZ);
            vec3d = vec3d.multiply(d);

            if(Math.abs(vec3d3.x) < PhysicsConstants.VELOCITY_EPSILON && Math.abs(vec3d3.z) < PhysicsConstants.VELOCITY_EPSILON && vec3d.length() < PhysicsConstants.FLUID_FLOW_MIN) {
                vec3d = vec3d.normalize().multiply(PhysicsConstants.FLUID_FLOW_MIN);
            }

            this.velX += vec3d.x;
            this.velY += vec3d.y;
            this.velZ += vec3d.z;
        }

        this.fluidHeight.put(tag, e);
        return bl2;
    }

    public void fall(WorldView world, double heightDifference, BlockState landedState) {
        if(!this.touchingWater) {
            this.checkWaterState(world);
        }

        //add soulspeed movement boost

        if(this.onGround) {
            if(this.fallDistance > 0.0F) {
                if(landedState.getBlock() instanceof BedBlock) {
                    this.handleFallDamage(this.fallDistance * 0.6F, 1.0F);
                } else if(landedState.getBlock() instanceof FarmlandBlock) {
                    //grief
                } else if(landedState.getBlock() instanceof HayBlock) {
                    this.handleFallDamage(this.fallDistance, 0.2F);
                } else if(landedState.getBlock() instanceof SlimeBlock) {
                    this.handleFallDamage(this.fallDistance, this.input.sneaking ? 1.0F : 0.0F);
                } else if(landedState.getBlock() instanceof TurtleEggBlock) {
                    //eggs can break (1/3)
                    this.handleFallDamage(this.fallDistance, 1.0F);
                } else {
                    this.handleFallDamage(this.fallDistance, 1.0F);
                }
            }

            this.fallDistance = 0.0F;
        } else if(heightDifference < 0.0D) {
            this.fallDistance -= (float)heightDifference;
        }
    }

    public boolean handleFallDamage(float fallDistance, float damageMultiplier) {
        int i = this.computeFallDamage(fallDistance, damageMultiplier);

        if(i > 0) {
            //this.damage(DamageSource.FALL, i);
        	this.velX = 0;
        	this.velZ = 0;
            return true;
        }

        return false;
    }

    public int computeFallDamage(float fallDistance, float damageMultiplier) {
        float f = this.jumpBoost < 0 ? 0.0F : (float)(this.jumpBoost + 1);
        return MathHelper.ceil((fallDistance - 3.0f - f) * damageMultiplier);
    }

    public void checkBlockCollision(WorldView world) {
        BlockPos blockPos = new BlockPos(this.box.minX + CollisionConstants.COLLISION_BOX_PADDING, this.box.minY + CollisionConstants.COLLISION_BOX_PADDING, this.box.minZ + CollisionConstants.COLLISION_BOX_PADDING);
        BlockPos blockPos2 = new BlockPos(this.box.maxX - CollisionConstants.COLLISION_BOX_PADDING, this.box.maxY - CollisionConstants.COLLISION_BOX_PADDING, this.box.maxZ - CollisionConstants.COLLISION_BOX_PADDING);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        if(world.isRegionLoaded(blockPos, blockPos2)) {
            for(int i = blockPos.getX(); i <= blockPos2.getX(); ++i) {
                for(int j = blockPos.getY(); j <= blockPos2.getY(); ++j) {
                    for(int k = blockPos.getZ(); k <= blockPos2.getZ(); ++k) {
                        pos.set(i, j, k);
                        BlockState state = world.getBlockState(pos);

                        if(state.getBlock() instanceof AbstractFireBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof AbstractPressurePlateBlock) {
                            //change block state
                        } else if(state.getBlock() instanceof BubbleColumnBlock) {
                            BlockState surface = world.getBlockState(pos.up());
                            boolean drag = surface.contains(BubbleColumnBlock.DRAG) && surface.get(BubbleColumnBlock.DRAG);

                            if(surface.isAir()) {
                                this.velY = drag ? Math.max(-0.9D, this.velY - PhysicsConstants.BUBBLE_COLUMN_SURFACE_DRAG) : Math.min(1.8D, this.velY + PhysicsConstants.BUBBLE_COLUMN_UP_SPEED);
                            } else {
                                this.velY = drag ? Math.max(-0.3D, this.velY - PhysicsConstants.BUBBLE_COLUMN_DOWN_SPEED) : Math.min(0.7D, this.velY + 0.06D);
                                this.fallDistance = 0.0F;
                            }
                        } else if(state.getBlock() instanceof CactusBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof CampfireBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof CampfireBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof CauldronBlock) {
                            //extinguish the entity
                        } else if(state.getBlock() instanceof CobwebBlock) {
                            this.fallDistance = 0.0F;
                            this.mulX = PhysicsConstants.COBWEB_VELOCITY_MULTIPLIER; this.mulY = PhysicsConstants.COBWEB_FALL_SPEED_MULTIPLIER; this.mulZ = PhysicsConstants.COBWEB_VELOCITY_MULTIPLIER;
                        } else if(state.getBlock() instanceof EndPortalBlock) {
                            //fuck
                        } else if(state.getBlock() instanceof HoneyBlock) {
                            if(this.isSliding(pos)) {
                                if(this.velY < -PhysicsConstants.HONEY_SLIDE_SPEED) {
                                    double m = -PhysicsConstants.HONEY_SLIDE_DOWN_SPEED / this.velY;
                                    this.velX *= m; this.velY = -PhysicsConstants.HONEY_SLIDE_DOWN_SPEED; this.velZ *= m;
                                } else {
                                    this.velY = -PhysicsConstants.HONEY_SLIDE_DOWN_SPEED;
                                }

                                this.fallDistance = 0.0F;
                            }
                        } else if(state.getBlock() instanceof NetherPortalBlock) {
                            //eh?
                        } else if(state.getBlock() instanceof SweetBerryBushBlock) {
                            this.mulX = PhysicsConstants.SWEET_BERRY_MOVEMENT_MULTIPLIER; this.mulY = PhysicsConstants.SWEET_BERRY_VELOCITY_MULTIPLIER; this.mulZ = PhysicsConstants.SWEET_BERRY_MOVEMENT_MULTIPLIER;
                            //damage the entity
                        } else if(state.getBlock() instanceof TripwireBlock) {
                            //change block state
                        } else if(state.getBlock() instanceof WitherRoseBlock) {
                            //damage the entity
                        }
                    }
                }
            }
        }
    }

    private boolean isSliding(BlockPos pos) {
        if(this.onGround) return false;
        if(this.posY > (double)pos.getY() + 0.9375D - CollisionConstants.BOUNDING_BOX_EPSILON) return false;
        if(this.velY >= -PhysicsConstants.HONEY_SLIDE_DOWN_SPEED) return false;

        double d = Math.abs((double)pos.getX() + 0.5D - this.posX);
        double e = Math.abs((double)pos.getZ() + 0.5D - this.posZ);
        double f = 0.4375D + (double)(this.dimensions.width / 2.0F);
        return d + CollisionConstants.BOUNDING_BOX_EPSILON > f || e + CollisionConstants.BOUNDING_BOX_EPSILON > f;
    }

    public float getVelocityMultiplier(WorldView world) {
        if(this.fallFlying) return 1.0F;

        Block block = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ)).getBlock();
        float blockMult = block.getVelocityMultiplier();

        if(block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN) return blockMult;

        BlockPos pos = new BlockPos(this.blockX, MathHelper.floor(this.posY - PhysicsConstants.BLOCK_SLIPPERINESS_OFFSET), this.blockZ);
        return (double)blockMult == 1.0D ? world.getBlockState(pos).getBlock().getVelocityMultiplier() : blockMult;
    }

    public BlockPos getLandingPos(WorldView world) {
        BlockPos pos = new BlockPos(this.blockX, MathHelper.floor(this.posY - PhysicsConstants.LANDING_POSITION_OFFSET), this.blockZ);

        if(!world.getBlockState(pos).isAir()) {
            return pos;
        }

        BlockState state = world.getBlockState(pos.down());

        if(state.getBlock() instanceof FenceGateBlock || state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS)) {
            return pos.down();
        }

        return pos;
    }

    private boolean isWalking() {
        return this.isSubmergedInWater ? this.input.hasForwardMovement() : (double)this.input.movementForward >= PhysicsConstants.WALKING_THRESHOLD;
    }

    public Box calculateBoundsForPose(EntityPose pose) {
        EntityDimensions size = POSE_DIMENSIONS.getOrDefault(pose, STANDING_DIMENSIONS);
        float f = size.width / 2.0F;
        Vec3d min = new Vec3d(this.posX - (double)f, this.posY, this.posZ - (double)f);
        Vec3d max = new Vec3d(this.posX + (double)f, this.posY + (double)size.height, this.posZ + (double)f);
        return new Box(min, max);
    }

    public boolean wouldPoseNotCollide(WorldView world, EntityPose pose) {
        return this.isSpaceEmpty(world, this.calculateBoundsForPose(pose).contract(CollisionConstants.BOUNDING_BOX_EPSILON));
    }

    private void pushOutOfBlocks(WorldView world, double x, double d) {
        Direction[] directions = new Direction[] { Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH };
        BlockPos blockPos = new BlockPos(x, this.posY, d);
        if (!this.wouldCollideAt(world, blockPos)) {
            return;
        }
        double e = x - (double)blockPos.getX();
        double f = d - (double)blockPos.getZ();
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

        if(direction != null) {
            if(direction.getAxis() == Direction.Axis.X) {
                this.velX = PhysicsConstants.PUSH_VELOCITY * (double)direction.getOffsetX();
            } else {
                this.velZ = PhysicsConstants.PUSH_VELOCITY * (double)direction.getOffsetZ();
            }
        }
    }

    public boolean canCollide(WorldView world, Box box) {
        AgentBlockCollisions collisions = new AgentBlockCollisions(world, this, box, true);

        do {
            if (!collisions.hasNext()) {
                this.scannedBlocks += collisions.scannedBlocks;
                return false;
            }
        } while (collisions.next().isEmpty());

        this.scannedBlocks += collisions.scannedBlocks;
        return true;
    }

    private boolean wouldCollideAt(WorldView world, BlockPos pos) {
        Box box2 = new Box(pos.getX(), this.box.minY, pos.getZ(),
            (double)pos.getX() + CollisionConstants.COLLISION_BOX_FULL_BLOCK, this.box.maxY, (double)pos.getZ() + CollisionConstants.COLLISION_BOX_FULL_BLOCK).contract(CollisionConstants.BOUNDING_BOX_EPSILON);
        return this.canCollide(world, box2);
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
        this.movementSpeed = PhysicsConstants.MOVEMENT_SPEED_BASE;

        if(sprinting) {
            this.movementSpeed *= (1.0D + PhysicsConstants.SPRINT_SPEED_MULTIPLIER);
        }

        if(this.speed >= 0) {
            double amplifier = PhysicsConstants.SPEED_EFFECT_AMPLIFIER * (double)(this.speed + 1);
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
            if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                j = 0;
                for (int k = 0; k < list.size(); ++k) {
                    if (list.get(k).hasVehicle()) continue;
                    ++j;
                }
                if (j > i - 1) {
                    this.damage(DamageSource.CRAMMING, 6.0f);
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

        if (f >= (double)0.01F) {
            f = MathHelper.sqrt(f);
            d /= f;
            e /= f;

            double g = 1.0 / f;
            if(g > 1.0) g = 1.0;

            d *= g;
            e *= g;
            d *= 0.05F;
            e *= 0.05F;
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

        if(this.posX != player.getX() || this.posY != player.getY() || this.posZ != player.getZ()) {
            values.add(String.format("Position mismatch (%s, %s, %s) vs (%s, %s, %s)",
                player.getPos().x == this.posX ? "x" : player.getPos().x,
                player.getPos().y == this.posY ? "y" : player.getPos().y,
                player.getPos().z == this.posZ ? "z" : player.getPos().z,
                player.getPos().x == this.posX ? "x" : this.posX,
                player.getPos().y == this.posY ? "y" : this.posY,
                player.getPos().z == this.posZ ? "z" : this.posZ));
        }

        if(this.velX != player.getVelocity().x || this.velY != player.getVelocity().y || this.velZ != player.getVelocity().z) {
            values.add(String.format("Velocity mismatch (%s, %s, %s) vs (%s, %s, %s)",
                player.getVelocity().x,
                player.getVelocity().y,
                player.getVelocity().z,
                player.getVelocity().x == this.velX ? "x" : this.velX,
                player.getVelocity().y == this.velY ? "y" : this.velY,
                player.getVelocity().z == this.velZ ? "z" : this.velZ));
        }

        if(this.mulX != ((AccessorEntity)player).getMovementMultiplier().x
            || this.mulY != ((AccessorEntity)player).getMovementMultiplier().y
            || this.mulZ != ((AccessorEntity)player).getMovementMultiplier().z) {
            values.add(String.format("Movement Multiplier mismatch (%s, %s, %s) vs (%s, %s, %s)",
                ((AccessorEntity)player).getMovementMultiplier().x,
                ((AccessorEntity)player).getMovementMultiplier().y,
                ((AccessorEntity)player).getMovementMultiplier().z,
                ((AccessorEntity)player).getMovementMultiplier().x == this.mulX ? "x" : this.mulX,
                ((AccessorEntity)player).getMovementMultiplier().y == this.mulY ? "y" : this.mulY,
                ((AccessorEntity)player).getMovementMultiplier().z == this.mulZ ? "z" : this.mulZ));
        }

        if(this.forwardSpeed != player.forwardSpeed || this.sidewaysSpeed != player.sidewaysSpeed || this.upwardSpeed != player.upwardSpeed) {
            values.add(String.format("Input Speed mismatch (%s, %s, %s) vs (%s, %s, %s)",
                player.forwardSpeed == this.forwardSpeed ? "f" : player.forwardSpeed,
                player.upwardSpeed == this.upwardSpeed ? "u" : player.upwardSpeed,
                player.sidewaysSpeed == this.sidewaysSpeed ? "s" : player.sidewaysSpeed,
                player.forwardSpeed == this.forwardSpeed ? "f" : this.forwardSpeed,
                player.upwardSpeed == this.upwardSpeed ? "u" : this.upwardSpeed,
                player.sidewaysSpeed == this.sidewaysSpeed ? "s" : this.sidewaysSpeed));
        }

        if(this.movementSpeed != player.getMovementSpeed()) {
            values.add(String.format("Movement Speed mismatch %f vs %f", player.getMovementSpeed(), this.movementSpeed));
        }

        if(this.pose != player.getPose()) {
            values.add(String.format("Pose mismatch %s vs %s", player.getPose(), this.pose));
        }

        if(this.isSubmergedInWater != player.isSubmergedInWater()) {
            values.add(String.format("Sprinting mismatch %s vs %s", player.isSprinting(), this.sprinting));
        }

        if(this.touchingWater != player.isTouchingWater()) {
            values.add(String.format("Touching water mismatch %s vs %s", player.isTouchingWater(), this.touchingWater));
        }

        if(this.isSubmergedInWater != player.isSubmergedInWater()) {
            values.add(String.format("Submerged in water mismatch %s vs %s", player.isSubmergedInWater(), this.isSubmergedInWater));
        }

	    if(this.input.sneaking != player.input.sneaking) {
		    values.add(String.format("Sneaking mismatch %s vs %s", player.input.sneaking, this.input.sneaking));
	    }

        if(this.swimming != player.isSwimming()) {
            values.add(String.format("Swimming mismatch %s vs %s", player.isSwimming(), this.swimming));
        }

        if(this.standingEyeHeight != player.getStandingEyeHeight()) {
            values.add(String.format("Eye height mismatch %s vs %s", player.getStandingEyeHeight(), this.standingEyeHeight));
        }

        if(this.fallDistance != player.fallDistance) {
            values.add(String.format("Fall distance mismatch %s vs %s", player.fallDistance, this.fallDistance));
        }

        if(this.horizontalCollision != player.horizontalCollision) {
            values.add(String.format("Horizontal Collision mismatch %s vs %s", player.horizontalCollision, this.horizontalCollision));
        }

        if(this.verticalCollision != player.verticalCollision) {
            values.add(String.format("Vertical Collision mismatch %s vs %s", player.verticalCollision, this.verticalCollision));
        }

        if(this.collidedSoftly != player.collidedSoftly) {
            values.add(String.format("Soft Collision mismatch %s vs %s", player.collidedSoftly, this.collidedSoftly));
        }

        if(this.jumping != ((AccessorLivingEntity)player).getJumping()) {
            values.add(String.format("Jumping mismatch %s vs %s", ((AccessorLivingEntity)player).getJumping(), this.jumping));
        }

        if(this.jumpingCooldown != ((AccessorLivingEntity)player).getJumpingCooldown()) {
            values.add(String.format("Jumping Cooldown mismatch %s vs %s", ((AccessorLivingEntity)player).getJumpingCooldown(), this.jumpingCooldown));
        }

        if(this.airStrafingSpeed != player.airStrafingSpeed) {
            values.add(String.format("Air Strafe Speed mismatch %s vs %s", player.airStrafingSpeed, this.airStrafingSpeed));
        }

        if(this.firstUpdate != ((AccessorEntity)player).getFirstUpdate()) {
            values.add(String.format("First Update mismatch %s vs %s", ((AccessorEntity)player).getFirstUpdate(), this.firstUpdate));
        }

        if(!this.submergedFluids.equals(((AccessorEntity)player).getSubmergedFluidTag())) {
            values.add(String.format("Submerged Fluids mismatch %s vs %s", ((AccessorEntity)player).getSubmergedFluidTag(), this.submergedFluids));
        }

        if(!values.isEmpty()) {
            System.out.println("Tick " + player.age + " ===========================================");
            values.forEach(System.out::println);
        }
    }

    public static Agent of(ClientPlayerEntity player) {
        Agent agent = new Agent();
        agent.keyForward = MinecraftClient.getInstance().options.forwardKey.isPressed();
        agent.keyBack = MinecraftClient.getInstance().options.backKey.isPressed();
        agent.keyLeft = MinecraftClient.getInstance().options.leftKey.isPressed();
        agent.keyRight = MinecraftClient.getInstance().options.rightKey.isPressed();
        agent.keyJump = MinecraftClient.getInstance().options.jumpKey.isPressed();
        agent.keySneak = MinecraftClient.getInstance().options.sneakKey.isPressed();
        agent.keySprint = MinecraftClient.getInstance().options.sprintKey.isPressed();

        agent.input.movementSideways = player.input.movementSideways;
        agent.input.movementForward = player.input.movementForward;
        agent.input.pressingForward = player.input.pressingForward;
        agent.input.pressingBack = player.input.pressingBack;
        agent.input.pressingLeft = player.input.pressingLeft;
        agent.input.pressingRight = player.input.pressingRight;
        agent.input.jumping = player.input.jumping;
        agent.input.sneaking = player.input.sneaking;

        agent.pose = player.getPose();
        agent.inSneakingPose = player.isInSneakingPose();
        agent.usingItem = player.isUsingItem();
        agent.sidewaysSpeed = player.sidewaysSpeed;
        agent.upwardSpeed = player.upwardSpeed;
        agent.forwardSpeed = player.forwardSpeed;
        agent.yaw = player.getYaw();
        agent.pitch = player.getPitch();
        agent.posX = player.getX();
        agent.posY = player.getY();
        agent.posZ = player.getZ();
        agent.blockX = player.getBlockPos().getX();
        agent.blockY = player.getBlockPos().getY();
        agent.blockZ = player.getBlockPos().getZ();
        agent.velX = player.getVelocity().x;
        agent.velY = player.getVelocity().y;
        agent.velZ = player.getVelocity().z;
        agent.mulX = ((AccessorEntity)player).getMovementMultiplier().x;
        agent.mulY = ((AccessorEntity)player).getMovementMultiplier().y;
        agent.mulZ = ((AccessorEntity)player).getMovementMultiplier().z;
        agent.fluidHeight.put(FluidTags.WATER, player.getFluidHeight(FluidTags.WATER));
        agent.fluidHeight.put(FluidTags.LAVA, player.getFluidHeight(FluidTags.LAVA));
        agent.submergedFluids.addAll(((AccessorEntity)player).getSubmergedFluidTag());
        agent.firstUpdate = ((AccessorEntity)player).getFirstUpdate();
        agent.box = player.getBoundingBox();
        agent.dimensions = player.getDimensions(player.getPose());
        agent.standingEyeHeight = player.getStandingEyeHeight();
        agent.onGround = player.isOnGround();
        agent.sleeping = player.isSleeping();
        agent.sneaking = player.isSneaky();
        agent.sprinting = player.isSprinting();
        agent.swimming = player.isSwimming();
        agent.fallFlying = player.isFallFlying();
        agent.stepHeight = player.stepHeight;
        agent.fallDistance = player.fallDistance;
        agent.touchingWater = player.isTouchingWater();
        agent.isSubmergedInWater = player.isSubmergedInWater();
        agent.horizontalCollision = player.horizontalCollision;
        agent.verticalCollision = player.verticalCollision;
        agent.collidedSoftly = player.collidedSoftly;
        agent.jumping = ((AccessorLivingEntity)player).getJumping();
        agent.speed = player.hasStatusEffect(StatusEffects.SPEED) ? player.getStatusEffect(StatusEffects.SPEED).getAmplifier() : -1;
        agent.blindness = player.hasStatusEffect(StatusEffects.BLINDNESS) ? player.getStatusEffect(StatusEffects.BLINDNESS).getAmplifier() : -1;
        agent.jumpBoost = player.hasStatusEffect(StatusEffects.JUMP_BOOST) ? player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() : -1;
        agent.slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING) ? player.getStatusEffect(StatusEffects.SLOW_FALLING).getAmplifier() : -1;
        agent.dolphinsGrace = player.hasStatusEffect(StatusEffects.DOLPHINS_GRACE) ? player.getStatusEffect(StatusEffects.DOLPHINS_GRACE).getAmplifier() : -1;
        agent.levitation = player.hasStatusEffect(StatusEffects.LEVITATION) ? player.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() : -1;
        agent.movementSpeed = player.getMovementSpeed();
        agent.airStrafingSpeed = player.airStrafingSpeed;
        agent.jumpingCooldown = ((AccessorLivingEntity)player).getJumpingCooldown();

        //TODO: frame.ticksToNextAutojump
        return agent;
    }

    public static Agent of(Agent other, boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint, float pitch, float yaw) {
        Agent agent = new Agent();
        agent.keyForward = forward;
        agent.keyBack = back;
        agent.keyLeft = left;
        agent.keyRight = right;
        agent.keyJump = jump;
        agent.keySneak = sneak;
        agent.keySprint = sprint;

        agent.input.movementSideways = other.input.movementSideways;
        agent.input.movementForward = other.input.movementForward;
        agent.input.pressingForward = other.input.pressingForward;
        agent.input.pressingBack = other.input.pressingBack;
        agent.input.pressingLeft = other.input.pressingLeft;
        agent.input.pressingRight = other.input.pressingRight;
        agent.input.jumping = other.input.jumping;
        agent.input.sneaking = other.input.sneaking;

        agent.pose = other.pose;
        agent.inSneakingPose = other.inSneakingPose;
        agent.usingItem = other.usingItem;
        agent.sidewaysSpeed = other.sidewaysSpeed;
        agent.upwardSpeed = other.upwardSpeed;
        agent.forwardSpeed = other.forwardSpeed;
        agent.yaw = yaw;
        agent.pitch = pitch;
        agent.posX = other.posX;
        agent.posY = other.posY;
        agent.posZ = other.posZ;
        agent.blockX = other.blockX;
        agent.blockY = other.blockY;
        agent.blockZ = other.blockZ;
        agent.velX = other.velX;
        agent.velY = other.velY;
        agent.velZ = other.velZ;
        agent.mulX = other.mulX;
        agent.mulY = other.mulY;
        agent.mulZ = other.mulZ;
        agent.fluidHeight.put(FluidTags.WATER, other.getFluidHeight(FluidTags.WATER));
        agent.fluidHeight.put(FluidTags.LAVA, other.getFluidHeight(FluidTags.LAVA));
        agent.submergedFluids.addAll(other.submergedFluids);
        agent.firstUpdate = other.firstUpdate;
        agent.dimensions = other.dimensions;
        agent.box = other.box;
        agent.standingEyeHeight = other.standingEyeHeight;
        agent.onGround = other.onGround;
        agent.sleeping = other.sleeping;
        agent.sneaking = other.sneaking;
        agent.sprinting = other.sprinting;
        agent.swimming = other.swimming;
        agent.fallFlying = other.fallFlying;
        agent.stepHeight = other.stepHeight;
        agent.fallDistance = other.fallDistance;
        agent.touchingWater = other.touchingWater;
        agent.isSubmergedInWater = other.isSubmergedInWater;
        agent.horizontalCollision = other.horizontalCollision;
        agent.verticalCollision = other.verticalCollision;
        agent.collidedSoftly = other.collidedSoftly;
        agent.jumping = other.jumping;
        agent.speed = other.speed;
        agent.blindness = other.blindness;
        agent.jumpBoost = other.jumpBoost;
        agent.slowFalling = other.slowFalling;
        agent.dolphinsGrace = other.dolphinsGrace;
        agent.levitation = other.levitation;
        agent.depthStrider = other.depthStrider;
        agent.movementSpeed = other.movementSpeed;
        agent.airStrafingSpeed = other.airStrafingSpeed;
        agent.jumpingCooldown = other.jumpingCooldown;
        //TODO: frame.ticksToNextAutojump
        return agent;
    }

    public static Agent of(Agent agent, PathInput input) {
        return of(agent, input.forward(), input.back(), input.left(), input.right(), input.jump(), input.sneak(), input.sprint(), input.pitch(), input.yaw());
    }

}
