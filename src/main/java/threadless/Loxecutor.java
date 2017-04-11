package threadless;

import java.time.Clock;
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

import threadless.Result.Type;

/**
 * Loxecutor combines locking and execution, doing non-blocking concurrency with queueing/ratelimiting/etc per whole
 * system and per context.
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

		public abstract void call(String key, TaskFuture<?> result);
	}

	/**
	 * TODO
	 */
	private interface Executable {

		public abstract Result invoke();

		public abstract boolean notify(String key, Object object);
	}

	private static final int HOW_MANY_TIME_SAMPLES = 4;
	private static final long HOW_LONG_IS_TOO_LONG = 2000;

	/**
	 * Tracks the average of the last n numbers it was given. Is not accurate at the start.
	 * 
	 * @author phil
	 */
	private static class RecentAverage {

		private long[] ts;
		private int n;

		public RecentAverage(int size) {
			ts = new long[size];
		}

		public long update(long t) {
			ts[n] = t;
			n = (n + 1) % ts.length;
			long s = 0;
			for (int i = 0; i < ts.length; i++) {
				s += ts[i];
			}
			return s / ts.length;
		}
	}

	private static TaskFuture<?> errorFuture(TaskError error) {
		return (TaskFuture<?>) new TaskFuture<Void>() {
			@Override
			public boolean isDone() {
				return true;
			}

			@Override
			public boolean isError() {
				return true;
			}

			@Override
			public TaskError error() {
				return error;
			}

			@Override
			public Void value() {
				return null;
			}
		};
	}

	private static TaskFuture<Object> valueFuture(Object value) {
		return new TaskFuture<Object>() {
			@Override
			public boolean isDone() {
				return true;
			}

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
				return value;
			}
		};
	}

	/**
	 * Manages a sequence of Executions waiting on the same lock.
	 */
	private class Executor {

		// lock this executor is guarding
		public final String lock;
		// currently active execution
		private Execution active;
		// executions that are waiting on this lock
		private Queue<Execution> queue = new LinkedList<>();
		// for tracking execution times
		private RecentAverage timings;
		// for tracking execution times
		private long queueMax = 1;

		public Executor(String lock) {
			super();
			this.lock = lock;
			this.timings = new RecentAverage(HOW_MANY_TIME_SAMPLES);
		}

		/**
		 * Adds an {@link Execution}, or returns an error saying it will not be run.
		 */
		public TaskError add(String id, ExecutionTask task) {
			if (queue.size() > queueMax) {
				return new TaskError("queue full");
			}

			Execution e = new Execution(this, id, task);
			queue.add(e);
			return null;
		}

		/**
		 * If nothing is active, this makes the next from the queue active, and returns it to be scheduled.
		 */
		public Execution getExecutionToSchedule() {
			if (active == null) {
				active = queue.poll();
				if (active != null) {
					return active;
				}
			}
			return null;
		}

		public void onComplete(Execution e) {
			assert e == active;
			active = null;
			long avg = timings.update(clock.millis() - e.tFirstRun);
			queueMax = HOW_LONG_IS_TOO_LONG / avg;
			System.out.format("lock: %s; average %dms; queue: %d; queue max: %d%n", lock, avg, queue.size(), queueMax);
		}
	}

	/**
	 * Tracks the execution of a task. Since the context lasts through all invocations in a task, it makes some sense
	 * for these to be the same thing.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private class Execution implements Executable, ExecutionContext {

		// which executor is managing this execution
		private final Executor executor;
		// unique id of this execution
		private final String id;
		// next task to be run
		private ExecutionTask task;
		// keys to wait on, if currently waiting
		// TODO - need to be able to store errors in here
		private Map<String, Object> keys;
		// timings
		private long tCreated, tFirstRun, tTotalRunning, tStartedWaiting, tTotalWaiting;

		public Execution(Executor executor, String id, ExecutionTask task) {
			this.executor = executor;
			this.id = id;
			this.task = task;
			this.tCreated = clock.millis();
		}

		@Override
		public Result invoke() {
			long t0 = clock.millis();
			if (tFirstRun == 0) {
				tFirstRun = t0;
			} else if (tStartedWaiting != 0) {
				tTotalWaiting += t0 - tStartedWaiting;
				tStartedWaiting = 0;
			}
			ExecutionResult result;
			try {
				result = task.call(this);
			} catch (Exception e) {
				e.printStackTrace();
				result = new ExecutionResult.ErrorResult(new TaskError(e.getMessage()));
			}
			long t1 = clock.millis();
			tTotalRunning += t1 - t0;
			if (result.type() == Type.EXECUTION_CONTINUATION) {
				tStartedWaiting = t1;
			} else {
				System.out.format("lock: %s; task: %s; running: %dms; waiting: %dms; total: %dms%n", executor.lock, id,
						tTotalRunning, tTotalWaiting, (t1 - tCreated));
			}
			return result;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public void actor(String id, Supplier<ActorTask> task, Object input) {
			throw new RuntimeException("not implemented");
		}

		@Override
		public <T> TaskFuture<T> submit(String lock, ExecutionTask task) {
			throw new RuntimeException("not implemented");
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
				public TaskFuture future() {
					// TODO - this future should check for errors
					return new TaskFuture() {
						@Override
						public boolean isDone() {
							return true;
						}

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
					};
				}

				@Override
				public void notify(Object value) {
					Loxecutor.this.notify(key, value);
				}
			};
		}

		@Override
		public boolean notify(String key, Object object) {
			keys.put(key, object);
			for (Map.Entry<String, Object> k : keys.entrySet()) {
				if (k.getValue() == null) {
					return false;
				}
			}
			return true;
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
			return new ExecutionResult.ContinuationResult(task);
		}
	}

	/**
	 * For tracking what should be spawned as the result of an invocation.
	 * 
	 * @author phil
	 */
	private static class Spawn {

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
	private class Actor implements Executable, ActorContext {

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

		private Map<String, Object> keys;

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
		public <T> TaskFuture<T> submit(String lock, ExecutionTask task) {
			if (spawns == null) {
				spawns = new LinkedList<>();
			}
			spawns.add(new Spawn(lock, task));
			// TODO - link up a future here
			return null;
		}

		@Override
		public boolean notify(String key, Object object) {
			keys.put(key, object);
			for (Map.Entry<String, Object> k : keys.entrySet()) {
				if (k.getValue() == null) {
					return false;
				}
			}
			return true;
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
	// executors, keyed by their locks
	private Map<String, Executor> executors = new HashMap<>();
	// actors, keyed by their id
	private Map<String, Actor> actors = new HashMap<>();
	// everything that is currently waiting for, keyed by the notification
	private Map<String, Executable> waiters = new HashMap<>();
	// everything that is currently runnable
	private Queue<Executable> queue = new LinkedList<>();
	// flag for signalling shutdown
	private AtomicBoolean shutdown = new AtomicBoolean(false);
	// clock
	private Clock clock = Clock.systemUTC();
	// global timings
	// private RecentAverage tBeforeRun = new RecentAverage(16);
	// private RecentAverage tInQueue = new RecentAverage(16);
	// TODO - more timings

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
						Loxecutor.this.notify0(key, object);
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
	 * Submit an actor. This is made threadsafe by running in the context of the {@link Loxecutor}.
	 * 
	 * @param lock
	 * @param task
	 * @return
	 */
	public <T> Future<String> submit(String lock, ExecutionTask task) {
		return thread.submit(() -> {
			return Loxecutor.this.submit0(lock, task);
		});
	}

	/**
	 * Internal submit function, must run in the main thread.
	 * 
	 * @param lock
	 * @param task
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private <T> String submit0(String lock, ExecutionTask task) {
		Executor er = executors.get(lock);
		if (er == null) {
			er = new Executor(lock);
			executors.put(lock, er);
		}

		n++;
		String id = Long.toString(n);

		TaskError error = er.add(id, task);
		if (error != null) {
			invokeCallback(new Pair(id, errorFuture(error)));
		}

		Execution e = er.getExecutionToSchedule();
		if (e != null) {
			queue.add(e);
			thread.execute(this::cycle);
		}

		return id;
	}

	/**
	 * Submit an actor. This is made threadsafe by running in the context of the {@link Loxecutor}.
	 * 
	 * @param id
	 * @param supplier
	 * @param input
	 */
	public void actor(String id, Supplier<ActorTask> supplier, Object input) {
		thread.submit(() -> {
			Loxecutor.this.actor0(id, supplier, input);
		});
	}

	/**
	 * Internal actor function, must run in the main thread.
	 * 
	 * @param id
	 * @param supplier
	 * @param input
	 */
	private void actor0(String id, Supplier<ActorTask> supplier, Object input) {
		Actor actor = actors.get(id);
		if (actor == null) {
			// need a new actor to process this
			actor = new Actor(id, supplier);
			actor.inputs.add(input);
			actors.put(id, actor);
			// schedule setup task, which will reschedule with first input
			queue.add(actor);
			thread.execute(this::cycle);
		} else if (actor.sleeper != null) {
			// if actor was sleeping, then it has no input and is now schedulable
			Actor a = actor;
			actor.task = (x) -> a.sleeper.call(input);
			actor.sleeper = null;
			queue.add(actor);
			thread.execute(this::cycle);
		} else {
			// if actor has no sleeper, then it is busy and this won't make it schedulable
			actor.inputs.add(input);
		}
	}

	public void notify(String key, Object object) {
		thread.submit(() -> {
			notify0(key, object);
		});
	}

	/**
	 * For external work to notify waiters. Must be called in the main thread.
	 * 
	 * @param key
	 * @param object
	 */
	private void notify0(String key, Object object) {
		Executable e = waiters.remove(key);
		if (e != null) {
			if (e.notify(key, object)) {
				queue.add(e);
				thread.execute(this::cycle);
			}
		}
	}

	/**
	 * Just runs {@link #cycle0()}, but exits process on exceptions. This is only here because cycle is often called as
	 * the first lox frame in the executor, so any exceptions from bugs would be lost.
	 */
	private void cycle() {
		try {
			cycle0();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Processes the work queue. Must be called in the main thread.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void cycle0() {
		Pair<String, TaskFuture<?>> result = null;

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
			result = new Pair(e.id, valueFuture(v));

			Executor er = e.executor;
			er.onComplete(e);
			Execution en = er.getExecutionToSchedule();
			if (en != null) {
				queue.add(en);
			}
			break;
		}
		case EXECUTION_ERROR: {
			Execution e = (Execution) e0;
			ExecutionResult.ErrorResult r = (ExecutionResult.ErrorResult) r0;
			TaskError v = r.error;
			result = new Pair(e.id, errorFuture(v));

			Executor er = e.executor;
			er.onComplete(e);
			Execution en = er.getExecutionToSchedule();
			if (en != null) {
				queue.add(e);
			}
			break;
		}
		case EXECUTION_CONTINUATION: {
			Execution e = (Execution) e0;
			ExecutionResult.ContinuationResult r = (ExecutionResult.ContinuationResult) r0;
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
			a.task = (x) -> r.task.call();
			for (String key : a.keys.keySet()) {
				waiters.put(key, a);
			}
			break;
		}
		}

		if (result != null) {
			invokeCallback(result);
		}

		if (!queue.isEmpty()) {
			thread.execute(this::cycle);
		}

		// TODO
		// if (shutdown.get() && ...) {
		// synchronized (this) {
		// thread.shutdown();
		// this.notify();
		// }
		// }
	}

	private void invokeCallback(Pair<String, TaskFuture<?>> cbr) {
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
		shutdown.set(true);
		thread.execute(this::cycle);
		wait();
	}
}
