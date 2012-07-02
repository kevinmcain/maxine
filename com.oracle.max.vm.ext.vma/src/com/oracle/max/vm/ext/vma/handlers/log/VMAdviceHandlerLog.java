/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.vm.ext.vma.handlers.log;

import com.oracle.max.vm.ext.vma.*;

/**
 * An interface that is derived from {@link VMAdviceHandler} but is suitable for persistent storage. I.e., object
 * instances are represented by unique identifiers, class instances by their name and classloader instance id, method by
 * name, etc.
 *
 * Although wall clock time of the event is an important consideration, its generation is left to the logging
 * implementation. That is, it is expected that there is no material delay between the actual event and the logging.
 *
 * A logger is responsible for generating the timestamp for an event. By default this class provides a
 * {@link TimeStampGenerator generator} that returns system time. However, it is possible to set a different
 * implementation after constructing the logger. This is particularly useful when converting one log format to another in
 * order to preserve the original timestamps.
 *
 * A log implementation must be thread-safe either by design or by appropriate synchronization.
 *
 * Much of this class is auto-generated by {@link VMAdviceHandlerLogGenerator}.
 *
 */
public abstract class VMAdviceHandlerLog {

    public interface TimeStampGenerator {
        /**
         * Get the time stamp for a trace.
         * @return absolute wall clock time of the trace
         */
        long getTimeStamp();
    }

    public TimeStampGenerator timeStampGenerator;

    protected static class SystemTimeGenerator implements TimeStampGenerator {
        public long getTimeStamp() {
            return System.nanoTime();
        }
    }

    protected VMAdviceHandlerLog() {
        this(new SystemTimeGenerator());
    }

    private VMAdviceHandlerLog(TimeStampGenerator timeStampGenerator) {
        this.timeStampGenerator = timeStampGenerator;
    }

    /**
     * Initialize the logging subsystem.
     * @param timeOrdered {@code true} iff the log is time ordered.
     *
     * @return {@code true} iff the initialization was successful.
     */
    public abstract boolean initializeLog(boolean timeOrdered);

    /**
     * Finalize the logging, e.g. flush trace.
     */
    public abstract void finalizeLog();

    /**
     * Explicitly set the time stamp generator.
     * @param timeStampGenerator
     */
    public void setTimeStampGenerator(TimeStampGenerator timeStampGenerator) {
        this.timeStampGenerator = timeStampGenerator;
    }

    /**
     * Log the removal on an object from the VM (i.e. object death).
     * @param id
     */
    public abstract void removal(long id);

    /**
     * Log an object that was not seen by the adviseNew methods but was used in an operation.
     *
     * @param threadName irrelevant but included for consistency with other methods
     * @param objId
     * @param className
     * @param clId
     */
    public abstract void unseenObject(String threadName, long objId, String className, long clId);

    /**
     * Available for the case where log records are not time ordered.
     * This resets the absolute time.
     * @param time
     */
    public abstract void resetTime();

// START GENERATED CODE
// EDIT AND RUN VMAdviceHandlerLogGenerator.main() TO MODIFY

    public abstract void adviseBeforeGC(String threadName);

    public abstract void adviseAfterGC(String threadName);

    public abstract void adviseBeforeThreadStarting(String threadName);

    public abstract void adviseBeforeThreadTerminating(String threadName);

    public abstract void adviseBeforeReturnByThrow(String threadName, long objId, int poppedFrames);

    public abstract void adviseBeforeConstLoad(String threadName, long value);

    public abstract void adviseBeforeConstLoadObject(String threadName, long value);

    public abstract void adviseBeforeConstLoad(String threadName, float value);

    public abstract void adviseBeforeConstLoad(String threadName, double value);

    public abstract void adviseBeforeLoad(String threadName, int arg1);

    public abstract void adviseBeforeArrayLoad(String threadName, long objId, int index);

    public abstract void adviseBeforeStore(String threadName, int index, long value);

    public abstract void adviseBeforeStore(String threadName, int index, float value);

