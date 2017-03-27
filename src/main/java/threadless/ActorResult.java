package threadless;

/**
 * Various different types of result that can come from a task invocation.
 *
 * @author phil
 */
public abstract class ActorResult extends Result {

	static class SleepResult extends ActorResult {

		final ActorSleeper task;

		SleepResult(ActorSleeper task) {
			this.task = task;
		}

		@Override
		Type type() {
			return Type.ACTOR_SLEEP;
		}
	}

	static class ErrorResult extends ActorResult {

		final TaskError error;

		ErrorResult(TaskError error) {
			this.error = error;
		}

		@Override
		Type type() {
			return Type.ACTOR_ERROR;
		}
	}

	static class ContinuationResult extends ActorResult {

		final ActorContinuation task;

		ContinuationResult(ActorContinuation task) {
			this.task = task;
		}

		@Override
		Type type() {
			return Type.ACTOR_CONTINUATION;
		}
	}
}
