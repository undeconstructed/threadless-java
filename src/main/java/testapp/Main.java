package testapp;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import threadless.ActorContext;
import threadless.ActorResult;
import threadless.ActorSleeper;
import threadless.ActorTask;
import threadless.ExecutionContext;
import threadless.Loxecutor;
import threadless.TaskExternal;
import threadless.TaskFuture;

/**
 * A sort of service that takes a long time to do whatever it does.
 * 
 * @author phil
 */
class SlowThing {

	ExecutorService e = Executors.newCachedThreadPool();
	Random r = new Random();

	public void doSlowThingFromExt(TaskExternal<String> ext) {
		e.submit(() -> {
			Thread.sleep(3000);
			ext.notify("result");
			return true;
		});
	}

	public TaskFuture<String> doSlowThingForFuture(ExecutionContext ctx) {
		int s = r.nextInt(3000);
		TaskExternal<String> ext = ctx.ext();
		e.submit(() -> {
			Thread.sleep(s);
			ext.notify(String.valueOf(s));
			return true;
		});
		return ext.future();
	}
}

/**
 * TODO
 *
 * @author phil
 */
public class Main {

	public static void main(String[] args) throws Exception {
		long t0 = System.currentTimeMillis();

		Loxecutor lox = new Loxecutor((id, result) -> {
			long td = System.currentTimeMillis() - t0;
			System.out.format("[%d] %s: %s%n", td, id, result);
		});

		SlowThing slow = new SlowThing();

		// directExecutions(t0, lox, slow);
		withRootActor(t0, lox, slow);

		lox.shutdown();
		System.out.format("[%d] done%n", System.currentTimeMillis() - t0);
		System.exit(0);
	}

	public static void directExecutions(long t0, Loxecutor lox, SlowThing slow) {
		lox.execute(ctl -> {
			ctl.submit("a", ctx -> {
				// TaskExternal<String> ext = ctx.ext();
				// slow.doSlowThingFromExt(ext);
				// return ctx.c(() -> ctx.v("do"));
				TaskFuture<String> f = slow.doSlowThingForFuture(ctx);
				return ctx.c(() -> {
					if (f.isError()) {
						return ctx.v("do error");
					}
					return ctx.v("do " + f.value());
				});
			});
			ctl.submit("a", ctx -> {
				return ctx.v("re 0");
			});
			ctl.submit("a", ctx -> {
				TaskFuture<String> f = slow.doSlowThingForFuture(ctx);
				return ctx.c(() -> {
					if (f.isError()) {
						return ctx.v("me error");
					}
					return ctx.v("me " + f.value());
				});
			});
			ctl.submit("b", ctx -> {
				TaskFuture<String> f = slow.doSlowThingForFuture(ctx);
				return ctx.c(() -> {
					if (f.isError()) {
						return ctx.v("fa error");
					}
					return ctx.v("fa " + f.value());
				});
			});
			System.out.format("[%d] start%n", System.currentTimeMillis() - t0);
		});
	}

	public static interface TailRecursable {

		void call(Object i);
	}

	public static ActorSleeper tail(ActorContext ctx, TailRecursable r) {
		return i -> {
			r.call(i);
			return ctx.s(tail(ctx, r));
		};
	}

	public static void withRootActor(long t0, Loxecutor lox, SlowThing slow) {
		Supplier<ActorTask> roots = () -> (ctx) -> {
			System.out.println("new root");
			// return ctx.e(new TaskError("actor error"));
			// return ctx.s(new RootLoop(ctx, slow));
			return ctx.s(tail(ctx, i -> {
				System.out.println("spawning a task: " + i);
				String lock = (Integer) i % 2 == 0 ? "even" : "odd";
				ctx.submit(lock, c -> {
					System.out.println("doing a " + lock + " task: " + i);
					TaskFuture<String> f = slow.doSlowThingForFuture(c);
					return c.c(() -> {
						if (f.isError()) {
							return c.v(lock + " error");
						}
						return c.v(lock + " " + f.value());
					});
				});
			}));
		};

		lox.execute(ctl -> {
			ctl.actor("root", roots, 1);
			ctl.actor("root", roots, 2);
			ctl.actor("root", roots, 3);
			System.out.format("[%d] start%n", System.currentTimeMillis() - t0);
		});
	}

	static class RootLoop implements ActorSleeper {

		private final ActorContext ctx;
		private final SlowThing slow;

		public RootLoop(ActorContext ctx, SlowThing slow) {
			this.ctx = ctx;
			this.slow = slow;
		}

		@Override
		public ActorResult call(Object i) {
			System.out.println("spawning a task: " + i);
			String lock = (Integer) i % 2 == 0 ? "even" : "odd";
			ctx.submit(lock, c -> {
				System.out.println("doing a " + lock + " task: " + i);
				TaskFuture<String> f = slow.doSlowThingForFuture(c);
				return c.c(() -> {
					if (f.isError()) {
						return c.v(lock + " error");
					}
					return c.v(lock + " " + f.value());
				});
			});
			return ctx.s(this::call);
		}
	}
}
