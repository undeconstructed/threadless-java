package threadless;

/**
 * As {@link ExecutionTask} but doesn't need a context because there will be closed one from earlier.
 * 
 * @author phil
 * @param <T>
 */
public interface ExecutionContinuation<T> {

	public abstract ExecutionResult<T> call();
}
