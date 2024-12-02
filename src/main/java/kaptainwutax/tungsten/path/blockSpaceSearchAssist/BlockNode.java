package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.text.AsyncBoxView.ChildLocator;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.path.calculators.ActionCosts;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cube;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.world.BetterBlockPos;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.HoneyBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SlimeBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SnowyBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;

public class BlockNode {
	
	/**
     * The position of this node
     */
    public final int x;
    public final int y;
    public final int z;
    
    /**
     * Cached, should always be equal to goal.heuristic(pos)
     */
    public double estimatedCostToGoal;

    /**
     * Total cost of getting from start to here
     * Mutable and changed by PathFinder
     */
    public double cost;

    /**
     * Should always be equal to estimatedCosttoGoal + cost
     * Mutable and changed by PathFinder
     */
    public double combinedCost;

    /**
     * In the graph search, what previous node contributed to the cost
     * Mutable and changed by PathFinder
     */
    public BlockNode previous;
    
    private boolean wasOnSlime;
    private boolean wasOnLadder;
    private boolean isDoingNeo = false;
    private Direction neoSide;

    /**
     * Where is this node in the array flattenization of the binary heap? Needed for decrease-key operations.
     */
    public int heapPosition;
    
    public BlockNode(BlockPos pos, Goal goal) {
    	this.previous = null;
        this.cost = ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(pos.getX(), pos.getY(), pos.getZ());
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException(goal + " calculated implausible heuristic");
        }
        this.heapPosition = -1;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.wasOnSlime = TungstenMod.mc.world.getBlockState(pos.down()).getBlock() instanceof SlimeBlock;
        this.wasOnLadder = TungstenMod.mc.world.getBlockState(pos).getBlock() instanceof LadderBlock;
    }
    
    public BlockNode(int x, int y, int z, Goal goal) {
        this.previous = null;
        this.cost = ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(x, y, z);
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException(goal + " calculated implausible heuristic");
        }
        this.heapPosition = -1;
        this.x = x;
        this.y = y;
        this.z = z;
        this.wasOnSlime = TungstenMod.mc.world.getBlockState(new BlockPos(x, y-1, z)).getBlock() instanceof SlimeBlock;
        this.wasOnLadder = TungstenMod.mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
    }
    
    public BlockNode(int x, int y, int z, Goal goal, BlockNode parent, double cost) {
        this.previous = parent;
        this.wasOnSlime = TungstenMod.mc.world.getBlockState(new BlockPos(x, y-1, z)).getBlock() instanceof SlimeBlock;
        this.wasOnLadder = TungstenMod.mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
        this.cost = parent != null ? 0 : ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(x, y, z);
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException(goal + " calculated implausible heuristic");
        }
        this.heapPosition = -1;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    /**
     * TODO: Possibly reimplement hashCode and equals. They are necessary for this class to function but they could be done better
     *
     * @return The hash code value for this {@link PathNode}
     */
    @Override
    public int hashCode() {
        return (int) BetterBlockPos.longHash(x, y, z);
    }
    
    public Vec3d getPos() {
    	return getPos(false);
    }
    
    public Vec3d getPos(boolean shift) {
    	if (shift) {
    		if (isDoingNeo) return BlockPosShifter.shiftForStraightNeo(this, neoSide);
        	return BlockPosShifter.getPosOnLadder(this);
    	}
    	return new Vec3d(x, y, z);
    }
    
    public boolean isDoingLongJump() {
    	if (this.previous != null) {
            double distance = DistanceCalculator.getHorizontalDistanceSquared(this.previous.getPos(), this.getPos());
    		if (distance >= 4 && distance < 6) {
    			return true;
    		}
		}
    	return false;
    }
    
    public boolean isDoingNeo() {
    	return this.isDoingNeo;
    }
    
    public BlockPos getBlockPos() {
    	return new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        // GOTTA GO FAST
        // ALL THESE CHECKS ARE FOR PEOPLE WHO WANT SLOW CODE
        // SKRT SKRT
        //if (obj == null || !(obj instanceof PathNode)) {
        //    return false;
        //}

        final BlockNode other = (BlockNode) obj;
        //return Objects.equals(this.pos, other.pos) && Objects.equals(this.goal, other.goal);

        return x == other.x && y == other.y && z == other.z;
    }
    
    public List<BlockNode> getChildren(WorldView world, Goal goal) {
		
		List<BlockNode> nodes = getNodesIn2DCircule(8, this, goal);
		nodes.removeIf((child) -> {
			return shouldRemoveNode(world, child);
		});
		
//		for (BlockNode blockNode : nodes) {
//			TungstenMod.TEST.add(new Cuboid(new Vec3d(blockNode.x, blockNode.y, blockNode.z), new Vec3d(1.0D, 1.0D, 1.0D), blockNode.wasOnLadder ? Color.GREEN : Color.WHITE));
//		}
//
//		if (wasOnLadder) {
//				try {
//					Thread.sleep(200);
//				} catch (InterruptedException ignored) {}
//		}
		
		TungstenMod.TEST.clear();
		
		return nodes;
		
    }
    
    public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end) {
    	return wasCleared(world, start, end, null, null);
    }
    
    public static boolean wasCleared(WorldView world, BlockPos start, BlockPos end, BlockNode startNode, BlockNode endNode) { 
		int x1 = start.getX();
	    int y1 = start.getY();
	    int z1 = start.getZ();
	
	    int x2 = end.getX();
	    int y2 = end.getY();
	    int z2 = end.getZ();
	    
	    boolean isJumpingOneBlock = y2-y1 == 1;
		TungstenMod.TEST.clear();
		BlockPos.Mutable currPos = new BlockPos.Mutable();
		int x = x1;
        int y = isJumpingOneBlock ? y1+1 : y1;
        int z = z1;
        
        boolean isMovingOnXAxis = x1-x2 == 0;
        boolean isMovingOnZAxis = z1-z2 == 0;
        boolean shouldCheckNeo = start.isWithinDistance(end, 4.2) && true;
      boolean shouldRender = false;
      boolean shouldSlow = false;
      if (shouldSlow) {
		TungstenMod.TEST.add(new Cuboid(new Vec3d(x1, y1, z1), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
		TungstenMod.TEST.add(new Cuboid(new Vec3d(x2, y2, z2), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
      }

        while (x != x2 || y != y2 || z != z2) {
        	if (TungstenMod.PATHFINDER.stop) return false;
            
            
            currPos.set(x, y, z);
        	if (isJumpingOneBlock && world.getBlockState(currPos.down()).getBlock() instanceof FenceBlock) return false;
        	if (isJumpingOneBlock && world.getBlockState(currPos).getBlock() instanceof SlabBlock) return false;
            if (isObscured(world, currPos, isJumpingOneBlock)) {
            	if (shouldCheckNeo) {
	            	if (!isNeoPossible(world, isMovingOnXAxis, isMovingOnZAxis, x, y, z, start, end, isJumpingOneBlock, endNode)){
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
						return false;
					}
            	} else return false;
			} else {
				if (shouldRender) {
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
				}
			}
            
            if (z < z2) {
                z++;
            } else if (z > z2) {
                z--;
            }

            currPos.set(x, y, z);
            if (isObscured(world, currPos, isJumpingOneBlock)) {
            	if (shouldCheckNeo) {
	            	if (!isNeoPossible(world, isMovingOnXAxis, isMovingOnZAxis, x, y, z, start, end, isJumpingOneBlock, endNode)){
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
						return false;
					}
            	} else return false;
			} else {
				if (shouldRender) {
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
				}
			}
//        }
            if (x < x2) {
                x++;
            } else if (x > x2) {
                x--;
            }
            currPos.set(x, y, z);

            if (isObscured(world, currPos, isJumpingOneBlock)) {
            	if (shouldCheckNeo) {
	            	if (!isNeoPossible(world, isMovingOnXAxis, isMovingOnZAxis, x, y, z, start, end, isJumpingOneBlock, endNode)){
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
						return false;
					}
            	} else return false;
			} else {
				if (shouldRender) {
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
				}
			}

            if (y < y2) {
                y++;
            } else if (y > y2) {
                y--;
            }
            currPos.set(x, y, z);
            
            if (isObscured(world, currPos, isJumpingOneBlock)) {
            	if (shouldCheckNeo) {
	            	if (!isNeoPossible(world, isMovingOnXAxis, isMovingOnZAxis, x, y, z, start, end, isJumpingOneBlock, endNode)){
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
						return false;
					}
            	} else return false;
			} else {
				if (shouldRender) {
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
				}
			}
        }
		if (shouldSlow) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException ignored) {}
		}

		return true;
	}
    
    private static boolean isNeoPossible(WorldView world, boolean isMovingOnXAxis, boolean isMovingOnZAxis, int x, int y, int z, BlockPos startPos, BlockPos endPos, boolean isJumpingOneBlock) {
    	return isNeoPossible(world, isMovingOnXAxis, isMovingOnZAxis, x, y, z, startPos, endPos, isJumpingOneBlock, null);
    }
    
    private static boolean isNeoPossible(WorldView world, boolean isMovingOnXAxis, boolean isMovingOnZAxis, int x, int y, int z, BlockPos startPos, BlockPos endPos, boolean isJumpingOneBlock, BlockNode node) {
    	int endX = endPos.getX();
    	int endY = endPos.getY();
    	int endZ = endPos.getZ();
    	int dx = startPos.getX() - endX;
    	int dz = startPos.getZ() - endZ;
    	double distance = Math.sqrt(dx * dx + dz * dz);
        boolean shouldRender = false;
        boolean shouldSlow = false;
        boolean isLadder = world.getBlockState(endPos).getBlock() instanceof LadderBlock
        		|| world.getBlockState(endPos.down()).getBlock() instanceof LadderBlock
        		|| world.getBlockState(startPos).getBlock() instanceof LadderBlock
    			|| world.getBlockState(startPos.down()).getBlock() instanceof LadderBlock;
    	BlockPos.Mutable currPos = new BlockPos.Mutable();
    	int count = 0;
    	if (isMovingOnXAxis && !isLadder) {
        	if (world.getBlockState(startPos).getBlock() instanceof LadderBlock
        			|| world.getBlockState(startPos.down()).getBlock() instanceof LadderBlock
        			|| isOpenTrapdoor(world.getBlockState(startPos.down()))) {
        		return false;
        	}
        	if (startPos.getZ() > endZ) {
	        	// West
	        	boolean isWestPossible = true;
	        	int neoX = x-1;
	        	int currZ = endZ > z ? z-1 : z+1;
	        	while (currZ != endZ) {
	        		if (TungstenMod.PATHFINDER.stop) return false;
	        		if (count > 3) return false;
	        		count++;
	            	currPos.set(neoX, y-1, currZ);
	            	if (!world.getBlockState(currPos).isAir()) return false;
	            	if (z < endZ) {
	            		currZ++;
	                } else if (z > endZ) {
	                	currZ--;
	                }
	            	currPos.set(neoX, y, currZ);
	            	if (isObscured(world, currPos, isJumpingOneBlock)) {
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
	            		isWestPossible = false;
	    			} else {
	    				if (shouldRender) {
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	    				}
	    			}
	        	}
	        	if (node != null && !node.isDoingNeo) {
		        	if (isWestPossible) {
		        		node.isDoingNeo = true;
		        		node.neoSide = Direction.WEST;
		        		return true;
		        	}
		        	return false;
	        	}
    		}
        	if (startPos.getZ() < endZ) {
        		count = 0;
	        	// East
	        	boolean isEastPossible = true;
	        	int neoX = x+1;
	        	int currZ = endZ > z ? z-1 : z+1;
	        	while (currZ != endZ) {
	        		if (TungstenMod.PATHFINDER.stop) return false;
	        		if (count > 3) return false;
	        		count++;
	            	currPos.set(neoX, y-1, currZ);
	            	if (!world.getBlockState(currPos).isAir()) return false;
	            	if (z < endZ) {
	            		currZ++;
	                } else if (z > endZ) {
	                	currZ--;
	                }
	            	currPos.set(neoX, y, currZ);
	            	if (isObscured(world, currPos, isJumpingOneBlock)) {
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
	            		isEastPossible = false;
	    			} else {
	    				if (shouldRender) {
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	    				}
	    			}
	        	}
	        	if (node != null && !node.isDoingNeo) {
		        	if (isEastPossible) {
		        		node.isDoingNeo = true;
		        		node.neoSide = Direction.EAST;
		        		return true;
		        	}
		        	return false;
	        	}
    		}
        } else if (isMovingOnZAxis && !isLadder) {
        	if (world.getBlockState(startPos).getBlock() instanceof LadderBlock
        			|| world.getBlockState(startPos.down()).getBlock() instanceof LadderBlock
        			|| isOpenTrapdoor(world.getBlockState(startPos.down()))) {
        		return false;
        	}
        	if (startPos.getX() < endX) {
        		// South
	        	boolean isSouthPossible = true;
	        	int neoZ = z+1;
	        	int currX =  endX > x ? x-1 : x+1;
	        	while (currX != endX) {
	        		if (TungstenMod.PATHFINDER.stop) return false;
	        		if (count > 3) return false;
	        		count++;
	            	currPos.set(currX, y-1, neoZ);
	            	if (!world.getBlockState(currPos).isAir()) {
	            		isSouthPossible = false;
	            		break;
	            	}
	            	if (x < endX) {
	            		currX++;
	                } else if (x > endX) {
	                	currX--;
	                }
	            	currPos.set(currX, y, neoZ);
	            	if (isObscured(world, currPos, isJumpingOneBlock)) {
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
	            		isSouthPossible = false;
	            		break;
	    			} else {
	    				if (shouldRender) {
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	    				}
	    			}
	        	}
	        	if (node != null && !node.isDoingNeo) {
		        	if (isSouthPossible) {
		        		node.isDoingNeo = true;
		        		node.neoSide = Direction.SOUTH;
		        		return true;
		        	}
		        	return false;
	        	}
	        	
        	}
        	if (startPos.getX() > endX) {
        		count = 0;
	        	// North
	        	boolean isNorthSidePossible = true;
	        	int neoZ = z-1;
	        	int currX =  endX > x ? x-1 : x+1;
	        	while (currX != endX) {
	        		if (TungstenMod.PATHFINDER.stop) return false;
	        		if (count > 3) return false;
	        		count++;
	            	currPos.set(currX, y-1, neoZ);
	            	if (!world.getBlockState(currPos).isAir()) {
	            		isNorthSidePossible = false;
	            		break;
	            	}
	            	if (x < endX) {
	            		currX++;
	                } else if (x > endX) {
	                	currX--;
	                }
	            	currPos.set(currX, y, neoZ);
	            	if (isObscured(world, currPos, isJumpingOneBlock)) {
	            		if (shouldRender) {
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	            		}
	            		isNorthSidePossible = false;
	            		break;
	    			} else {
	    				if (shouldRender) {
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY(), currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
	    					TungstenMod.TEST.add(new Cuboid(new Vec3d(currPos.getX(), currPos.getY()+1, currPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
							if (shouldSlow) {
								try {
									Thread.sleep(450);
								} catch (InterruptedException ignored) {}
							}
	    				}
	    			}
	        	}
	        	if (node != null && !node.isDoingNeo) {
		        	if (isNorthSidePossible) {
		        		node.isDoingNeo = true;
		        		node.neoSide = Direction.NORTH;
		            	return true;
		        	}
		        	return false;
	        	}
        	}
        } else {
			if (shouldRender) {
				TungstenMod.TEST.add(new Cuboid(new Vec3d(endX, endY, endZ), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
				TungstenMod.TEST.add(new Cuboid(new Vec3d(startPos.getX(), startPos.getY(), startPos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
			}
			x = startPos.getX();
			y = startPos.getY();
			z = startPos.getZ();
        	while (x != endX || y != endY || z != endZ) {
        		if (distance >= 2) return false;
            	if (TungstenMod.PATHFINDER.stop) return false;
                

//				if (shouldRender) {
//					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//				}
//                currPos.set(x-1, y, z-1);
//                if (isObscured(world, currPos, isJumpingOneBlock)) {
//            		if (shouldRender) {
//						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//						if (shouldSlow) {
//							try {
//								Thread.sleep(450);
//							} catch (InterruptedException ignored) {}
//						}
//            		}
//					return false;
//    			} else {
//    				if (shouldRender) {
//    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//    				}
//    			}
//                if (x < endX) {
//                    x++;
//                } else if (x > endX) {
//                    x--;
//                }
//
//                currPos.set(x, y, z);
//                if (isObscured(world, currPos, isJumpingOneBlock)) {
//            		if (shouldRender) {
//						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//						if (shouldSlow) {
//							try {
//								Thread.sleep(450);
//							} catch (InterruptedException ignored) {}
//						}
//            		}
//					return false;
//    			} else {
//    				if (shouldRender) {
//    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//    				}
//    			}
            	if (y < endY) {
            		if (y < endY) {
	                    y++;
	                } else if (y > endY) {
	                    y--;
	                }
            		currPos.set(x, y, z);
                    if (isObscured(world, currPos, isJumpingOneBlock)) {
                		if (shouldRender) {
    						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
    						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
    						if (shouldSlow) {
    							try {
    								Thread.sleep(450);
    							} catch (InterruptedException ignored) {}
    						}
                		}
    					return false;
        			} else {
        				if (shouldRender) {
        					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
        					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
        				}
        			}
            	}
                if (x < endX) {
                    x++;
                } else if (x > endX) {
                    x--;
                }

                currPos.set(x, y, z);
                if (isObscured(world, currPos, isJumpingOneBlock)) {
            		if (shouldRender) {
						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
						if (shouldSlow) {
							try {
								Thread.sleep(450);
							} catch (InterruptedException ignored) {}
						}
            		}
					return false;
    			} else {
    				if (shouldRender) {
    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
    				}
    			}
                
                if (z < endZ) {
                    z++;
                } else if (z > endZ) {
                    z--;
                }
                currPos.set(x, y, z);

                if (isObscured(world, currPos, isJumpingOneBlock)) {
                	if (shouldRender) {
						TungstenMod.TEST.add(new Cuboid(new Vec3d(x+0.5, y, z+0.5), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
						TungstenMod.TEST.add(new Cuboid(new Vec3d(x+0.5, y+1, z+0.5), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
						if (shouldSlow) {
							try {
								Thread.sleep(450);
							} catch (InterruptedException ignored) {}
						}
            		}
					return false;
    			} else {
    				if (shouldRender) {
    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
    				}
    			}

                if (y < endY) {
                    y++;
                } else if (y > endY) {
                    y--;
                }
                currPos.set(x, y, z);
                
                if (isObscured(world, currPos, isJumpingOneBlock)) {
                	if (shouldRender) {
						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
						TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
						if (shouldSlow) {
							try {
								Thread.sleep(450);
							} catch (InterruptedException ignored) {}
						}
            		}
					return false;
    			} else {
    				if (shouldRender) {
    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
    					TungstenMod.TEST.add(new Cuboid(new Vec3d(x, y+1, z), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
    				}
    			}
            }
    		if (shouldSlow) {
    			try {
    				Thread.sleep(850);
    			} catch (InterruptedException ignored) {}
    		}
    	}
    	
    	return true;
    }
    
    private static boolean isObscured(WorldView world, BlockPos pos, boolean isJumpingUp) {
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(pos.getX(), pos.getY()+1, pos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//		try {
//			Thread.sleep(250);
//		} catch (InterruptedException ignored) {}
    	return (
    			(world.getBlockState(pos).isFullCube(world, pos)
    			|| (world.getBlockState(pos).getBlock() instanceof SlabBlock && !isJumpingUp)
    			|| world.getBlockState(pos).getBlock() instanceof LeavesBlock
    			)
    			&& (!hasBiggerCollisionShapeThanAbove(world, pos) && !isJumpingUp)
    			// This causes it not to understand bottom slab under fence
    			|| hasBiggerCollisionShapeThanAbove(world, pos)
    			&& !(world.getBlockState(pos.up()).getBlock() instanceof LeavesBlock)
				)
    			&& !(world.getBlockState(pos).getBlock() instanceof SlabBlock)
    			&& !(world.getBlockState(pos).getBlock() instanceof CarpetBlock)
    			&& !(world.getBlockState(pos).getBlock() instanceof DaylightDetectorBlock)
    			&& !(world.getBlockState(pos).getBlock()  == Blocks.LAVA )
    			|| (world.getBlockState(pos.up()).isFullCube(world, pos.up())
    	    			|| world.getBlockState(pos.up()).getBlock() instanceof SlabBlock
    					|| (world.getBlockState(pos.up()).getBlock() instanceof LeavesBlock)
    					)
//    			&& !(world.getBlockState(pos.up()).getBlock() instanceof SlabBlock)
    			&& !(world.getBlockState(pos.up()).getBlock() instanceof CarpetBlock)
//    			&& !(world.getBlockState(pos.up()).getBlock() instanceof DaylightDetectorBlock)
    			&& !(world.getBlockState(pos).getBlock()  == Blocks.LAVA )
    			||
    			(BlockStateChecker.isConnected(pos)
    			|| BlockStateChecker.isConnected(pos.up()));
//    	return ((world.getBlockState(pos).isFullCube(world, pos)
//    			|| (world.getBlockState(pos).getBlock() instanceof SlabBlock && !isJumpingUp)
//    			|| world.getBlockState(pos).getBlock() instanceof LeavesBlock
//    			)
//    			&& (!hasBiggerCollisionShapeThanAbove(world, pos) && !isJumpingUp)
//    			&& ((world.getBlockState(pos.up()).getBlock() instanceof FenceBlock)
//    			|| (world.getBlockState(pos.up()).getBlock() instanceof WallBlock))
//    			// This causes it not to understand bottom slab under fence
//    			|| hasBiggerCollisionShapeThanAbove(world, pos)
//    			&& !(world.getBlockState(pos.up()).getBlock() instanceof FenceBlock)
//    			&& !(world.getBlockState(pos.up()).getBlock() instanceof WallBlock)
//    			&& !(world.getBlockState(pos.up()).getBlock() instanceof LeavesBlock)
//				)
//		&& !(world.getBlockState(pos).getBlock() instanceof SlabBlock)
//		&& !(world.getBlockState(pos).getBlock() instanceof CarpetBlock)
//		&& !(world.getBlockState(pos).getBlock() instanceof DaylightDetectorBlock)
//		&& !(world.getBlockState(pos).getBlock()  == Blocks.LAVA )
//		|| (world.getBlockState(pos.up()).isFullCube(world, pos.up())
//    			|| world.getBlockState(pos.up()).getBlock() instanceof SlabBlock
//				|| (world.getBlockState(pos.up()).getBlock() instanceof LeavesBlock)
//				)
//		&& (world.getBlockState(pos.up()).getBlock() instanceof FenceBlock)
//		&& (world.getBlockState(pos.up()).getBlock() instanceof WallBlock)
////		&& !(world.getBlockState(pos.up()).getBlock() instanceof SlabBlock)
//		&& !(world.getBlockState(pos.up()).getBlock() instanceof CarpetBlock)
////		&& !(world.getBlockState(pos.up()).getBlock() instanceof DaylightDetectorBlock)
//		&& !(world.getBlockState(pos).getBlock()  == Blocks.LAVA )
//		;
    }
    
    public static boolean hasBiggerCollisionShapeThanAbove(WorldView world, BlockPos pos) {
        // Get the block states of the block at pos and the two blocks above it
        BlockState blockState = world.getBlockState(pos);
        if (blockState.getBlock() instanceof LadderBlock) return false;
        BlockState aboveBlockState1 = world.getBlockState(pos.up(1));
        BlockState aboveBlockState2 = world.getBlockState(pos.up(2));

        // Get the collision shapes of these blocks
        VoxelShape blockShape = blockState.getCollisionShape(world, pos);
        VoxelShape aboveBlockShape1 = aboveBlockState1.getCollisionShape(world, pos.up(1));
        VoxelShape aboveBlockShape2 = aboveBlockState2.getCollisionShape(world, pos.up(2));
        
        // Calculate the volume of the collision shapes
        double blockVolume = getShapeVolume(blockShape);
        double aboveBlockVolume1 = getShapeVolume(aboveBlockShape1);
        double aboveBlockVolume2 = getShapeVolume(aboveBlockShape2);
//        if (blockState.getBlock() instanceof FenceBlock) {
//        	return true;
//        }

        // Compare the volumes
        return blockVolume > aboveBlockVolume1 && blockVolume > aboveBlockVolume2;
    }
    
    private static double getShapeVolume(VoxelShape shape) {
        // Iterate over the shape's bounding boxes and sum their volumes
        return shape.getBoundingBoxes().stream()
                .mapToDouble(box -> (box.maxX - box.minX) * (box.maxZ - box.minZ))
                .sum();
    }
    
    private List<BlockNode> getNodesIn2DCircule(int d, BlockNode parent, Goal goal) {
    	List<BlockNode> nodes = new ArrayList<>();
//        for( int py = -3; py < 3; py++ ) {
//	    	for (int id = 1; id <= (py < 0 ? d+(py*-1) : d); id++) {
//		        int px = id;
//		        int pz = 0;
//		        int dx = -1, dz = 1;
//		        int n = id * 4;
//    	        for( int i = 0; i < n; i++ ) {
//		            if( px == id && dx > 0 ) dx = -1;
//		            else if( px == -id && dx < 0 ) dx = 1;
//		            if( pz == id && dz > 0 ) dz = -1;
//		            else if( pz == -id && dz < 0 ) dz = 1;
//		            px += dx;
//		            pz += dz;
//		            BlockNode newNode = new BlockNode(this.x + px, this.y + py, this.z + pz, goal, this, ActionCosts.WALK_ONE_BLOCK_COST);
//		            nodes.add(newNode);
//	            }
//	        }
//		}
		double g = 32.656;  // Acceleration due to gravity in m/s^2
		double v_sprint = 5.8;  // Sprinting speed in m/s 5.8 based on meteor player.speed var
		double yMax = (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y != 0 ? getSlimeBounceHeight(parent.previous.y - parent.y)-0.5 :  4);
//		if (parent.wasOnLadder) {
//			yMax += 1;
//		}
//		if (yMax != 2.0)System.out.println(yMax);
		int distanceWanted = d;
		for( int py = -104; py < yMax; py++ ) {
		
			if (py < 0 && py < -5) {
				double t = Math.sqrt((2 * py*-1) / g);
				d = (int) Math.ceil(v_sprint * t);
		//	    		if (py == -1)
		//	    		System.out.println(d);
			}
			else
				d = distanceWanted+1;
            nodes.add(new BlockNode(this.x, this.y + py, this.z, goal, this, ActionCosts.WALK_ONE_BLOCK_COST));
			for (int id = 1; id <= d; id++) {
		        int px = id;
		        int pz = 0;
		        int dx = -1, dz = 1;
		        int n = id * 4;
		        for( int i = 0; i < n; i++ ) {
		            if( px == id && dx > 0 ) dx = -1;
		            else if( px == -id && dx < 0 ) dx = 1;
		            if( pz == id && dz > 0 ) dz = -1;
		            else if( pz == -id && dz < 0 ) dz = 1;
		            px += dx;
		            pz += dz;
//					TungstenMod.TEST.add(new Cuboid(new Vec3d(this.x + px, this.y + py, this.z + pz), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
		            BlockNode newNode = new BlockNode(this.x + px, this.y + py, this.z + pz, goal, this, ActionCosts.WALK_ONE_BLOCK_COST);
		            nodes.add(newNode);
		        }
		    }
		}
//		try {
//			Thread.sleep(350);
//		} catch (InterruptedException ignored) {}
//		TungstenMod.TEST.clear();
        return nodes;
    }
    
    private double getSlimeBounceHeight(double startHeight) {
    	return -0.0011 * Math.pow(startHeight, 2) + 0.43529 * startHeight + 1.7323;
    }
    
    private boolean shouldRemoveNode(WorldView world, BlockNode child) {
    	if (TungstenMod.PATHFINDER.stop) return true;

    	BlockState currentBlockState = world.getBlockState(getBlockPos());
    	BlockState childState = world.getBlockState(child.getBlockPos());
        BlockState childBelowState = world.getBlockState(child.getBlockPos().down());
        Block currentBlock = currentBlockState.getBlock();
        Block childBlock = childState.getBlock();
        Block childBelowBlock = childBelowState.getBlock();
    	double heightDiff = this.y - child.y; // -1 is going up and +1 is going down, in negative y levels its reversed
        double distance = DistanceCalculator.getHorizontalDistanceSquared(getPos(true), child.getPos(true));
        
        // Check for air below
        if (childBelowState.isAir() && !(childBlock instanceof LadderBlock)) {
            if (!(childBlock instanceof SlabBlock)) return true;
        }

//        if (BlockStateChecker.isConnected(child.getBlockPos())) {
//    		TungstenMod.TEST.add(new Cuboid(new Vec3d(child.getBlockPos().getX(), child.getBlockPos().getY(), child.getBlockPos().getZ()), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//    		try {
//    			Thread.sleep(250);
//    		} catch (InterruptedException ignored) {}
//        }

        // Check for water
//        if (isWater(childState) && wasCleared(world, getBlockPos(), child.getBlockPos())) return false;

        // Ladder checks
        if ((currentBlock instanceof LadderBlock) && distance > 4.3) {
            return true;
        }
        if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock) && distance > 6.3) {
            return true;
        }
        if ((childBelowBlock instanceof LadderBlock || childBlock instanceof LadderBlock) && wasCleared(world, getBlockPos(), child.getBlockPos()) && distance < 6.3 && heightDiff >= -1 ) {
            return false;
        }

        // General height and distance checks
//        if (previous != null && (previous.y - y < 1 && wasOnSlime || (!wasOnSlime && child.y - y > 1))) return true;
        if (distance >= 7) return true;

        // Slab checks
        if (isBottomSlab(childState)) return true;
//        if (isBottomSlab(childState) && !hasBiggerCollisionShapeThanAbove(world, child.getBlockPos())) return true;

        // Collision shape and block exceptions
        if (!hasBiggerCollisionShapeThanAbove(world, child.getBlockPos().down())
                && (!(childBlock instanceof DaylightDetectorBlock)
                && !(childBlock instanceof CarpetBlock)
                && !(childBelowBlock instanceof SlabBlock)
                && !(childBelowBlock instanceof LanternBlock)
                && !(childBelowBlock == Blocks.SNOW)
                )
                || (childBelowBlock instanceof StairsBlock)
                && !(childBelowBlock instanceof LanternBlock)
                && !(childBelowBlock == Blocks.SNOW)
                && getShapeVolume(childState.getCollisionShape(world, child.getBlockPos())) >= 1) {
            return true;
        }

        // Specific block checks
        if (childState.isOf(Blocks.LAVA)) return true;
        if (childBelowBlock instanceof LilyPadBlock) return true;
        if (childBelowBlock instanceof CarpetBlock) return true;
        if (childBelowBlock instanceof DaylightDetectorBlock) return true;
        if (childBlock instanceof StairsBlock) return true;
        if (childBelowBlock instanceof SeaPickleBlock) return true;
        if (childBelowBlock instanceof CropBlock) return true;
        if (isBottomSlab(childBelowState) && !(childBlock instanceof AirBlock)) return true;

		if (isJumpImpossible(world, child)) return true;
		
		// TODO: Fix bottom slab under fence thing
		if (!wasCleared(world, getBlockPos(), child.getBlockPos(), this, child)) {
            return true;
        }

		return false;
    }
    
    
    public boolean isJumpImpossible(WorldView world, BlockNode child) {
    	double heightDiff = this.y - child.y; // -1 is going up and +1 is going down, in negative y levels its reversed
        double distance = DistanceCalculator.getHorizontalDistanceSquared(getPos(true), child.getPos(true));
        
        BlockState childBlockState = world.getBlockState(child.getBlockPos().down());
        BlockState currentBlockState = world.getBlockState(getBlockPos().down());
        Block childBlock = childBlockState.getBlock();
        Block currentBlock = currentBlockState.getBlock();
        
//    	if (world.getBlockState(child.getBlockPos().down()).getBlock() instanceof TrapdoorBlock) {
//			System.out.println(!world.getBlockState(child.getBlockPos().down()).get(Properties.OPEN));
//    	}

        VoxelShape blockShape = childBlockState.getCollisionShape(world, child.getBlockPos().down());
        VoxelShape currentBlockShape = currentBlockState.getCollisionShape(world, getBlockPos().down());
        
        double childBlockHeight = getBlockHeight(blockShape);
        double currentBlockHeight = getBlockHeight(currentBlockShape);
        
        double blockHeightDiff = currentBlockHeight - childBlockHeight; // Negative values means currentBlockHeight is lower, and positive means currentBlockHeight is higher
        
        // VoxelShape-based checks
        if (!Double.isInfinite(blockHeightDiff) && !Double.isNaN(blockHeightDiff)) {
	        if (blockHeightDiff != 0) {
	        	if (Math.abs(blockHeightDiff) > 0.5 && Math.abs(blockHeightDiff) <= 1.0) {
	    			if (heightDiff < 0 && (blockShape.getMin(Axis.Y) == 0.0 && currentBlockHeight <= 1.0)) return true;
	        		if (heightDiff == -2 && distance <= 5.3) return false;
	        		if (heightDiff <= 0 && distance <= 5.3) return false;
	        	}
	        	if (Math.abs(blockHeightDiff) <= 0.5 && (blockShape.getMin(Axis.Y) == 0.0 || childBlockHeight > 1.0)) {
	        		if (blockHeightDiff == -0.5 && heightDiff <= -1) return true;
	        		if (heightDiff >= -1 && distance <= 7.4) return false;
	        	}
	        	
	        	if (Math.abs(blockHeightDiff) >= 0.5 && (blockShape.getMin(Axis.Y) == 0.0 || childBlockHeight > 1.0)) return true;
	        }
        }

        if (!wasOnSlime) {
            // Basic height and distance checks
            if (heightDiff <= -2) return true;
            if (heightDiff == 1 && distance > 6) return true;
            if (heightDiff >= 2 && distance > 7) return true;
            if (heightDiff == -1 && distance >= 4.5) return true;
            if (heightDiff == 0 && distance >= 6.3) return true;
            if (heightDiff < 0 && distance >= 5.5) return true;

            // Trapdoor checks
            if (heightDiff == 1 && isOpenTrapdoor(childBlockState) && distance > 5) return true;
            if (heightDiff >= 2 && isOpenTrapdoor(childBlockState) && distance > 6) return true;

            // Slab and ladder checks
            if (heightDiff <= 0
            		&& (isBottomSlab(childBlockState)
            				|| (!wasOnLadder
            						&& childBlock instanceof LadderBlock)
            				)
            		&& distance >= 6.5) {
                return true;
            }
        }

        // Large height drop
        if (heightDiff < 1 && distance >= 6) return true;
        
        if (isWater(currentBlockState) && distance >= 2) return true; 

        // Bottom slab checks
        if (currentBlockHeight <= 0.5 
                && heightDiff < 0 
                && childBlockHeight > 0.5) {
            return true;
        }
//		TungstenMod.TEST.add(new Cuboid(new Vec3d(child.x, child.x, child.x), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
//		try {
//			Thread.sleep(350);
//		} catch (InterruptedException ignored) {}
//		TungstenMod.TEST.clear();

        return false;
    }
    
    // Helper method to check if the block contains water
    private static boolean isWater(BlockState state) {
        return state.getFluidState().isOf(Fluids.WATER) || state.getFluidState().isOf(Fluids.FLOWING_WATER);
    }

    // Helper method to check if the block is an open trapdoor
    private static boolean isOpenTrapdoor(BlockState state) {
        return state.getBlock() instanceof TrapdoorBlock && state.get(Properties.OPEN);
    }

    // Helper method to check if the block is a bottom slab
    private static boolean isBottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.get(Properties.SLAB_TYPE) == SlabType.BOTTOM;
    }
    
    private static double getBlockHeight(WorldView world, BlockState state, BlockPos pos) {
    	VoxelShape blockShape = state.getCollisionShape(world, pos);
    	
    	return getBlockHeight(blockShape);
    }
    
    // Helper method to get blocks height
    private static double getBlockHeight(VoxelShape blockShape) {
        return blockShape.getMax(Axis.Y);
    }


}
