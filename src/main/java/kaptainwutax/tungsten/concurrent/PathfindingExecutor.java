package kaptainwutax.tungsten.concurrent;

import kaptainwutax.tungsten.constants.pathfinding.PathfindingConstants;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Singleton thread pool executor for all pathfinding operations.
 * This class manages a single shared ForkJoinPool to eliminate the overhead
 * of creating hundreds of thread pools per second during pathfinding.
 * <p>
 * Performance improvements:
 * - Eliminates thread creation/destruction overhead
 * - Uses work-stealing for better load distribution
 * - Provides centralized metrics collection
 * - Supports timeout handling without creating new pools
 */
public class PathfindingExecutor {

    private static volatile PathfindingExecutor instance;
    private final ForkJoinPool executor;

    /**
     * Private constructor for a singleton pattern
     */
    private PathfindingExecutor() {

        // Create ForkJoinPool with a custom thread factory for better monitoring
        ForkJoinPool.ForkJoinWorkerThreadFactory factory = pool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName("Pathfinding-Worker-" + thread.getPoolIndex());
            return thread;
        };

        this.executor = new ForkJoinPool(
                Runtime.getRuntime().availableProcessors(),
                factory,
                null, // Use default uncaught exception handler
                PathfindingConstants.ThreadPool.WORK_STEALING_ENABLED
        );
    }

    /**
     * Get the singleton instance
     */
    public static PathfindingExecutor getInstance() {
        PathfindingExecutor result = instance;
        if (result == null) {
            synchronized (PathfindingExecutor.class) {
                result = instance;
                if (result == null) {
                    instance = result = new PathfindingExecutor();
                }
            }
        }
        return result;
    }

    /**
     * Submit a single task with timeout handling
     *
     * @param task      The callable task to execute
     * @param timeoutMs Timeout in milliseconds
     * @return Future representing the task result
     */
    public <T> Future<T> submitTask(Callable<T> task, long timeoutMs) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);

        // Apply timeout
        return future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> null);
    }

    /**
     * Submit a batch of tasks for parallel execution with timeout
     *
     * @param tasks     Collection of tasks to execute
     * @param timeoutMs Timeout for the entire batch
     * @return List of futures representing task results
     */
    public <T> List<Future<T>> submitBatch(Collection<Callable<T>> tasks, long timeoutMs) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Future<T>> futures = new ArrayList<>(tasks.size());

        for (Callable<T> task : tasks) {
            futures.add(submitTask(task, timeoutMs));
        }

        return futures;
    }
}