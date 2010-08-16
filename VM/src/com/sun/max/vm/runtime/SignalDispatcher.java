/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.runtime;

import java.util.concurrent.atomic.*;

import sun.misc.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.vm.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * The thread used to post and dispatch signals to user supplied {@link SignalHandler}s.
 *
 * @author Doug Simon
 */
public final class SignalDispatcher extends Thread {

    /**
     * A set of counters, one per supported signal, used to post and consume signals.
     */
    private static final AtomicIntegerArray PendingSignals = new AtomicIntegerArray(nativeNumberOfSignals() + 1);

    /**
     * The special signal used to terminate the signal dispatcher thread.
     */
    private static final int ExitSignal = PendingSignals.length() - 1;

    /**
     * The singleton {@link SignalDispatcher} thread.
     * This is initialized by {@link #initialize()}.
     */
    static SignalDispatcher INSTANCE;

    /**
     * Called early on in the Java startup sequence during the {@link MaxineVM.Phase#STARTING} phase.
     * This creates the {@linkplain #INSTANCE singleton} dispatcher thread and starts it.
     */
    public static void initialize() {
        assert INSTANCE == null;
        INSTANCE = new SignalDispatcher();
        INSTANCE.start();

        Signal.handle(new Signal("HUP"), new PrintThreads(true));
    }

    /**
     * Gets the number of signals supported by the platform that may be delivered to the VM.
     * The range of signal numbers that the VM expects to see is between 0 (inclusive) and
     * {@code nativeNumberOfSignals()} (exclusive).
     */
    @HOSTED_ONLY
    private static native int nativeNumberOfSignals();

    private SignalDispatcher() {
        super("Signal Dispatcher");
    }

    /**
     * Blocks the current thread until a pending signal is available.
     *
     * @return the next available pending signal (which is removed from the set of pending signals)
     */
    public int waitForSignal() {
        while (true) {
            for (int signal = 0; signal < PendingSignals.length(); signal++) {
                int n = PendingSignals.get(signal);
                if (n > 0 && PendingSignals.compareAndSet(signal, n, n - 1)) {
                    return signal;
                }
            }

            synchronized (INSTANCE) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Adds a signal to the set of pending signals and notifies the dispatcher thread.
     */
    public static void postSignal(int signal) {
        PendingSignals.incrementAndGet(signal);
        synchronized (INSTANCE) {
            INSTANCE.notify();
        }
    }

    /**
     * Terminates the signal dispatcher thread.
     */
    public static void terminate() {
        postSignal(ExitSignal);
    }

    @Override
    public void run() {
        while (true) {
            int signal = waitForSignal();

            if (signal == ExitSignal) {
                return;
            }

            try {
                ClassRegistry.Signal_dispatch.invoke(IntValue.from(signal));
            } catch (Exception e) {
                Log.println("Exception occurred while dispatching signal " + signal + " to handler - VM may need to be forcibly terminated");
                Log.print(Utils.stackTraceAsString(e));
            }
        }
    }
}
