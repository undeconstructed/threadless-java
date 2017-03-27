package threadless;

/**
 * First call into the {@link Loxecutor} takes a context.
 * 
 * @author phil
 * @param <T>
 */
public interface ActorTask {

	public abstract ActorResult call(ActorContext ctx);
}
