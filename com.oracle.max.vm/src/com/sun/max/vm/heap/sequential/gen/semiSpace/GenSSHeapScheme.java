/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.heap.sequential.gen.semiSpace;

import static com.sun.max.vm.VMConfiguration.*;

import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.code.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.gcx.*;
import com.sun.max.vm.heap.gcx.rset.*;
import com.sun.max.vm.heap.gcx.rset.ctbl.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;
/**
 * A heap scheme implementing a two-generations heap, where each generation implements a semi-space collector.
 *
 *
 */
public final class GenSSHeapScheme extends HeapSchemeWithTLABAdaptor implements XirWriteBarrierSpecification, RSetCoverage {
    /**
     * Knob for the fixed ratio resizing policy.
     */
    static int YoungGenHeapPercent = 30;
    static {
        VMOptions.addFieldOption("-XX:", "YoungGenHeapPercent", GenSSHeapScheme.class, "Fixed percentage of heap size that must be used by young gen", Phase.PRISTINE);
    }

    /**
     * Refiller for the OldSpace allocator.
     */
    final class OldSpaceRefiller extends Refiller {
        @Override
        public Address allocateRefill(Pointer startOfSpaceLeft, Size spaceLeft) {
            // TODO
            // Either we're already GC-ing the old space, in which case we have an out of memory situation.
            // Or this is a direct allocation and we need to raise a full collection.
            return Address.zero();
        }

        @Override
        protected void doBeforeGC() {
        }

    }

    /**
     * Refiller for the young space allocator. Just trigger a garbage collect.
     */
    class YoungSpaceRefiller extends Refiller {
        @Override
        public Address allocateRefill(Pointer startOfSpaceLeft, Size spaceLeft) {
            AtomicBumpPointerAllocator<YoungSpaceRefiller> allocator = youngSpace.allocator();
            Size size = allocator.size();
            while (!Heap.collectGarbage(size)) {
                size = allocator.size();
                // TODO: condition for OOM
            }
            // We're out of safepoint. The current thread hold the refill lock and will do the refill of the allocator.
            return Address.zero();
        }

        @Override
        protected void doBeforeGC() {
            // Nothing to do.
        }
    }

    /**
     * Always start with a minor collection.
     * If space left after the minor collection in the old generation is less than estimated space for survivors of the next minor collection,
     * a full GC is performed immediately.
     * It is possible for the minor collection to overflow the old generation because of under-estimated survivor space at the last minor collection.
     * This is caught by the refiller of the old generation allocator, which in this case allocate space directly in the second semi-space.
     */
    final class GenCollection extends GCOperation {
        private int minSurvivingPercent = 15; // expected percentage of survivors. Arbitrary for now

        GenCollection() {
            super("GenCollection");
        }
        private void verifyAfterMinorCollection() {
            // Verify that:
            // 1. offset table is correctly setup
            // 2. there are no pointer from old to young.
            oldSpace.visit(fotVerifier);
            noFromSpaceReferencesVerifiers.setEvacuatedSpace(youngSpace);
            oldSpace.visit(noFromSpaceReferencesVerifiers);
        }

        private void verifyAfterFullCollection() {
            oldSpace.visit(fotVerifier);
            noFromSpaceReferencesVerifiers.setEvacuatedSpace(oldSpace.fromSpace);
            oldSpace.visit(noFromSpaceReferencesVerifiers);
        }

        private void doOldGenCollection() {
            youngSpaceEvacuator.doBeforeGC();
            // NOTE: counter must be incremented before a heap phase change  to ANALYZING.
            fullCollectionCount++;
            oldSpace.flipSpaces();
            oldSpaceEvacuator.setGCOperation(this);
            oldSpaceEvacuator.setEvacuationSpace(oldSpace.fromSpace, oldSpace);
            oldSpaceEvacuator.evacuate();
            final CardFirstObjectTable fot = cardTableRSet.cfoTable;
            final int startIndex = fot.tableEntryIndex(oldSpace.fromSpace.start());
            final int endIndex = fot.tableEntryIndex(oldSpace.fromSpace.committedEnd());
            fot.clear(startIndex, endIndex);
            cardTableRSet.cardTable.clean(startIndex, endIndex);
            youngSpaceEvacuator.doAfterGC();
            oldSpaceEvacuator.setGCOperation(null);
        }

