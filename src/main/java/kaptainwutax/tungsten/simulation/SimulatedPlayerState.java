package kaptainwutax.tungsten.simulation;

import net.minecraft.registry.tag.FluidTags;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.WorldView;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.CollisionEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.function.Consumer;

public final class SimulatedPlayerState {
    private final Input input;
    private final int ticksLeftToDoubleTapSprint;
    private final PlayerAbilities abilities;
    private final int ticksToNextAutoJump;
    private final boolean noClip;
    private final Vec3d pos;
    private final Vec3d velocity;
    private final BlockPos blockPos;
    private final EntityDimensions dimensions;
    private final Box boundingBox;
    private final boolean usingItem;
    private final Entity vehicle;
    private final DataTracker dataTracker;
    private final HungerManager hungerManager;
    private final Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects;
    private final AttributeContainer attributes;
    private final GameMode gameMode;
    private final boolean touchingWater;
    private final boolean horizontalCollision;
    private final boolean verticalCollision;
    private final boolean collidedSoftly;
    private final boolean isSubmergedInWater;
    private final int abilityResyncCountdown;
    private final boolean onGround;
    private final float yaw;
    private final float pitch;
    private final boolean velocityDirty;
    private final Optional<BlockPos> climbingPos;
    private final boolean falling;
    private final Set<TagKey<Fluid>> submergedFluidTag;
    private final int underwaterVisibilityTicks;
    private final boolean isCamera;
    private final int field_3938;
    private final float mountJumpStrength;
    private final float lastStrideDistance;
    private final double fallDistance;
    private final boolean handSwinging;
    private final int handSwingTicks;
    private final float handSwingProgress;
    private final float headYaw;
    private final float movementSpeed;
    private final float strideDistance;
    private final int jumpingCooldown;
    private final boolean isLogicalSideForUpdatingMovement;
    private final float forwardSpeed;
    private final float sidewaysSpeed;
    private final float upwardSpeed;
    private final boolean jumping;
    private final boolean firstUpdate;
    private final Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    private final List<Entity.QueuedCollisionCheck> currentlyCheckedCollisions;
    private final List<List<Entity.QueuedCollisionCheck>> queuedCollisionChecks;
    private final Entity.RemovalReason removalReason;
    private final int fireTicks;
    private final EntityCollisionHandler.Impl collisionHandler;
    private final LongSet collidedBlockPositions;
    private final boolean groundCollision;
    private final float distanceTraveled;
    private final float speed;
    private final float nextStepSoundDistance;

