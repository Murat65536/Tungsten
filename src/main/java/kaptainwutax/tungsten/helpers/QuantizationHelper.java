package kaptainwutax.tungsten.helpers;

import kaptainwutax.tungsten.constants.pathfinding.PathfindingConstants;
import net.minecraft.util.math.Vec3d;

/**
 * Utility for quantizing continuous position/velocity values into discrete buckets
 * for hash-based deduplication in the pathfinder's closed set.
 */
public final class QuantizationHelper {

    private QuantizationHelper() {}

    public static int quantizePos(double value) {
        return (int) Math.round(value * PathfindingConstants.ClosedSetScale.POSITION_ROUNDING);
    }

    public static int quantizeVel(double value) {
        return (int) Math.round(value * PathfindingConstants.ClosedSetScale.VELOCITY_ROUNDING);
    }

    /** Returns [qx, qy, qz] for position. */
    public static int[] quantizePosition(Vec3d pos) {
        if (pos == null) return new int[]{0, 0, 0};
        return new int[]{quantizePos(pos.x), quantizePos(pos.y), quantizePos(pos.z)};
    }

    /** Returns [qvx, qvy, qvz] for velocity. */
    public static int[] quantizeVelocity(double vx, double vy, double vz) {
        return new int[]{quantizeVel(vx), quantizeVel(vy), quantizeVel(vz)};
    }

    /** Checks whether two positions are equal after quantization. */
    public static boolean positionsEqual(Vec3d a, Vec3d b) {
        if (a == null || b == null) return a == b;
        return quantizePos(a.x) == quantizePos(b.x)
            && quantizePos(a.y) == quantizePos(b.y)
            && quantizePos(a.z) == quantizePos(b.z);
    }

    /** Checks whether two velocity vectors are equal after quantization. */
    public static boolean velocitiesEqual(double ax, double ay, double az, double bx, double by, double bz) {
        return quantizeVel(ax) == quantizeVel(bx)
            && quantizeVel(ay) == quantizeVel(by)
            && quantizeVel(az) == quantizeVel(bz);
    }
}