        private Size estimatedNextEvac() {
            final Size min = youngSpace.totalSpace().dividedBy(100).times(minSurvivingPercent);
            final Size lastSurvivorCount = youngSpaceEvacuator.evacuatedBytes();
            return lastSurvivorCount.greaterThan(min) ? lastSurvivorCount : min;
        }

        private void resize(HeapSpace space, Size newSize) {
            if (newSize.lessThan(space.totalSpace())) {
                Size delta = space.totalSpace().minus(newSize);
                space.shrinkAfterGC(delta);
            } else if (newSize.greaterThan(space.totalSpace())) {
                Size delta = newSize.minus(space.totalSpace());
                space.growAfterGC(delta);
            }
        }

        @Override
        protected void collect(int invocationCount) {
            VmThreadMap.ACTIVE.forAllThreadLocals(null, tlabFiller);
            vmConfig().monitorScheme().beforeGarbageCollection();
            if (Heap.verbose()) {
                Log.println("--Begin nursery evacuation");
            }
            youngSpaceEvacuator.setGCOperation(this);
            youngSpaceEvacuator.setEvacuationBufferSize(oldSpace.freeSpace());
            youngSpaceEvacuator.evacuate();
            youngSpaceEvacuator.setGCOperation(null);
            if (Heap.verbose()) {
                Log.println("--End nursery evacuation");
            }
            if (VerifyAfterGC) {
                verifyAfterMinorCollection();
            }
            final Size estimatedEvac = estimatedNextEvac();
            if (resizingPolicy.shouldPerformFullGC(estimatedEvac, oldSpace.freeSpace())) {
                // Force a temporary transition to MUTATING state.
                // This simplifies the inspector's maintenance of references state and GC counters.
                HeapScheme.Inspect.notifyHeapPhaseChange(HeapPhase.MUTATING);
                if (Heap.verbose()) {
                    Log.println("--Begin old geneneration collection");
                }
                doOldGenCollection();
                if (Heap.verbose()) {
                    Log.println("--End   old geneneration collection");
                }
                if (VerifyAfterGC) {
                    verifyAfterFullCollection();
                }
                if (resizingPolicy.resizeAfterFullGC(estimatedEvac, oldSpace.freeSpace())) {
                    resize(youngSpace, resizingPolicy.youngGenSize());
                    resize(oldSpace, resizingPolicy.oldGenSize());
                }
            }
            vmConfig().monitorScheme().afterGarbageCollection();
            HeapScheme.Inspect.notifyHeapPhaseChange(HeapPhase.MUTATING);
        }
    }

    /**
     * Old generation, organized as a semi-space.
     */
    @INSPECTED
    final ContiguousSemiSpace<CardSpaceAllocator<OldSpaceRefiller>> oldSpace;

    /**
     * Young generation, organized as a simple linear space. The collector doesn't do aging
     * in this first prototype. Later, this will be changed for a semi-space variant
     * (either with equal-size semi-space, or with a eden / survivor semi-space).
     */
    @INSPECTED
    final ContiguousAllocatingSpace<AtomicBumpPointerAllocator<YoungSpaceRefiller>> youngSpace;

    /**
     * Policy for resizing the heap after each GC.
     */
    private final GenSSHeapSizingPolicy resizingPolicy;

    /**
     * Operation to submit to the {@link VmOperationThread} to perform a generational collection.
     */
    private final GenCollection genCollection;

    /**
     * Card-table based remembered set for the nursery.
     */
    @INSPECTED
    private final CardTableRSet cardTableRSet;

    /**
     * Implementation of young space evacuation. Used by minor collection operations.
     */
    @INSPECTED
    private final NoAgingNurseryEvacuator youngSpaceEvacuator;

    @INSPECTED
    private final EvacuatorToCardSpace oldSpaceEvacuator;

    @INSPECTED
    private int fullCollectionCount;

    /**
     * Support for heap verification. All live objects are evacuated to the old to space on minor collection.
     * There should remain no references from the old space to the young space.
     * Similarly, all live objects are evacuated from the old "from" space on full collection.
     * There should remain no references from the old "to" space to the old "from" space.
     * This verifier can be used for both verification.
     */
    private final NoEvacuatedSpaceReferenceVerifier noFromSpaceReferencesVerifiers;

