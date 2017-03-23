package threadless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
	 * @return
	 */
	public abstract <T2> TaskFuture<T2> fut();

	/**
	 * Get a key for allowing an external process to notify us.
	 * 
	 * @return
	 */
	public abstract String ext();

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
	 * task will be invoked when all requirements (created with ext and fut) are
	 * notified.
	 * 
	 * @param task
	 * @return
	 */
	public abstract TaskResult<T> c(TaskContinuation<T> task);
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

		final TaskContinuation<T> task;
		final Map<String, Object> keys;

		ContinuationResult(TaskContinuation<T> task, Map<String, Object> keys) {
			this.keys = keys;
			this.task = task;
		}
	}
}

/**
 * A variation of a future. When these are created they will always be empty -
 * they are only ever supposed to be read inside continuations.
 */
interface TaskFuture<T> {

	public abstract boolean isError();

	public abstract TaskError error();

	public abstract T value();
}

interface Task<T> {

	public abstract TaskResult<T> call(TaskContext<T> ctx);
}

interface TaskContinuation<T> {

	public abstract TaskResult<T> call();
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
		private Map<String, Object> keys;

		public Execution(String id, Task<?> task) {
			this.id = id;
			this.task = task;
		}

		public TaskResult invoke() {
			try {
				TaskResult result = task.call(this);
				keys = null;
				return result;
			} catch (Exception e) {
				return new TaskResult.ErrorResult(new TaskError(e.getMessage()));
			}
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public TaskFuture fut() {
			String key = ext();
			return new TaskFuture() {
				@Override
				public boolean isError() {
					return false;
				}

				@Override
				public TaskError error() {
					return null;
				}

				@Override
				public Object value() {
					return null;
				}
			};
		}

		@Override
		public String ext() {
			if (keys == null) {
				keys = new HashMap<>();
			}
			c++;
			String key = Long.toString(c);
			keys.put(key, null);
			return key;
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
		public TaskResult c(TaskContinuation task) {
			return new TaskResult.ContinuationResult(task, keys);
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
			e.keys.put(key, object);
			for (Map.Entry<String, Object> k : e.keys.entrySet()) {
				if (k.getValue() == null) {
					return;
				}
			}
			queue.add(e);
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

			TaskResult r = e.invoke();
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
				e.task = (x) -> ((TaskResult.ContinuationResult) r).task.call();
				for (String key : e.keys.keySet()) {
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

class SlowThing {
	
	ExecutorService thread2 = Executors.newSingleThreadExecutor();
}

/**
 * TODO
 *
 * @author phil
 */
public class Main {

	public static void main(String[] args) {
		Loxecutor lox = new Loxecutor();

		ExecutorService thread1 = Executors.newSingleThreadExecutor();
		ExecutorService thread2 = Executors.newSingleThreadExecutor();

		thread1.execute(() -> {
			lox.execute(ctx -> {
				String ext = ctx.ext();
				thread2.submit(() -> {
					Thread.sleep(3000);
					thread1.execute(() -> {
						lox.notify(ext, true);
						printResults(lox);
						synchronized (Main.class) {
							Main.class.notify();
						}
					});
					return true;
				});
				return ctx.c(() -> ctx.v("do"));
			});
			lox.execute(ctx -> {
				return ctx.v("re");
			});
			lox.execute(ctx -> {
				return ctx.v("me");
			});

			printResults(lox);
		});

		synchronized (Main.class) {
			try {
				Main.class.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		thread1.shutdown();
		thread2.shutdown();
	}

	private static void printResults(Loxecutor l) {
		for (Pair<String, Object> p : l.cycle()) {
			System.out.format("%s: %s%n", p.a, p.b);
		}
	}
}
