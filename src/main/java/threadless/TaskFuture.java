package threadless;

/**
 * A variation of a future. When these are created they will always be empty - they are only ever supposed to be read
 * inside continuations.
 */
public interface TaskFuture<T> {

	public abstract boolean isError();

	public abstract TaskError error();

	public abstract T value();

	public abstract void notify(Object value);
}
