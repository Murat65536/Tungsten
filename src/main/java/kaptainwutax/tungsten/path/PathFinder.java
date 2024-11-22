package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.Goal;
import kaptainwutax.tungsten.path.calculators.BinaryHeapOpenSet;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Line;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class PathFinder {

	public boolean active = false;
	public boolean stop = false;
	public Thread thread = null;
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final Node[] bestSoFar = new Node[COEFFICIENTS.length];
	private static final double minimumImprovement = 0.21;
	protected static final double MIN_DIST_PATH = 5;
	protected static int NEXT_CLOSEST_BLOCKNODE_IDX = 1;
	
	
	public void find(WorldView world, Vec3d target) {
		if(active)return;
		active = true;
		NEXT_CLOSEST_BLOCKNODE_IDX = 1;

		thread = new Thread(() -> {
			try {
				NEXT_CLOSEST_BLOCKNODE_IDX = 1;
				search(world, target);
			} catch(Exception e) {
				e.printStackTrace();
			}

			active = false;
		});
		thread.start();
	}

	private void search(WorldView world, Vec3d target) {
		boolean failing = true;
		TungstenMod.RENDERERS.clear();
		NEXT_CLOSEST_BLOCKNODE_IDX = 1;
		long startTime = System.currentTimeMillis();
		long primaryTimeoutTime = startTime + 2500000L;
		int numNodesConsidured = 0;
		int timeCheckInterval = 1 << 3;
		ClientPlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);
		if (player.getPos().distanceTo(target) < 1.0) {
			Debug.logMessage("Already at target location!");
			return;
		}
		
		Node start = new Node(null, Agent.of(player), null, 0);
		start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target);
		
		Optional<List<BlockNode>> blockPath = kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, target);
		
		double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];//keep track of the best node by the metric of (estimatedCostToGoal + cost / COEFFICIENTS[i])
		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.heuristic;
            bestSoFar[i] = start;
        }
		BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
		Set<Vec3d> closed = new HashSet<>();
		openSet.insert(start);
		while(!openSet.isEmpty()) {
			if(TungstenMod.pauseKeyBinding.isPressed() || stop) break;
			TungstenMod.RENDERERS.clear();
			 renderPathCurrentlyExecuted();
			if (blockPath.isPresent()) renderBlockPath(blockPath.get());
			Node next = openSet.removeLowest();
			if (shouldNodeBeSkiped(next, target, closed, true)) continue;

			
			if(TungstenMod.pauseKeyBinding.isPressed()) break;
			double minVel = 0.07;
			if(next.agent.getPos().squaredDistanceTo(target) <= 0.1D && !failing /*|| !failing && (startTime + 5000) - System.currentTimeMillis() <= 0*/) {
				TungstenMod.RENDERERS.clear();
				if (blockPath.isPresent()) renderBlockPath(blockPath.get());
				Node n = next;
				List<Node> path = new ArrayList<>();
				while(n.parent != null) {
					path.add(n);
					TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
					TungstenMod.RENDERERS.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
					n = n.parent;
				}

				path.add(n);
				Collections.reverse(path);
				if (!TungstenMod.EXECUTOR.isRunning() 
						&& path.get(path.size()-1).agent.velX < minVel && path.get(path.size()-1).agent.velX > -minVel && path.get(path.size()-1).agent.velZ < minVel && path.get(path.size()-1).agent.velZ > -minVel
						) {					
					TungstenMod.EXECUTOR.cb = () -> {
						Debug.logMessage("Finished!");
						TungstenMod.RENDERERS.clear();
	        			TungstenMod.TEST.clear();
					};
					TungstenMod.EXECUTOR.setPath(path);
					stop = true;
					return;
				}
			} 
			if ((numNodesConsidured & (timeCheckInterval - 1)) == 0 && NEXT_CLOSEST_BLOCKNODE_IDX > blockPath.get().size() - 10 && !TungstenMod.EXECUTOR.isRunning() && blockPath.get().getLast().getPos().squaredDistanceTo(next.agent.getPos()) < 3.0D && blockPath.get().getLast().getPos().squaredDistanceTo(target) > 1.0D ) {
        			numNodesConsidured = 0;
//	        		if (next.agent.onGround) {
	        			Node n = next;
	    				List<Node> path = new ArrayList<>();
	    				while(n.parent != null) {
	    					path.add(n);
	    					TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
	    					TungstenMod.RENDERERS.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
	    					n = n.parent;
	    				}

	    				path.add(n);
	    				Collections.reverse(path);
	    				minVel = 0.1;
	    				if (path.get(path.size()-1).agent.velX < minVel && path.get(path.size()-1).agent.velX > -minVel && path.get(path.size()-1).agent.velZ < minVel && path.get(path.size()-1).agent.velZ > -minVel) {					
		        			closed = new HashSet<>();
		        			TungstenMod.EXECUTOR.setPath(path);
			            	openSet = new BinaryHeapOpenSet();
			        		openSet.insert(new Node(null, next.agent, null, 0));
			        		start = new Node(null, next.agent, null, 0);
			        		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
			                    bestHeuristicSoFar[i] = start.heuristic;
			                    bestSoFar[i] = start;
			                }
		                	Debug.logMessage("Searching new block path");
		                	NEXT_CLOSEST_BLOCKNODE_IDX = 1;
							blockPath = kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, new BlockNode(start.agent.getBlockPos(), new Goal((int) target.x, (int) target.y, (int) target.z)), target);
							continue;
	    				}
