package threadless;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * An error that is leading to the task being aborted.
 *
 * @author phil
 */
class TaskError {

	public final String message;

	TaskError(String message) {
		this.message = message;
	}
}

/**
 * The context in which a task runs. This follows the initial and subsequent
 * invocations of a task.
 *
 * @author phil
 */
interface TaskContext<T> {

	/**
	 * Id of this context.
	 * 
	 * @return
	 */
	public abstract String id();

	/**
	 * Get a value result to return.
	 * 
	 * @param result
	 * @return
	 */
	public abstract TaskResult<T> v(T result);

	/**
	 * Get an error result to return.
	 * 
	 * @param error
	 * @return
	 */
	public abstract TaskResult<T> e(TaskError error);

	/**
	 * Get a continuation result to return. The context will remain open and the
	 * task will be invoked when the key is notified.
	 * 
	 * @param task
	 * @param keys
	 * @return
	 */
	public abstract TaskResult<T> c(Task<T> task, String... keys);

	/**
	 * Get a key for allowing an external process to notify us.
	 * 
	 * @return
	 */
	public abstract String ext();
}

/**
 * Various different types of result that can come from a task invocation.
 *
 * @author phil
 */
class TaskResult<T> {

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

		final Set<String> keys;
		final Task<T> task;

		ContinuationResult(Task<T> task, Set<String> keys) {
			this.keys = keys;
			this.task = task;
		}
	}
}

/**
 * A variation of a future. Be aware that this is basically
 */
interface TaskFuture<T> {

}

interface Task<T> {

	public abstract TaskResult<T> call(TaskContext<T> ctx);
}

class Pair<A, B> {

	public final A a;
	public final B b;

	public Pair(A a, B b) {
		this.a = a;
		this.b = b;
	}
}

class Loxecutor {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	class Execution implements TaskContext {

		private final String id;
		private Task<?> task;
		private Set<String> keys;

		public Execution(String id, Task<?> task) {
			this.id = id;
			this.task = task;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public TaskResult v(Object result) {
			return new TaskResult.ValueResult(result);
		}

		@Override
		public TaskResult e(TaskError error) {
			return new TaskResult.ErrorResult(error);
		}

		@Override
		public TaskResult c(Task task, String... keys) {
			return new TaskResult.ContinuationResult(task, new HashSet<String>(Arrays.asList(keys)));
		}

		@Override
		public String ext() {
			c++;
			return Long.toString(c);
		}
	}

	private long n = 0, c = 0;
	private Map<String, Execution> work = new HashMap<>();
	private Map<String, Execution> blocked = new HashMap<>();
	private Queue<Execution> queue = new LinkedList<>();

	public <T> String execute(Task<T> task) {
		n++;
		String id = Long.toString(n);

		Execution e = new Execution(id, task);
		work.put(id, e);
		queue.add(e);

		return id;
	}

	public void notify(String key, Object object) {
		Execution e = blocked.remove(key);
		if (e != null) {
			// TODO - gather the results somehow
			e.keys.remove(key);
			if (e.keys.isEmpty()) {
				queue.add(e);
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Pair<String, Object>> cycle() {
		List<Pair<String, Object>> results = new ArrayList<>();

		while (true) {
			Execution e = queue.poll();
			if (e == null) {
				break;
			}

			TaskResult r = e.task.call(e);
			if (r instanceof TaskResult.ValueResult) {
				Object v = ((TaskResult.ValueResult) r).value;
				results.add(new Pair(e.id, v));
				work.remove(e.id);
			} else if (r instanceof TaskResult.ErrorResult) {
				TaskError v = ((TaskResult.ErrorResult) r).error;
				results.add(new Pair(e.id, v));
				work.remove(e.id);
			} else if (r instanceof TaskResult.ContinuationResult) {
				e.keys = ((TaskResult.ContinuationResult) r).keys;
				e.task = ((TaskResult.ContinuationResult) r).task;
				for (String key : e.keys) {
					blocked.put(key, e);
				}
			}
		}

		return results;
	}

	public boolean pending() {
		return !work.isEmpty();
	}
}

/**
 * TODO
 *
 * @author phil
 */
public class Main {

	public static void main(String[] args) {
		Loxecutor l = new Loxecutor();

		l.execute(ctx -> {
			return ctx.v("do");
		});
		l.execute(ctx -> {
			return ctx.v("re");
		});
		l.execute(ctx -> {
			return ctx.v("me");
		});

		while (l.pending()) {
			for (Pair<String, Object> p : l.cycle()) {
				System.out.format("%s: %s%n", p.a, p.b);
			}
		}
	}
}
