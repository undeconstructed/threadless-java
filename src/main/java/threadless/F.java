package threadless;

/**
 * A variation of a future. When these are created they will always be empty - they are only ever supposed to be read
 * inside continuations.
 */
public interface F<T> {

	/**
	 * In the context of an actor, it is possible that futures will not be done when the actor is awoken, in the context
	 * of tasks there is no need to check.
	 * 
	 * @return
	 */
	public abstract boolean isDone();

	/**
	 * Returns whether there was an error. If this is true, then {@link #error()} will return a non-null. Will only
	 * return true if {@link #isDone()} does also.
	 * 
	 * @return
	 */
	public abstract boolean isError();

	/**
	 * The error encountered in completing this future, will only be set when {@link #isError()} returns true.
	 * 
	 * @return
	 */
	public abstract TaskError error();

	/**
	 * The complete value. This will only be set when {@link #isDone()} returns true.
	 * 
	 * @return
	 */
	public abstract T value();
}
