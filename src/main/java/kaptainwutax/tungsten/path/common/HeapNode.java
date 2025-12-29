package kaptainwutax.tungsten.path.common;

/**
 * Common interface for nodes that can be stored in a binary heap.
 * Both Node and BlockNode implement this interface.
 *
 * @author leijurv (original IOpenSet implementation)
 */
public interface HeapNode {
    /**
     * Gets the current position of this node in the heap.
     *
     * @return The heap position index
     */
    int getHeapPosition();

    /**
     * Sets the position of this node in the heap.
     *
     * @param position The new heap position index
     */
    void setHeapPosition(int position);

    /**
     * Gets the combined cost (g + h) used for heap ordering.
     *
     * @return The combined cost value
     */
    double getCombinedCost();
}