package fibres;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Context of a fibre.
 */
interface C {

	public R l(String l);

	public R v(Object v);

	public R c(F f);

	public R c(F0 f);
}

/**
 * Result of an execution.
 */
class R {
}

/**
 * Result that is a lock.
 */
class RL extends R {

	private final String l;

	public RL(String l) {
		this.l = l;
	}
}

/**
 * Result that is a value.
 */
class RV extends R {

	private final Object v;

	public RV(Object v) {
		this.v = v;
	}

	public Object v() {
		return v;
	}
}

/**
 * Result that is a fibre continuation.
 */
class RF extends R {

	private final F0 f;

	public RF(F0 f) {
		this.f = f;
	}
}

/**
 * Fibre entry point.
 */
interface F0 {

	public R r(C c);
}

/**
 * Fibre continuation.
 */
interface F {

	public R r();
}

/**
 * Fibre callback.
 */
interface X {

	public void r(RV r);
}

/**
 * Fibre execution.
 */
class E implements C {

	F0 f;
	R r;
	X x;

	public E(long n, F0 f, X x) {
		this.f = f;
		this.x = x;
	}

	public R l(String l) {
		assert r == null;
		r = new RL(l);
		return r;
	}

	public R v(Object v) {
		assert r == null;
		r = new RV(v);
		return r;
	}

	public R c(F f) {
		return c(c -> f.r());
	}

	public R c(F0 f) {
		assert r == null;
		r = new RF(f);
		return r;
	}

	void one() {
		f.r(this);
	}
}

/**
 * Little fibre-powered server.
 */
public class Web {

	private final ExecutorService pool = Executors.newCachedThreadPool();

	private AtomicLong counter;
	private BlockingQueue<E> queue;

	public Web() {
	}

	private void start() throws Exception {

		counter = new AtomicLong();
		queue = new ArrayBlockingQueue<>(1000);

		new Thread(() -> {
			while (true) {
				try {
					E e = queue.take();
					e.one();
					if (e.r instanceof RV) {
						pool.execute(() -> e.x.r((RV) e.r));
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();

		try (ServerSocket server = new ServerSocket(9090)) {
			while (true) {
				Socket socket = server.accept();
				onSocket1(socket);
			}
		}
	}

	private void onSocket1(Socket socket) {
		pool.submit(() -> {
			// TODO - NIO?
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				String line = in.readLine();
				queue.offer(new E(counter.incrementAndGet(), r(line), r -> {
					try {
						out.write(r.v().toString());
						out.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private F0 r(String line) {
		return c -> {
			System.out.println(line);
			return c.v("ok " + line);
		};
	}

	public static void main(String[] args) throws Exception {
		new Web().start();
	}
}
