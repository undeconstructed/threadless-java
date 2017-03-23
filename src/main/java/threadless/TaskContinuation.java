package threadless;

/**
 * Further calls don't for simplicities sake - can just use the closed one from before.
 * 
 * @author phil
 * @param <T>
 */
public interface TaskContinuation<T> {

	public abstract TaskResult<T> call();
}
