package threadless;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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

		public abstract void actor(String lock, Supplier<ActorTask> task, Object input);

		public abstract String submit(String lock, ExecutionTask task);

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

		public abstract void call(String key, Object result);
	}

	/**
	 * TODO
	 */
	interface Executable {

		public abstract Result invoke();
	}

	/**
	 * Tracks the execution of a task. Since the context lasts through all
	 * invocations in a task, it makes some sense for these to be the same
	 * thing.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	class Execution implements Executable, ExecutionContext {

		private final String id;
		private final String lock;
		private ExecutionTask task;
		private Map<String, Object> keys;

		public Execution(String id, String lock, ExecutionTask task) {
			this.id = id;
			this.lock = lock;
			this.task = task;
		}

		@Override
		public Result invoke() {
			try {
				ExecutionResult result = task.call(this);
				keys = null;
				return result;
			} catch (Exception e) {
				e.printStackTrace();
				return new ExecutionResult.ErrorResult(new TaskError(e.getMessage()));
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
		public ExecutionResult v(Object result) {
			return new ExecutionResult.ValueResult(result);
		}

		@Override
		public ExecutionResult e(TaskError error) {
			return new ExecutionResult.ErrorResult(error);
		}

		@Override
		public ExecutionResult c(ExecutionContinuation task) {
			return new ExecutionResult.ContinuationResult(task, keys);
		}
	}

	@SuppressWarnings("rawtypes")
	static class Spawn {

		public String lock;
		public ExecutionTask task;

		public String id;
		public Supplier<ActorTask> supplier;
		public Object input;

		public Spawn(String lock, ExecutionTask task) {
			this.lock = lock;
			this.task = task;
		}

		public Spawn(String id, Supplier<ActorTask> supplier, Object input) {
			this.id = id;
			this.supplier = supplier;
			this.input = input;
		}
	}

	/**
	 * Tracks the execution of an actor.
	 */
	class Actor implements Executable, ActorContext {

		// unique id
		private final String id;
		// how to create the initial actor task
		private final Supplier<ActorTask> supplier;
		// the current actor task
		private ActorTask task;
		// the current actor sleeper, if this actor is sleeping
		private ActorSleeper sleeper;
		// the spawns created during an execution
		public List<Spawn> spawns;
		// queued up inputs
		public Queue<Object> inputs = new LinkedList<>();

		public Actor(String id, Supplier<ActorTask> supplier) {
			this.id = id;
			this.supplier = supplier;
		}

		@Override
		public Result invoke() {
			if (task == null) {
				task = supplier.get();
			}

			try {
				ActorResult result = task.call(this);
				return result;
			} catch (Exception e) {
				e.printStackTrace();
				return new ActorResult.ErrorResult(new TaskError(e.getMessage()));
			}
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public void actor(String id, Supplier<ActorTask> supplier, Object input) {
			if (spawns == null) {
				spawns = new LinkedList<>();
			}
			spawns.add(new Spawn(id, supplier, input));
		}

		@Override
		public void submit(String lock, ExecutionTask task) {
			if (spawns == null) {
				spawns = new LinkedList<>();
			}
			spawns.add(new Spawn(lock, task));
		}

		@Override
		public ActorResult s(ActorSleeper task) {
			return new ActorResult.SleepResult(task);
		}

		@Override
		public ActorResult e(TaskError error) {
			return new ActorResult.ErrorResult(error);
		}

		@Override
		public ActorResult c(ActorContinuation task) {
			return new ActorResult.ContinuationResult(task);
		}
	}

	// thread in which all Loxecutor work must be done
	private final ExecutorService thread = Executors.newSingleThreadExecutor();
	// callback for all Loxecutor outputs
	private final LoxCallback callback;
	// n and c ...
	private long n = 0, c = 0;
	// executions in progress, keyed by their id
	private Map<String, Execution> work = new HashMap<>();
	// actors, keyed by their id
	private Map<String, Actor> actors = new HashMap<>();
	// everything that is currently waiting for, keyed by the notification
	private Map<String, Execution> waiters = new HashMap<>();
	// executions that cannot be run because their lock is taken, keyed by lock
	private Map<String, Queue<Execution>> locked = new HashMap<>();
	// everything that is currently runnable
	private Queue<Executable> queue = new LinkedList<>();
	// flag for signalling shutdown
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
			try {
				work.call(new LoxCtl() {
					@Override
					public void actor(String id, Supplier<ActorTask> supplier, Object input) {
						Loxecutor.this.actor0(id, supplier, input);
					}

					@Override
					public void notify(String key, Object object) {
						Loxecutor.this.notify(key, object);
					}

					@Override
					public String submit(String lock, ExecutionTask task) {
						return Loxecutor.this.submit0(lock, task);
					}
				});
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			thread.execute(this::cycle);
		});
	}

	/**
	 * Submit an actor. This is made threadsafe by running in the context of the
	 * {@link Loxecutor}.
	 * 
	 * @param work
	 */
	public <T> Future<String> submit(String lock, ExecutionTask task) {
		return thread.submit(() -> {
			thread.execute(this::cycle);
			return Loxecutor.this.submit0(lock, task);
		});
	}

	private <T> String submit0(String lock, ExecutionTask task) {
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

	/**
	 * Submit a task. This is made threadsafe by running in the context of the
	 * {@link Loxecutor}.
	 * 
	 * @param work
	 */
	public Future<Void> actor(String id, Supplier<ActorTask> supplier, Object input) {
		return thread.submit(() -> {
			thread.execute(this::cycle);
			Loxecutor.this.actor0(id, supplier, input);
			return null;
		});
	}

	private <T> String actor0(String id, Supplier<ActorTask> supplier, Object input) {
		Actor actor = actors.get(id);
		if (actor == null) {
			actor = new Actor(id, supplier);
			actor.inputs.add(input);
			actors.put(id, actor);
			// schedule setup task, which will reschedule with first input
			queue.add(actor);
		} else if (actor.sleeper != null) {
			Actor a = actor;
			actor.task = (x) -> a.sleeper.call(input);
			actor.sleeper = null;
		} else {
			actor.inputs.add(input);
		}

		return null;
	}

	private void notify(String key, Object object) {
		Execution e = waiters.remove(key);
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

	private void cycle() {
		try {
			cycle0();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void cycle0() {
		Pair<String, Object> result = null;

		Executable e0 = queue.poll();
		if (e0 == null) {
			return;
		}

		Result r0 = e0.invoke();

		switch (r0.type()) {
		case EXECUTION_VALUE: {
			Execution e = (Execution) e0;
			ExecutionResult.ValueResult r = (ExecutionResult.ValueResult) r0;
			Object v = r.value;
			result = new Pair(e.id, v);
			work.remove(e.id);
			Queue<Execution> l = locked.get(e.lock);
			if (l != null) {
				if (!l.isEmpty()) {
					Execution next = l.poll();
					queue.add(next);
				}
				if (l.isEmpty()) {
					locked.remove(e.lock);
				}
			}
			break;
		}
		case EXECUTION_ERROR: {
			Execution e = (Execution) e0;
			ExecutionResult.ErrorResult r = (ExecutionResult.ErrorResult) r0;
			TaskError v = r.error;
			result = new Pair(e.id, v);
			work.remove(e.id);
			// TODO check queue
			break;
		}
		case EXECUTION_CONTINUATION: {
			Execution e = (Execution) e0;
			ExecutionResult.ContinuationResult r = (ExecutionResult.ContinuationResult) r0;
			e.keys = r.keys;
			e.task = (x) -> r.task.call();
			for (String key : e.keys.keySet()) {
				waiters.put(key, e);
			}
			break;
		}
		case ACTOR_SLEEP: {
			Actor a = (Actor) e0;
			ActorResult.SleepResult r = (ActorResult.SleepResult) r0;

			if (a.spawns != null) {
				for (Spawn spawn : a.spawns) {
					if (spawn.lock != null) {
						submit0(spawn.lock, spawn.task);
					} else if (spawn.id == null) {
						actor0(spawn.id, spawn.supplier, spawn.input);
					}
				}
				a.spawns = null;
			}

			Object input = a.inputs.poll();
			if (input != null) {
				a.task = (x) -> r.task.call(input);
				queue.add(a);
			} else {
				a.sleeper = r.task;
			}

			break;
		}
		case ACTOR_ERROR: {
			Actor a = (Actor) e0;
			ActorResult.ErrorResult r = (ActorResult.ErrorResult) r0;
			// TODO hmm
			System.out.println("cannot handle actor errors");
			System.exit(1);
			break;
		}
		case ACTOR_CONTINUATION: {
			Actor a = (Actor) e0;
			ActorResult.ContinuationResult r = (ActorResult.ContinuationResult) r0;
			// TODO hmm
			System.out.println("cannot handle actor continuations");
			System.exit(1);
			break;
		}
		}

		if (result != null) {
			invokeCallback(result);
		}

		if (!queue.isEmpty()) {
			thread.execute(this::cycle);
		}
		// TODO - detect no more actor inputs
		// if (work.isEmpty() && shutdown.get()) {
		// synchronized (this) {
		// thread.shutdown();
		// this.notify();
		// }
		// }
	}

	private void invokeCallback(Pair<String, Object> cbr) {
		thread.execute(() -> {
			try {
				callback.call(cbr.a, cbr.b);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
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
