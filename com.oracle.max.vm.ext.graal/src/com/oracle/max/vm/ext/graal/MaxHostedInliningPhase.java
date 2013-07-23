/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.graal;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.InliningUtil.*;
import com.sun.max.annotate.*;
import com.sun.max.vm.actor.member.*;

/**
 * Custom inlining phase for building the boot image that honors {@link @INLINE}. {@link NEVER_INLINE}.
 * It otherwise follows the standard policy.
 *
 * Handling the no null-check semantics of {@link INLINE} is tricky as there is no hook in the actual inlining mechanism
 * where we can get control, unlike when processing snippets. So when we detect such a method we record the predecessor
 * of the {@link Invoke} in the graph and fix them all up at the end.
 *
 */
@HOSTED_ONLY
public class MaxHostedInliningPhase extends InliningPhase {

    private static class MaxGreedyInliningPolicy extends GreedyInliningPolicy {

        public MaxGreedyInliningPolicy(Replacements replacements, Map<Invoke, Double> hints) {
            super(replacements, hints);
        }

        @Override
        public boolean isWorthInlining(InlineInfo info, int inliningDepth, double probability, double relevance, boolean fullyProcessed) {
            CallTargetNode callTargetNode = info.invoke().callTarget();
            if (callTargetNode instanceof MethodCallTargetNode) {
                MethodCallTargetNode methodCallTargetNode = (MethodCallTargetNode) callTargetNode;
                if (methodCallTargetNode.isResolved()) {
                    MethodActor ma = (MethodActor) MaxResolvedJavaMethod.getRiResolvedMethod(methodCallTargetNode.targetMethod());
                    if (ma.isInline()) {
                        if (!ma.isStatic()) {
                            ((ExactInlineInfo) info).suppressNullCheck();
                        }
                        return true;
                    } else if (ma.isIntrinsic()) {
                        return false;
                    } else if (ma.isNeverInline()) {
                        return false;
                    }
                }
            }
            return super.isWorthInlining(info, inliningDepth, probability, relevance, fullyProcessed);
        }
    }

    MaxHostedInliningPhase(MetaAccessProvider runtime,  Map<Invoke, Double> hints, Replacements replacements, Assumptions assumptions,
                    GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {
        super(runtime, replacements, assumptions, cache, plan, optimisticOpts, hints, new MaxGreedyInliningPolicy(replacements, hints));
    }

}
