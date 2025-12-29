package kaptainwutax.tungsten.path.common;

/**
 * A generic open set for A* or similar graph search algorithm.
 * This replaces the duplicate IOpenSet interfaces for Node and BlockNode.
 *
 * @param <T> The type of node stored in the open set, must extend HeapNode
 * @author leijurv (original implementation)
 */
public interface IOpenSet<T extends HeapNode> {

    /**
     * Inserts the specified node into the heap
     *
     * @param node The node
     */
    void insert(T node);

    /**
     * @return {@code true} if the heap has no elements; {@code false} otherwise.
     */
    boolean isEmpty();

    /**
     * Removes and returns the minimum element in the heap.
     *
     * @return The minimum element in the heap
     */
    T removeLowest();

    /**
     * A faster path has been found to this node, decreasing its cost. Perform a decrease-key operation.
     *
     * @param node The node
     */
    void update(T node);
}