//	        		} else {
//	        			player.sendMessage(Text.literal("Failed!"));
//	                	NEXT_CLOSEST_BLOCKNODE_IDX = 1;
//						blockPath = kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, target);
//		            	openSet = new BinaryHeapOpenSet();
//		        		openSet.insert(new Node(null, Agent.of(player), null, 0));
//	        			closed = new HashSet<>();
//	                    break;
//	        		}
				}
			if(TungstenMod.RENDERERS.size() > 9000) {
				TungstenMod.RENDERERS.clear();
			}
			if ((numNodesConsidured & (timeCheckInterval - 1)) == 0) { // only call this once every 64 nodes (about half a millisecond)
                long now = System.currentTimeMillis(); // since nanoTime is slow on windows (takes many microseconds)
        		Optional<List<Node>> result = bestSoFar(true, numNodesConsidured, start);
                if (((!failing && now - primaryTimeoutTime >= 0))) {
					numNodesConsidured = 0;
                	if (result.isPresent() && result.get().getLast().input != null) {
                    	Debug.logMessage("Time ran out");
	            		if (player.getPos().distanceTo(result.get().getFirst().agent.getPos()) < 1) {
		            		if (result.isPresent()) {
		                    	Debug.logMessage("Path set!");
		            			TungstenMod.EXECUTOR.setPath(result.get());
		            		} else {
		            			Debug.logWarning("Failed!");
		                        break;
		            		}
		                	openSet = new BinaryHeapOpenSet();
		            		openSet.insert(new Node(null, Agent.of(
		            				result.get().getLast().agent,
		            				result.get().getLast().input.forward,
		            				result.get().getLast().input.back,
		            				result.get().getLast().input.left,
		            				result.get().getLast().input.right,
		            				result.get().getLast().input.jump,
		            				result.get().getLast().input.sneak,
		            				result.get().getLast().input.sprint,
		            				result.get().getLast().input.pitch,
		            				result.get().getLast().input.yaw
		            				), null, 0));
		            		closed = new HashSet<>();
		            		startTime = System.currentTimeMillis() - 200;
		            		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
		                        bestHeuristicSoFar[i] = start.heuristic;
		                        bestSoFar[i] = start;
		                    }
		            		continue;
	            		}
            		}
                }
            }
			numNodesConsidured++;
			 renderPathSoFar(next);
			 renderPathCurrentlyExecuted();

			 TungstenMod.RENDERERS.add(new Cuboid(next.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), Color.RED));
			 
