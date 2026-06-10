package com.computermod.runtime;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * A sandboxed LuaJ runtime for one computer block, modelled on a microcontroller.
 *
 * <p>The program runs on a dedicated daemon <b>worker thread</b> so its own loops run continuously at
 * a fixed clock rate. The main server thread never blocks on the worker: once per game tick it calls
 * {@link #onServerTick()} to (1) execute any world-access requests the worker queued (the only safe
 * place to touch the world is the server thread) and (2) grant the worker its next clock slice
 * (N instructions). World-access functions call {@link #runOnMain(Supplier)}, which marshals the work
 * to the server thread and briefly blocks only the worker. This avoids the deadlock that a naive
 * "main thread waits for Lua" design causes.
 */
public class LuaRuntime {

	public enum State { STOPPED, RUNNING, FINISHED, ERROR }

	private static final int MAX_TERMINAL_LINES = 200;
	private static final int MAX_TASKS_PER_TICK = 64;

	/** Thrown to unwind the worker on shutdown. Extends Error so Lua {@code pcall} can't swallow it. */
	private static final class HaltError extends Error {}

	private final int sliceSize;
	private Consumer<Globals> apiInstaller;

	private volatile State state = State.STOPPED;
	private volatile String error = "";
	private final List<String> terminal = new ArrayList<>(); // guarded by 'terminal'
	private volatile int terminalRevision = 0; // bumped per printed line (survives the ring-buffer cap)

	private Thread worker;
	private volatile boolean alive;
	/** The flashed files; {@value #MAIN_FILE} is the entry point, the rest load via require(). */
	private Map<String, String> files;

	public static final String MAIN_FILE = "main.lua";

	// Clock throttle (worker waits, main grants).
	private final Object clockLock = new Object();
	private boolean sliceGranted;

	// World-access tasks to run on the server thread.
	private final ConcurrentLinkedQueue<MainTask<?>> tasks = new ConcurrentLinkedQueue<>();

	public LuaRuntime(int sliceSize) {
		this.sliceSize = Math.max(1, sliceSize);
	}

	public State getState() {
		return state;
	}

	public String getError() {
		return error;
	}

	public boolean isRunning() {
		return state == State.RUNNING;
	}

	public List<String> getTerminalSnapshot() {
		synchronized (terminal) {
			return new ArrayList<>(terminal);
		}
	}

	/** Monotonic counter that increments on every printed line, even after the ring buffer is full. */
	public int getTerminalRevision() {
		return terminalRevision;
	}

	/** Wipe the terminal buffer (the program keeps running). */
	public void clearTerminal() {
		synchronized (terminal) {
			terminal.clear();
			terminalRevision++;
		}
	}

	public void setApiInstaller(Consumer<Globals> installer) {
		this.apiInstaller = installer;
	}

	/** Boot: launch the worker thread. Call on the server thread. */
	public void start(Map<String, String> files) {
		this.files = files;
		this.alive = true;
		this.sliceGranted = false;
		this.state = State.RUNNING;
		worker = new Thread(this::runProgram, "computermod-lua");
		worker.setDaemon(true);
		worker.start();
	}

	/** Halt: stop the worker and discard it. Call on the server thread. */
	public void stop() {
		alive = false;
		synchronized (clockLock) {
			clockLock.notifyAll();
		}
		MainTask<?> task;
		while ((task = tasks.poll()) != null)
			task.cancel();
		if (worker != null) {
			worker.interrupt();
			worker = null;
		}
		if (state == State.RUNNING)
			state = State.STOPPED;
	}

	/** Server thread, once per tick: fulfill queued world ops, then grant the next clock slice. */
	public void onServerTick() {
		MainTask<?> task;
		int processed = 0;
		while (processed++ < MAX_TASKS_PER_TICK && (task = tasks.poll()) != null)
			task.run();
		synchronized (clockLock) {
			sliceGranted = true;
			clockLock.notifyAll();
		}
	}

	private void runProgram() {
		try {
			Globals globals = createSandbox();
			String main = files.get(MAIN_FILE);
			if (main == null)
				throw new LuaError("no " + MAIN_FILE + " — flash a program first");
			// The chunk name is the file name, so errors read "main.lua:12: ...".
			LuaValue chunk = globals.load(main, MAIN_FILE, globals);
			chunk.call();
			if (alive)
				state = State.FINISHED;
		} catch (HaltError halt) {
			// normal shutdown
		} catch (LuaError e) {
			if (alive) {
				error = e.getMessage();
				printLine("error: " + error);
				state = State.ERROR;
			}
		} catch (Throwable e) {
			if (alive) {
				error = String.valueOf(e.getMessage());
				printLine("error: " + error);
				state = State.ERROR;
			}
		}
	}

	/** Worker thread: block until the main thread grants the next clock slice; returns its size. */
	private int nextSlice() {
		synchronized (clockLock) {
			while (alive && !sliceGranted) {
				try {
					clockLock.wait();
				} catch (InterruptedException ignored) {
				}
			}
			if (!alive)
				throw new HaltError();
			sliceGranted = false;
		}
		return sliceSize;
	}

	/** Worker thread API: run {@code action} on the server thread and wait for its result. */
	public <T> T runOnMain(Supplier<T> action) {
		if (!alive)
			throw new HaltError();
		MainTask<T> task = new MainTask<>(action);
		tasks.add(task);
		return task.await();
	}

	/** Worker thread API: sleep for {@code ticks} game ticks without consuming clock budget. */
	public void sleepTicks(int ticks) {
		for (int i = 0; i < ticks; i++)
			nextSlice();
	}

	private Globals createSandbox() {
		Globals g = JsePlatform.standardGlobals();

		g.STDOUT = new PrintStream(new TerminalOutputStream(), true, StandardCharsets.UTF_8);
		g.STDERR = g.STDOUT;

		// Install the clock-throttle hook first (while `package` still exists; DebugLib registers into
		// package.loaded), then harden the sandbox.
		BudgetDebugLib budget = new BudgetDebugLib(this::nextSlice, sliceSize);
		g.load(budget);
		g.debuglib = budget;

		g.set("luajava", LuaValue.NIL);
		g.set("io", LuaValue.NIL);
		g.set("debug", LuaValue.NIL);
		g.set("package", LuaValue.NIL);
		g.set("dofile", LuaValue.NIL);
		g.set("loadfile", LuaValue.NIL);
		g.set("load", LuaValue.NIL);
		g.set("loadstring", LuaValue.NIL);
		g.set("collectgarbage", LuaValue.NIL);

		// require("name"): load a sibling flash file, like Lua's module system. Each file runs once;
		// its return value is cached and handed back on every later require. Cycles are an error.
		Map<String, LuaValue> moduleCache = new HashMap<>();
		Deque<String> loading = new ArrayDeque<>();
		g.set("require", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue arg) {
				String name = arg.checkjstring();
				String fileName = name.endsWith(".lua") ? name : name + ".lua";
				if (fileName.equals(MAIN_FILE))
					throw new LuaError("cannot require '" + MAIN_FILE + "' (it is the entry point)");
				String source = files.get(fileName);
				if (source == null)
					throw new LuaError("module '" + name + "' not found: no file '" + fileName + "'");
				LuaValue cached = moduleCache.get(fileName);
				if (cached != null)
					return cached;
				if (loading.contains(fileName))
					throw new LuaError("circular require: " + String.join(" -> ", loading) + " -> " + fileName);
				loading.push(fileName);
				try {
					LuaValue result = g.load(source, fileName, g).call();
					// Lua convention: a module that returns nothing still counts as loaded (true).
					if (result.isnil())
						result = LuaValue.TRUE;
					moduleCache.put(fileName, result);
					return result;
				} finally {
					loading.pop();
				}
			}
		});

		LuaValue os = g.get("os");
		if (os.istable()) {
			LuaValue safeOs = new org.luaj.vm2.LuaTable();
			safeOs.set("time", os.get("time"));
			safeOs.set("clock", os.get("clock"));
			safeOs.set("date", os.get("date"));
			g.set("os", safeOs);
		}

		if (apiInstaller != null)
			apiInstaller.accept(g);
		return g;
	}

	private void printLine(String line) {
		synchronized (terminal) {
			for (String part : line.split("\n", -1)) {
				terminal.add(part);
				terminalRevision++;
				while (terminal.size() > MAX_TERMINAL_LINES)
					terminal.remove(0);
			}
		}
	}

	/** Collects bytes written to STDOUT and splits them into terminal lines. */
	private class TerminalOutputStream extends OutputStream {
		private final StringBuilder current = new StringBuilder();

		@Override
		public void write(int b) {
			if (b == '\n') {
				printLine(current.toString());
				current.setLength(0);
			} else if (b != '\r') {
				current.append((char) b);
				if (current.length() > 4096) {
					printLine(current.toString());
					current.setLength(0);
				}
			}
		}
	}

	/** A unit of work the worker hands to the server thread, with a result/await handshake. */
	private final class MainTask<T> {
		private final Supplier<T> action;
		private final CountDownLatch latch = new CountDownLatch(1);
		private volatile T result;
		private volatile RuntimeException failure;

		MainTask(Supplier<T> action) {
			this.action = action;
		}

		void run() {
			try {
				result = action.get();
			} catch (RuntimeException e) {
				failure = e;
			} finally {
				latch.countDown();
			}
		}

		void cancel() {
			latch.countDown();
		}

		T await() {
			try {
				latch.await();
			} catch (InterruptedException e) {
				throw new HaltError();
			}
			if (!alive)
				throw new HaltError();
			if (failure != null)
				throw failure;
			return result;
		}
	}
}
