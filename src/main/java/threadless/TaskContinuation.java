package threadless;

/**
 * As {@link TaskEntry} but doesn't need a context because there will be closed one from earlier.
 * 
 * @author phil
 * @param <T>
 */
public interface TaskContinuation {

	public abstract TaskResult<?> call();
}
