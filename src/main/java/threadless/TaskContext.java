package threadless;

/**
 * The context in which a task runs. This follows the initial and subsequent invocations of a task.
 *
 * @author phil
 */
public interface TaskContext extends Context {

	/**
	 * Id of this context.
	 * 
	 * @return
	 */
	public abstract String id();

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
	public abstract <T> TaskResult<T> v(T result);

	/**
	 * Get an error result to return.
	 * 
	 * @param error
	 * @return
	 */
	public abstract TaskResult<Void> e(TaskError error);

	/**
	 * Get a continuation result to return. The context will remain open and the task will be invoked when all
	 * requirements (created with ext and fut) are notified.
	 * 
	 * @param task
	 * @return
	 */
	public abstract TaskResult<Void> c(TaskContinuation task);

	/**
	 * As {@link #c(TaskContinuation)}, but allows switching mode. By default all tasks are in read only mode and may be
	 * scheduled concurrently; switching to write mode will put this task on hold until all read mode tasks on the lock
	 * are completed, at which point it will be run exclusively. If multiple readers attempt to gain write mode at the
	 * same time they will be queued and run sequentially.
	 * 
	 * @param task
	 * @param mode
	 * @return
	 */
	public abstract TaskResult<Void> c(TaskContinuation task, TaskMode mode);
}
