package threadless;

import java.util.function.Supplier;

/**
 * Contexts track the execution of an actor or an indivual task.
 * 
 * @author phil
 */
public interface Context {

	/**
	 * Spawn or notify another actor.
	 * 
	 * @param id
	 * @param task
	 * @param input
	 */
	public abstract void actor(String id, Supplier<ActorTask> task, Object input);

	/**
	 * Submit a task under a lock, getting a future to say when it has been completed. Continuations will not be run
	 * until the task has completed (or failed, or been rejected).
	 * 
	 * @param lock
	 * @param task
	 */
	public abstract <T> F<T> submit(String lock, TaskEntry task);

	/**
	 * Log to this context.
	 * 
	 * @param message
	 */
	public abstract void log(String message);
}
