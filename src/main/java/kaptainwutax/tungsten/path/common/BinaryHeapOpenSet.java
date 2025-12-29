package kaptainwutax.tungsten.path.common;

import java.util.Arrays;

/**
 * A generic binary heap implementation of an open set.
 * This replaces the duplicate BinaryHeapOpenSet implementations for Node and BlockNode.
 *
 * @param <T> The type of node stored in the heap, must extend HeapNode
 * @author leijurv (original implementation)
 */
public final class BinaryHeapOpenSet<T extends HeapNode> implements IOpenSet<T> {

    /**
     * The initial capacity of the heap (2^10)
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * The array backing the heap
     */
    private T[] array;

    /**
     * The size of the heap
     */
    private int size;

    public BinaryHeapOpenSet() {
        this(INITIAL_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public BinaryHeapOpenSet(int size) {
        this.size = 0;
        this.array = (T[]) new HeapNode[size];
    }

    public int size() {
        return size;
    }

    @Override
    public void insert(T value) {
        if (size >= array.length - 1) {
            array = Arrays.copyOf(array, array.length << 1);
        }
        size++;
        value.setHeapPosition(size);
        array[size] = value;
        update(value);
    }

    @Override
    public void update(T val) {
        int index = val.getHeapPosition();
        int parentInd = index >>> 1;
        double cost = val.getCombinedCost();
        T parentNode = array[parentInd];
        while (index > 1 && parentNode.getCombinedCost() > cost) {
            array[index] = parentNode;
            array[parentInd] = val;
            val.setHeapPosition(parentInd);
            parentNode.setHeapPosition(index);
            index = parentInd;
            parentInd = index >>> 1;
            parentNode = array[parentInd];
        }
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public T removeLowest() {
        if (size == 0) {
            throw new IllegalStateException();
        }
        T result = array[1];
        T val = array[size];
        array[1] = val;
        val.setHeapPosition(1);
        array[size] = null;
        size--;
        result.setHeapPosition(-1);
        if (size < 2) {
            return result;
        }
        int index = 1;
        int smallerChild = 2;
        double cost = val.getCombinedCost();
        do {
            T smallerChildNode = array[smallerChild];
            double smallerChildCost = smallerChildNode.getCombinedCost();
            if (smallerChild < size) {
                T rightChildNode = array[smallerChild + 1];
                double rightChildCost = rightChildNode.getCombinedCost();
                if (smallerChildCost > rightChildCost) {
                    smallerChild++;
                    smallerChildCost = rightChildCost;
                    smallerChildNode = rightChildNode;
                }
            }
            if (cost <= smallerChildCost) {
                break;
            }
            array[index] = smallerChildNode;
            array[smallerChild] = val;
            val.setHeapPosition(smallerChild);
            smallerChildNode.setHeapPosition(index);
            index = smallerChild;
        } while ((smallerChild <<= 1) <= size);
        return result;
    }
}