    /**
     * Verify that the FOT table is correctly setup.
     */
    private final FOTVerifier fotVerifier;

    @HOSTED_ONLY
    public GenSSHeapScheme() {
        cardTableRSet = new CardTableRSet();
        AtomicBumpPointerAllocator<YoungSpaceRefiller> nurseryAllocator =
            new AtomicBumpPointerAllocator<YoungSpaceRefiller>(new YoungSpaceRefiller());
        CardSpaceAllocator<OldSpaceRefiller> tenuredAllocator =
            new CardSpaceAllocator<GenSSHeapScheme.OldSpaceRefiller>(new OldSpaceRefiller(), cardTableRSet);
        resizingPolicy = new GenSSHeapSizingPolicy();
        youngSpace = new ContiguousAllocatingSpace<AtomicBumpPointerAllocator<YoungSpaceRefiller>>(nurseryAllocator, "Young Generation");
        oldSpace = new ContiguousSemiSpace<CardSpaceAllocator<OldSpaceRefiller>>(tenuredAllocator, "Old Generation");
        youngSpaceEvacuator = new NoAgingNurseryEvacuator(youngSpace, oldSpace, cardTableRSet);
        oldSpaceEvacuator = new  EvacuatorToCardSpace(oldSpace.fromSpace, oldSpace, cardTableRSet);
        noFromSpaceReferencesVerifiers = new NoEvacuatedSpaceReferenceVerifier(cardTableRSet, youngSpace);
        fotVerifier = new FOTVerifier(cardTableRSet);
        genCollection = new GenCollection();
    }

    @Override
    public void initialize(MaxineVM.Phase phase) {
        super.initialize(phase);
        cardTableRSet.initialize(phase);
    }

    @Override
    public boolean contains(Address address) {
        return oldSpace.contains(address) || youngSpace.contains(address);
    }

    @Override
    public boolean collectGarbage(Size requestedFreeSpace) {
        if ((requestedFreeSpace.isZero() && !DisableExplicitGC) || youngSpace.freeSpace().lessThan(requestedFreeSpace)) {
            if (!Heap.gcDisabled()) {
                genCollection.submit();
            }
        }
        return oldSpace.freeSpace().plus(youngSpace.freeSpace()).greaterThan(requestedFreeSpace);
    }

    @Override
    public Size reportFreeSpace() {
        return oldSpace.freeSpace().plus(youngSpace.freeSpace());
    }

    @Override
    public Size reportUsedSpace() {
        return oldSpace.usedSpace().plus(youngSpace.usedSpace());
    }

    @INLINE
    @Override
    public boolean needsBarrier(IntBitSet<WriteBarrierSpecification.WriteBarrierSpec> writeBarrierSpec) {
        return writeBarrierSpec.isSet(WriteBarrierSpec.POST_WRITE);
    }

    @INLINE
    @Override
    public void postWriteBarrier(Reference ref, Offset offset, Reference value) {
        cardTableRSet.record(ref, offset);
    }

    @INLINE
    @Override
    public void postWriteBarrier(Reference ref,  int displacement, int index, Reference value) {
        cardTableRSet.record(ref, displacement, index);
    }

