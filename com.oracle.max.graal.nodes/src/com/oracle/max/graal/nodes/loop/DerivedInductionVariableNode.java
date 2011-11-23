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
package com.oracle.max.graal.nodes.loop;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

/**
 * LinearInductionVariable that is computed in the loops with offset + scale * base.
 * This is computed in the loop only when necessary, puts less pressure on registers.
 */
public class DerivedInductionVariableNode extends LinearInductionVariableNode  implements Canonicalizable {

    @Input private InductionVariableNode base;

    public DerivedInductionVariableNode(CiKind kind, ValueNode offset, ValueNode scale, InductionVariableNode base) {
        super(kind, scale, offset);
        this.base = base;
    }

    public InductionVariableNode base() {
        return base;
    }

    public ValueNode offset() {
        return b();
    }

    public void setOffset(ValueNode offset) {
        setB(offset);
    }

    public ValueNode scale() {
        return a();
    }

    public void setScale(ValueNode scale) {
        setA(scale);
    }

    @Override
    public LoopBeginNode loopBegin() {
        return base().loopBegin();
    }

    @Override
    public void peelOneIteration() {
        // nop
    }

    /**
     * This will apply strength reduction to this induction variable but will augment register pressure in the loop.
     * @return the new BasicInductionVariable
     */
    public BasicInductionVariableNode toBasicInductionVariable() {
        InductionVariableNode base = base();
        if (base instanceof DerivedInductionVariableNode) {
            base = ((DerivedInductionVariableNode) base).toBasicInductionVariable();
        }
        ValueNode init;
        ValueNode stride;
        LoopCounterNode counter;
        if (base instanceof BasicInductionVariableNode) {
            BasicInductionVariableNode basic = (BasicInductionVariableNode) base;
            // let the canonicalizer do its job with this
            init = IntegerArithmeticNode.add(offset(), IntegerArithmeticNode.mul(scale(), basic.init()));
            stride = IntegerArithmeticNode.mul(scale(), basic.stride());
            counter = basic.loopCounter();
        } else {
            assert base instanceof LoopCounterNode;
            init = offset();
            stride = scale();
            counter = (LoopCounterNode) base;
        }
        BasicInductionVariableNode newBIV = graph().add(new BasicInductionVariableNode(kind, init, stride, counter));
        this.replaceAndDelete(newBIV);
        return newBIV;
    }

    @Override
    public ValueNode lowerInductionVariable() {
        return IntegerArithmeticNode.add(offset(), IntegerArithmeticNode.mul(scale(), base()));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (base() instanceof DerivedInductionVariableNode) {
            DerivedInductionVariableNode divBase = (DerivedInductionVariableNode) base();
            IntegerArithmeticNode newOffset = IntegerArithmeticNode.add(offset(), IntegerArithmeticNode.mul(scale(), divBase.offset()));
            IntegerArithmeticNode newScale = IntegerArithmeticNode.mul(scale(), divBase.scale());
            return graph().add(new DerivedInductionVariableNode(kind, newOffset, newScale, divBase.base()));
        }
        return this;
    }

    @Override
    public boolean isNextIteration(InductionVariableNode other) {
        if (other instanceof LoopCounterNode && this.base() == other) {
            if (this.offset().isConstant() && this.offset().asConstant().asLong() == -1 && this.scale().isConstant() && this.scale().asConstant().asLong() == 1) {
                return true;
            }
        } else if (other instanceof LinearInductionVariableNode) {
            if ((other instanceof BasicInductionVariableNode && ((BasicInductionVariableNode) other).loopCounter() == base())
                            || (other instanceof DerivedInductionVariableNode && ((DerivedInductionVariableNode) other).base() == base())) {
                LinearInductionVariableNode liv = (LinearInductionVariableNode) other;
                if (liv.a() == scale() && IntegerAddNode.isIntegerAddition(liv.b(), offset(), scale())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public StrideDirection strideDirection() {
        ValueNode stride = a();
        if (stride.isConstant()) {
            long val = stride.asConstant().asLong();
            if (val > 0) {
                return base().strideDirection();
            }
            if (val < 0) {
                return StrideDirection.opposite(base().strideDirection());
            }
        }
        return null;
    }
}