//			 try {
//                 if(next.agent.onGround) Thread.sleep(200);
//             } catch (InterruptedException ignored) {}
			 
			for(Node child : next.getChildren(world, target)) {
				if(stop) break;
				if (player.getHungerManager().getFoodLevel() <= 6 && (child.input.sprint)) continue;
//				if (!child.agent.isSubmergedInWater && !child.agent.isClimbing(world) && shouldNodeBeSkiped(child, target, closed)) continue;
//				if(closed.contains(child.agent.getPos()))continue;
				
				// DUMB HEURISTIC CALC
//				child.heuristic = child.pathCost / child.agent.getPos().distanceTo(start.agent.getPos()) * child.agent.getPos().distanceTo(target);

				// NOT SO DUMB HEURISTIC CALC
//				double heuristic = 20.0D * child.agent.getPos().distanceTo(target);
//				
//				if (child.agent.horizontalCollision) {
//		            //massive collision punish
//		            double d = 25+ (Math.abs(next.agent.velZ-child.agent.velY)+Math.abs(next.agent.velX-child.agent.velX))*120;
//		            heuristic += d;
//		        }
//				
//				child.heuristic = heuristic;
				
				// AStar? HEURISTIC CALC
//				if (next.agent.getPos().distanceTo(child.agent.getPos()) < 0.2) continue;
				updateNode(world, next, child, target, blockPath.get());
				
                if (child.isOpen()) {
                    openSet.update(child);
                } else {
                    openSet.insert(child);//dont double count, dont insert into open set if it's already there
                }
                
                failing = updateBestSoFar(child, bestHeuristicSoFar, target);

		        
//				open.add(child);

//				TungstenMod.RENDERERS.add(new Line(child.agent.getPos(), child.parent.agent.getPos(), child.color));
				TungstenMod.RENDERERS.add(new Cuboid(child.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), child.color));
			}
		}
		if (stop) {
			stop = false;
			return;
		}
		if (openSet.isEmpty()) Debug.logWarning("Ran out of nodes!");
	}
	
	protected static Optional<List<Node>> bestSoFar(boolean logInfo, int numNodes, Node startNode) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = 0;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            if (bestSoFar[i] == null) {
                continue;
            }
            double dist = computeHeuristic(startNode.agent.getPos(), startNode.agent.onGround || startNode.agent.slimeBounce, bestSoFar[i].agent.getPos());
            if (dist > bestDist) {
                bestDist = dist;
            }
//            if (dist < MIN_DIST_PATH * MIN_DIST_PATH) { // square the comparison since distFromStartSq is squared
                if (logInfo) {
                    if (COEFFICIENTS[i] >= 3) {
                        System.out.println("Warning: cost coefficient is greater than three! Probably means that");
                        System.out.println("the path I found is pretty terrible (like sneak-bridging for dozens of blocks)");
                        System.out.println("But I'm going to do it anyway, because yolo");
                    }
                    System.out.println("Path goes for " + Math.sqrt(dist) + " blocks");
                }
                Node n = bestSoFar[i];
                if (!n.agent.onGround) continue;
                List<Node> path = new ArrayList<>();

				while(n.parent != null) {
					path.add(n);
					TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
					TungstenMod.RENDERERS.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
					n = n.parent;
				}

				path.add(n);
				Collections.reverse(path);
                return Optional.of(path);
            }
