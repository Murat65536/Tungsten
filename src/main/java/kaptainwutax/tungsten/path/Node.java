package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Line;
import kaptainwutax.tungsten.world.BetterBlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
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
	 
	 public int hashCode() {
		 return (int) hashCode(1);
	 }
	 
	 public int hashCode(int round) {
		 long result = 3241;
		 if (this.input != null) {
		 	result = Boolean.hashCode(this.input.forward);
		    result = result + Boolean.hashCode(this.input.back);
		    result = result + Boolean.hashCode(this.input.right);
		    result = result + Boolean.hashCode(this.input.left);
		    result = result + Boolean.hashCode(this.input.jump);
		    result = result + Boolean.hashCode(this.input.sneak);
		    result = result + Boolean.hashCode(this.input.sprint);
//		    result = result + (Math.round(this.input.pitch));
		    result = result + (Math.round(this.input.yaw));
		 }
//	    if (round > 1) {
//		    result = 34L * result + Double.hashCode(roundToPrecision(this.agent.getPos().x, round));
//		    result = 87L * result + Double.hashCode(roundToPrecision(this.agent.getPos().y, round));
//		    result = 28L * result + Double.hashCode(roundToPrecision(this.agent.getPos().z, round));
//	    } else {
//		    result = 34L * result + Double.hashCode(this.agent.getPos().x);
//		    result = 87L * result + Double.hashCode(this.agent.getPos().y);
//		    result = 28L * result + Double.hashCode(this.agent.getPos().z);
//	    }
	    return (int) result;
    }
	 
	 private static double roundToPrecision(double value, int precision) {
		    double scale = Math.pow(10, precision);
		    return Math.round(value * scale);
	}

	public List<Node> getChildren(WorldView world, Vec3d target, BlockNode nextBlockNode) {
		Node n = this.parent;
		boolean mismatch = false;
		int i;


//		for(i = 0; i < 4 && n != null; i++) {
//			if(n.agent.blockX != this.agent.blockX || n.agent.blockY != this.agent.blockY || n.agent.blockZ != this.agent.blockZ) {
//				mismatch = true;
//				break;
//			}
//
//			n = n.parent;
//		}
//		if(!mismatch && i == 5) {
//			return new ArrayList<>();
//		}
		if(n != null && n.agent.isInLava() || this.agent.isInLava() || this.agent.fallDistance > 3 && !this.agent.slimeBounce&& !this.agent.touchingWater) return new ArrayList<>();


		if(this.agent.onGround || this.agent.touchingWater) {
			List<Node> nodes = new ArrayList<>();
			// float[] pitchValues = {0.0f, 45.0f, 90.0f}; // Example pitch values
//        	float[] yawValues = {-135.0f, -90.0f, -67.5f, -45.0f, -22.5f, 0.0f, 22.5f, 45.0f, 67.5f, 90.0f, 135.0f, 180.0f}; // Example yaw values
	        Box adjustedBox = this.agent.box.offset(0, -0.5, 0).expand(-0.001, 0, -0.001);
	        boolean isDoingLongJump = nextBlockNode.isDoingLongJump();
	        boolean isCloseToBlockNode = DistanceCalculator.getHorizontalDistanceSquared(this.agent.getPos(), nextBlockNode.getPos(true)) < 1;

			for (boolean forward : new boolean[]{true, false}) {
//				for (boolean back : new boolean[]{true, false}) {
					for (boolean right : new boolean[]{true, false}) {
						for (boolean left : new boolean[]{true, false}) {
								for (boolean sneak : new boolean[]{false, true}) {
										// for (float pitch : pitchValues) {
//											for (float yaw : yawValues) {
										for (float yaw = -180.0f; yaw < 180.0f; yaw += 22.5 + Math.random()) {
											for (boolean sprint : new boolean[]{true, false}) {
												if ((sneak || ((right || left) && !forward)) && sprint) continue;
												for (boolean jump : new boolean[]{true, false}) {
													try {
														double addNodeCost = 1;
														
														boolean isMoving = (forward || right || left);

														if (isMoving && sprint && jump && !sneak) addNodeCost -= 0.2;
												        if (!isCloseToBlockNode) {
												        	if (isDoingLongJump && (sneak || !isMoving)) continue;
													        Stream<VoxelShape> blockCollisions = Streams.stream(TungstenMod.mc.world.getBlockCollisions(TungstenMod.mc.player, adjustedBox));
															if (isDoingLongJump 
																	&& (jump
																	&& blockCollisions.findAny().isPresent())) continue;
												        }
														if (sneak) addNodeCost += 2;
														if (this.agent.isSubmergedInWater || this.agent.touchingWater) {
															for (float pitch = -90.0f; pitch < 90.0f; pitch += 45) {
																Node newNode = new Node(this, world, new PathInput(forward, false, right, left, jump, sneak, sprint, pitch, yaw), new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost + addNodeCost);
																if (!jump) {
																	for (int j = 0; j < 35; j++) {
																		newNode = new Node(newNode, world, new PathInput(forward, false, right, left, false,
																				sneak, sprint, this.agent.pitch, yaw), new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost + addNodeCost);
																	}
																}
																newNode.cost += newNode.agent.isSubmergedInWater || this.agent.touchingWater ? 50 : 0;
																nodes.add(newNode);
															}
														} else {
															Node newNode = new Node(this, world, new PathInput(forward, false, right, left, jump, sneak, sprint, agent.pitch, yaw), new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost + addNodeCost);
//															if (!jump)
																for (int j = 0; j < 
//																		(DistanceCalculator.getHorizontalDistance(this.agent.getPos(), nextBlockNode.getPos(true)) >= 4 ? 6 : 3)
																		(sneak || !jump ? 3 : 10)
																		; j++) {
																	if (newNode.agent.getPos().y < nextBlockNode.getBlockPos().getY() || !isMoving) break;
																	newNode = new Node(newNode, world, new PathInput(forward, false, right, left, jump,
																			sneak, sprint, this.agent.pitch, yaw), 
																			jump ? new Color(0, 255, 255) : new Color(sneak ? 220 : 0, 255, sneak ? 50 : 0)
																			
																			, this.cost + addNodeCost);
																}
															newNode.cost += newNode.agent.isSubmergedInWater || this.agent.touchingWater ? 50 : 0;
															
//															if (jump) {
//																Node newNode2 = new Node(newNode, world, new PathInput(true, false, false, false, false,
//																		false, true, newNode.agent.pitch, newNode.agent.yaw), new Color(0, 255, 255), this.cost + 1);
//																int i2 = 0;
//																while (!newNode2.agent.onGround && i2 < 5) {
//																	Node node2 = new Node(newNode2, world, new PathInput(true, false, false, false, false,
//																			false, true, newNode2.agent.pitch, newNode2.agent.yaw), new Color(0, 255, 255), this.cost + 1);
//																	i2++;
//																	newNode2 = node2;
//																}
////																try {
////																Thread.sleep(2);
////															} catch (InterruptedException e1) {}
//																nodes.add(newNode2);
//															} else {
																nodes.add(newNode);
//															}
														}
														
														
													} catch (java.util.ConcurrentModificationException e) {
														try {
															Thread.sleep(2);
														} catch (InterruptedException e1) {}
													}
													
//											}
											}
										 }
									}
//								}
							}
						}
					}
//				}
			}	
			
			nodes.sort((n1, n2) -> {
				double desiredYaw = PathFinder.calcYawFromVec3d(this.agent.getPos(), target);
			    
			    double diff1 = Math.abs(n1.agent.yaw - desiredYaw);
			    double diff2 = Math.abs(n2.agent.yaw - desiredYaw);
			    
			    // Compare the absolute differences
			    return Double.compare(diff1, diff2);
			});

			return nodes;
			
			// return new Node[] {
			// 	new Node(this, world, new PathInput(true, false, false, false, true,
			// 		false, true, this.agent.pitch, this.agent.yaw), new Color(0, 255, 0), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, this.agent.yaw), new Color(255, 0, 0), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, this.agent.yaw + 90.0F), new Color(255, 255, 0), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, this.agent.yaw - 90.0F), new Color(255, 0, 255), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, (-1 * (this.agent.yaw)) + 90.0F), new Color(255, 0, 25), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, (-1 * (this.agent.yaw)) - 90.0F), new Color(25, 0, 255), this.pathCost + 1),
			// };
		} else {
			List<Node> nodes = new ArrayList<Node>();
			try {
				long jumpCost = 0;
				ClientPlayerEntity player = Objects.requireNonNull(TungstenMod.mc.player);
				Node newNode = new Node(this, world, new PathInput(true, false, false, false, false,
						false, player.getHungerManager().getFoodLevel() > 6, this.agent.pitch, this.agent.yaw), new Color(0, 255, 255), this.cost + jumpCost);

				for (float yaw = this.agent.yaw - 45; yaw < 180.0f; yaw += 22.5) {
					for (boolean forward : new boolean[]{true, false}) {
						for (boolean back : new boolean[]{true, false}) {
							if (back 
									&& (this.agent.getPos().y - nextBlockNode.getBlockPos().getY() < 2
//									|| (this.agent.getPos().y - newNode.agent.getPos().y) < 0
									))  continue;
							for (boolean right : new boolean[]{false, true}) {
								while (!newNode.agent.onGround && !newNode.agent.isClimbing(world) 
										&& newNode.agent.getPos().y > nextBlockNode.getBlockPos().getY()) {
										if (TungstenMod.PATHFINDER.stop) return nodes;
										if (newNode.agent.isClimbing(world)) break;
										newNode = new Node(newNode, world, new PathInput(forward, back, right, false, false,
												false, true, this.agent.pitch, yaw), new Color(back ? 220 : 0, 255, 255), this.cost + jumpCost);
								}
		//						Node renderNode = newNode;
		//						while(renderNode.parent != null) {
		//							TungstenMod.RENDERERS.add(new Line(renderNode.agent.getPos(), renderNode.parent.agent.getPos(), renderNode.color));
		//							TungstenMod.RENDERERS.add(new Cuboid(renderNode.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), renderNode.color));
		//							renderNode = renderNode.parent;
		//						}
		//						try {
		//							Thread.sleep(50);
		//						} catch (InterruptedException ignored) {}	
								nodes.add(newNode);
							}	
						}
					}
				}
//				for (boolean forward : new boolean[]{true, false}) {
//					for (boolean sprint : new boolean[]{true, false}) {
//						for (boolean right : new boolean[]{false, true}) {
//							
//								Node newNode = new Node(this, world, new PathInput(forward, false, right, false, false,
//										false, sprint, this.agent.pitch, this.agent.yaw), new Color(0, 255, 255), this.cost + 1);
//								if (newNode.agent.onGround) nodes.add(newNode);
//								else {
//									for (float yaw = -180.0f; yaw < 180.0f; yaw += 22.5) {
//										newNode = new Node(newNode, world, new PathInput(forward, false, right, false, false,
//												false, sprint, this.agent.pitch, yaw), new Color(0, 255, 255), this.cost + 1);
//									}
//								}
//						}
//					}
//				}
//				ClientPlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);
//				Node newNode = new Node(this, world, new PathInput(true, false, false, false, false,
//						false, player.getHungerManager().getFoodLevel() > 6, this.agent.pitch, this.agent.yaw), new Color(0, 255, 255), this.cost + 100000);
//				nodes.add(newNode);
			} catch (java.util.ConcurrentModificationException e) {
				try {
					Thread.sleep(2);
				} catch (InterruptedException e1) {}
			}
			nodes.sort((n1, n2) -> {
				double desiredYaw = PathFinder.calcYawFromVec3d(this.agent.getPos(), target);
			    
			    double diff1 = Math.abs(n1.agent.yaw - desiredYaw);
			    double diff2 = Math.abs(n2.agent.yaw - desiredYaw);
			    
			    // Compare the absolute differences
			    return Double.compare(diff1, diff2);
			});
			return nodes;
		}
	}

}
