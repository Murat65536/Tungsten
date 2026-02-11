package kaptainwutax.tungsten.helpers;

import kaptainwutax.tungsten.simulation.SimulatedPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Helper class to easily calculate distance.
 */
public class DistanceCalculator {
	
	/**
     * Calculates the horizontal Euclidean distance from startPos to endPos.
     * 
     * @param startPos start position
     * @param endPos end position
     * @return horizontal Euclidean distance from startPos to endPos.
     */
	public static double getHorizontalEuclideanDistance(BlockPos startPos, BlockPos endPos) {
		return getHorizontalEuclideanDistance(new Vec3d(startPos.getX(), startPos.getY(), startPos.getZ()), new Vec3d(endPos.getX(), endPos.getY(), endPos.getZ()));
	}
	
	/**
     * Calculates the horizontal Euclidean distance from startPos to endPos.
     * 
     * @param startPos start position
     * @param endPos end position
     * @return horizontal Manhattan distance from startPos to endPos.
     */
	public static double getHorizontalEuclideanDistance(Vec3d startPos, Vec3d endPos) {
		double dx = endPos.getX() - startPos.getX();
    	double dz = endPos.getZ() - startPos.getZ();
    	return Math.sqrt(dx * dx + dz * dz);
	}
	
	/**
     * Calculates the horizontal Manhattan distance from startPos to endPos.
     * 
     * @param startPos start position
     * @param endPos end position
     * @return horizontal Manhattan distance from startPos to endPos.
     */
	public static double getHorizontalManhattanDistance(BlockPos startPos, BlockPos endPos) {
		return getHorizontalManhattanDistance(new Vec3d(startPos.getX(), startPos.getY(), startPos.getZ()), new Vec3d(endPos.getX(), endPos.getY(), endPos.getZ()));
	}
	
	/**
     * Calculates the horizontal Manhattan distance from startPos to endPos.
     * 
     * @param startPos start position
     * @param endPos end position
     * @return horizontal Manhattan distance from startPos to endPos.
     */
	public static double getHorizontalManhattanDistance(Vec3d startPos, Vec3d endPos) {
		double dx = endPos.getX() - startPos.getX();
    	double dz = endPos.getZ() - startPos.getZ();
    	return dx + dz;
	}
	
	/**
     * Calculates the distance to the edge of the block the player is standing on in the direction they're looking.
     * 
     * @param agent The simulated player
     * @return distance to the edge of the block in the look direction
     */
    public static double getDistanceToEdge(SimulatedPlayer agent) {
        Vec3d position = agent.getPos(); // Get player's position
        BlockPos blockPos = agent.getBlockPos(); // Block the player is standing on
		float f = agent.pitch * (float) (Math.PI / 180.0);
		float g = -agent.yaw * (float) (Math.PI / 180.0);
		float h = MathHelper.cos(g);
		float i = MathHelper.sin(g);
		float j = MathHelper.cos(f);
		float k = MathHelper.sin(f);
		Vec3d lookDirection =  new Vec3d(i * j, -k, h * j); // Direction player is looking

        // Determine block bounds relative to the player's position
        double deltaX = position.x - blockPos.getX();
        double deltaZ = position.z - blockPos.getZ();

        // Calculate edge distance based on facing direction
        double distance;
        if (Math.abs(lookDirection.x) > Math.abs(lookDirection.z)) {
            // Looking more along the X axis
            if (lookDirection.x > 0) {
                distance = 1.0 - deltaX; // Distance to east edge
            } else {
                distance = deltaX; // Distance to west edge
            }
        } else {
            // Looking more along the Z axis
            if (lookDirection.z > 0) {
                distance = 1.0 - deltaZ; // Distance to south edge
            } else {
                distance = deltaZ; // Distance to north edge
            }
        }

        return MathHelper.clamp(distance, 0.0, 1.0); // Ensure distance is within block bounds
    }
    
    /**
	 * Returns jump height.
	 * 
	 * @param from
	 * @param to
	 * @return positive is going up and negative is going down
	 */
	public static double getJumpHeight(double from, double to) {
		
		double diff = to - from;
		
		// if `to` is higher then `from` return value should be positive
		if (to > from) {
			return diff > 0 ? diff : diff * -1;
		}
		return diff > 0 ? diff * -1 : diff;
	}

    /**
	 * Returns jump height.
	 * 
	 * @param from
	 * @param to
	 * @return positive is going up and negative is going down
	 */
	public static int getJumpHeight(int from, int to) {
		
		int diff = to - from;
		
		// if `to` is higher then `from` return value should be positive
		if (to > from) {
			return diff > 0 ? diff : diff * -1;
		}
		return diff > 0 ? diff * -1 : diff;
	}
}
