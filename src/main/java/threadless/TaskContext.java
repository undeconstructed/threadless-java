package threadless;

/**
 * The context in which a task runs. This follows the initial and subsequent invocations of a task.
 *
 * @author phil
 */
public interface TaskContext<T> {

	/**
	 * Id of this context.
	 * 
	 * @return
	 */
	public abstract String id();

	/**
	 * @return
	 */
	public abstract <T2> TaskFuture<T2> fut();

	/**
	 * Get a sort of context for allowing an external process to notify us.
	 * 
	 * @return
	 */
	public abstract <T2> TaskExternal<T2> ext();

	/**
	 * Get a value result to return.
	 * 
	 * @param result
	 * @return
	 */
	public abstract TaskResult<T> v(T result);

	/**
	 * Get an error result to return.
	 * 
	 * @param error
	 * @return
	 */
	public abstract TaskResult<T> e(TaskError error);

	/**
	 * Get a continuation result to return. The context will remain open and the task will be invoked when all
	 * requirements (created with ext and fut) are notified.
	 * 
	 * @param task
	 * @return
	 */
	public abstract TaskResult<T> c(TaskContinuation<T> task);
}
