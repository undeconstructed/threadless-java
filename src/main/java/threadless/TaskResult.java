package threadless;

/**
 * Various different types of result that can come from a task invocation.
 *
 * @author phil
 */
public abstract class TaskResult<T> extends Result {

	static class ValueResult<T> extends TaskResult<T> {

		final T value;

		ValueResult(T value) {
			this.value = value;
		}

		@Override
		Type type() {
			return Type.EXECUTION_VALUE;
		}
	}

	static class ErrorResult<T> extends TaskResult<T> {

		final TaskError error;

		ErrorResult(TaskError error) {
			this.error = error;
		}

		@Override
		Type type() {
			return Type.EXECUTION_ERROR;
		}
	}

	static class ContinuationResult<T> extends TaskResult<T> {

		final TaskContinuation task;

		ContinuationResult(TaskContinuation task) {
			this.task = task;
		}

		@Override
		Type type() {
			return Type.EXECUTION_CONTINUATION;
		}
	}

	private TaskResult() {
	}
}
