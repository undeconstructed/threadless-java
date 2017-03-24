package threadless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loxecutor combines locking and execution, doing non-blocking concurrency with
 * queueing/ratelimiting/etc per whole system and per context.
 * 
 * @author phil
 */
public class Loxecutor {

	/**
	 * For general interaction with the {@link Loxecutor}.
	 */
	public interface LoxCtl {

		public abstract <T> String submit(String lock, Task<T> task);

		public abstract void notify(String key, Object object);
	}

	/**
	 * For control tasks.
	 * 
	 * @author phil
	 */
	public interface LoxCtlTask {

		public abstract void call(LoxCtl ctl);
	}

	/**
	 * General callback for getting results out of this {@link Loxecutor}.
	 * 
	 * @author phil
	 */
	public interface LoxCallback {

		public abstract void call(List<Pair<String, Object>> results);
	}

	/**
	 * Tracks the execution of a task. Since the context lasts through all
	 * invocations in a task, it makes some sense for these to be the same
	 * thing.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	class Execution implements TaskContext {

		private final String id;
		private final String lock;
		private Task<?> task;
		private Map<String, Object> keys;

		public Execution(String id, String lock, Task<?> task) {
			this.id = id;
			this.lock = lock;
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
			String key = ext0();
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
					return Execution.this.keys.get(key);
				}

				@Override
				public void notify(Object value) {
					Loxecutor.this.execute(ctl -> {
						ctl.notify(key, value);
					});
				}
			};
		}

		private String ext0() {
			if (keys == null) {
				keys = new HashMap<>();
			}
			c++;
			String key = Long.toString(c);
			keys.put(key, null);
			return key;
		}

		@Override
		public TaskExternal ext() {
			String key = ext0();
			return new TaskExternal() {
				@Override
				public void notify(Object value) {
					Loxecutor.this.execute(ctl -> {
						ctl.notify(key, value);
					});
				}
			};
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

	private final ExecutorService thread = Executors.newSingleThreadExecutor();
	private final LoxCallback callback;
	private long n = 0, c = 0;
	private Map<String, Execution> work = new HashMap<>();
	private Map<String, Execution> blocked = new HashMap<>();
	private Map<String, Queue<Execution>> locked = new HashMap<>();
	private Queue<Execution> queue = new LinkedList<>();
	private AtomicBoolean shutdown = new AtomicBoolean(false);

	/**
	 * Make a new {@link Loxecutor}.
	 * 
	 * @param callback
	 */
	public Loxecutor(LoxCallback callback) {
		this.callback = callback;
	}

	/**
	 * Perform arbitrary work in the context of this {@link Loxecutor}.
	 * 
	 * @param work
	 */
	public void execute(LoxCtlTask work) {
		thread.execute(() -> {
			work.call(new LoxCtl() {
				@Override
				public void notify(String key, Object object) {
					Loxecutor.this.notify(key, object);
				}

				@Override
				public <T> String submit(String lock, Task<T> task) {
					return Loxecutor.this.submit0(lock, task);
				}
			});

			thread.execute(this::cycle);
		});
	}

	/**
	 * Submit a task. This is made threadsafe by running in the context of the
	 * {@link Loxecutor}.
	 * 
	 * @param work
	 */
	public <T> Future<String> submit(String lock, Task<T> task) {
		return thread.submit(() -> {
			return Loxecutor.this.submit0(lock, task);
		});
	}

	private <T> String submit0(String lock, Task<T> task) {
		n++;
		String id = Long.toString(n);

		Execution e = new Execution(id, lock, task);
		work.put(id, e);
		Queue<Execution> l = locked.get(lock);
		if (l == null) {
			// XXX existence of queue is used to mark lock as taken, but queue
			// itself might never even be used
			locked.put(lock, new LinkedList<>());
			queue.add(e);
		} else {
			l.add(e);
		}

		return id;
	}

	private void notify(String key, Object object) {
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
	private void cycle() {
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
				Queue<Execution> l = locked.get(e.lock);
				if (l != null && !l.isEmpty()) {
					Execution next = l.poll();
					queue.add(next);
					if (l.isEmpty()) {
						locked.remove(e.lock);
					}
				}
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

		callback.call(results);

		if (work.isEmpty() && shutdown.get()) {
			synchronized (this) {
				thread.shutdown();
				this.notify();
			}
		}
	}

	/**
	 * Shutdown the {@link Loxecutor} when all work is completed.
	 * 
	 * @throws InterruptedException
	 */
	public synchronized void shutdown() throws InterruptedException {
		// XXX - if already no work left this never returns
		shutdown.set(true);
		wait();
	}
}
