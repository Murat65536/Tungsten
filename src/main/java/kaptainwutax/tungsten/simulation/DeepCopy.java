package kaptainwutax.tungsten.simulation;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import sun.misc.Unsafe;

/**
 * Deep-copies a real player entity into a simulated class instance.
 * Every reachable object is recursively deep-copied so that the simulated
 * player shares absolutely no mutable state with the original.
 * <p>
 * Exceptions: truly immutable objects (String, boxed primitives, Class,
 * enum constants) and infrastructure singletons (ClassLoader, Thread, Logger)
 * are shared by reference since they cannot be mutated.
 */
public final class DeepCopy {
	private static final Unsafe UNSAFE = getUnsafe();
	private final Map<String, String> classMap; // original dot-name → simulated dot-name
	private final Set<String> immutableTypes;
	/** Maps hierarchy class dot-name → set of whitelisted field names.
	 *  Non-whitelisted fields are skipped during deep copy (left as default). */
	private final Map<String, Set<String>> whitelistedFields;

	/** Types that are inherently immutable or are infrastructure singletons — safe to share. */
	private static final Set<String> ALWAYS_SHARED = Set.of(
		"java.lang.Class",
		"java.lang.ClassLoader",
		"java.lang.Thread",
		"java.lang.ThreadGroup",
		"java.lang.reflect.Method",
		"java.lang.reflect.Field",
		"java.lang.reflect.Constructor",
		"java.lang.StackTraceElement",
		"java.util.Locale",
		"java.io.File",
		"java.nio.file.Path",
		"java.security.ProtectionDomain",
		"java.security.CodeSource",
		"java.security.AccessControlContext",
		"sun.misc.Unsafe"
	);

	/** Package prefixes where all types are treated as shared singletons. */
	private static final String[] SHARED_PACKAGE_PREFIXES = {
		"java.util.concurrent.locks.",
		"java.util.logging.",
		"org.slf4j.",
		"org.apache.logging.",
		"com.google.common.base.",
		"io.netty.",
		"com.mojang.serialization.",
		"com.mojang.datafixers.",
		"com.mojang.brigadier.",
		"net.minecraft.registry.",
		"net.minecraft.network.",
		"net.minecraft.server.",
		"net.minecraft.world.", // World, ServerWorld — large singleton graphs
		"net.minecraft.block.", // BlockState, Block — registry singletons
		"net.minecraft.state.", // State — parent of BlockState
		"net.minecraft.entity.EntityType", // registry singleton
		"net.minecraft.entity.data.", // DataTracker — complex internal state
		"net.minecraft.entity.EntityCollisionHandler", // collision infrastructure
		"net.minecraft.entity.TrackedPosition", // tracked position
		"net.minecraft.client.MinecraftClient",
		"net.minecraft.client.option.",
		"net.minecraft.client.render.",
		"net.minecraft.client.sound.",
		"net.minecraft.client.network.ClientPlayNetworkHandler",
		"net.minecraft.client.tutorial.",
		"net.minecraft.stat.",
		"net.minecraft.recipe.",
		"net.minecraft.client.recipebook.",
		"net.minecraft.item.", // ItemStack, Item — registry singletons
		"net.minecraft.sound.", // SoundEvent etc.
		"net.minecraft.particle.", // ParticleEffect etc.
		"net.minecraft.fluid.", // FluidState etc.
		"net.minecraft.text.", // Text components
		"net.minecraft.nbt.", // NBT data
	};

	public DeepCopy(SimMetadata metadata) {
		Objects.requireNonNull(metadata, "metadata");
		this.classMap = new HashMap<>(metadata.classMap);
		this.immutableTypes = Set.copyOf(metadata.immutableTypes);
		this.whitelistedFields = new HashMap<>();
		for (var entry : metadata.whitelistedFields.entrySet()) {
			this.whitelistedFields.put(entry.getKey(), Set.copyOf(entry.getValue()));
		}
	}

	public Object copyToSimulated(Object source, Class<?> simClass) {
		IdentityHashMap<Object, Object> seen = new IdentityHashMap<>();
		return deepCopyObject(source, simClass, seen);
	}

	/**
	 * Deep-copy an object, allocating it as targetClass (used for hierarchy
	 * remapping). For non-hierarchy objects, targetClass == source.getClass().
	 */
	private Object deepCopyObject(Object source, Class<?> targetClass, IdentityHashMap<Object, Object> seen) {
		Object existing = seen.get(source);
		if (existing != null) {
			return existing;
		}

		Object target;
		try {
			target = UNSAFE.allocateInstance(targetClass);
		} catch (InstantiationException ex) {
			// Can't allocate — fall back to sharing by reference
			return source;
		}
		seen.put(source, target);

		Class<?> currentSource = source.getClass();
		while (currentSource != null && currentSource != Object.class) {
			// For hierarchy classes, only copy whitelisted fields
			Set<String> allowed = whitelistedFields.get(currentSource.getName());
			for (Field field : currentSource.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				if (allowed != null && !allowed.contains(field.getName())) {
					continue; // skip non-whitelisted fields in hierarchy classes
				}
				try {
					long offset = UNSAFE.objectFieldOffset(field);
					Class<?> ft = field.getType();
					if (ft.isPrimitive()) {
						copyPrimitive(source, target, offset, ft);
					} else {
						Object value = UNSAFE.getObject(source, offset);
						Object copied = deepCopyValue(value, seen);
						UNSAFE.putObject(target, offset, copied);
					}
				} catch (Exception ex) {
					// Skip fields that can't be copied
				}
			}
			currentSource = currentSource.getSuperclass();
		}
		return target;
	}