//        }
        return Optional.empty();
    }
	
	private static boolean shouldNodeBeSkiped(Node n, Vec3d target, Set<Vec3d> closed) {
		return shouldNodeBeSkiped(n, target, closed, false);
	}
	
	private static boolean shouldNodeBeSkiped(Node n, Vec3d target, Set<Vec3d> closed, boolean addToClosed) {
		if (n.agent.getPos().distanceTo(target) < 1.0 /*|| n.agent.isSubmergedInWater*/ /*|| n.agent.isClimbing(MinecraftClient.getInstance().world)*/) {
			if(closed.contains(new Vec3d(Math.round(n.agent.getPos().x*1000), Math.round(n.agent.getPos().y * 1000), Math.round(n.agent.getPos().z*1000)))) return true;
			if (addToClosed) closed.add(new Vec3d(Math.round(n.agent.getPos().x*1000), Math.round(n.agent.getPos().y * 1000), Math.round(n.agent.getPos().z*1000)));
		} else if(closed.contains(new Vec3d(Math.round(n.agent.getPos().x*100), Math.round(n.agent.getPos().y * 100), Math.round(n.agent.getPos().z*100)))) return true;
		if (addToClosed) closed.add(new Vec3d(Math.round(n.agent.getPos().x*100), Math.round(n.agent.getPos().y * 100), Math.round(n.agent.getPos().z*100)));
		
		return false;
	}
	
	private static double computeHeuristic(Vec3d position, boolean onGround, Vec3d target) {
	    double dx = position.x - target.x;
	    double dy = 0;
	    if (target.y != Double.MIN_VALUE) {
		    dy = (position.y - target.y) * 3.6;//*16;
		    if (!onGround || dy < 1.6 && dy > -1.6) dy = 0;
	    }
	    double dz = position.z - target.z;
	    return (Math.sqrt(dx * dx + dy * dy + dz * dz) + ((NEXT_CLOSEST_BLOCKNODE_IDX - 1) * -20));
	}
	
	private static void updateNode(WorldView world, Node current, Node child, Vec3d target, List<BlockNode> blockPath) {
	    Vec3d childPos = child.agent.getPos();

	    double collisionScore = 0;
	    double tentativeCost = child.cost + 1; // Assuming uniform cost for each step
	    if (child.agent.horizontalCollision) {
	        collisionScore += 2500 + (Math.abs(current.agent.velZ - child.agent.velZ) + Math.abs(current.agent.velX - child.agent.velX)) * 120;
	    }
	    if (child.agent.touchingWater) {
//	    	collisionScore = 20000^20;
	    	collisionScore += 2000;
	    }
	    if (child.agent.isClimbing(MinecraftClient.getInstance().world)) {
//	    	collisionScore *= 20000;
	    	collisionScore += 200;
	    }
	    if (child.agent.slimeBounce) {
	    	collisionScore -= 20000;
	    }

	    double estimatedCostToGoal = /*computeHeuristic(childPos, child.agent.onGround, target) - 200 +*/ collisionScore;
	    if (blockPath != null) {
	    	int closestPosIDX = findClosestPositionIDX(world, new BlockPos(child.agent.blockX, child.agent.blockY, child.agent.blockZ), blockPath);
	    	BlockNode closestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX);
//	    	if (closestPosIDX+1 - NEXT_CLOSEST_BLOCKNODE_IDX <= 2) {
		    	if (closestPosIDX+1 > NEXT_CLOSEST_BLOCKNODE_IDX && closestPosIDX +1 < blockPath.size() && child.agent.getPos().distanceTo(blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX).getPos()) < 1.0) {
		    		NEXT_CLOSEST_BLOCKNODE_IDX = closestPosIDX+1;
			    	closestPos = blockPath.get(closestPosIDX+1);
		    	}
//	    	}	    	    	
	    	estimatedCostToGoal +=  computeHeuristic(childPos, child.agent.onGround || child.agent.slimeBounce, new Vec3d(closestPos.x + 0.5, closestPos.y, closestPos.z + 0.5)) * 600.5;
	    }

