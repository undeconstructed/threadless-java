package threadless;

/**
 * A pair.
 * 
 * @author phil
 * @param <T>
 */
public class ValueOrError<T> {

	public static <T> ValueOrError<T> value(T value) {
		return new ValueOrError<T>(value, null);
	}

	public static <T> ValueOrError<T> error(TaskError error) {
		return new ValueOrError<T>(null, error);
	}

	public static <T> ValueOrError<T> error(Throwable error) {
		return new ValueOrError<T>(null, new TaskError(error));
	}

	public final T value;
	public final TaskError error;

	ValueOrError(T value, TaskError error) {
		this.value = value;
		this.error = error;
	}

	public boolean isError() {
		return error != null;
	}

	public T value() {
		return value;
	}

	public TaskError error() {
		return error;
	}
}
