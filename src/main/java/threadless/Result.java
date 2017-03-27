package threadless;

/**
 * TODO
 * 
 * @author phil
 */
abstract class Result {

	enum Type {
		EXECUTION_VALUE, EXECUTION_ERROR, EXECUTION_CONTINUATION, ACTOR_SLEEP, ACTOR_ERROR, ACTOR_CONTINUATION
	}

	Result() {
	}

	abstract Type type();
}
