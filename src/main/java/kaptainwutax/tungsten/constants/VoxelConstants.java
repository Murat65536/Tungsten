package kaptainwutax.tungsten.constants;

/**
 * Voxel world constants for chunk storage and indexing.
 * These values control the chunk dimensions and bit manipulation for 3D array indexing.
 */
public final class VoxelConstants {

    private VoxelConstants() {} // Prevent instantiation

    public static final int CHUNK_SIZE_CUBED = 32 * 32 * 32;

    // ========== Bit Shift Operations ==========
    // For converting 3D coordinates to 1D array index
    public static final int CHUNK_SHIFT_Y = 10; // For Y coordinate (32*32 = 1024 = 2^10)
    public static final int CHUNK_SHIFT_X = 5;  // For X coordinate (32 = 2^5)

    // ========== Bit Masks ==========
    public static final int CHUNK_MASK = 31; // For masking to 0-31 range (0x1F)

}