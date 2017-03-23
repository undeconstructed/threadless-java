package threadless;

/**
 * Used for setting up external work that needs to notify the lox about when it is done.
 *
 * @author phil
 */
public interface TaskExternal<T> {

	public void notify(T result);
}
