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
package com.sun.max.vm.heap.beltway.ba2;

import com.sun.max.annotate.*;
import com.sun.max.profile.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.beltway.*;
import com.sun.max.vm.heap.beltway.ba2.BeltwayBA2Collector.*;
import com.sun.max.vm.heap.beltway.profile.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.tele.*;

/**
 * An Heap Scheme for a Appel-style collector implemented with Beltway.
 * Uses two belts: one for the nursery and one for the mature space.
 *
 * @author Christos Kotselidis
 * @author Laurent Daynes
 */

public class BeltwayHeapSchemeBA2 extends BeltwayHeapScheme {

    private static int[] DEFAULT_BELT_HEAP_PERCENTAGE = new int[] {70, 30};
    private final String [] BELT_DESCRIPTIONS = new String[] {"Nursery Belt", "Mature Belt" };

    Runnable minorGCCollector;
    Runnable fullGCCollector;

    public Runnable getMinorGC() {
        return minorGCCollector;
    }

    public Runnable getMajorGC() {
        return fullGCCollector;
    }

    public BeltwayHeapSchemeBA2(VMConfiguration vmConfiguration) {
        super(vmConfiguration);
    }

    @Override
    protected int [] beltHeapPercentage() {
        return DEFAULT_BELT_HEAP_PERCENTAGE;
    }

    @Override
    protected String [] beltDescriptions() {
        return BELT_DESCRIPTIONS;
    }

    @Override
    public void initialize(MaxineVM.Phase phase) {
        super.initialize(phase);
        if (phase == MaxineVM.Phase.PRISTINE) {
            adjustedCardTableAddress = BeltwayCardRegion.adjustedCardTableBase(cardRegion.cardTableBase().asPointer());
            beltManager.swapBelts(getMatureSpace(), getNurserySpace());
            getMatureSpace().setExpandable(true);
            // The following line enables allocation to take place.
            tlabAllocationBelt = getNurserySpace();
            // Watch out: the following create a MemoryRegion array
            InspectableHeapInfo.init(getNurserySpace(), getMatureSpace());
            if (parallelScavenging) {
                minorGCCollector =  new ParMinorGCCollector();
                fullGCCollector = new ParFullGCCollector();
            } else {
                minorGCCollector =  new MinorGCCollector();
                fullGCCollector =  new FullGCCollector();
            }
        } else if (phase == MaxineVM.Phase.RUNNING) {
            heapVerifier.initialize(beltManager.getApplicationHeap(), getMatureSpace());
            if (Heap.verbose()) {
                HeapTimer.initializeTimers(Clock.SYSTEM_MILLISECONDS, "TotalGC", "NurserySpaceGC", "MatureSpaceGC", "Clear", "RootScan", "BootHeapScan", "CodeScan", "CardScan", "Scavenge");
            }
        }
    }

    @INLINE
    public Belt getNurserySpace() {
        return beltManager.getBelt(0);
    }

    @INLINE
    public Belt getMatureSpace() {
        return beltManager.getBelt(1);
    }

    @INLINE
    public  Size getUsableMemory() {
        return getMaxHeapSize().dividedBy(2);
    }

    @INLINE
    public  Size getCopyReserveMemory() {
        return getMaxHeapSize().minus(getUsableMemory());
    }

    public synchronized boolean collectGarbage(Size requestedFreeSpace) {
        boolean result = false;
        if (minorCollect(requestedFreeSpace)) {
            result = true;
            if (getMatureSpace().getUsedMemorySize().greaterEqual(getUsableMemory())) {
                result = majorCollect(requestedFreeSpace);
            }
        }
        cardRegion.clearAllCards();
        return result;

    }


    public boolean minorCollect(Size requestedFreeSpace) {
        if (outOfMemory) {
            return false;
        }
        collectorThread.execute(getMinorGC());
        if (immediateFreeSpace(getNurserySpace()).greaterEqual(requestedFreeSpace)) {
            return true;
        }

        return false;
    }

    public boolean majorCollect(Size requestedFreeSpace) {
        if (outOfMemory) {
            return false;
        }
        collectorThread.execute(getMajorGC());
        if (!BeltwayHeapScheme.outOfMemory == true) {
            if (immediateFreeSpace(getMatureSpace()).greaterEqual(requestedFreeSpace)) {
                return true;
            }
        }
        return false;
    }

    @INLINE
    private Size immediateFreeSpace(Belt belt) {
        return belt.end().minus(belt.getAllocationMark()).asSize();
    }

    @Override
    public boolean contains(Address address) {
        return address.greaterEqual(Heap.bootHeapRegion.start()) && address.lessEqual(getNurserySpace().end());
    }

    public boolean checkOverlappingBelts(Belt from, Belt to) {
        return from.getAllocationMark().greaterThan(to.start()) || from.end().greaterThan(to.start());
    }

    @INLINE(override = true)
    public void writeBarrier(Reference from, Reference to) {
        // do nothing.
    }

}
