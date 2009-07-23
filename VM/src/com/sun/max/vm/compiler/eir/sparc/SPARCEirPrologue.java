/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.max.vm.compiler.eir.sparc;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.asm.sparc.complete.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.eir.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.sparc.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.sparc.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;

/**
 * @author Bernd Mathiske
 * @author Laurent Daynes
 */
public final class SPARCEirPrologue extends EirPrologue<SPARCEirInstructionVisitor, SPARCEirTargetEmitter> implements SPARCEirInstruction {

    public SPARCEirPrologue(EirBlock block, EirMethod eirMethod,
                            EirValue[] calleeSavedValues, EirLocation[] calleeSavedRegisters,
                            BitSet isCalleeSavedParameter,
                            EirValue[] parameters, EirLocation[] parameterLocations) {
        super(block, eirMethod, calleeSavedValues, calleeSavedRegisters, isCalleeSavedParameter, parameters, parameterLocations);
    }

    private static final SPARCAssembler ASM = SPARCAssembler.createAssembler(WordWidth.BITS_64);

    /**
     * The maximum imm13 offset we can get for a stack offset. It leaves about 4K
     * for the stack overflow routine to operate if we hit a protected zone.
     */
    private static final int DEFAULT_STACK_BANG_OFFSET = -2045;

    private static int stackBangOffset(int frameSize) {
        if (SPARCAssembler.isSimm13(frameSize)) {
            return DEFAULT_STACK_BANG_OFFSET;
        }
        return StackBias.SPARC_V9.stackBias() - frameSize;
    }

    /**
     * Returns the number of instructions of the frame builder for a given frame size.
     * The save instruction in the last instruction of the frame builder.
     * This information is useful for stack walkers to determine whether a method activation is in its caller
     * register window.
     * @param frameSize
     * @return
     */
    public static int numberOfFrameBuilderInstructions(int frameSize) {
        if (SPARCAssembler.isSimm13(frameSize)) {
            return 2;
        }
        final int stackBangOffset = stackBangOffset(frameSize);
        if (SPARCAssembler.isSimm13(stackBangOffset)) {
            return 2 + ASM.setswNumberOfInstructions(stackBangOffset);
        }
        return 3 + ASM.setswNumberOfInstructions(stackBangOffset);
    }

    /**
     * Emit the sequence of instructions that build an optimized code frame.
     * The sequences varies for different frame size due to SPARC limitation on using immediate operand in instructions.
     *
     * @param asm the assembler that'll be used to emit the frame builder
     * @param frameSize size of the frame
     * @param stackPointer stack pointer
     * @param scratchRegister a scratch register (may not necessarily be used)
     */
    public static void emitFrameBuilder(SPARCAssembler asm, int frameSize, GPR stackPointer, GPR scratchRegister) {
        // We must make sure we will not be in a situation where we will not be able to flush the register window for the
        // frame we're creating should an stack overflow occur (especially if a save instruction subsequent to the one that
        // create this frame traps). To avoid this, we bang on the top of the frame we're creating. If this one cause a SIGSEGV,
        // we know the current register window can take the trap.
        final int stackBangOffset = stackBangOffset(frameSize);
        if (SPARCAssembler.isSimm13(frameSize)) {
            assert SPARCAssembler.isSimm13(stackBangOffset);
            asm.lduw(stackPointer, stackBangOffset, GPR.G0);
            asm.save(stackPointer, -frameSize, stackPointer);
        } else {
            final GPR frameSizeReg = scratchRegister;
            try {
                if (SPARCAssembler.isSimm13(stackBangOffset)) {
                    asm.lduw(stackPointer, stackBangOffset, GPR.G0);
                    asm.setsw(-frameSize, frameSizeReg);
                } else {
                    asm.setsw(stackBangOffset, frameSizeReg);
                    asm.lduw(stackPointer, frameSizeReg, GPR.G0);
                    asm.sub(frameSizeReg, StackBias.SPARC_V9.stackBias(), frameSizeReg);
                }
                asm.save(stackPointer, frameSizeReg, stackPointer);
            } catch (AssemblyException e) {
            }
        }
    }

