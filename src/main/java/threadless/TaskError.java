package threadless;

/**
 * An error that is leading to the task being aborted.
 *
 * @author phil
 */
public class TaskError {

	public final String message;

	public TaskError(String message) {
		this.message = message;
	}

	public TaskError(Throwable error) {
		this.message = error.getMessage();
	}

	@Override
	public String toString() {
		return message;
	}
}