    public abstract void adviseBeforeStore(String threadName, int index, double value);

    public abstract void adviseBeforeStoreObject(String threadName, int index, long value);

    public abstract void adviseBeforeArrayStore(String threadName, long objId, int index, float value);

    public abstract void adviseBeforeArrayStore(String threadName, long objId, int index, long value);

    public abstract void adviseBeforeArrayStore(String threadName, long objId, int index, double value);

    public abstract void adviseBeforeArrayStoreObject(String threadName, long objId, int index, long value);

    public abstract void adviseBeforeStackAdjust(String threadName, int arg1);

    public abstract void adviseBeforeOperation(String threadName, int arg1, long arg2, long arg3);

    public abstract void adviseBeforeOperation(String threadName, int arg1, float arg2, float arg3);

    public abstract void adviseBeforeOperation(String threadName, int arg1, double arg2, double arg3);

    public abstract void adviseBeforeConversion(String threadName, int arg1, float arg2);

    public abstract void adviseBeforeConversion(String threadName, int arg1, long arg2);

    public abstract void adviseBeforeConversion(String threadName, int arg1, double arg2);

    public abstract void adviseBeforeIf(String threadName, int opcode, int op1, int op2);

    public abstract void adviseBeforeIfObject(String threadName, int opcode, long objId1, long objId2);

    public abstract void adviseBeforeBytecode(String threadName, int arg1);

    public abstract void adviseBeforeReturn(String threadName);

    public abstract void adviseBeforeReturn(String threadName, long value);

    public abstract void adviseBeforeReturn(String threadName, float value);

    public abstract void adviseBeforeReturn(String threadName, double value);

    public abstract void adviseBeforeReturnObject(String threadName, long value);

    public abstract void adviseBeforeGetStatic(String threadName, String className, long clId, String fieldName);

    public abstract void adviseBeforePutStaticObject(String threadName, String className, long clId, String fieldName, long value);

    public abstract void adviseBeforePutStatic(String threadName, String className, long clId, String fieldName, float value);

    public abstract void adviseBeforePutStatic(String threadName, String className, long clId, String fieldName, double value);

    public abstract void adviseBeforePutStatic(String threadName, String className, long clId, String fieldName, long value);

    public abstract void adviseBeforeGetField(String threadName, long objId, String className, long clId, String fieldName);

    public abstract void adviseBeforePutFieldObject(String threadName, long objId, String className, long clId, String fieldName, long value);

    public abstract void adviseBeforePutField(String threadName, long objId, String className, long clId, String fieldName, float value);

    public abstract void adviseBeforePutField(String threadName, long objId, String className, long clId, String fieldName, double value);

    public abstract void adviseBeforePutField(String threadName, long objId, String className, long clId, String fieldName, long value);

    public abstract void adviseBeforeInvokeVirtual(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseBeforeInvokeSpecial(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseBeforeInvokeStatic(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseBeforeInvokeInterface(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseBeforeArrayLength(String threadName, long objId, int length);

    public abstract void adviseBeforeThrow(String threadName, long objId);

    public abstract void adviseBeforeCheckCast(String threadName, long objId, String className, long clId);

    public abstract void adviseBeforeInstanceOf(String threadName, long objId, String className, long clId);

    public abstract void adviseBeforeMonitorEnter(String threadName, long objId);

    public abstract void adviseBeforeMonitorExit(String threadName, long objId);

    public abstract void adviseAfterInvokeVirtual(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseAfterInvokeSpecial(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseAfterInvokeStatic(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseAfterInvokeInterface(String threadName, long objId, String className, long clId, String methodName);

    public abstract void adviseAfterNew(String threadName, long objId, String className, long clId);

    public abstract void adviseAfterNewArray(String threadName, long objId, String className, long clId, int length);

    public abstract void adviseAfterMultiNewArray(String threadName, long objId, String className, long clId, int length);

    public abstract void adviseAfterMethodEntry(String threadName, long objId, String className, long clId, String methodName);

// END GENERATED CODE
}
