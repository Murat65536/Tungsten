package kaptainwutax.tungsten.simulation;

import net.minecraft.client.network.ClientPlayerEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Factory class for creating hollow ClientPlayerEntity instances.
 * These instances have all methods returning default values when the hollow flag is set.
 * Used for high-performance pathfinding simulation.
 */
public final class HollowPlayerFactory {

    private static ClientPlayerEntity SINGLETON_INSTANCE;
    private static final String HOLLOW_FIELD_NAME = "tungsten$isHollow";

    private HollowPlayerFactory() {
        // Prevent instantiation
    }

    /**
     * Gets or creates the singleton hollow ClientPlayerEntity instance.
     * This instance has all methods nullified for maximum performance.
     *
     * @return A hollow ClientPlayerEntity instance
     */
    public static synchronized ClientPlayerEntity getHollowPlayer() {
        if (SINGLETON_INSTANCE == null) {
            SINGLETON_INSTANCE = createHollowPlayer();
        }
        return SINGLETON_INSTANCE;
    }

    /**
     * Creates a new hollow ClientPlayerEntity instance.
     * Use getHollowPlayer() for the singleton instance unless you specifically need multiple instances.
     *
     * @return A new hollow ClientPlayerEntity instance
     */
    public static ClientPlayerEntity createHollowPlayer() {
        try {
            // Get the no-arg constructor (created by our transformer)
            Constructor<ClientPlayerEntity> constructor = ClientPlayerEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            // Create instance using null constructor
            ClientPlayerEntity player = constructor.newInstance();

            // The transformer should have already set the hollow flag in the constructor,
            // but we can verify/ensure it's set
            setHollowFlag(player, true);

            return player;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create hollow ClientPlayerEntity. " +
                "Ensure ClientPlayerEntityTransformer is properly registered and loaded.", e);
        }
    }

    /**
     * Sets the hollow flag on a ClientPlayerEntity instance.
     * When true, all methods will return default values.
     *
     * @param player The player instance
     * @param hollow Whether to enable hollow mode
     */
    public static void setHollowFlag(ClientPlayerEntity player, boolean hollow) {
        try {
            // The field is in the Entity base class
            Class<?> entityClass = player.getClass();
            while (entityClass != null && !entityClass.getName().endsWith("Entity")) {
                entityClass = entityClass.getSuperclass();
            }

            if (entityClass == null) {
                throw new RuntimeException("Could not find Entity base class");
            }

            Field field = entityClass.getDeclaredField(HOLLOW_FIELD_NAME);
            field.setAccessible(true);
            field.setBoolean(player, hollow);
        } catch (Exception e) {
            // Field might not exist if transformer hasn't run yet
            // This is not critical as the constructor should set it
            System.err.println("Warning: Could not set hollow flag on ClientPlayerEntity: " + e.getMessage());
        }
    }

    /**
     * Checks if a ClientPlayerEntity instance has the hollow flag set.
     *
     * @param player The player instance to check
     * @return true if the player is hollow, false otherwise
     */
    public static boolean isHollow(ClientPlayerEntity player) {
        try {
            // The field is in the Entity base class
            Class<?> entityClass = player.getClass();
            while (entityClass != null && !entityClass.getName().endsWith("Entity")) {
                entityClass = entityClass.getSuperclass();
            }

            if (entityClass == null) {
                return false;
            }

            Field field = entityClass.getDeclaredField(HOLLOW_FIELD_NAME);
            field.setAccessible(true);
            return field.getBoolean(player);
        } catch (Exception e) {
            // Field doesn't exist or couldn't be accessed
            return false;
        }
    }

    /**
     * Resets the singleton instance, forcing creation of a new one on next access.
     * Useful for testing or if the instance becomes corrupted.
     */
    public static synchronized void reset() {
        SINGLETON_INSTANCE = null;
    }
}