package threadless;

import java.util.function.Supplier;

/**
 * The context in which a task runs. This follows the initial and subsequent invocations of a task.
 *
 * @author phil
 */
public interface ActorContext {

	/**
	 * Id of this context.
	 * 
	 * @return
	 */
	public abstract String id();

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
	public abstract <T> void submit(String lock, ExecutionTask task);

	/**
	 * Get a sleep result to return. This implies the actor is ready for more input.
	 * 
	 * @param task
	 * @return
	 */
	public abstract ActorResult s(ActorSleeper task);

	/**
	 * Get an error result to return.
	 * 
	 * @param error
	 * @return
	 */
	public abstract ActorResult e(TaskError error);

	/**
	 * Get a continuation result to return. No new messages input will be delivered, instead the actor will wait for
	 * notifications.
	 * 
	 * @param task
	 * @return
	 */
	public abstract ActorResult c(ActorContinuation task);
}
