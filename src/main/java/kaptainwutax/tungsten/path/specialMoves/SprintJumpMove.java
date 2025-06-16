package kaptainwutax.tungsten.path.specialMoves;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.Node;
import kaptainwutax.tungsten.path.PathInput;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.world.WorldView;

public class SprintJumpMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		double cost = 0.02;
		WorldView world = TungstenMod.mc.world;
		Agent agent = parent.agent;
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
		double closestDistance = Double.MAX_VALUE;
	    Node newNode = new Node(parent, world, new PathInput(false, false, false, false, false, false, false, parent.agent.pitch, desiredYaw),
	    				new Color(0, 255, 150), parent.cost + 0.1);
		int limit = 0;
		Node lastHigheastNodeSinceGround = null;
        // Run forward to the node
//		TungstenMod.RENDERERS.clear();
		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
		while (distance > 0.3 && limit < 80 && !newNode.agent.horizontalCollision && !newNode.agent.isInLava() || (distance <= 0.3 && !newNode.agent.onGround)) {
//        	RenderHelper.renderNode(newNode);
//        	try {
//				Thread.sleep(50);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
        	
        	if (closestDistance > distance) {
        		closestDistance = distance;
        	} else {
        		break;
        	}

			if (newNode.agent.onGround || lastHigheastNodeSinceGround != null && lastHigheastNodeSinceGround.agent.getPos().y < newNode.agent.getPos().y) {
				lastHigheastNodeSinceGround = newNode;
			} else if (lastHigheastNodeSinceGround != null
					&& (!TungstenMod.ignoreFallDamage
					&& !BlockStateChecker.isAnyWater(world.getBlockState(newNode.agent.getLandingPos(world))))
					&& DistanceCalculator.getJumpHeight(lastHigheastNodeSinceGround.agent.getPos().y, newNode.agent.getPos().y) < -3) {
				newNode = new Node(newNode, world, new PathInput(true, false, false, false, distance > 1.2, false, true, parent.agent.pitch, desiredYaw),
	            		new Color(255, 0, 0), newNode.cost + cost);
				break;
			}
			
        	limit++;
    		distance = DistanceCalculator.getHorizontalEuclideanDistance(newNode.agent.getPos(), nextBlockNode.getPos(true));
            newNode = new Node(newNode, world, new PathInput(true, false, false, false, distance > 1.2 && newNode.agent.onGround, false, true, parent.agent.pitch, desiredYaw),
            		new Color(0, 255, 150), newNode.cost + cost);
            
        }
            
        return newNode;
	}

}