    private SimulatedPlayerState(Input input,
                                int ticksLeftToDoubleTapSprint,
                                PlayerAbilities abilities,
                                int ticksToNextAutoJump,
                                boolean noClip,
                                Vec3d pos,
                                Vec3d velocity,
                                BlockPos blockPos,
                                EntityDimensions dimensions,
                                Box boundingBox,
                                boolean usingItem,
                                Entity vehicle,
                                DataTracker dataTracker,
                                HungerManager hungerManager,
                                Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects,
                                AttributeContainer attributes,
                                GameMode gameMode,
                                boolean touchingWater,
                                boolean horizontalCollision,
                                boolean verticalCollision,
                                boolean collidedSoftly,
                                boolean isSubmergedInWater,
                                int abilityResyncCountdown,
                                boolean onGround,
                                float yaw,
                                float pitch,
                                boolean velocityDirty,
                                Optional<BlockPos> climbingPos,
                                boolean falling,
                                Set<TagKey<Fluid>> submergedFluidTag,
                                int underwaterVisibilityTicks,
                                boolean isCamera,
                                int field_3938,
                                float mountJumpStrength,
                                float lastStrideDistance,
                                double fallDistance,
                                boolean handSwinging,
                                int handSwingTicks,
                                float handSwingProgress,
                                float headYaw,
                                float movementSpeed,
                                float strideDistance,
                                int jumpingCooldown,
                                boolean isLogicalSideForUpdatingMovement,
                                float forwardSpeed,
                                float sidewaysSpeed,
                                float upwardSpeed,
                                boolean jumping,
                                boolean firstUpdate,
                                Object2DoubleMap<TagKey<Fluid>> fluidHeight,
                                List<Entity.QueuedCollisionCheck> currentlyCheckedCollisions,
                                List<List<Entity.QueuedCollisionCheck>> queuedCollisionChecks,
                                Entity.RemovalReason removalReason,
                                int fireTicks,
                                EntityCollisionHandler.Impl collisionHandler,
                                LongSet collidedBlockPositions,
                                boolean groundCollision,
                                float distanceTraveled,
                                float speed,
                                float nextStepSoundDistance) {
        this.input = input;
        this.ticksLeftToDoubleTapSprint = ticksLeftToDoubleTapSprint;
        this.abilities = copyPlayerAbilities(abilities);
        this.ticksToNextAutoJump = ticksToNextAutoJump;
        this.noClip = noClip;
        this.pos = pos;
        this.velocity = velocity;
        this.blockPos = blockPos;
        this.dimensions = dimensions;
        this.boundingBox = boundingBox;
        this.usingItem = usingItem;
        this.vehicle = vehicle;
        this.dataTracker = copyDataTracker(dataTracker);
        this.hungerManager = copyHungerManager(hungerManager);
        this.activeStatusEffects = copyActiveStatusEffects(activeStatusEffects);
        this.attributes = copyAttributes(attributes);
        this.gameMode = gameMode;
        this.touchingWater = touchingWater;
        this.horizontalCollision = horizontalCollision;
        this.verticalCollision = verticalCollision;
        this.collidedSoftly = collidedSoftly;
        this.isSubmergedInWater = isSubmergedInWater;
        this.abilityResyncCountdown = abilityResyncCountdown;
        this.onGround = onGround;
        this.yaw = yaw;
        this.pitch = pitch;
        this.velocityDirty = velocityDirty;
        this.climbingPos = climbingPos;
        this.falling = falling;
        this.submergedFluidTag = new HashSet<>(submergedFluidTag);
        this.underwaterVisibilityTicks = underwaterVisibilityTicks;
        this.isCamera = isCamera;
        this.field_3938 = field_3938;
        this.mountJumpStrength = mountJumpStrength;
        this.lastStrideDistance = lastStrideDistance;
        this.fallDistance = fallDistance;
        this.handSwinging = handSwinging;
        this.handSwingTicks = handSwingTicks;
        this.handSwingProgress = handSwingProgress;
        this.headYaw = headYaw;
        this.movementSpeed = movementSpeed;
        this.strideDistance = strideDistance;
        this.jumpingCooldown = jumpingCooldown;
        this.isLogicalSideForUpdatingMovement = isLogicalSideForUpdatingMovement;
        this.forwardSpeed = forwardSpeed;
        this.sidewaysSpeed = sidewaysSpeed;
        this.upwardSpeed = upwardSpeed;
        this.jumping = jumping;
        this.firstUpdate = firstUpdate;
        this.fluidHeight = new Object2DoubleArrayMap<>(fluidHeight);
        this.currentlyCheckedCollisions = new ObjectArrayList<>(currentlyCheckedCollisions);
        this.queuedCollisionChecks = copyQueuedCollisionChecks(queuedCollisionChecks);
        this.removalReason = removalReason;
        this.fireTicks = fireTicks;
        this.collisionHandler = copyCollisionHandler(collisionHandler);
        this.collidedBlockPositions = new LongOpenHashSet(collidedBlockPositions);
        this.groundCollision = groundCollision;
        this.distanceTraveled = distanceTraveled;
        this.speed = speed;
        this.nextStepSoundDistance = nextStepSoundDistance;
    }

