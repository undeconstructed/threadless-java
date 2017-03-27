package threadless;

import java.util.Map;

/**
 * Various different types of result that can come from a task invocation.
 *
 * @author phil
 */
public abstract class ExecutionResult<T> extends Result {

	static class ValueResult<T> extends ExecutionResult<T> {

		final T value;

		ValueResult(T value) {
			this.value = value;
		}

		@Override
		Type type() {
			return Type.EXECUTION_VALUE;
		}
	}

	static class ErrorResult<T> extends ExecutionResult<T> {

		final TaskError error;

		ErrorResult(TaskError error) {
			this.error = error;
		}

		@Override
		Type type() {
			return Type.EXECUTION_ERROR;
		}
	}

	static class ContinuationResult<T> extends ExecutionResult<T> {

		final ExecutionContinuation<T> task;
		final Map<String, Object> keys;

		ContinuationResult(ExecutionContinuation<T> task, Map<String, Object> keys) {
			this.keys = keys;
			this.task = task;
		}

		@Override
		Type type() {
			return Type.EXECUTION_CONTINUATION;
		}
	}
}
