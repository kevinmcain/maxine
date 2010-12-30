/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.c1x;

import java.io.*;

import com.sun.c1x.alloc.*;
import com.sun.c1x.asm.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This class encapsulates global information about the compilation of a particular method,
 * including a reference to the runtime, statistics about the compiled code, etc.
 *
 * @author Ben L. Titzer
 */
public class C1XCompilation {

    private static ThreadLocal<C1XCompilation> currentCompilation = new ThreadLocal<C1XCompilation>();

    public final C1XCompiler compiler;
    public final CiTarget target;
    public final RiRuntime runtime;
    public final RiMethod method;
    public final RiRegisterConfig registerConfig;
    public final CiStatistics stats;
    public final int osrBCI;

    private boolean hasExceptionHandlers;

    private final C1XCompilation parent;

    /**
     * @see #setNotTypesafe()
     * @see #isTypesafe()
     */
    private boolean typesafe = true;

    private int nextID = 1;

    private FrameMap frameMap;
    private AbstractAssembler assembler;

    private IR hir;

    /**
     * Object used to generate the trace output that can be fed to the
     * <a href="https://c1visualizer.dev.java.net/">C1 Visualizer</a>.
     */
    private final CFGPrinter cfgPrinter;

    /**
     * Buffer that {@link #cfgPrinter} writes to. Using a buffer allows writing to the
     * relevant {@linkplain CFGPrinter#cfgFileStream() file} to be serialized so that
     * traces for independent compilations are not interleaved.
     */
    private ByteArrayOutputStream cfgPrinterBuffer;

    /**
     * Creates a new compilation for the specified method and runtime.
     *
     * @param compiler the compiler
     * @param method the method to be compiled or {@code null} if generating code for a stub
     * @param osrBCI the bytecode index for on-stack replacement, if requested
     */
    public C1XCompilation(C1XCompiler compiler, RiMethod method, int osrBCI) {
        this.parent = currentCompilation.get();
        currentCompilation.set(this);
        this.compiler = compiler;
        this.target = compiler.target;
        this.runtime = compiler.runtime;
        this.method = method;
        this.osrBCI = osrBCI;
        this.stats = new CiStatistics();
        this.registerConfig = method == null ? compiler.globalStubRegisterConfig : runtime.getRegisterConfig(method);

        CFGPrinter cfgPrinter = null;
        if (C1XOptions.PrintCFGToFile && method != null && !TTY.isSuppressed()) {
            cfgPrinterBuffer = new ByteArrayOutputStream();
            cfgPrinter = new CFGPrinter(cfgPrinterBuffer, target);
            cfgPrinter.printCompilation(method);
        }
        this.cfgPrinter = cfgPrinter;
    }

    public void close() {
        currentCompilation.set(parent);
    }

    public IR hir() {
        return hir;
    }

    /**
     * Records that this compilation has exception handlers.
     */
    public void setHasExceptionHandlers() {
        hasExceptionHandlers = true;
    }

    /**
     * Records that this compilation encountered an instruction (e.g. {@link Bytecodes#UNSAFE_CAST})
     * that breaks the type safety invariant of the input bytecode.
     */
    public void setNotTypesafe() {
        typesafe = false;
    }

    /**
     * Checks whether this compilation is for an on-stack replacement.
     *
     * @return {@code true} if this compilation is for an on-stack replacement
     */
    public boolean isOsrCompilation() {
        return osrBCI >= 0;
    }

    /**
     * Translates a given kind to a canonical architecture kind.
     * This is an identity function for all but {@link CiKind#Word}
     * which is translated to {@link CiKind#Int} or {@link CiKind#Long}
     * depending on whether or not this is a {@linkplain #is64Bit() 64-bit}
     * compilation.
     */
    public CiKind archKind(CiKind kind) {
        if (kind.isWord()) {
            return target.arch.is64bit() ? CiKind.Long : CiKind.Int;
        }
        return kind;
    }

    /**
     * Determines if two given kinds are equal at the {@linkplain #archKind(CiKind) architecture} level.
     */
    public boolean archKindsEqual(CiKind kind1, CiKind kind2) {
        return archKind(kind1) == archKind(kind2);
    }

    /**
     * Gets the frame which describes the layout of the OSR interpreter frame for this method.
     *
     * @return the OSR frame
     */
    public RiOsrFrame getOsrFrame() {
        return runtime.getOsrFrame(method, osrBCI);
    }

    /**
     * Records an assumption made by this compilation that the specified type is a leaf class.
     *
     * @param type the type that is assumed to be a leaf class
     * @return {@code true} if the assumption was recorded and can be assumed; {@code false} otherwise
     */
    public boolean recordLeafTypeAssumption(RiType type) {
        return false;
    }

    /**
     * Records an assumption made by this compilation that the specified method is a leaf method.
     *
     * @param method the method that is assumed to be a leaf method
     * @return {@code true} if the assumption was recorded and can be assumed; {@code false} otherwise
     */
    public boolean recordLeafMethodAssumption(RiMethod method) {
        return runtime.recordLeafMethodAssumption(method);
    }

    /**
     * Records an assumption that the specified type has no finalizable subclasses.
     *
     * @param receiverType the type that is assumed to have no finalizable subclasses
     * @return {@code true} if the assumption was recorded and can be assumed; {@code false} otherwise
     */
    public boolean recordNoFinalizableSubclassAssumption(RiType receiverType) {
        return false;
    }

    /**
     * Gets the {@code RiType} corresponding to {@code java.lang.Throwable}.
     *
     * @return the compiler interface type for {@link Throwable}
     */
    public RiType throwableType() {
        return runtime.getRiType(Throwable.class);
    }