//	    child.parent = current;
	    child.cost = tentativeCost;
	    child.estimatedCostToGoal = estimatedCostToGoal;
	    child.combinedCost = tentativeCost + estimatedCostToGoal;
	}
	
	private static BlockNode findClosestPosition(WorldView world, BlockPos current, List<BlockNode> positions) {
		return positions.get(findClosestPositionIDX(world, current, positions));
	}
	private static int findClosestPositionIDX(WorldView world, BlockPos current, List<BlockNode> positions) {
        if (positions == null || positions.isEmpty()) {
            throw new IllegalArgumentException("The list of positions must not be null or empty.");
        }

        int closestIDX = NEXT_CLOSEST_BLOCKNODE_IDX;
        BlockNode closest = positions.get(closestIDX);
        double minDistance = current.getSquaredDistance(closest.getPos())/* + Math.abs(closest.y - current.getY()) * 160*/;
        for (int i = closestIDX+1; i < positions.size(); i++) {
        	BlockNode position = positions.get(i);
//			if (i % 5 != 0) {
//        		continue;
//        	}
            double distance = current.getSquaredDistance(position.getPos())/* + Math.abs(position.y - current.getY()) * 160*/;
            if ( distance < 1) continue;
            if (distance < minDistance
            		&& position.wasCleared(world, position.getBlockPos(), current)
            		) {
                minDistance = distance;
                closest = position;
                closestIDX = i;
            }
		}
        return closestIDX;
    }
	
	private static boolean updateBestSoFar(Node child, double[] bestHeuristicSoFar, Vec3d target) {
		boolean failing = false;
	    for (int i = 0; i < COEFFICIENTS.length; i++) {
	        double heuristic = child.estimatedCostToGoal + child.cost / COEFFICIENTS[i];
	        if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
	            bestHeuristicSoFar[i] = heuristic;
	            bestSoFar[i] = child;
	            if (failing && getDistFromStartSq(child, target) > MIN_DIST_PATH * MIN_DIST_PATH) {
                    failing = false;
                }
	        }
	    }
	    return failing;
	}
	
	protected static double getDistFromStartSq(Node n, Vec3d target) {
        double xDiff = n.agent.getPos().x - target.x;
        double yDiff = n.agent.getPos().y - target.y;
        double zDiff = n.agent.getPos().z - target.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }

	public static double calcYawFromVec3d(Vec3d orig, Vec3d dest) {
        double[] delta = {orig.x - dest.x, orig.y - dest.y, orig.z - dest.z};
        double yaw = Math.atan2(delta[0], -delta[2]);
        return yaw * 180.0 / Math.PI;
    }
	
	private static Direction getHorizontalDirectionFromYaw(double yaw) {
        yaw %= 360.0F;
        if (yaw < 0) {
            yaw += 360.0F;
        }

        if ((yaw >= 45 && yaw < 135) || (yaw >= -315 && yaw < -225)) {
            return Direction.WEST;
        } else if ((yaw >= 135 && yaw < 225) || (yaw >= -225 && yaw < -135)) {
            return Direction.NORTH;
        } else if ((yaw >= 225 && yaw < 315) || (yaw >= -135 && yaw < -45)) {
            return Direction.EAST;
        } else {
            return Direction.SOUTH;
        }
    }
	
	private static void renderBlockPath(List<BlockNode> nodes) {
		for (Iterator<BlockNode> iterator = nodes.iterator(); iterator.hasNext();) {
			BlockNode node = iterator.next();
			
			if (node.previous != null)
			TungstenMod.RENDERERS.add(new Line(new Vec3d(node.previous.x + 0.5, node.previous.y + 0.1, node.previous.z + 0.5), new Vec3d(node.x + 0.5, node.y + 0.1, node.z + 0.5), Color.RED));
			if (nodes.size() <= NEXT_CLOSEST_BLOCKNODE_IDX) NEXT_CLOSEST_BLOCKNODE_IDX = nodes.size()-1;
            TungstenMod.RENDERERS.add(new Cuboid(node.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), 
            		(nodes.get(NEXT_CLOSEST_BLOCKNODE_IDX).equals(node)) ? Color.WHITE : Color.BLUE
            		));
	}
	}
	
	private static void renderPathSoFar(Node n) {
		int i = 0;
		while(n.parent != null) {
			TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
			i++;
			n = n.parent;
		}
	}
	
	public static void renderPathCurrentlyExecuted() {
		if (TungstenMod.EXECUTOR == null || TungstenMod.EXECUTOR.getPath() == null || !TungstenMod.EXECUTOR.isRunning()) return;
		Node n = TungstenMod.EXECUTOR.getPath().getLast();
		while (n.parent != null) {
			TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
			TungstenMod.RENDERERS.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
			n = n.parent;
		}
		
	}
	
}
