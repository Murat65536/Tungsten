package kaptainwutax.tungsten.path;

import org.jetbrains.annotations.NotNull;

public record PathInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak,
                        boolean sprint, float pitch, float yaw) {

	@Override
	public @NotNull String toString() {

        return "{\n" +
                "forward: " +
                forward +
                "\n" +
                "back: " +
                back +
                "\n" +
                "right: " +
                right +
                "\n" +
                "left: " +
                left +
                "\n" +
                "jump: " +
                jump +
                "\n" +
                "sneak: " +
                sneak +
                "\n" +
                "sprint: " +
                sprint +
                "\n" +
                "pitch: " +
                pitch +
                "\n" +
                "yaw: " +
                yaw +
                "\n" +
                "}";
	}

}
