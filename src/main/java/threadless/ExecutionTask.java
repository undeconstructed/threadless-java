package threadless;

/**
 * First call into the {@link Loxecutor} takes a context.
 * 
 * @author phil
 * @param <T>
 */
public interface ExecutionTask {

	public abstract ExecutionResult<?> call(ExecutionContext ctx);
}
