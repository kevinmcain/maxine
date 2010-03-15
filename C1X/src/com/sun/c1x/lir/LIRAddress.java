/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.c1x.lir;

import com.sun.c1x.ci.*;
import com.sun.c1x.util.*;

/**
 * The {@code LIRAddress} class represents an operand that is in
 * memory. It includes a base address, and index, a scale and a displacement.
 *
 * @author Marcelo Cintra
 * @author Ben L. Titzer
 */
public final class LIRAddress extends LIROperand {

    private static final Scale[] SCALE = Scale.values();

    public enum Scale {
        Times1,
        Times2,
        Times4,
        Times8;

        Scale() {
            multiplier = 2 ^ ordinal();
        }

        /**
         * Gets the Scale constant whose {@linkplain #multiplier} log base 2 is equal to a given value.
         */
        public static Scale fromLog2(int shift) {
            assert shift < SCALE.length;
            return SCALE[shift];
        }

        public int toInt() {
            return ordinal();
        }

        public final int multiplier;
    }

    public final LIRLocation base;
    public final LIRLocation index;
    public final Scale scale;
    public final int displacement;

    /**
     * Creates a new LIRAddress with the specified base address, index, and kind.
     *
     * @param base the LIROperand representing the base address
     * @param index the LIROperand representing the index
     * @param kind the kind of the resulting operand
     */
    public LIRAddress(LIRLocation base, LIRLocation index, CiKind kind) {
        this(base, index, Scale.Times1, 0, kind);
    }

    /**
     * Creates a new LIRAddress with the specified base address, displacement, and kind.
     *
     * @param base the LIROperand representing the base address
     * @param displacement the constant displacement from the base address
     * @param kind the kind of the resulting operand
     */
    public LIRAddress(LIRLocation base, int displacement, CiKind kind) {
        this(base, IllegalLocation, Scale.Times1, displacement, kind);
    }

    /**
     * Creates a new LIRAddress with the specified base address, index, and kind.
     *
     * @param base the LIROperand representing the base address
     * @param index the LIROperand representing the index
     * @param scale the scaling factor for the index
     * @param displacement the constant displacement from the base address
     * @param kind the kind of the resulting operand
     */
    public LIRAddress(LIRLocation base, LIRLocation index, Scale scale, int displacement, CiKind kind) {
        super(kind);
        this.base = base;
        this.index = index;
        this.scale = scale;
        this.displacement = displacement;
    }

    /**
     * The equals() for object comparisons.
     *
     * @return {@code true} if this address is equal to the other address
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof LIRAddress) {
            LIRAddress otherAddress = (LIRAddress) other;
            return base == otherAddress.base && index == otherAddress.index && displacement == otherAddress.displacement && scale == otherAddress.scale;
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder out = new StringBuilder();
        out.append("Base:").append(base);
        if (LIROperand.isLegal(index)) {
            out.append(" Index:").append(index);
            if (scale != Scale.Times1) {
                out.append(" * ").append(scale.multiplier);
            }
        }
        out.append(" Disp: %d").append(displacement);
        return out.toString();
    }

    /**
     * Verifies the address is valid on the specified architecture.
     * @param architecture the architecture to validate on
     */
    public void verify(CiArchitecture architecture) {
        if (architecture.isSPARC()) {
            assert scale == Scale.Times1 : "Scaled addressing mode not available on SPARC and should not be used";
            assert displacement == 0 || LIROperand.isIllegal(index) : "can't have both";
        } else if (architecture.is64bit()) {
            assert base.isVariableOrRegister() : "wrong base operand";
            assert LIROperand.isIllegal(index) || index.isDoubleRegister() : "wrong index operand";
            assert base.kind == CiKind.Object || base.kind == CiKind.Long : "wrong type for addresses";
        } else {
            assert base.isSingleRegister() : "wrong base operand";
            assert LIROperand.isIllegal(index) || index.isSingleRegister() : "wrong index operand";
            assert base.kind == CiKind.Object || base.kind == CiKind.Int : "wrong type for addresses";
        }
    }

    /**
     * Computes the scaling factor for the specified size in bytes.
     * @param size the size in bytes
     * @return the scaling factor
     */
    public static Scale scale(int size) {
        switch (size) {
            case 1: return Scale.Times1;
            case 2: return Scale.Times2;
            case 4: return Scale.Times4;
            case 8: return Scale.Times8;
            default:
                throw Util.shouldNotReachHere();
        }
    }

    public LIROperand createCopy(LIRLocation newBase, LIRLocation newIndex) {
        return new LIRAddress(newBase, newIndex, scale, displacement, kind);
    }
}
