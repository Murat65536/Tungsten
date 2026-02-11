package kaptainwutax.tungsten.simulation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public final class InputState {
	private final Vec2f movementVector;
	private final PlayerInput playerInput;
	private final Float yaw;
	private final Float pitch;
	private final Vec3d velocityOverride;

	public InputState(Vec2f movementVector, PlayerInput playerInput, Float yaw, Float pitch, Vec3d velocityOverride) {
		this.movementVector = movementVector;
		this.playerInput = playerInput;
		this.yaw = yaw;
		this.pitch = pitch;
		this.velocityOverride = velocityOverride;
	}

	public static InputState fromPlayer(ClientPlayerEntity player) {
		Objects.requireNonNull(player, "player");
		Input input = player.input;
		Vec2f movement = input != null ? input.getMovementInput() : Vec2f.ZERO;
		PlayerInput playerInput = input != null ? input.playerInput : PlayerInput.DEFAULT;
		return new InputState(movement, playerInput, player.getYaw(), player.getPitch(), null);
	}

	public void applyTo(Object simulatedPlayer) {
		if (simulatedPlayer == null) {
			return;
		}
		if (yaw != null) {
			invoke(simulatedPlayer, "setYaw", new Class<?>[]{float.class}, new Object[]{yaw});
		}
		if (pitch != null) {
			invoke(simulatedPlayer, "setPitch", new Class<?>[]{float.class}, new Object[]{pitch});
		}
		if (velocityOverride != null) {
			invoke(simulatedPlayer, "setVelocity", new Class<?>[]{Vec3d.class}, new Object[]{velocityOverride});
		}

		// Write movement input and player input flags to the sim player's Input object.
		// We replace the input field with a plain Input instance (not KeyboardInput) so
		// that Input.tick() is a no-op — the sim player should not re-read keyboard state.
		Object currentInput = getFieldValue(simulatedPlayer, "input");
		Input simInput;
		if (currentInput instanceof Input existingInput && !(currentInput instanceof net.minecraft.client.input.KeyboardInput)) {
			simInput = existingInput;
		} else {
			simInput = new Input();
			setFieldValue(simulatedPlayer, "input", simInput);
		}
		simInput.playerInput = playerInput != null ? playerInput : PlayerInput.DEFAULT;
		if (movementVector != null) {
			setFieldValue(simInput, "movementVector", movementVector);
		}
	}

	private static Object getFieldValue(Object target, String name) {
		Field field = findField(target.getClass(), name);
		if (field == null) {
			return null;
		}
		try {
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to read field " + name, ex);
		}
	}

	private static void setFieldValue(Object target, String name, Object value) {
		Field field = findField(target.getClass(), name);
		if (field == null) {
			throw new IllegalStateException("Field not found: " + name);
		}
		try {
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to set field " + name, ex);
		}
	}

	private static Field findField(Class<?> type, String name) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			try {
				return current.getDeclaredField(name);
			} catch (NoSuchFieldException ex) {
				current = current.getSuperclass();
			}
		}
		return null;
	}

	private static void invoke(Object target, String name, Class<?>[] paramTypes, Object[] args) {
		Method method = findMethod(target.getClass(), name, paramTypes);
		if (method == null) {
			throw new IllegalStateException("Method not found: " + name);
		}
		try {
			method.setAccessible(true);
			method.invoke(target, args);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to invoke " + name, ex);
		}
	}

	private static Method findMethod(Class<?> type, String name, Class<?>[] paramTypes) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			try {
				return current.getDeclaredMethod(name, paramTypes);
			} catch (NoSuchMethodException ex) {
				current = current.getSuperclass();
			}
		}
		return null;
	}
}
