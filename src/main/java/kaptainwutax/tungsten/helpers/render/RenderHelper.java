package kaptainwutax.tungsten.helpers.render;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.path.Node;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Line;
import kaptainwutax.tungsten.render.Renderer;
import net.minecraft.util.math.Vec3d;

/**
 * Helper class to easily render out paths and nodes.
 */
public class RenderHelper {

	public static void renderBlockPath(List<BlockNode> nodes, int nextNodeIDX) {
		TungstenMod.BLOCK_PATH_RENDERER.clear();
//		TungstenMod.RENDERERS.clear();
//		TungstenMod.TEST.clear();
		BlockNode previous = null;
		for (Iterator<BlockNode> iterator = nodes.iterator(); iterator.hasNext();) {
			BlockNode node = iterator.next();
			
			if (previous != null)
			TungstenMod.BLOCK_PATH_RENDERER.add(new Line(new Vec3d(previous.x + 0.5, previous.y + 0.1, previous.z + 0.5), new Vec3d(node.x + 0.5, node.y + 0.1, node.z + 0.5), Color.RED));
			if (nodes.size() <= nextNodeIDX) nextNodeIDX = nodes.size()-1;
            TungstenMod.BLOCK_PATH_RENDERER.add(new Cuboid(node.getPos(true).subtract(0.1, 0, 0.1), new Vec3d(0.2D, 0.2D, 0.2D), 
            		(nodes.get(nextNodeIDX).equals(node)) ? Color.WHITE : Color.BLUE
            		));
            previous = node;
//            TungstenMod.BLOCK_PATH_RENDERER.add(new Cuboid(new Vec3d(node.getBlockPos().getX(), node.getBlockPos().getY(), node.getBlockPos().getZ()), new Vec3d(1.0D, 1.0D, 1.0D), 
//            		(nodes.get(NEXT_CLOSEST_BLOCKNODE_IDX).equals(node)) ? Color.WHITE : Color.BLUE
//            		));
		}
	}
	
	public static void renderPathCurrentlyExecuted() {
		TungstenMod.RUNNING_PATH_RENDERER.clear();
		TungstenMod.RENDERERS.clear();
		TungstenMod.TEST.clear();
		if (TungstenMod.EXECUTOR == null || TungstenMod.EXECUTOR.getPath() == null || !TungstenMod.EXECUTOR.isRunning()) return;
		Node n = TungstenMod.EXECUTOR.getPath().getLast();
		while (n.parent != null) {
			TungstenMod.RUNNING_PATH_RENDERER.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
			if (TungstenMod.renderPositonBoxes) {				
				TungstenMod.RUNNING_PATH_RENDERER.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
			}
			n = n.parent;
		}
	}
	
	public static void renderPathSoFar(BlockNode n) {
		TungstenMod.RENDERERS.clear();
		TungstenMod.RENDERERS.add(new Cuboid(n.getPos(true).subtract(0.1, 0, 0.1), new Vec3d(0.2D, 0.2D, 0.2D), Color.RED));
		while(n.previous != null) {
			TungstenMod.RENDERERS.add(new Line(new Vec3d(n.previous.x + 0.5, n.previous.y + 0.1, n.previous.z + 0.5), new Vec3d(n.x + 0.5, n.y + 0.1, n.z + 0.5), Color.WHITE));
			n = n.previous;
		}
	}
	
	public static void renderPathSoFar(Node n) {
		TungstenMod.RENDERERS.clear();
		TungstenMod.RENDERERS.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), Color.RED));
		while(n.parent != null) {
			TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
			n = n.parent;
		}
	}
	
	public static void renderNode(Node n) {
		TungstenMod.RENDERERS.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
	}
	
	public static void renderNode(Node n, Collection<Renderer> renderer) {
		renderer.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
	}
	
	public static void renderNodeConnection(Node child, Node parent) {
	    TungstenMod.RUNNING_PATH_RENDERER.add(new Line(child.agent.getPos(), parent.agent.getPos(), child.color));
	    if (TungstenMod.renderPositonBoxes) {
	    	TungstenMod.RUNNING_PATH_RENDERER.add(new Cuboid(child.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), child.color));
	    }
	}
	
	public static void renderNodeConnection(BlockNode child, BlockNode parent) {
		TungstenMod.RENDERERS.add(new Line(new Vec3d(parent.x + 0.5, parent.y + 0.1, parent.z + 0.5), new Vec3d(child.x + 0.5, child.y + 0.1, child.z + 0.5), Color.RED));
        TungstenMod.RENDERERS.add(new Cuboid(child.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.BLUE));
	}
	
	public static void clearRenderers() {
	    TungstenMod.RENDERERS.clear();
//	    TungstenMod.RUNNING_PATH_RENDERER.clear();
	    TungstenMod.BLOCK_PATH_RENDERER.clear();
	    TungstenMod.TEST.clear();
	}
}
