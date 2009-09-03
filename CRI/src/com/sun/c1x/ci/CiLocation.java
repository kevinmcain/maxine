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
package com.sun.c1x.ci;


/**
 *
 * @author Thomas Wuerthinger
 *
 */
public final class CiLocation extends CiValue {

    public static final CiLocation InvalidLocation = new CiLocation();

    public final CiKind kind;
    public final CiRegister first;
    public final CiRegister second;
    public final int stackOffset;
    public final int stackSize;
    public final boolean callerStack;

    public CiLocation(CiKind kind, CiRegister register) {
        assert kind.size == 1;
        this.kind = kind;
        first = register;
        second = null;
        stackOffset = 0;
        stackSize = 0;
        this.callerStack = false;
    }

    public CiLocation(CiKind kind, CiRegister first, CiRegister second) {
        assert kind.size == 2;
        this.kind = kind;
        this.first = first;
        this.second = second;
        stackOffset = 0;
        stackSize = 0;
        this.callerStack = false;
    }

    private CiLocation() {
        this.kind = CiKind.Illegal;
        this.first = null;
        this.second = null;
        this.stackOffset = 0;
        this.stackSize = 0;
        this.callerStack = false;
    }

    public CiLocation(CiKind kind, int stackOffset, int stackSize, boolean callerStack) {
        this.kind = kind;
        this.first = null;
        this.second = null;
        this.stackOffset = stackOffset;
        this.stackSize = stackSize;
        this.callerStack = callerStack;
    }

    public boolean isSingleRegister() {
        return second == null && first != null;
    }

    public boolean isDoubleRegister() {
        return second != null;
    }

    public boolean isRegister() {
        return isSingleRegister() || isDoubleRegister();
    }

    public boolean isStackOffset() {
        return !isRegister() && isValid();
    }

    public boolean isValid() {
        return this != InvalidLocation;
    }

    @Override
    public int hashCode() {
        return first.hashCode() * 13 + second.hashCode() * 7 + stackOffset;
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }

        if (obj instanceof CiLocation) {
            final CiLocation other = (CiLocation) obj;
            return other.first == first && other.second == second && other.stackOffset == stackOffset;
        }

        return false;
    }

    @Override
    public String toString() {
        if (isSingleRegister()) {
            return first.name;
        } else if (isDoubleRegister()) {
            return first.name + "+" + second.name;
        } else if (isStackOffset()) {
            return "STACKED REG at " + stackOffset;
        } else {
            assert !this.isValid();
            return "BAD";
        }
    }
}