    public SimulatedPlayerState(SimulatedPlayerState playerState) {
        this(
                playerState.input,
                playerState.ticksLeftToDoubleTapSprint,
                playerState.abilities,
                playerState.ticksToNextAutoJump,
                playerState.noClip,
                playerState.pos,
                playerState.velocity,
                playerState.blockPos,
                playerState.dimensions,
                playerState.boundingBox,
                playerState.usingItem,
                playerState.vehicle,
                playerState.dataTracker,
                playerState.hungerManager,
                playerState.activeStatusEffects,
                playerState.attributes,
                playerState.gameMode,
                playerState.touchingWater,
                playerState.horizontalCollision,
                playerState.verticalCollision,
                playerState.collidedSoftly,
                playerState.isSubmergedInWater,
                playerState.abilityResyncCountdown,
                playerState.onGround,
                playerState.yaw,
                playerState.pitch,
                playerState.velocityDirty,
                playerState.climbingPos,
                playerState.falling,
                playerState.submergedFluidTag,
                playerState.underwaterVisibilityTicks,
                playerState.isCamera,
                playerState.field_3938,
                playerState.mountJumpStrength,
                playerState.lastStrideDistance,
                playerState.fallDistance,
                playerState.handSwinging,
                playerState.handSwingTicks,
                playerState.handSwingProgress,
                playerState.headYaw,
                playerState.movementSpeed,
                playerState.strideDistance,
                playerState.jumpingCooldown,
                playerState.isLogicalSideForUpdatingMovement,
                playerState.forwardSpeed,
                playerState.sidewaysSpeed,
                playerState.upwardSpeed,
                playerState.jumping,
                playerState.firstUpdate,
                playerState.fluidHeight,
                playerState.currentlyCheckedCollisions,
                playerState.queuedCollisionChecks,
                playerState.removalReason,
                playerState.fireTicks,
                playerState.collisionHandler,
                playerState.collidedBlockPositions,
                playerState.groundCollision,
                playerState.distanceTraveled,
                playerState.speed,
                playerState.nextStepSoundDistance
        );
    }

    public SimulatedPlayerState(ClientPlayerEntity player, SimulatedInput input) {
        this(
                input,
                player.ticksLeftToDoubleTapSprint,
                player.abilities,
                player.ticksToNextAutoJump,
                player.noClip,
                player.pos,
                player.velocity,
                player.blockPos,
                player.dimensions,
                player.boundingBox,
                player.usingItem,
                player.vehicle,
                player.dataTracker,
                player.hungerManager,
                player.activeStatusEffects,
                player.attributes,
                player.getGameMode(),
                player.isTouchingWater(),
                player.horizontalCollision,
                player.verticalCollision,
                player.collidedSoftly,
                player.isSubmergedInWater(),
                player.abilityResyncCountdown,
                player.onGround,
                input.yaw,
                input.pitch,
                player.velocityDirty,
                player.climbingPos,
                player.falling,
                player.submergedFluidTag,
                player.underwaterVisibilityTicks,
                player.isCamera(),
                player.field_3938,
                player.mountJumpStrength,
                player.lastStrideDistance,
                player.fallDistance,
                player.handSwinging,
                player.handSwingTicks,
                player.handSwingProgress,
                player.headYaw,
                player.getMovementSpeed(),
                player.strideDistance,
                player.jumpingCooldown,
                player.isLogicalSideForUpdatingMovement(),
                player.forwardSpeed,
                player.sidewaysSpeed,
                player.upwardSpeed,
                player.jumping,
                player.firstUpdate,
                player.fluidHeight,
                player.currentlyCheckedCollisions,
                player.queuedCollisionChecks,
                player.removalReason,
                player.fireTicks,
                player.collisionHandler,
                player.collidedBlockPositions,
                player.groundCollision,
                player.distanceTraveled,
                player.speed,
                player.nextStepSoundDistance
        );
    }

