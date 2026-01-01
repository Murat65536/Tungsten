package kaptainwutax.tungsten.path;

public record PathInput(boolean forward, boolean back, boolean left, boolean right, boolean sneak, boolean sprint,
                        boolean jump,
                        float pitch, float yaw) {

}
