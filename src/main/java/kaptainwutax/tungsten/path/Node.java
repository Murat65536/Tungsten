package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.List;

import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.constants.PathfindingConstants;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.NotNull;

public class Node {

    public Node parent;
    public Agent agent;
    public PathInput input;
    public double cost;
    public double heuristic;
    public double estimatedCostToGoal = 0;
    public int heapPosition;
    public double combinedCost;
    public Color color;

    public Node(Node parent, Agent agent, Color color, double pathCost) {
        this.parent = parent;
        this.agent = agent;
        this.color = color;
        this.cost = pathCost;
        this.heuristic = 0;
        this.heapPosition = -1;
    }

    public Node(Node parent, WorldView world, PathInput input, Color color, double pathCost) {
        this.parent = parent;
        this.agent = Agent.of(parent.agent, input).tick(world);
        this.input = input;
        this.color = color;
        this.cost = pathCost;
        this.heuristic = 0;
        this.heapPosition = -1;
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    public List<Node> getChildren(WorldView world, Vec3d target) {
        List<Node> nodes = new ArrayList<>();;
        if (this.agent.onGround || this.agent.touchingWater) {
	        for (boolean forward : new boolean[]{true, false}) {
		        for (boolean back : new boolean[]{true, false}) {
			        for (boolean right : new boolean[]{true, false}) {
				        for (boolean left : new boolean[]{true, false}) {
					        for (boolean sneak : new boolean[]{true, false}) {
						        for (float yaw = PathfindingConstants.YAW_MIN; yaw <= PathfindingConstants.YAW_MAX; yaw += PathfindingConstants.YAW_INCREMENT) {
							        for (boolean sprint : new boolean[]{true, false}) {
								        for (boolean jump : new boolean[]{true, false}) {
									        Node newNode = new Node(this, world, new PathInput(forward, back, right, left, jump, sneak, sprint, agent.pitch, yaw), new Color(0, 255, 0), this.cost + (jump ? sneak ? 4 : 0.5 : sneak ? 4 : 2));
									        nodes.add(newNode);
								        }
							        }
						        }
					        }
				        }
			        }
		        }
	        }
        } else {
            nodes.add(
                    new Node(this, world, new PathInput(true, false, false, false, false, false, true, this.agent.pitch, this.agent.yaw), new Color(0, 255, 255), this.cost + PathfindingConstants.UNIFORM_COST_PER_STEP)
            );
        }
        return nodes;
    }
}