    public void applyTo(ClientPlayerEntity player) {
        player.input = input;
        player.ticksLeftToDoubleTapSprint = ticksLeftToDoubleTapSprint;
        player.abilities = new PlayerAbilities();
        player.abilities.invulnerable = abilities.invulnerable;
        player.abilities.flying = abilities.flying;
        player.abilities.allowFlying = abilities.allowFlying;
        player.abilities.creativeMode = abilities.creativeMode;
        player.abilities.allowModifyWorld = abilities.allowModifyWorld;
        player.abilities.setFlySpeed(abilities.getFlySpeed());
        player.abilities.setWalkSpeed(abilities.getWalkSpeed());
        player.ticksToNextAutoJump = ticksToNextAutoJump;
        player.noClip = noClip;
        player.pos = pos;
        player.velocity = velocity;
        player.blockPos = blockPos;
        player.dimensions = dimensions;
        player.boundingBox = boundingBox;
        player.usingItem = usingItem;
        player.vehicle = vehicle; // This should be good as long as it isn't updated // NVM it's being updated :/
        player.dataTracker = dataTracker;
        player.hungerManager = hungerManager;
        player.activeStatusEffects = activeStatusEffects;
        player.attributes = attributes;
        // player.gameMode = gameMode; // Not available in ClientPlayerEntity
        player.touchingWater = touchingWater;
        player.horizontalCollision = horizontalCollision;
        player.verticalCollision = verticalCollision;
        player.collidedSoftly = collidedSoftly;
        player.isSubmergedInWater = isSubmergedInWater;
        player.abilityResyncCountdown = abilityResyncCountdown;
        player.onGround = onGround;
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.velocityDirty = velocityDirty;
        player.climbingPos = climbingPos;
        player.falling = falling;
        player.submergedFluidTag = submergedFluidTag;
        player.underwaterVisibilityTicks = underwaterVisibilityTicks;
        // player.isCamera = isCamera; // Not available in ClientPlayerEntity
        player.field_3938 = field_3938;
        player.mountJumpStrength = mountJumpStrength;
        player.lastStrideDistance = lastStrideDistance;
        player.fallDistance = (float) fallDistance;
        player.handSwinging = handSwinging;
        player.handSwingTicks = handSwingTicks;
        player.handSwingProgress = handSwingProgress;
        player.headYaw = headYaw;
        player.movementSpeed = movementSpeed;
        player.strideDistance = strideDistance;
        player.jumpingCooldown = jumpingCooldown;
        // player.isLogicalSideForUpdatingMovement = isLogicalSideForUpdatingMovement; // Not available in ClientPlayerEntity
        player.forwardSpeed = forwardSpeed;
        player.sidewaysSpeed = sidewaysSpeed;
        player.upwardSpeed = upwardSpeed;
        player.jumping = jumping;
        player.firstUpdate = firstUpdate;
        player.fluidHeight = fluidHeight;
        player.currentlyCheckedCollisions = currentlyCheckedCollisions;
        player.queuedCollisionChecks = queuedCollisionChecks;
        player.removalReason = removalReason;
        player.fireTicks = fireTicks;
        player.collisionHandler = collisionHandler;
        player.collidedBlockPositions = collidedBlockPositions;
        player.groundCollision = groundCollision;
        player.distanceTraveled = distanceTraveled;
        player.speed = speed;
        player.nextStepSoundDistance = nextStepSoundDistance;
    }

    public Input getInput() {
        return input;
    }

    public Vec3d getPos() {
        return pos;
    }