    @Override
    protected void allocateHeapAndGCStorage() {
        final Size reservedSpace = Size.K.times(reservedVirtualSpaceKB());
        final Size initSize = Heap.initialSize();
        final Size maxSize = Heap.maxSize();
        final int pageSize = Platform.platform().pageSize;
        final int log2Alignment = Integer.numberOfTrailingZeros(pageSize);
        // Verify that the constraint of the heap scheme are met:
        FatalError.check(Heap.bootHeapRegion.start() ==
            Heap.startOfReservedVirtualSpace(),
            "Boot heap region must be mapped at start of reserved virtual space");

        final Address endOfCodeRegion = Code.getCodeManager().getRuntimeOptCodeRegion().end();
        final Address endOfReservedSpace = Heap.bootHeapRegion.start().plus(reservedSpace);
        final Address  firstUnusedByteAddress = endOfCodeRegion.alignUp(pageSize);
        try {
            // Use immortal memory for now.
            Heap.enableImmortalMemoryAllocation();
            resizingPolicy.initialize(initSize, maxSize, YoungGenHeapPercent, log2Alignment);
            youngSpace.initialize(firstUnusedByteAddress, resizingPolicy.maxYoungGenSize(), resizingPolicy.initialYoungGenSize());
            Address startOfOldSpace = youngSpace.space.end().alignUp(pageSize);
            oldSpace.initialize(startOfOldSpace, resizingPolicy.maxOldGenSize(), resizingPolicy.initialOldGenSize());
            /*
             * FIXME:
             * We set retireAfterEvacuation parameter to true. We allocate the entire old free space as evacuation LAB when doing a minor evacuation,
             * and retire the entire left over. This is necessary in order for the oldSpace.freeSpace to report the free space accurately independently
             * of the youngSpaceEvacuator (otherwise, we'd have to include the evacuator's ELAB in the calculation). It is also necessary to
             * retire the TLAB if we need mutators to allocate directly in the old gen.
             * This is rather complicated and we need to rethink the APIs here and how to share the evacuator.
              * An alternative would be to allocate an ELAB of size equal to the expected survivor space minus leftover in the current ELAB, but that isn't satisfying either.
            */
            youngSpaceEvacuator.initialize(2, oldSpace.freeSpace(), Size.fromInt(256), true);
            oldSpaceEvacuator.initialize(2, oldSpace.freeSpace(), Size.fromInt(256), true);
            initializeCoverage(firstUnusedByteAddress, oldSpace.highestAddress().minus(firstUnusedByteAddress).asSize());
            cardTableRSet.initializeXirStartupConstants();

            Address unusedReservedSpaceStart = cardTableRSet.memory().end().alignUp(pageSize);
            // Free reserved space we will not be using.
            Size leftoverSize = endOfReservedSpace.minus(unusedReservedSpaceStart).asSize();

            // First, uncommit range we want to free (this will create a new mapping that can then be deallocated)
            if (!Heap.AvoidsAnonOperations) {
                if (!VirtualMemory.uncommitMemory(unusedReservedSpaceStart, leftoverSize,  VirtualMemory.Type.DATA)) {
                    MaxineVM.reportPristineMemoryFailure("reserved space leftover", "uncommit", leftoverSize);
                }
            }
            if (VirtualMemory.deallocate(unusedReservedSpaceStart, leftoverSize, VirtualMemory.Type.DATA).isZero()) {
                MaxineVM.reportPristineMemoryFailure("reserved space leftover", "deallocate", leftoverSize);
            }
            // Make the heap inspectable
            HeapScheme.Inspect.init(true);
            // HeapScheme.Inspect.notifyHeapRegions(youngSpace.space, oldSpace.space, oldSpace.fromSpace, cardTableRSet.memory());
        } finally {
            Heap.disableImmortalMemoryAllocation();
        }
    }

    @Override
    protected void reportTotalGCTimes() {
        // TODO Auto-generated method stub

    }

    /**
     * Allocate a chunk of memory of the specified size and refill a thread's TLAB with it.
     * @param etla the thread whose TLAB will be refilled
     * @param tlabSize the size of the chunk of memory used to refill the TLAB
     */
    private void allocateAndRefillTLAB(Pointer etla, Size tlabSize) {
        Pointer tlab = youngSpace.allocate(tlabSize);
        Size effectiveSize = tlabSize.minus(tlabHeadroom());
        refillTLAB(etla, tlab, effectiveSize);
    }

