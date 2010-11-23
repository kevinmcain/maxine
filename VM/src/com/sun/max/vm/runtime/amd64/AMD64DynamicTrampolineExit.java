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
package com.sun.max.vm.runtime.amd64;

import static com.sun.max.vm.compiler.CallEntryPoint.*;

import com.sun.max.unsafe.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.compiler.target.amd64.AMD64AdapterGenerator.Jit2Opt.Jit2OptAdapterFrameLayout;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.trampoline.*;

/**
 * Dynamic Trampoline Exit for AMD64.
 *
 * @author Laurent Daynes
 */
public class AMD64DynamicTrampolineExit extends DynamicTrampolineExit {

    /**
     * Returns the exit point of the trampoline.
     * The exit point will be used by the trampoline epilogue to overwrite its RIP.
     * The exit point is either the entry point of the method invoked via the trampoline, or the adapter frame, if there is one. If the
     * latter, the trampoline will exit to the entry point of the selected method upon return from the adapter. To this end, the RIP of the adapter
     *  is patched to the entry point of the method invoked via the trampoline. Note that in this case the stack frame is temporarily in an inconsistent
     * state (i.e., during the ret instruction), and stack walkers (other than inspection) must not access the stack of the current thread.
     * @param dynamicTrampoline the dynamic trampoline to exit
     * @param vtableEntryPoint the target method entry the trampoline should exit to.
     * @param stackPointer the stack pointer of the dynamic trampoline
     *
     * @see Jit2OptAdapterFrameLayout
     */
    @Override
    public Address trampolineReturnAddress(DynamicTrampoline dynamicTrampoline, Address vtableEntryPoint, Pointer stackPointer) {
        final TargetMethod trampoline = dynamicTrampoline.trampolineTargetMethod();
        final int trampolineFrameSize = trampoline.frameSize();
        final Pointer ripLocation = stackPointer.plus(trampolineFrameSize);
        final Pointer ripPointer = ripLocation.readWord(0).asPointer();
        final TargetMethod caller = Code.codePointerToTargetMethod(ripPointer);
        CallEntryPoint callEntryPoint = caller.callEntryPoint;
        if (caller.classMethodActor != null && caller.classMethodActor.isVmEntryPoint()) {
            callEntryPoint = CallEntryPoint.OPTIMIZED_ENTRY_POINT;
        }
        return vtableEntryPoint.plus(callEntryPoint.offset() - VTABLE_ENTRY_POINT.offset());
    }

}