    private void emitTrapStubPrologue(SPARCAssembler asm, GPR stackPointer) {
        // Note: the safepoint latch register is already set to the disabled state (the C code in trap.c took care of that)
        // The value of the latch register at the trap instruction is stored in the trap state.
        final GPR latchRegister = SPARCSafepoint.LATCH_REGISTER;
        final int frameSize = eirMethod().frameSize() + SPARCSafepoint.TRAP_STATE_SIZE;
        final GPR scratchRegister = GPR.L0;
        final GPR scratchRegister2 = GPR.L1;
        assert SPARCAssembler.isSimm13(frameSize);
        eirMethod().setFrameSize(frameSize);

        emitFrameBuilder(asm, frameSize, stackPointer, scratchRegister /* will no be use */);
        // Only need to save the %i and %g of the trap stub frame, plus the %f.
        // Can use all %l and %o of the trap stub frame as temp registers, since these doesn't contain any state of the
        // trapped frame.

        // flush register window as the trap stub will access the register window of the trapped frame.
        // this is likely a no-op as we're entering here from returning from a signal handler.
        asm.flushw();
        final int wordSize = Word.size();
        final int trapStateOffset =  SPARCStackFrameLayout.offsetToFirstFreeSlotFromStackPointer();
        int offset = trapStateOffset;

        // We want to copy into the trap state the value of the latch register at the instruction that causes the trap.
        asm.ldx(latchRegister, VmThreadLocal.TRAP_LATCH_REGISTER.offset, scratchRegister);

        for (GPR register :  SPARCSafepoint.TRAP_SAVED_GLOBAL_SYMBOLIZER) {
            if (register == latchRegister) {
                asm.stx(scratchRegister, stackPointer, offset);
            } else {
                asm.stx(register, stackPointer, offset);
            }
            offset += wordSize;
        }
        for (GPR register : GPR.IN_SYMBOLIZER) {
            asm.stx(register, stackPointer, offset);
            offset += wordSize;
        }
        for (int i = 0; i < 64; i += 2) {
            final FPR fpr = FPR.fromValue(i);
            asm.std((DFPR) fpr, stackPointer, offset);
            offset += wordSize;
        }
        asm.rd(StateRegister.CCR, scratchRegister);
        asm.rd(StateRegister.FPRS, scratchRegister2);
        asm.stx(scratchRegister, stackPointer, offset);
        offset += wordSize;
        asm.stx(scratchRegister2, stackPointer, offset);
        offset += wordSize;
        // offset now points to the location where the trap number will be stored in the trap state.

        final TargetABI targetABI = VMConfiguration.hostOrTarget().targetABIsScheme().optimizedJavaABI();

        // Setup return address -- to enable stack walker
        asm.ldx(latchRegister, VmThreadLocal.TRAP_INSTRUCTION_POINTER.offset, GPR.I7);
        // Setup arguments for the trapStub
        final IndexedSequence parameterRegisters = targetABI.integerIncomingParameterRegisters();
        asm.ldx(latchRegister, VmThreadLocal.TRAP_NUMBER.offset, (GPR) parameterRegisters.get(0));
        asm.add(stackPointer, trapStateOffset, (GPR) parameterRegisters.get(1));
        asm.ldx(latchRegister, VmThreadLocal.TRAP_FAULT_ADDRESS.offset, (GPR) parameterRegisters.get(2));

        // Write trap number in corresponding trap state location
        asm.stx((GPR) parameterRegisters.get(0), stackPointer, offset);
    }

    @Override
    public void emit(SPARCEirTargetEmitter emitter) {
        if (!eirMethod().isTemplate()) {
            final SPARCAssembler asm = emitter.assembler();
            final SPARCEirRegister.GeneralPurpose stackPointer = (SPARCEirRegister.GeneralPurpose) emitter.abi().stackPointer();
            if (eirMethod().classMethodActor().isTrapStub()) {
               // emit a special prologue that saves all the registers
                emitTrapStubPrologue(asm, stackPointer.as());
            } else {
                final SPARCEirRegister.GeneralPurpose scratchRegister = (SPARCEirRegister.GeneralPurpose) emitter.abi().getScratchRegister(Kind.INT);
                emitFrameBuilder(asm, eirMethod().frameSize(), stackPointer.as(), scratchRegister.as());
            }
            if (eirMethod().literalPool().hasLiterals()) {
                asm.bindLabel(emitter.literalBaseLabel());
                asm.rd(StateRegister.PC, ((SPARCEirABI) emitter.abi()).literalBaseRegister().as());
            }
        }
    }

    @Override
    public void acceptVisitor(SPARCEirInstructionVisitor visitor) {
        visitor.visit(this);
    }

}
