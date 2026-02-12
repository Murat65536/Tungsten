package kaptainwutax.tungsten.simulation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.entity.EntityChangeListener;

public final class SimulatedPlayerFactory {
	private static final String METADATA_RESOURCE = "/tungsten/simulated-metadata.json";
	private static volatile SimMetadata metadata;
	private static volatile SimulatedPlayerAccess cachedAccess;
	private static volatile DeepCopy cachedCopier;
	private static volatile Field cachedChangeListenerField;

	private SimulatedPlayerFactory() {
	}

	public static SimulatedPlayerHandle createFrom(ClientPlayerEntity player) {
		SimMetadata data = loadMetadata();
		try {
			String simClassName = data.classMap.get("net.minecraft.client.network.ClientPlayerEntity");
			if (simClassName == null) {
				throw new IllegalStateException("No simulated class mapping for ClientPlayerEntity");
			}
			Class<?> simClass = Class.forName(simClassName);
			DeepCopy copier = getCopier(data);
			Object instance = copier.copyToSimulated(player, simClass);
			setChangeListener(instance);
			return new SimulatedPlayerHandle(instance, getAccess(simClass));
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to create simulated player", ex);
		}
	}

	public static SimulatedPlayerHandle copyFrom(SimulatedPlayerHandle source) {
		SimMetadata data = loadMetadata();
		try {
			DeepCopy copier = getCopier(data);
			Object instance = copier.copyToSimulated(source.getRaw(), source.getRaw().getClass());
			setChangeListener(instance);
			return new SimulatedPlayerHandle(instance, source.access);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to copy simulated player", ex);
		}
	}

	private static DeepCopy getCopier(SimMetadata data) {
		if (cachedCopier == null) {
			synchronized (SimulatedPlayerFactory.class) {
				if (cachedCopier == null) {
					cachedCopier = new DeepCopy(data);
				}
			}
		}
		return cachedCopier;
	}

	private static void setChangeListener(Object instance) throws ReflectiveOperationException {
		if (cachedChangeListenerField == null) {
			synchronized (SimulatedPlayerFactory.class) {
				if (cachedChangeListenerField == null) {
					cachedChangeListenerField = net.minecraft.entity.Entity.class.getDeclaredField("changeListener");
					cachedChangeListenerField.setAccessible(true);
				}
			}
		}
		cachedChangeListenerField.set(instance, EntityChangeListener.NONE);
	}

	public static void attachInput(SimulatedPlayerHandle handle, Input input) {
		SimulatedPlayerAccess access = handle.access;
		if (access.inputField != null) {
			try {
				access.inputField.setAccessible(true);
				access.inputField.set(handle.getRaw(), input);
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("Failed to attach input", ex);
			}
		}
	}

	public static SimulationResult simulateTicks(ClientPlayerEntity player, List<InputState> inputs) {
		SimulatedPlayerHandle handle = createFrom(player);
		List<Vec3d> positions = new ArrayList<>();
		List<Vec3d> velocities = new ArrayList<>();
		for (InputState input : inputs) {
			input.applyTo(handle.getRaw());
			handle.tickMovement();
			positions.add(handle.getPos());
			velocities.add(handle.getVelocity());
		}
		return new SimulationResult(positions, velocities);
	}

	private static SimMetadata loadMetadata() {
		if (metadata == null) {
			synchronized (SimulatedPlayerFactory.class) {
				if (metadata == null) {
					try {
						metadata = SimMetadata.loadResource(METADATA_RESOURCE);
					} catch (Exception ex) {
						throw new IllegalStateException("Failed to load simulation metadata", ex);
					}
				}
			}
		}
		return metadata;
	}

	private static SimulatedPlayerAccess getAccess(Class<?> simClass) {
		if (cachedAccess == null) {
			synchronized (SimulatedPlayerFactory.class) {
				if (cachedAccess == null) {
					cachedAccess = new SimulatedPlayerAccess(simClass);
				}
			}
		}
		return cachedAccess;
	}

	public static final class SimulatedPlayerHandle {
		private final Object instance;
		final SimulatedPlayerAccess access;

		SimulatedPlayerHandle(Object instance, SimulatedPlayerAccess access) {
			this.instance = instance;
			this.access = access;
		}

		public Object getRaw() {
			return instance;
		}

