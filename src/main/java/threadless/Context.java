package threadless;

import java.util.function.Supplier;

/**
 * TODO
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
	 * 
	 * @param lock
	 * @param task
	 */
	public abstract <T> F<T> submit(String lock, TaskEntry task);
}
