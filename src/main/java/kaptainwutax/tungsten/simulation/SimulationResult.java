package kaptainwutax.tungsten.simulation;

import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.Vec3d;

public final class SimulationResult {
	private final List<Vec3d> positions;
	private final List<Vec3d> velocities;

	public SimulationResult(List<Vec3d> positions, List<Vec3d> velocities) {
		this.positions = List.copyOf(positions);
		this.velocities = List.copyOf(velocities);
	}

	public List<Vec3d> getPositions() {
		return Collections.unmodifiableList(positions);
	}

	public List<Vec3d> getVelocities() {
		return Collections.unmodifiableList(velocities);
	}
}
