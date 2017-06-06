package threadless;

/**
 * First call into the {@link Loxecutor} takes a context.
 * 
 * @author phil
 * @param <T>
 */
public interface TaskEntry {

	public abstract TaskResult<?> call(TaskContext ctx);
}
