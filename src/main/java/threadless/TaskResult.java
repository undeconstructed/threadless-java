package threadless;

import java.util.Map;

/**
 * Various different types of result that can come from a task invocation.
 *
 * @author phil
 */
public class TaskResult<T> {

	static class ValueResult<T> extends TaskResult<T> {

		final T value;

		ValueResult(T value) {
			this.value = value;
		}
	}

	static class ErrorResult<T> extends TaskResult<T> {

		final TaskError error;

		ErrorResult(TaskError error) {
			this.error = error;
		}
	}

	static class ContinuationResult<T> extends TaskResult<T> {

		final TaskContinuation<T> task;
		final Map<String, Object> keys;

		ContinuationResult(TaskContinuation<T> task, Map<String, Object> keys) {
			this.keys = keys;
			this.task = task;
		}
	}
}