    @NEVER_INLINE
    @Override
    protected Pointer handleTLABOverflow(Size size, Pointer etla, Pointer tlabMark, Pointer tlabEnd) {
        // Should we refill the TLAB ?
        final TLABRefillPolicy refillPolicy = TLABRefillPolicy.getForCurrentThread(etla);
        if (refillPolicy == null) {
            // No policy yet for the current thread. This must be the first time this thread uses a TLAB (it does not have one yet).
            FatalError.check(tlabMark.isZero(), "thread must not have a TLAB yet");
            if (!usesTLAB()) {
                // We're not using TLAB. So let's assign the never refill tlab policy.
                TLABRefillPolicy.setForCurrentThread(etla, NEVER_REFILL_TLAB);
                return youngSpace.allocate(size);
            }
            // Allocate an initial TLAB and a refill policy. For simplicity, this one is allocated from the TLAB (see comment below).
            final Size tlabSize = initialTlabSize();
            allocateAndRefillTLAB(etla, tlabSize);
            // Let's do a bit of meta-circularity. The TLAB is refilled, and no-one except the current thread can use it.
            // So the TLAB allocation is going to succeed here
            TLABRefillPolicy.setForCurrentThread(etla, new SimpleTLABRefillPolicy(tlabSize));
            // Now, address the initial request. Note that we may recurse down to handleTLABOverflow again here if the
            // request is larger than the TLAB size. However, this second call will succeed and allocate outside of the TLAB.
            return tlabAllocate(size);
        }
        final Size nextTLABSize = refillPolicy.nextTlabSize();
        if (size.greaterThan(nextTLABSize)) {
            // This couldn't be allocated in a TLAB, so go directly to direct allocation routine.
            // NOTE: this is where we always go if we don't use TLABs (the "never refill" TLAB policy
            // always return zero for the next TLAB size.
            return youngSpace.allocate(size);
        }
        if (!refillPolicy.shouldRefill(size, tlabMark)) {
            // Size would fit in a new tlab, but the policy says we shouldn't refill the TLAB yet, so allocate directly in the young generation.
            return youngSpace.allocate(size);
        }
        // Refill TLAB and allocate (we know the request can be satisfied with a fresh TLAB and will therefore succeed).
        allocateAndRefillTLAB(etla, nextTLABSize);
        return tlabAllocate(size);
    }

    @Override
    protected Pointer customAllocate(Pointer customAllocator, Size size) {
        return ImmortalHeap.allocate(size, true);
    }

    /**
     * Interface to the heap region manager to request coverage of all heap spaces by remembered set.
     * This must be called before the first assignment to a reference location so that code
     * generated with write barrier doesn't fail.
     */
    @Override
    public void initializeCoverage(Address coveredAreaStart, Size coveredAreaSize) {
        final int pageSize = Platform.platform().pageSize;
        final Address endOfCoveredArea = coveredAreaStart.plus(coveredAreaSize);
        final Size cardTableCoveredAreaSize = endOfCoveredArea.minus(Heap.bootHeapRegion.start()).asSize();

        // Allocate Card Table Data at the end of the covered area (i.e., space reserved to the heap regions).
        final Address cardTableDataStart =  endOfCoveredArea.roundedUpBy(pageSize);

        // We want the card table to cover not just the dynamic heap, but also the boot image and code cache to avoid testing
        // for boundaries in the write barrier. Note that covering these with the card table doesn't mean we will iterate over these
        // cards to find references to young objects (i.e., it may be cheaper to use the reference maps for the boot image).
        final Size cardTableDataSize = cardTableRSet.memoryRequirement(cardTableCoveredAreaSize);
        if (!Heap.AvoidsAnonOperations) {
            if (!VirtualMemory.commitMemory(cardTableDataStart, cardTableDataSize,  VirtualMemory.Type.DATA)) {
                MaxineVM.reportPristineMemoryFailure("card table space", "commit", cardTableDataSize);
            }
        }
        cardTableRSet.initialize(Heap.bootHeapRegion.start(), cardTableCoveredAreaSize, cardTableDataStart, cardTableDataSize);
    }

    @HOSTED_ONLY
    public XirWriteBarrierGenerator barrierGenerator(IntBitSet<WriteBarrierSpecification.WriteBarrierSpec> writeBarrierSpec) {
        if (writeBarrierSpec.equals(TUPLE_POST_BARRIER)) {
            return new XirWriteBarrierGenerator() {
                @Override
                public void genWriteBarrier(CiXirAssembler asm, XirOperand ... operands) {
                    cardTableRSet.genTuplePostWriteBarrier(asm, operands[0]);
                }
            };
        } else if (writeBarrierSpec.equals(ARRAY_POST_BARRIER)) {
            return new XirWriteBarrierGenerator() {
                @Override
                public void genWriteBarrier(CiXirAssembler asm, XirOperand ... operands) {
                    cardTableRSet.genArrayPostWriteBarrier(asm, operands[0], operands[1]);
                }
            };
        }
        return XirWriteBarrierSpecification.NULL_WRITE_BARRIER_GEN;
    }

}