    public Vec3d getVelocity() {
        return velocity;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isTouchingWater() {
        return touchingWater;
    }

    public boolean isSubmergedInWater() {
        return isSubmergedInWater;
    }

    public double getFallDistance() {
        return fallDistance;
    }

    public boolean isHorizontalCollision() {
        return horizontalCollision;
    }
    
    public boolean isVerticalCollision() {
        return verticalCollision;
    }

    public boolean hasCollidedSoftly() {
        return collidedSoftly;
    }
    
    public boolean isJumping() {
        return jumping;
    }
    
    public int getJumpingCooldown() {
        return jumpingCooldown;
    }
    
    public float getMovementSpeed() {
        return movementSpeed;
    }
    
    public float getForwardSpeed() {
        return forwardSpeed;
    }
    
    public float getSidewaysSpeed() {
        return sidewaysSpeed;
    }
    
    public float getUpwardSpeed() {
        return upwardSpeed;
    }
    
    public Object2DoubleMap<TagKey<Fluid>> getFluidHeight() {
        return fluidHeight;
    }
    
    public Set<TagKey<Fluid>> getSubmergedFluidTag() {
        return submergedFluidTag;
    }
    
    public boolean isFirstUpdate() {
        return firstUpdate;
    }
    
    public float getStandingEyeHeight() {
        return dimensions.eyeHeight();
    }
    
    public Box getBoundingBox() {
        return boundingBox;
    }

    public SimulatedPlayerState withInput(Input input) {
        return new SimulatedPlayerState(
            input,
            this.ticksLeftToDoubleTapSprint,
            this.abilities,
            this.ticksToNextAutoJump,
            this.noClip,
            this.pos,
            this.velocity,
            this.blockPos,
            this.dimensions,
            this.boundingBox,
            this.usingItem,
            this.vehicle,
            this.dataTracker,
            this.hungerManager,
            this.activeStatusEffects,
            this.attributes,
            this.gameMode,
            this.touchingWater,
            this.horizontalCollision,
            this.verticalCollision,
            this.collidedSoftly,
            this.isSubmergedInWater,
            this.abilityResyncCountdown,
            this.onGround,
            this.yaw,
            this.pitch,
            this.velocityDirty,
            this.climbingPos,
            this.falling,
            this.submergedFluidTag,
            this.underwaterVisibilityTicks,
            this.isCamera,
            this.field_3938,
            this.mountJumpStrength,
            this.lastStrideDistance,
            this.fallDistance,
            this.handSwinging,
            this.handSwingTicks,
            this.handSwingProgress,
            this.headYaw,
            this.movementSpeed,
            this.strideDistance,
            this.jumpingCooldown,
            this.isLogicalSideForUpdatingMovement,
            this.forwardSpeed,
            this.sidewaysSpeed,
            this.upwardSpeed,
            this.jumping,
            this.firstUpdate,
            this.fluidHeight,
            this.currentlyCheckedCollisions,
            this.queuedCollisionChecks,
            this.removalReason,
            this.fireTicks,
            this.collisionHandler,
            this.collidedBlockPositions,
            this.groundCollision,
            this.distanceTraveled,
            this.speed,
            this.nextStepSoundDistance
        );
    }
    
    public boolean isInLava() {
        return !this.firstUpdate && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0D;
    }

    public boolean isClimbing(WorldView world) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        mutable.set(this.blockPos);
        BlockState state = world.getBlockState(mutable);
        if (state.isIn(BlockTags.CLIMBABLE)) return true;
        return state.getBlock() instanceof TrapdoorBlock && this.canEnterTrapdoor(world, state);
    }

    private boolean canEnterTrapdoor(WorldView world, BlockState trapdoor) {
        if (!trapdoor.get(TrapdoorBlock.OPEN)) return false;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        mutable.set(this.blockPos.getX(), this.blockPos.getY() - 1, this.blockPos.getZ());
        BlockState ladder = world.getBlockState(mutable);

        return ladder.isOf(Blocks.LADDER) && ladder.get(LadderBlock.FACING) == trapdoor.get(TrapdoorBlock.FACING);
    }
    
    private static PlayerAbilities copyPlayerAbilities(PlayerAbilities playerAbilities) {
        PlayerAbilities copy = new PlayerAbilities();
        copy.invulnerable = playerAbilities.invulnerable;
        copy.flying = playerAbilities.flying;
        copy.allowFlying = playerAbilities.allowFlying;
        copy.creativeMode = playerAbilities.creativeMode;
        copy.allowModifyWorld = playerAbilities.allowModifyWorld;
        copy.setFlySpeed(playerAbilities.getFlySpeed());
        copy.setWalkSpeed(playerAbilities.getWalkSpeed());
        return copy;
    }

    private static DataTracker copyDataTracker(DataTracker dataTracker) {
        DataTracker.Builder copy = new DataTracker.Builder(dataTracker.trackedEntity);
        for (int i = 0; i < dataTracker.entries.length; i++) {
            DataTracker.Entry<?> entry = dataTracker.entries[i];
            copy.entries[i] = new SimulatedDataTrackerEntry(entry.getData(), entry.initialValue, entry.get(), entry.isDirty());
        }
        return copy.build();
    }

    private static HungerManager copyHungerManager(HungerManager hungerManager) {
        HungerManager copy = new HungerManager();
        copy.setFoodLevel(hungerManager.getFoodLevel());
        copy.setSaturationLevel(hungerManager.getSaturationLevel());
        copy.exhaustion = hungerManager.exhaustion;
        copy.foodTickTimer = hungerManager.foodTickTimer;
        return copy;
    }

