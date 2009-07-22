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
package com.sun.max.vm.compiler.cir.builtin;

import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.compiler.cir.*;
import com.sun.max.vm.compiler.cir.optimize.*;

/**
 * Wrapper for safepoint builtins.
 * The optimizer may eliminate some of these as appropriate.
 *
 * @author Bernd Mathiske
 */
public final class CirSafepointBuiltin extends CirSpecialBuiltin {

    public CirSafepointBuiltin() {
        super(SafepointBuiltin.BUILTIN);
    }

    @Override
    public boolean isFoldable(CirOptimizer cirOptimizer, CirValue[] arguments) {
        if (cirOptimizer.cirMethod().classMethodActor().noSafepoints()) {
            // this method has been annotated with a directive to suppress safepoints
            return true;
        }
        return false; //TODO: eliminate redundant soft safepoints - requires analysis
    }
}