	/**
	 * Decide how to copy a single value: null, primitive wrapper, immutable,
	 * shared singleton, array, hierarchy remap, or recursive deep copy.
	 */
	private Object deepCopyValue(Object value, IdentityHashMap<Object, Object> seen) {
		if (value == null) {
			return null;
		}

		// Already copied (cycle detection)
		Object existing = seen.get(value);
		if (existing != null) {
			return existing;
		}

		Class<?> type = value.getClass();

		// Primitives wrappers, enums, strings, records — immutable, share by reference
		// Records cannot be deep-copied via Unsafe (objectFieldOffset throws on record classes)
		// and are immutable by design (all fields are final with no setters).
		if (type.isPrimitive() || type.isEnum() || type.isRecord()
				|| value instanceof String
				|| value instanceof Number || value instanceof Boolean
				|| value instanceof Character) {
			return value;
		}

		// User-declared immutable types from whitelist config
		if (immutableTypes.contains(type.getName())) {
			return value;
		}

		// Infrastructure / singleton types — share by reference
		if (isSharedType(type)) {
			return value;
		}

		// Hierarchy types — remap to simulated class
		String simName = classMap.get(type.getName());
		if (simName != null) {
			try {
				Class<?> simClass = Class.forName(simName);
				return deepCopyObject(value, simClass, seen);
			} catch (ClassNotFoundException ex) {
				return value;
			}
		}

		// Arrays
		if (type.isArray()) {
			return deepCopyArray(value, type, seen);
		}

		// All other objects — full recursive deep copy
		return deepCopyObject(value, type, seen);
	}

	private Object deepCopyArray(Object source, Class<?> arrayType, IdentityHashMap<Object, Object> seen) {
		int length = Array.getLength(source);
		Class<?> componentType = arrayType.getComponentType();
		Object copy = Array.newInstance(componentType, length);
		seen.put(source, copy);

		if (componentType.isPrimitive()) {
			System.arraycopy(source, 0, copy, 0, length);
		} else {
			for (int i = 0; i < length; i++) {
				Object element = Array.get(source, i);
				Array.set(copy, i, deepCopyValue(element, seen));
			}
		}
		return copy;
	}

	private boolean isSharedType(Class<?> type) {
		String name = type.getName();

		if (ALWAYS_SHARED.contains(name)) {
			return true;
		}

		for (String prefix : SHARED_PACKAGE_PREFIXES) {
			if (name.startsWith(prefix)) {
				return true;
			}
		}

		// java.lang.ref.* (WeakReference, SoftReference, etc.)
		if (name.startsWith("java.lang.ref.")) {
			return true;
		}

		// Functional interface implementations / lambdas
		if (name.contains("$$Lambda") || name.contains("$Lambda$")) {
			return true;
		}

		// Iterators are transient state objects with internal bookkeeping that cannot be safely deep-copied.
		// Copying an iterator via Unsafe leaves transient/final fields (like 'wrapped' in fastutil iterators) null,
		// causing NPEs. Iterators should always be fresh instances created from the copied collection itself.
		// Match inner class iterators (e.g., ObjectOpenHashSet$SetIterator) and standard iterator types.
		if (name.contains("$Iterator") || name.contains("$Itr") || name.endsWith("Iterator") ||
				(name.startsWith("java.util.") && name.contains("Iterator"))) {
			return true;
		}

		return false;
	}

	private static void copyPrimitive(Object source, Object target, long offset, Class<?> ft) {
		if (ft == boolean.class) {
			UNSAFE.putBoolean(target, offset, UNSAFE.getBoolean(source, offset));
		} else if (ft == byte.class) {
			UNSAFE.putByte(target, offset, UNSAFE.getByte(source, offset));
		} else if (ft == short.class) {
			UNSAFE.putShort(target, offset, UNSAFE.getShort(source, offset));
		} else if (ft == char.class) {
			UNSAFE.putChar(target, offset, UNSAFE.getChar(source, offset));
		} else if (ft == int.class) {
			UNSAFE.putInt(target, offset, UNSAFE.getInt(source, offset));
		} else if (ft == long.class) {
			UNSAFE.putLong(target, offset, UNSAFE.getLong(source, offset));
		} else if (ft == float.class) {
			UNSAFE.putFloat(target, offset, UNSAFE.getFloat(source, offset));
		} else if (ft == double.class) {
			UNSAFE.putDouble(target, offset, UNSAFE.getDouble(source, offset));
		}
	}

	private static Unsafe getUnsafe() {
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (Unsafe)field.get(null);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to access Unsafe", ex);
		}
	}
}