    /**
     * Converts this compilation to a string.
     *
     * @return a string representation of this compilation
     */
    @Override
    public String toString() {
        if (isOsrCompilation()) {
            return "osr-compile @ " + osrBCI + ": " + method;
        }
        return "compile: " + method;
    }

    /**
     * Builds the block map for the specified method.
     *
     * @param method the method for which to build the block map
     * @param osrBCI the OSR bytecode index; {@code -1} if this is not an OSR
     * @return the block map for the specified method
     */
    public BlockMap getBlockMap(RiMethod method, int osrBCI) {
        // PERF: cache the block map for methods that are compiled or inlined often
        BlockMap map = new BlockMap(method, hir.numberOfBlocks());
        boolean isOsrCompilation = false;
        if (osrBCI >= 0) {
            map.addEntrypoint(osrBCI, BlockBegin.BlockFlag.OsrEntry);
            isOsrCompilation = true;
        }
        if (!map.build(!isOsrCompilation && C1XOptions.PhiLoopStores)) {
            throw new CiBailout("build of BlockMap failed for " + method);
        } else {
            if (cfgPrinter() != null) {
                cfgPrinter().printCFG(method, map, method.code().length, "BlockListBuilder " + CiUtil.format("%f %r %H.%n(%p)", method, true), false, false);
            }
        }
        map.cleanup();
        stats.byteCount += map.numberOfBytes();
        stats.blockCount += map.numberOfBlocks();
        return map;
    }

    /**
     * Returns the frame map of this compilation.
     * @return the frame map
     */
    public FrameMap frameMap() {
        return frameMap;
    }

    public AbstractAssembler masm() {
        if (assembler == null) {
            assembler = compiler.backend.newAssembler(registerConfig);
            assembler.setFrameSize(frameMap.frameSize());
        }
        return assembler;
    }

    public boolean hasExceptionHandlers() {
        return hasExceptionHandlers;
    }

    /**
     * Determines if this compilation has encountered any instructions (e.g. {@link Bytecodes#UNSAFE_CAST})
     * that break the type safety invariant of the input bytecode.
     */
    public boolean isTypesafe() {
        return typesafe;
    }

    public CiResult compile() {
        CiTargetMethod targetMethod;
        try {
            emitHIR();
            emitLIR();
            targetMethod = emitCode();

            if (C1XOptions.PrintMetrics) {
                C1XMetrics.BytecodesCompiled += method.code().length;
            }
        } catch (CiBailout b) {
            return new CiResult(null, b, stats);
        } catch (Throwable t) {
            return new CiResult(null, new CiBailout("Exception while compiling: " + method, t), stats);
        } finally {
            flushCfgPrinterToFile();
        }

        return new CiResult(targetMethod, null, stats);
    }

    private void flushCfgPrinterToFile() {
        if (cfgPrinter != null) {
            cfgPrinter.flush();
            OutputStream cfgFileStream = CFGPrinter.cfgFileStream();
            if (cfgFileStream != null) {
                synchronized (cfgFileStream) {
                    try {
                        cfgFileStream.write(cfgPrinterBuffer.toByteArray());
                    } catch (IOException e) {
                        TTY.println("WARNING: Error writing CFGPrinter output for %s to disk: %s", method, e);
                    }
                }
            }
        }
    }

    public IR emitHIR() {

        hir = new IR(this);
        hir.build();
        return hir;
    }

    public void initFrameMap(int numberOfLocks) {
        frameMap = this.compiler.backend.newFrameMap(method, numberOfLocks);
    }

    private void emitLIR() {
        if (C1XOptions.GenLIR) {
            if (C1XOptions.PrintTimers) {
                C1XTimers.LIR_CREATE.start();
            }

            initFrameMap(hir.topScope.maxLocks());

            final LIRGenerator lirGenerator = compiler.backend.newLIRGenerator(this);
            for (BlockBegin begin : hir.linearScanOrder()) {
                lirGenerator.doBlock(begin);
            }

            if (C1XOptions.PrintTimers) {
                C1XTimers.LIR_CREATE.stop();
            }

            new LinearScan(this, hir, lirGenerator, frameMap()).allocate();
        }
    }

    private CiTargetMethod emitCode() {
        if (C1XOptions.GenLIR && C1XOptions.GenCode) {
            final LIRAssembler lirAssembler = compiler.backend.newLIRAssembler(this);
            lirAssembler.emitCode(hir.linearScanOrder());

            // generate code for slow cases
            lirAssembler.emitLocalStubs();

            // generate exception adapters
            lirAssembler.emitExceptionEntries();

            // generate traps at the end of the method
            lirAssembler.emitTraps();

            CiTargetMethod targetMethod = masm().finishTargetMethod(method, runtime, lirAssembler.registerRestoreEpilogueOffset, false);

            if (cfgPrinter() != null) {
                cfgPrinter().printCFG(hir.startBlock, "After code generation", false, true);
                cfgPrinter().printMachineCode(runtime.disassemble(targetMethod));
            }

            if (C1XOptions.PrintTimers) {
                C1XTimers.CODE_CREATE.stop();
            }
            return targetMethod;
        }

        return null;
    }

    public CFGPrinter cfgPrinter() {
        return cfgPrinter;
    }

    public int nextID() {
        return nextID++;
    }

    public static C1XCompilation compilation() {
        C1XCompilation compilation = currentCompilation.get();
        assert compilation != null;
        return compilation;
    }

//    public static C1XCompilation setCurrent(C1XCompilation compilation) {
//        assert compilation == null || currentCompilation.get() == null : "cannot have more than one current compilation per thread";
//        currentCompilation.set(compilation);
//        return compilation;
//    }
}