    private static Map<RegistryEntry<StatusEffect>, StatusEffectInstance> copyActiveStatusEffects(Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects) {
        Map<RegistryEntry<StatusEffect>, StatusEffectInstance> copy = Maps.newHashMapWithExpectedSize(activeStatusEffects.size());
        for (Map.Entry<RegistryEntry<StatusEffect>, StatusEffectInstance> entry : activeStatusEffects.entrySet()) {
            StatusEffectInstance instance = new StatusEffectInstance(entry.getValue());
            StatusEffectInstance hiddenEffectCopy = instance;
            StatusEffectInstance hiddenEffectEntry = entry.getValue().hiddenEffect;
            while (hiddenEffectEntry != null) {
                hiddenEffectCopy.hiddenEffect = new StatusEffectInstance(hiddenEffectEntry);
                hiddenEffectCopy = hiddenEffectCopy.hiddenEffect;
                hiddenEffectEntry = hiddenEffectEntry.hiddenEffect;
            }
            copy.put(entry.getKey(), instance);
        }
        return copy;
    }

    private static AttributeContainer copyAttributes(AttributeContainer attributes) {
        AttributeContainer copy = new AttributeContainer(attributes.fallback);
        for (Map.Entry<RegistryEntry<EntityAttribute>, EntityAttributeInstance> entityAttribute : attributes.custom.entrySet()) {
            copy.custom.put(entityAttribute.getKey(), copyEntityAttributeInstance(entityAttribute.getValue()));
        }
        for (EntityAttributeInstance entityAttribute : attributes.getTracked()) {
            copy.getTracked().add(copyEntityAttributeInstance(entityAttribute));
        }
        for (EntityAttributeInstance entityAttribute : attributes.getPendingUpdate()) {
            copy.getPendingUpdate().add(copyEntityAttributeInstance(entityAttribute));
        }
        return copy;
    }

    private static EntityAttributeInstance copyEntityAttributeInstance(EntityAttributeInstance instance) {
        EntityAttributeInstance copy = new EntityAttributeInstance(instance.type, temp -> {
        });
        copy.setFrom(instance);
        copy.dirty = instance.dirty;
        copy.value = instance.value;
        copy.updateCallback = instance.updateCallback; // Update callback? What to do with it?
        return copy;
    }

    private static List<List<Entity.QueuedCollisionCheck>> copyQueuedCollisionChecks(List<List<Entity.QueuedCollisionCheck>> queuedCollisionChecks) {
        List<List<Entity.QueuedCollisionCheck>> copy = new ObjectArrayList<>(queuedCollisionChecks.size());
        for (List<Entity.QueuedCollisionCheck> list : queuedCollisionChecks) {
            copy.add(new ObjectArrayList<>(list));
        }
        return copy;
    }

    private static EntityCollisionHandler.Impl copyCollisionHandler(EntityCollisionHandler.Impl collisionHandler) {
        EntityCollisionHandler.Impl copy = new EntityCollisionHandler.Impl();
        copy.activeEvents.addAll(collisionHandler.activeEvents);
        for (Map.Entry<CollisionEvent, List<Consumer<Entity>>> preCallback : collisionHandler.preCallbacks.entrySet()) {
            copy.preCallbacks.put(preCallback.getKey(), new ArrayList<>(preCallback.getValue()));
        }
        for (Map.Entry<CollisionEvent, List<Consumer<Entity>>> postCallback : collisionHandler.postCallbacks.entrySet()) {
            copy.postCallbacks.put(postCallback.getKey(), new ArrayList<>(postCallback.getValue()));
        }
        copy.callbacks.addAll(collisionHandler.callbacks);
        copy.version = collisionHandler.version;
        return copy;
    }

    private static final class SimulatedDataTrackerEntry<T> extends DataTracker.Entry<T> {
        public SimulatedDataTrackerEntry(TrackedData<T> data, T initialValue, T value, boolean dirty) {
            super(data, initialValue);
            set(value);
            setDirty(dirty);
        }
    }
}
