package kaptainwutax.tungsten.simulation.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import org.objenesis.strategy.StdInstantiatorStrategy;

/**
 * Manages Kryo instances for deep copying player attributes.
 */
public class TungstenKryo {

    private static final Pool<Kryo> kryoPool = new Pool<>(true, false, 16) {
        @Override
        protected Kryo create() {
            Kryo kryo = new Kryo();
            // Use StdInstantiatorStrategy to handle classes without no-arg constructors
            kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
            // Enable references to handle circular references (default is true, but being explicit)
            kryo.setReferences(true);
            // Registration is optional by default in newer Kryo versions, but we can enable it if strictness is needed.
            // For now, we allow unregistered classes for flexibility with Minecraft's large codebase.
            kryo.setRegistrationRequired(false);
            
            return kryo;
        }
    };

    /**
     * obtains a Kryo instance from the pool.
     * Must be released after use.
     */
    public static Kryo obtain() {
        return kryoPool.obtain();
    }

    /**
     * Releases the Kryo instance back to the pool.
     */
    public static void release(Kryo kryo) {
        kryoPool.free(kryo);
    }

    /**
     * Deep copies an object using a pooled Kryo instance.
     * @param object The object to copy
     * @return The deep copy
     */
    public static <T> T copy(T object) {
        Kryo kryo = obtain();
        try {
            return kryo.copy(object);
        } finally {
            release(kryo);
        }
    }
}
