package threadless;

/**
 * An error that is leading to the task being aborted.
 *
 * @author phil
 */
public class TaskError {

	public final String message;

	TaskError(String message) {
		this.message = message;
	}
}
