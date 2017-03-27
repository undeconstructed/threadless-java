package threadless;

/**
 * First call into the {@link Loxecutor} takes a context.
 * 
 * @author phil
 * @param <T>
 */
public interface ExecutionTask<T> {

	public abstract ExecutionResult<T> call(ExecutionContext<T> ctx);
}
