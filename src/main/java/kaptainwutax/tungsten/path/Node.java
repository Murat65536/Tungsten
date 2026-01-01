package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.List;

import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.constants.PathfindingConstants;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

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

    public Node[] getChildren(WorldView world, Vec3d target) {
        int length = this.agent.onGround || this.agent.touchingWater ? PathfindingConstants.ALL_INPUTS.length : PathfindingConstants.ALL_INPUTS.length / 2;
        Node[] children = new Node[length];
        for (int i = 0; i < length; i++) {
            children[i] = new Node(this, world, PathfindingConstants.ALL_INPUTS[i], new Color(0, 255, 0), this.cost);
        }
        return children;
    }
}
