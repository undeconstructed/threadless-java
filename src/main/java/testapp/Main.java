package testapp;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threadless.Loxecutor;
import threadless.Pair;
import threadless.TaskContext;
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

	public TaskFuture<String> doSlowThingForFuture(TaskContext ctx) {
		int s = r.nextInt(3000);
		TaskFuture<String> f = ctx.fut();
		e.submit(() -> {
			Thread.sleep(s);
			f.notify(String.valueOf(s));
			return true;
		});
		return f;
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

		Loxecutor lox = new Loxecutor(results -> {
			long td = System.currentTimeMillis() - t0;
			for (Pair<String, Object> p : results) {
				System.out.format("[%d] %s: %s%n", td, p.a, p.b);
			}
		});

		SlowThing slow = new SlowThing();

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

		lox.shutdown();
		System.out.format("[%d] done%n", System.currentTimeMillis() - t0);
		System.exit(0);
	}
}
