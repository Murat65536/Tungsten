package kaptainwutax.tungsten.simulation.serialization;

import com.esotericsoftware.kryo.Kryo;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import kaptainwutax.tungsten.simulation.HollowClientPlayerEntity;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Helper class to copy attributes from one ClientPlayerEntity to another
 * using Kryo for deep copying of complex mutable fields.
 * The list of attributes copied corresponds to SimulatedPlayerState.
 */
public class PlayerAttributeCopier {

    /**
     * Copies attributes from the source player to the target player.
     * This uses Kryo for deep copying complex objects.
     *
     * @param source The source player (usually the actual player)
     * @param target The target player (usually the hollow player)
     */
    public static void copyAttributes(ClientPlayerEntity source, ClientPlayerEntity target) {
        Kryo kryo = TungstenKryo.obtain();
        try {
            // Primitives and Immutables (Assignment is enough or simple clone)
            
            // Input
            // Input is mutable but often replaced. Kryo copy is safest.
            target.input = kryo.copy(source.input);
            
            target.ticksLeftToDoubleTapSprint = source.ticksLeftToDoubleTapSprint;
            
            // PlayerAbilities - Deep Copy
            target.abilities = kryo.copy(source.abilities);
            
            target.ticksToNextAutoJump = source.ticksToNextAutoJump;
            target.noClip = source.noClip;
            
            // Vec3d is immutable, so reference copy is fine, but if we want to be safe against 
            // unexpected mutation (unlikely for Vec3d), we can copy. 
            // Assignment is standard for Vec3d in MC.
            target.pos = source.pos;
            target.velocity = source.velocity;
            
            // BlockPos is immutable
            target.blockPos = source.blockPos;
            
            target.dimensions = source.dimensions; // Dimensions usually shared/immutable-ish
            target.boundingBox = source.boundingBox; // Box is immutable? Yes.
            
            target.usingItem = source.usingItem;
            
            // Vehicle - This is a reference. 
            // SimulatedPlayerState copies the reference.
            target.vehicle = source.vehicle;
            
            // DataTracker - Deep Copy
            // DataTracker has a lock and complex internal state. Kryo should handle it if registered or reflecting.
            // WARNING: DataTracker holds a reference to the entity ('trackedEntity').
            // If we deep copy it, the new DataTracker might still point to the OLD entity (source).
            // We might need to fix that reference after copy.
            target.dataTracker = kryo.copy(source.dataTracker);
            fixDataTrackerEntity(target.dataTracker, target);

            // HungerManager - Deep Copy
            target.hungerManager = kryo.copy(source.hungerManager);
            
            // Active Status Effects - Deep Copy
            // Map<RegistryEntry<StatusEffect>, StatusEffectInstance>
            target.activeStatusEffects = kryo.copy(source.activeStatusEffects);
            
            // Attributes - Deep Copy
            target.attributes = kryo.copy(source.attributes);
            
            // GameMode - Enum (Immutable)
            // ClientPlayerEntity doesn't have a public gameMode field setter usually, 
            // it's handled by InteractionManager. 
            // SimulatedPlayerState stores it but ClientPlayerEntity might not expose it simply.
            // However, SimulatedPlayerState accesses 'player.getGameMode()'.
            // Writing it back might be tricky if there's no field.
            // SimulatedPlayerState's applyTo() DOES NOT write gameMode back. 
            // // player.gameMode = gameMode; // Not available in ClientPlayerEntity
            // So we skip writing gameMode to target as per SimulatedPlayerState implementation.
            
            target.touchingWater = source.touchingWater;
            target.horizontalCollision = source.horizontalCollision;
            target.verticalCollision = source.verticalCollision;
            target.collidedSoftly = source.collidedSoftly;
            
            // isSubmergedInWater is a boolean in SimulatedPlayerState, 
            // but in Entity it's derived or a field? 'isSubmergedInWater' field exists in Entity? 
            // SimulatedPlayerState has 'player.isSubmergedInWater()' getter.
            // Entity has 'boolean isSubmergedInWater'.
            target.isSubmergedInWater = source.isSubmergedInWater;
            
            target.abilityResyncCountdown = source.abilityResyncCountdown;
            target.onGround = source.onGround;
            
            target.setYaw(source.getYaw());
            target.setPitch(source.getPitch());
            
            target.velocityDirty = source.velocityDirty;
            target.climbingPos = source.climbingPos; // Optional<BlockPos> is immutable
            target.falling = source.falling;
            
            // SubmergedFluidTag - Deep Copy (Set)
            target.submergedFluidTag = kryo.copy(source.submergedFluidTag);
            
            target.underwaterVisibilityTicks = source.underwaterVisibilityTicks;
            
            // isCamera - Not writable on ClientPlayerEntity easily? 
            // SimulatedPlayerState comments: // player.isCamera = isCamera; // Not available
            
            target.field_3938 = source.field_3938;
            target.mountJumpStrength = source.mountJumpStrength;
            target.lastStrideDistance = source.lastStrideDistance;
            
            // Fall Distance
            target.fallDistance = source.fallDistance;
            
            target.handSwinging = source.handSwinging;
            target.handSwingTicks = source.handSwingTicks;
            target.handSwingProgress = source.handSwingProgress;
            target.headYaw = source.headYaw;
            target.movementSpeed = source.movementSpeed;
            target.strideDistance = source.strideDistance;
            target.jumpingCooldown = source.jumpingCooldown;
            
            // isLogicalSideForUpdatingMovement - Not writable?
            
            target.forwardSpeed = source.forwardSpeed;
            target.sidewaysSpeed = source.sidewaysSpeed;
            target.upwardSpeed = source.upwardSpeed;
            target.jumping = source.jumping;
            target.firstUpdate = source.firstUpdate;
            
            // FluidHeight - Deep Copy
            target.fluidHeight = kryo.copy(source.fluidHeight);
            
            // Currently Checked Collisions - List
            target.currentlyCheckedCollisions = kryo.copy(source.currentlyCheckedCollisions);
            
            // Queued Collision Checks
            target.queuedCollisionChecks = kryo.copy(source.queuedCollisionChecks);
            
            target.removalReason = source.removalReason;
            target.fireTicks = source.fireTicks;
            
            // Collision Handler - Deep Copy
            target.collisionHandler = kryo.copy(source.collisionHandler);
            
            // Collided Block Positions - Deep Copy
            target.collidedBlockPositions = kryo.copy(source.collidedBlockPositions);
            
            target.groundCollision = source.groundCollision;
            target.distanceTraveled = source.distanceTraveled;
            target.speed = source.speed;
            target.nextStepSoundDistance = source.nextStepSoundDistance;

        } finally {
            TungstenKryo.release(kryo);
        }
    }

    /**
     * Fixes the trackedEntity reference in a DataTracker after it has been copied.
     */
    private static void fixDataTrackerEntity(DataTracker dataTracker, Entity newOwner) {
        if (dataTracker == null) return;
        try {
            // Need reflection to set the private final field 'trackedEntity'
            // The access widener exposes 'trackedEntity' as accessible field?
            // Let's check access widener: 
            // accessible field net/minecraft/entity/data/DataTracker trackedEntity Lnet/minecraft/entity/data/DataTracked;
            // It says accessible, so we can access it. But is it mutable?
            // "accessible" usually means public, but not necessarily non-final.
            // If it's final, we still need reflection to set it, unless access widener made it non-final (mutable).
            // Access widener "mutable" keyword does that.
            // The widener has: accessible field ... trackedEntity
            // It does NOT say "mutable field".
            // So we might need reflection.
            
            java.lang.reflect.Field field = DataTracker.class.getDeclaredField("trackedEntity");
            field.setAccessible(true);
            field.set(dataTracker, newOwner);
            
        } catch (Exception e) {
            // Log warning or rethrow?
            // This is critical for DataTracker to work correctly on the new entity.
            throw new RuntimeException("Failed to update DataTracker owner", e);
        }
    }
}
