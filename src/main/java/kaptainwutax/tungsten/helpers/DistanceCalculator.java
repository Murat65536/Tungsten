package kaptainwutax.tungsten.helpers;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class DistanceCalculator {
	
	public static double getHorizontalDistanceSquared(BlockPos startPos, BlockPos endPos) {
		return getHorizontalDistanceSquared(new Vec3d(startPos.getX(), startPos.getY(), startPos.getZ()), new Vec3d(endPos.getX(), endPos.getY(), endPos.getZ()));
	}
	
	public static double getHorizontalDistanceSquared(Vec3d startPos, Vec3d endPos) {
		double dx = startPos.getX() - endPos.getX();
    	double dz = startPos.getZ() - endPos.getZ();
    	return Math.sqrt(dx * dx + dz * dz);
	}
	
	public static double getHorizontalDistance(BlockPos startPos, BlockPos endPos) {
		return getHorizontalDistance(new Vec3d(startPos.getX(), startPos.getY(), startPos.getZ()), new Vec3d(endPos.getX(), endPos.getY(), endPos.getZ()));
	}
	
	public static double getHorizontalDistance(Vec3d startPos, Vec3d endPos) {
		double dx = startPos.getX() - endPos.getX();
    	double dz = startPos.getZ() - endPos.getZ();
    	return dx + dz;
	}

}
