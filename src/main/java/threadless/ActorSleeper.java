package threadless;

/**
 * As {@link ActorTask} but doesn't need a context because there will be closed one from earlier.
 * 
 * @author phil
 */
public interface ActorSleeper {

	public abstract ActorResult call(Object input);
}