		public void tickMovement() {
			Method method = access.movementTick != null ? access.movementTick : access.tickMovement;
			if (method == null) {
				throw new IllegalStateException("Movement method not found");
			}
			try {
				method.invoke(instance);
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("Failed to invoke movement tick", ex);
			}
		}

		public Vec3d getPos() {
			return invoke(access.getPos, Vec3d.ZERO);
		}

		public Vec3d getVelocity() {
			return invoke(access.getVelocity, Vec3d.ZERO);
		}

		public BlockPos getBlockPos() {
			Vec3d pos = getPos();
			return new BlockPos(MathHelper.floor(pos.x), MathHelper.floor(pos.y), MathHelper.floor(pos.z));
		}

		public boolean isOnGround() {
			return invokeBoolean(access.isOnGround, false);
		}

		public boolean isTouchingWater() {
			return invokeBoolean(access.isTouchingWater, false);
		}

		public boolean isSubmergedInWater() {
			return invokeBoolean(access.isSubmergedInWater, false);
		}

		public boolean isInLava() {
			return invokeBoolean(access.isInLava, false);
		}

		public boolean isSprinting() {
			return invokeBoolean(access.isSprinting, false);
		}

		public boolean isSwimming() {
			return invokeBoolean(access.isSwimming, false);
		}

		public boolean isFallFlying() {
			return invokeBoolean(access.isFallFlying, false);
		}

		public Box getBoundingBox() {
			return invoke(access.getBoundingBox, null);
		}

		public float getStepHeight() {
			return invokeFloat(access.getStepHeight, 0.6f);
		}

		public float getStandingEyeHeight() {
			return invokeFloat(access.getStandingEyeHeight, 1.62f);
		}

		public float getYaw() {
			if (access.getYaw != null) {
				return invokeFloat(access.getYaw, 0f);
			}
			if (access.getYawWithDelta != null) {
				return invokeFloatWithArg(access.getYawWithDelta, 0f, 0f);
			}
			return readFloatField(access.yawField, 0f);
		}

		public float getPitch() {
			if (access.getPitch != null) {
				return invokeFloat(access.getPitch, 0f);
			}
			if (access.getPitchWithDelta != null) {
				return invokeFloatWithArg(access.getPitchWithDelta, 0f, 0f);
			}
			return readFloatField(access.pitchField, 0f);
		}

		public void setYaw(float yaw) {
			invokeSetter(access.setYaw, access.yawField, yaw);
			writeFloatField(access.lastYawField, yaw);
		}

		public void setPitch(float pitch) {
			invokeSetter(access.setPitch, access.pitchField, pitch);
			writeFloatField(access.lastPitchField, pitch);
		}

		public boolean getHorizontalCollision() {
			return readBooleanField(access.horizontalCollisionField, false);
		}

		public boolean getVerticalCollision() {
			return readBooleanField(access.verticalCollisionField, false);
		}

		public boolean getCollidedSoftly() {
			return readBooleanField(access.collidedSoftlyField, false);
		}

		public double getFallDistance() {
			return readDoubleField(access.fallDistanceField, 0.0);
		}

		public boolean getSlimeBounce() {
			return readBooleanField(access.slimeBounceField, false);
		}

		private <T> T invoke(Method method, T fallback) {
			if (method == null) {
				return fallback;
			}
			try {
				@SuppressWarnings("unchecked")
				T result = (T) method.invoke(instance);
				return result;
			} catch (ReflectiveOperationException ex) {
				return fallback;
			}
		}

		private boolean invokeBoolean(Method method, boolean fallback) {
			if (method == null) {
				return fallback;
			}
			try {
				return (boolean) method.invoke(instance);
			} catch (ReflectiveOperationException ex) {
				return fallback;
			}
		}

		private float invokeFloat(Method method, float fallback) {
			if (method == null) {
				return fallback;
			}
			try {
				return ((Number) method.invoke(instance)).floatValue();
			} catch (ReflectiveOperationException ex) {
				return fallback;
			}
		}

		private float invokeFloatWithArg(Method method, float fallback, float arg) {
			if (method == null) {
				return fallback;
			}
			try {
				return ((Number) method.invoke(instance, arg)).floatValue();
			} catch (ReflectiveOperationException ex) {
				return fallback;
			}
		}

		private void invokeSetter(Method method, Field field, float value) {
			try {
				if (method != null) {
					method.invoke(instance, value);
				} else if (field != null) {
					field.setAccessible(true);
					field.setFloat(instance, value);
				}
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("Failed to set value", ex);
			}
		}

