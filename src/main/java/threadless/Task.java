package threadless;

/**
 * First call into the {@link Loxecutor} takes a context.
 * 
 * @author phil
 * @param <T>
 */
public interface Task<T> {

	public abstract TaskResult<T> call(TaskContext<T> ctx);
}
