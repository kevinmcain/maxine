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
package com.oracle.max.vm.ext.vma;

/**
 * The VM executes an (extended) bytecode instruction set and the execution can be subject to advice,
 * specified by the methods below.
 *
 * Auto-generated by {@link BytecodeAdviceGenerator} and currently incomplete.
 */

public abstract class BytecodeAdvice {

    // BEGIN GENERATED CODE

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayLoad(Object array, int index, long value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayLoad(Object array, int index, float value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayLoad(Object array, int index, double value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayLoad(Object array, int index, Object value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayStore(Object array, int index, long value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayStore(Object array, int index, float value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayStore(Object array, int index, double value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeArrayStore(Object array, int index, Object value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetStatic(Object staticTuple, int offset, double value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetStatic(Object staticTuple, int offset, long value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetStatic(Object staticTuple, int offset, float value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetStatic(Object staticTuple, int offset, Object value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, double value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, long value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, float value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, Object value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetField(Object object, int offset, double value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetField(Object object, int offset, long value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetField(Object object, int offset, float value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforeGetField(Object object, int offset, Object value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutField(Object object, int offset, double value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutField(Object object, int offset, long value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutField(Object object, int offset, float value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseBeforePutField(Object object, int offset, Object value);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseAfterInvokeSpecial(Object object);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseAfterNew(Object object);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseAfterNewArray(Object object, int length);

    // GENERATED -- EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY
    public abstract void adviseAfterMultiNewArray(Object object, int[] lengths);


}
