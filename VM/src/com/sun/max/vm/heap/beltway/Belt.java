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
package com.sun.max.vm.heap.beltway;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.debug.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.runtime.*;

/**
 * @author Christos Kotselidis
 */
public class Belt extends RuntimeMemoryRegion {

    /**
     * The relative order of a belt. Always starts from Zero (0). The lower the value, the older
     * the generation and vice versa.
     */
    private int index;

    /**
     * Specifies if the belt is expandable or not (i.e. whether it can overlap with a contiguous memory segment).
     */
    private boolean expandable = false;

    /**
     * The percent of usable memory frames the belt occupies. This corresponds to the "X" described in the Beltway paper.
     */
    private int framePercentageOfUsableMemory;

    // The address and the pointer to the address
    // of the previous allocation mark
    private volatile Address prevAllocationMark = Address.zero();

    private Address usableMemoryEnd;
    private Address usableMemoryStart;

    private Size usableMemory;

    public Belt(int index, Address beltStartAddress, Size beltSize, int framePercentageOfUsableMemory) {
        super(beltStartAddress, beltSize);
        this.index = index;
        this.usableMemoryStart = beltStartAddress;
        this.framePercentageOfUsableMemory = framePercentageOfUsableMemory;
        this.mark.set(beltStartAddress);
    }

    public Belt() {
    }

    public final Address getPrevAllocationMark() {
        return prevAllocationMark;
    }

    public final Size getUsedMemorySize() {
        return getAllocationMark().minus(start()).asSize();
    }

    public final Size getRemainingMemorySize() {
        return end().asSize().minus(getAllocationMark());
    }

    public final void resetAllocationMark() {
        mark.set(start());
    }

