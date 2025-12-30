package kaptainwutax.tungsten.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import kaptainwutax.tungsten.Debug;

/**
 * TaskManager handles the lifecycle of pathfinding tasks, providing
 * timeout management, cancellation, and batch processing capabilities.
 * <p>
 * This replaces the per-node ExecutorService creation with intelligent
 * task management using the shared PathfindingExecutor.
 */
public class TaskManager {

    private final PathfindingExecutor executor;
    private final List<Future<?>> activeTasks;
    private final AtomicBoolean cancelled;

    /**
     * Create a new TaskManager
     */
    public TaskManager() {
        this.executor = PathfindingExecutor.getInstance();
        this.activeTasks = new CopyOnWriteArrayList<>();
        this.cancelled = new AtomicBoolean(false);
    }

    /**
     * Process a batch of node filtering tasks in parallel
     * This replaces the first executor in PathFinder.java (lines 657-700)
     *
     * @param tasks List of filtering tasks to execute
     * @param validResults Concurrent collection to store valid results
     * @param stopSignal AtomicBoolean to check for early termination
     * @param timeoutMs Timeout for this batch
     */
    public <T> void processNodeFilteringBatch(
            List<Callable<T>> tasks,
            Collection<T> validResults,
            AtomicBoolean stopSignal,
            long timeoutMs) {

        if (cancelled.get() || stopSignal.get() || tasks.isEmpty()) {
            return;
        }

        // Convert tasks to handle interruption and stop signals
        List<Callable<T>> wrappedTasks = tasks.stream()
            .map(task -> (Callable<T>) () -> {
                if (cancelled.get() || stopSignal.get() || Thread.currentThread().isInterrupted()) {
                    return null;
                }
                return task.call();
            })
            .collect(Collectors.toList());

        // Submit all tasks for parallel execution
        List<Future<T>> futures = executor.submitBatch(wrappedTasks, timeoutMs);
        activeTasks.addAll(futures);

        long deadline = System.currentTimeMillis() + timeoutMs;

        // Collect results with timeout handling
        for (Future<T> future : futures) {
            if (cancelled.get() || stopSignal.get()) {
                future.cancel(true);
                continue;
            }

            long remainingTime = deadline - System.currentTimeMillis();
            if (remainingTime <= 0) {
                future.cancel(true);
                continue;
            }

            try {
                T result = future.get(remainingTime, TimeUnit.MILLISECONDS);
                if (result != null && validResults != null) {
                    validResults.add(result);
                }
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                break;
            } catch (ExecutionException e) {
                // Task failed, log if needed but continue
                if (Debug.isDebugEnabled()) {
                    Debug.logWarning("Task failed: " + e.getCause().getMessage());
                }
            } catch (CancellationException e) {
                // Task was cancelled, continue
            }
        }

        // Clean up completed tasks from tracking
        activeTasks.removeAll(futures);
    }

    /**
     * Process node update tasks in parallel (for openSet updates)
     * This replaces the second executor in PathFinder.java (lines 715-750)
     *
     * @param updateTasks Tasks that update nodes and add to openSet
     * @param timeoutMs Timeout for this batch
     */
    public void processNodeUpdates(List<Runnable> updateTasks, long timeoutMs) {
        if (cancelled.get() || updateTasks.isEmpty()) {
            return;
        }

        // Convert Runnables to Callables
        List<Callable<Void>> callables = updateTasks.stream()
            .map(task -> (Callable<Void>) () -> {
                if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                    return null;
                }
                task.run();
                return null;
            })
            .collect(Collectors.toList());

        processNodeFilteringBatch(callables, null, new AtomicBoolean(false), timeoutMs);
    }


    /**
     * Cancel all active tasks managed by this TaskManager
     */
    public void cancelAll() {
        cancelled.set(true);
        for (Future<?> future : activeTasks) {
            future.cancel(true);
        }
        activeTasks.clear();
    }
}