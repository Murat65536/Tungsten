package kaptainwutax.tungsten.simulation;

import com.sun.jdi.InvocationException;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * Wrapper class for hollow ClientPlayerEntity instances.
 * This class provides utility methods for working with hollow (nullified) player instances
 * created by the ClientPlayerEntityHierarchyTransformer.
 */
public class HollowClientPlayerEntity {
    private final ClientPlayerEntity player;

    private HollowClientPlayerEntity(ClientPlayerEntity player) {
        this.player = player;
    }

    /**
     * Creates a hollow ClientPlayerEntity instance using reflection.
     * The no-arg constructor is added by ClientPlayerEntityTransformer at runtime.
     *
     * @return A hollow ClientPlayerEntity wrapped in HollowClientPlayerEntity
     */
    public static HollowClientPlayerEntity createHollow() {
        try {
            // Get the no-arg constructor (created by our transformer)
            Constructor<ClientPlayerEntity> constructor =
                ClientPlayerEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            // Create instance using null constructor
            ClientPlayerEntity player = constructor.newInstance();

            // Ensure the hollow flag is set (should already be set by transformer)
            setHollowFlag(player, true);

            return new HollowClientPlayerEntity(player);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException |
                 InvocationTargetException e) {
            throw new RuntimeException("Failed to create hollow ClientPlayerEntity.", e);
        }
    }

    /**
     * Ticks the movement of the hollow player with a simulated state.
     * This method applies the state to the player and then calls tickMovement,
     * but the actual tickMovement logic is skipped due to the hollow flag.
     *
     * @param state The simulated player state to apply
     */
    public void tickMovement(SimulatedPlayerState state) {
        // Apply state to the player
        state.applyTo(player);

        // Call the actual tickMovement
        // The transformer has been modified to allow tickMovement to run even when hollow,
        // with NPE protection injected to skip operations on null fields.
        player.tickMovement();
    }

    /**
     * Gets the underlying ClientPlayerEntity instance.
     */
    public ClientPlayerEntity getPlayer() {
        return player;
    }

    /**
     * Sets the hollow flag on a player instance.
     */
    private static void setHollowFlag(Entity player, boolean hollow) {
        try {
            // The field is in the Entity base class
            Class<?> entityClass = Entity.class;
            Field field = entityClass.getDeclaredField("tungsten$isHollow");
            field.setAccessible(true);
            field.setBoolean(player, hollow);
        } catch (Exception e) {
            // Field might not exist if transformer hasn't run yet
            // This is not critical as the constructor should set it
            System.err.println("Warning: Could not set hollow flag: " + e.getMessage());
        }
    }
}