    @Override
    public void setStart(Address beltStartAddress) {
        super.setStart(beltStartAddress);
        mark.set(beltStartAddress);
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public int getFramePercentageOfUsableMemory() {
        return framePercentageOfUsableMemory;
    }

    public void setFramePercentageOfUsableMemory(int framePercentageOfUsableMemory) {
        this.framePercentageOfUsableMemory = framePercentageOfUsableMemory;
    }

    public void setExpandable(boolean expandable) {
        this.expandable = expandable;
    }

    public Pointer allocate(RuntimeMemoryRegion from, Size size) {
        return null;
    }

    @NO_SAFEPOINTS("Slow path for allocation in a belt")
    @INLINE
    public final Pointer allocate(Size size) {
        final Pointer oldAllocationMark = mark();
        final Pointer cell = DebugHeap.adjustForDebugTag(oldAllocationMark);
        final Pointer end = cell.plus(size);

        if (!checkObjectInBelt(size)) {
            if (!expandable) {
                return Pointer.zero();
            }
        }
        if (mark.compareAndSwap(oldAllocationMark, end) != oldAllocationMark) {
            if (Heap.verbose()) {
                Log.println("Conflict! retry-allocate");
            }
            return retryAllocate(size);
        }
        return cell;
    }

    @NO_SAFEPOINTS("TODO")
    @INLINE
    public final Pointer bumpAllocate(Size size) {
        final Pointer oldAllocationMark = mark();
        final Pointer cell = DebugHeap.adjustForDebugTag(oldAllocationMark);
        final Pointer end = cell.plus(size);

        if (!checkObjectInBelt(size)) {
            if (!expandable) {
                return Pointer.zero();
            }
        }
        mark.set(end);
        return cell;
    }

    @NEVER_INLINE
    private Pointer retryAllocate(Size size) {
        Pointer oldAllocationMark;
        Pointer cell;
        Address end;
        do {
            oldAllocationMark = mark();
            cell = DebugHeap.adjustForDebugTag(oldAllocationMark);
            end = cell.plus(size);
            if (checkNotExceedUsable(end.asSize())) {
                return Pointer.zero();
            }
            oldAllocationMark = mark();
        } while (mark.compareAndSwap(oldAllocationMark, end) != oldAllocationMark);
        return cell;
    }

    @INLINE
    public final Pointer gcAllocate(Size size) {
        final Pointer oldAllocationMark = mark();
        final Pointer cell = DebugHeap.adjustForDebugTag(oldAllocationMark);
        final Pointer end = cell.plus(size);

        if (!end.lessThan(end())) {
            if (!expandable) {
                if (Heap.verbose()) {
                    Log.println(" Trying to Overwrite contiguous spaces");
                }
                // Allocation Overflow, throw outOfMemory exception
                return Pointer.zero();
            }

            if (!end.lessThan(((BeltwayHeapScheme) VMConfiguration.hostOrTarget().heapScheme()).getBeltManager().getApplicationHeap().end())) {
                return Pointer.zero();
            }
            if (!(mark.compareAndSwap(oldAllocationMark, end) == oldAllocationMark)) {
                if (Heap.verbose()) {
                    Log.println("Conflict - Retry allocate");
                }
                return retryGCAllocate(size);
            }

        } else {
            if (!(mark.compareAndSwap(oldAllocationMark, end) == oldAllocationMark)) {
                if (Heap.verbose()) {
                    Log.println("Conflict - Retry allocate");
                }
                return retryGCAllocate(size);
            }
        }
        return cell;
    }

    @NEVER_INLINE
    private Pointer retryGCAllocate(Size size) {
        Pointer oldAllocationMark;
        Pointer cell;
        Address end;
        do {
            oldAllocationMark = mark();
            if (MaxineVM.isDebug()) {
                cell = oldAllocationMark.plusWords(1);
            } else {
                cell = oldAllocationMark;
            }
            end = cell.plus(size);
            oldAllocationMark = mark();
        } while (mark.compareAndSwap(oldAllocationMark, end) != oldAllocationMark);

        return cell;
    }

    @INLINE
    public final Pointer gcBumpAllocate(Size size) {
        Pointer cell;
        final Pointer oldAllocationMark = mark();

        if (MaxineVM.isDebug()) {
            cell = oldAllocationMark.plusWords(1);
        } else {
            cell = oldAllocationMark;
        }

        final Pointer end = cell.plus(size);

        if (!end.lessThan(end())) {
            if (!expandable) {
                FatalError.check(mark().lessThan(end()), "GC allocation overflow");
            }
            if (Heap.verbose()) {
                Log.println(" Trying to Overwrite contiguous spaces");
            }
            mark.set(end);

        } else {
            mark.set(end);
        }
        return cell;
    }

    @INLINE
    private boolean checkObjectInBelt(Size size) {
        final Pointer oldAllocationMark = mark();
        if (oldAllocationMark.plus(size).greaterThan(end().asPointer())) {
            if (Heap.verbose()) {
                Log.println("Allocation did not fit, check whether a new frame can be fit in the belt");
                Log.println(" Check whether a new frame can be fit into the belt ");
                Log.print(" Allocation Size:");
                Log.println(size);
                Log.print(" Allocation Mark: ");
                Log.println(oldAllocationMark);
            }
            return false;
        }
        return true;

    }

    public void setAllocationMarkSnapshot() {
        prevAllocationMark = mark();
    }

    public void setAllocationMark(Address address) {
        mark.set(address);
    }

    public Pointer getAllocationMarkSnapshot() {
        return prevAllocationMark.asPointer();
    }

    public final boolean checkNotExceedUsable(Size size) {
        final Size usedMemory = calculateUsedMemory();
        if (usedMemory.plus(size).greaterThan(BeltwayConfiguration.getUsableMemory())) {
            if (Heap.verbose()) {
                Log.println("Allocation is trying to exceed usable memory");
                Log.print(" Usable memory Size: ");
                Log.println(BeltwayConfiguration.getUsableMemory().toLong());
                Log.print(" Used Memory: ");
                Log.println(usedMemory.toLong());
                Log.print(" Allocation Size: ");
                Log.println(size.toLong());
                Log.print(" BeltwayConfiguration.getUsableMemory() Index: ");
                Log.println(BeltwayConfiguration.getUsableMemory().toLong());
            }
            return false;
        }
        return true;

    }

    public Size calculateUsedMemory() {
        Size usableMemory = Size.zero();
        if (mark().lessThan(this.usableMemoryStart)) {
            usableMemory = usableMemory.plus(this.usableMemoryStart.plus(end())).asSize();
            usableMemory = usableMemory.plus(start().plus(mark())).asSize();
        } else {
            usableMemory = mark().minus(this.usableMemoryStart).asSize();
        }
        return usableMemory;
    }

    public void printInfo() {
        Log.println();
        Log.print("Belt index: ");
        Log.println(index);
        Log.print("Belt start address: ");
        Log.println(start());
        Log.print("Belt stop address:  ");
        Log.println(end());
        Log.print("Belt alloc address:  ");
        Log.println(getAllocationMark());
        Log.print("Belt size: ");
        Log.println(size().toLong());
        Log.print("Frame percentage of usable memory: ");
        Log.println(framePercentageOfUsableMemory);
        if (start().isAligned(BeltwayHeapSchemeConfiguration.ALIGNMENT)) {
            Log.println("true");
        }

    }

    /**
     * Evacuate objects of the specified belt reachable from objects of this belt into this belt using the specified  cell visitor.
     * This method is not multi-thread safe and cannot be used for parallel evacuation.
     *
     * @param cellVisitor visits objects of this belt to find references to evacuation candidates
     * @param from the belt that contains the objects that will be evacuated into this belt.
     */
    public final void evacuate(BeltCellVisitor cellVisitor, Belt from) {
        cellVisitor.init(from, this);
        Pointer cell = start().asPointer();
        while (cell.lessThan(getAllocationMark())) {
            cell = DebugHeap.checkDebugCellTag(from.start(), cell);
            cell = cellVisitor.visitCell(cell);
        }
    }
}