		private boolean readBooleanField(Field field, boolean fallback) {
			if (field == null) {
				return fallback;
			}
			try {
				field.setAccessible(true);
				return field.getBoolean(instance);
			} catch (ReflectiveOperationException ex) {
				return fallback;
			}
		}

		private float readFloatField(Field field, float fallback) {
			if (field == null) {
				return fallback;
			}
			try {
				field.setAccessible(true);
				return field.getFloat(instance);
			} catch (ReflectiveOperationException ex) {
				return fallback;
			}
		}

		private double readDoubleField(Field field, double fallback) {
			if (field == null) {
				return fallback;
			}
			try {
				field.setAccessible(true);
				return field.getDouble(instance);
			} catch (ReflectiveOperationException ex) {
				return fallback;
			}
		}

		private void writeFloatField(Field field, float value) {
			if (field == null) return;
			try {
				field.setAccessible(true);
				field.setFloat(instance, value);
			} catch (ReflectiveOperationException ignored) {
			}
		}
	}

	static final class SimulatedPlayerAccess {
		final Method movementTick;
		final Method tickMovement;
		final Method getPos;
		final Method getVelocity;
		final Method isOnGround;
		final Method isTouchingWater;
		final Method isSubmergedInWater;
		final Method isInLava;
		final Method isSprinting;
		final Method isSwimming;
		final Method isFallFlying;
		final Method getBoundingBox;
		final Method getStepHeight;
		final Method getStandingEyeHeight;
		final Method getYaw;
		final Method getYawWithDelta;
		final Method getPitch;
		final Method getPitchWithDelta;
		final Method setYaw;
		final Method setPitch;
		final Field yawField;
		final Field pitchField;
		final Field lastYawField;
		final Field lastPitchField;
		final Field horizontalCollisionField;
		final Field verticalCollisionField;
		final Field collidedSoftlyField;
		final Field fallDistanceField;
		final Field slimeBounceField;
		final Field inputField;

		SimulatedPlayerAccess(Class<?> type) {
			this.movementTick = findMethod(type, "movementTick");
			this.tickMovement = findMethod(type, "tickMovement");
			this.getPos = findMethod(type, "getPos");
			this.getVelocity = findMethod(type, "getVelocity");
			this.isOnGround = findMethod(type, "isOnGround");
			this.isTouchingWater = findMethod(type, "isTouchingWater");
			this.isSubmergedInWater = findMethod(type, "isSubmergedInWater");
			this.isInLava = findMethod(type, "isInLava");
			this.isSprinting = findMethod(type, "isSprinting");
			this.isSwimming = findMethod(type, "isSwimming");
			this.isFallFlying = findMethod(type, "isFallFlying");
			this.getBoundingBox = findMethod(type, "getBoundingBox");
			this.getStepHeight = findMethod(type, "getStepHeight");
			this.getStandingEyeHeight = findMethod(type, "getStandingEyeHeight");
			this.getYaw = findMethod(type, "getYaw");
			this.getYawWithDelta = findMethod(type, "getYaw", float.class);
			this.getPitch = findMethod(type, "getPitch");
			this.getPitchWithDelta = findMethod(type, "getPitch", float.class);
			this.setYaw = findMethod(type, "setYaw", float.class);
			this.setPitch = findMethod(type, "setPitch", float.class);
			this.yawField = findField(type, "yaw");
			this.pitchField = findField(type, "pitch");
			this.lastYawField = findField(type, "lastYaw");
			this.lastPitchField = findField(type, "lastPitch");
			this.horizontalCollisionField = findField(type, "horizontalCollision");
			this.verticalCollisionField = findField(type, "verticalCollision");
			this.collidedSoftlyField = findField(type, "collidedSoftly");
			this.fallDistanceField = findField(type, "fallDistance");
			this.slimeBounceField = findField(type, "slimeBounce");
			this.inputField = findField(type, "input");
		}

		private Method findMethod(Class<?> type, String name, Class<?>... params) {
			for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
				try {
					Method method = current.getDeclaredMethod(name, params);
					method.setAccessible(true);
					return method;
				} catch (NoSuchMethodException ignored) {
				}
			}
			return null;
		}

		private Field findField(Class<?> type, String name) {
			for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
				try {
					Field field = current.getDeclaredField(name);
					field.setAccessible(true);
					return field;
				} catch (NoSuchFieldException ignored) {
				}
			}
			return null;
		}
	}
}
