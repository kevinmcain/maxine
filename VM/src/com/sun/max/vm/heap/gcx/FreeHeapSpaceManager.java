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
package com.sun.max.vm.heap.gcx;

import static com.sun.max.vm.VMOptions.*;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.debug.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.tele.*;

/**
 * Free heap space management.
 * Nothing ambitious, just to get going and test the tracing algorithm of the future hybrid mark-sweep-evacuate.
 * Implement the HeapSweeper abstract class which defines method called by a HeapMarker to notify free space.
 * The FreeHeapSpace manager records these into an vector of list of free space based on size of the free space.
 *
 * Space allocation is primarily handled via TLABs, which are made of one or more heap chunks.
 * Request too large to be handled by TLABs are handled by the free space manager directly.
 * This one keeps a simple table of list of chunks, indexed by a power of 2 of the size requested, such that
 * Size >> log2FirstBin is an index to that table. The first bin in the table contains a linked list of chunk of any size
 * between log2FirstBin and minReclaimableSpace and is used primarily for TLAB and small object allocation.
 * The other bins are used for large object space allocation. "Bin" allocation are synchronized.
 *
 * @author Laurent Daynes.
 */
public class FreeHeapSpaceManager extends HeapSweeper {
    private static final VMIntOption largeObjectsMinSizeOption =
        register(new VMIntOption("-XX:LargeObjectsMinSize=", Size.K.times(64).toInt(),
                        "Minimum size to be treated as a large object"), MaxineVM.Phase.PRISTINE);

    private static final VMIntOption freeChunkMinSizeOption =
        register(new VMIntOption("-XX:FreeChunkMinSize=", 256,
                        "Minimum size of contiguous space considered for space reclamation." +
                        "Below this size, the space is ignored (dark matter)"),
                        MaxineVM.Phase.PRISTINE);

    private static final VMBooleanXXOption doImpreciseSweepOption = register(new VMBooleanXXOption("-XX:+",
                    "ImpreciseSweep",
                    "Perform imprecise sweeping phase"),
                    MaxineVM.Phase.PRISTINE);


    private static final VMBooleanXXOption traceSweepingOption =  register(new VMBooleanXXOption("-XX:+",
                    "TraceSweep",
                    "Trace heap sweep operations. Do nothing for PRODUCT images"),
                    MaxineVM.Phase.PRISTINE);


    private static boolean TraceSweep = false;

    private static final int REFILL_RATIO =  6;

    /**
     * Minimum size to be treated as a large object.
     */
    @CONSTANT_WHEN_NOT_ZERO
    static Size minLargeObjectSize;

    /**
     * Log 2 of the maximum size to enter the first bin of free space.
     */
    int log2FirstBinSize;

    /**
     * A linear space allocator.
     * Allocate space linearly from a region of the heap.
     *
     * FIXME: needs HEADROOM like semi-space to make sure we're never left with not enough space
     * at the end of a chunk to plant a dead object (for heap parsability).
     */
    class HeapSpaceAllocator extends LinearAllocationMemoryRegion {
        /**
         * End of space allocator.
         */
        private Address end;

        /**
         * Maximum size one can allocate with this allocator. Request for size larger than this
         * gets delegated to the allocation failure handler.
         */
        @CONSTANT_WHEN_NOT_ZERO
        private Size sizeLimit;

        private Size refillSize;

        HeapSpaceAllocator(String description) {
            super(description);
        }

        void initialize(Address initialChunk, Size initialChunkSize, Size sizeLimit, Size refillSize) {
            this.sizeLimit = sizeLimit;
            this.refillSize = refillSize;
            if (initialChunk.isZero()) {
                clear();
            } else {
                refill(initialChunk, initialChunkSize);
            }
        }

        // FIXME: concurrency
        void clear() {
            start = Address.zero();
            end = Address.zero();
            mark.set(Address.zero());
        }

        // FIXME: concurrency
        void refill(Address chunk, Size chunkSize) {
            // Make sure we can cause any attempt to allocate to fail, regardless of the
            // value of the mark.
            end = Address.zero();
            // Now refill.
            start = chunk;
            size = chunkSize;
            mark.set(start);
            end = chunk.plus(chunkSize);
        }

        private Size refillLimit() {
            return size().dividedBy(REFILL_RATIO);
        }

        @INLINE
        private Pointer setTopToEnd() {
            Pointer cell;
            do {
                cell = top();
                if (cell.equals(end)) {
                    // Already at end
                    return cell;
                }
            } while(mark.compareAndSwap(cell, end) != cell);
            return cell;
        }

        Size freeSpaceLeft() {
            return size.minus(used());
        }

        @INLINE
        private Pointer top() {
            return mark.get().asPointer();
        }

        @INLINE
        private boolean isLarge(Size size) {
            return size.greaterThan(sizeLimit);
        }

        synchronized Pointer refillOrAllocate(Size size) {
            if (isLarge(size)) {
                return binAllocate(size).asPointer();
            }
            // We may have raced with another concurrent thread which may have
            // refilled the allocator.
            Pointer cell = top();

            if (cell.plus(size).greaterThan(end)) {
                // Fill up the allocator to bring all mutators to the refill point.
                Size spaceLeft = end.minus(cell).asSize();
                if (spaceLeft.greaterThan(refillLimit())) {
                    // Don't refill, waste would be too high. Allocate from the bin table.
                    return binAllocate(size).asPointer();
                }
                // Refill. First, fill up the allocator to bring everyone to refill synchronization.
                Pointer start = setTopToEnd();

                Address chunk = binRefill(refillSize, start, end.minus(start).asSize());
                refill(chunk, HeapFreeChunk.getFreechunkSize(chunk));
                // Fall-off to return zero.
            }
            // There was a race for refilling the allocator. Just return to
            // the non-blocking allocation loop.
            return Pointer.zero();
        }

        /**
         * Allocate space of the specified size.
         *
         * @param size size requested in bytes.
         * @return
         */
        final Pointer allocate(Size size) {
            if (MaxineVM.isDebug()) {
                FatalError.check(size.isWordAligned(), "Size must be word aligned");
            }
            // Try first a non-blocking allocation out of the current chunk.
            // This may fail for a variety of reasons, all captured by the test
            // against the current chunk limit.
            Pointer cell;
            Pointer nextMark;
            size = DebugHeap.adjustForDebugTag(size.asPointer()).asSize();
            do {
                cell = top();
                nextMark = cell.plus(size);
                while (nextMark.greaterThan(end)) {
                    cell = refillOrAllocate(size);
                    if (!cell.isZero()) {
                        return cell;
                    }
                    // loop back to retry.
                    cell = top();
                    nextMark = cell.plus(size);
                }
            } while (mark.compareAndSwap(cell, nextMark) != cell);
            return DebugHeap.adjustForDebugTag(cell);
        }

        @INLINE
        final Pointer allocateCleared(Size size) {
            Pointer allocated = allocate(size);
            Memory.clearWords(allocated, size.unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt());
            return allocated;
        }

        final Pointer allocateTLAB(Size size) {
            Pointer tlab = allocate(size);
            HeapFreeChunk.setFreeChunkSize(tlab, size);
            HeapFreeChunk.setFreeChunkNext(tlab, null);
            return tlab;
        }
        /**
         * Fill up the allocator and return address of its allocation mark
         * before filling.
         * This is used to ease concurrent refill: a thread requesting a refill first
         * grab a refill monitor, then fill up the allocator to force every racer to
         * to grab the refill monitor.
         * Refill can then be performed by changing first the bounds of the allocator, then
         * the allocation mark.
         *
         * @return
         */
        Pointer fillUp() {
            Pointer cell = setTopToEnd();
            if (cell.lessThan(end)) {
                HeapSchemeAdaptor.fillWithDeadObject(cell.asPointer(), end.asPointer());
            }
            return cell;
        }

        void makeParsable() {
            fillUp();
        }

    }

    /**
     * The currently committed heap space.
     * As a temporary hack to please the inspector, we use a LinearAllocationMemoryRegion to
     * record the committed heap space. The mark represents the top of the committed space,
     * whereas the end is the end of the reserved memory.
     */
    private final LinearAllocationMemoryRegion committedHeapSpace;
    private boolean doImpreciseSweep;
    private final HeapSpaceAllocator smallObjectAllocator;
    private boolean useTLABBin;

    /**
     * Head of a linked list of free space recovered by the Sweeper.
     * Chunks are appended in the list only during sweeping.
     * The entries are therefore ordered from low to high addresses
     * (they are entered as the sweeper discover them).
     */
    final class FreeSpaceList {
        Address head;
        Address last;
        long totalSize;
        long totalChunks;

        FreeSpaceList() {
            reset();
        }

        void reset() {
            head = Address.zero();
            last = Address.zero();
            totalSize = 0L;
            totalChunks = 0L;
        }

        @INLINE
        private void appendChunk(Address chunk, Size size) {
            if (last.isZero()) {
                head = chunk;
            } else {
                HeapFreeChunk.setFreeChunkNext(last, chunk);
            }
            last = chunk;
            totalSize += size.toLong();
            totalChunks++;
        }

        void append(Address chunk, Size size) {
            HeapFreeChunk.format(chunk, size);
            appendChunk(chunk, size);
        }

        void append(HeapFreeChunk chunk) {
            appendChunk(HeapFreeChunk.fromHeapFreeChunk(chunk), chunk.size);
        }

        @INLINE
        private void remove(HeapFreeChunk prev, HeapFreeChunk chunk) {
            totalChunks--;
            totalSize -= chunk.size.toLong();
            if (prev == null) {
                head =  HeapFreeChunk.fromHeapFreeChunk(chunk.next);
            } else {
                prev.next = chunk.next;
            }
            chunk.next = null;
        }

        /**
         * Allocate first chunk of the free list fitting the size.
         * Space left-over is re-entered in the appropriate bin, or dismissed as dark matter.
         * @param size
         * @return
         */
        Address allocateFirstFit(Size size, boolean exactFit) {
            Size spaceWithHeadRoom = size.plus(HeapSchemeAdaptor.MIN_OBJECT_SIZE);
            HeapFreeChunk prevChunk = null;
            HeapFreeChunk chunk = HeapFreeChunk.toHeapFreeChunk(head);
            do {
                if (chunk.size.greaterEqual(spaceWithHeadRoom)) {
                    Address result = HeapFreeChunk.fromHeapFreeChunk(chunk);
                    if (!exactFit) {
                        remove(prevChunk, chunk);
                        return result;
                    }
                    Size spaceLeft = chunk.size.minus(size);
                    if (spaceLeft.greaterEqual(minReclaimableSpace)) {
                        // Space is allocated at the end of the chunk to avoid reformatting it.
                        result = result.plus(spaceLeft);
                        chunk.size = spaceLeft;
                        FreeSpaceList newFreeList =  freeChunkBins[binIndex(spaceLeft)];
                        if (newFreeList == this) {
                            // Chunk remains in its free list.
                            totalSize -= size.toLong();
                            return result;
                        }
                        // Chunk changes of free list.
                        remove(prevChunk, chunk);
                        newFreeList.append(chunk);
                    } else {
                        // Chunk is removed.
                        remove(prevChunk, chunk);
                        Pointer start = result.asPointer().plus(size);
                        darkMatter = darkMatter.plus(spaceLeft);
                        HeapSchemeAdaptor.fillWithDeadObject(start, start.plus(spaceLeft));
                    }
                    return result;
                } else if (chunk.size.equals(size)) {
                    // Exact fit.
                    Address result = HeapFreeChunk.fromHeapFreeChunk(chunk);
                    remove(prevChunk, chunk);
                    return result;
                }
                prevChunk = chunk;
                chunk = chunk.next;
            } while(chunk != null);
            return Address.zero();
        }


        Address allocateTLAB(Size size) {
            // Allocate enough chunks to meet TLAB size.
            // This is very imprecise and we may end up with a TLAB twice the
            // size initially requested.
            Size allocated = Size.zero();
            HeapFreeChunk nextChunk = HeapFreeChunk.toHeapFreeChunk(head);
            int numAllocatedChunks = 0;
            do {
                allocated = allocated.plus(nextChunk.size);
                nextChunk = nextChunk.next;
                numAllocatedChunks++;
            } while(allocated.lessThan(size) && nextChunk != null);
            Address result = head;
            head =  HeapFreeChunk.fromHeapFreeChunk(nextChunk);
            totalChunks -= numAllocatedChunks;
            totalSize -= allocated.toLong();
            totalFreeChunkSpace -= allocated.toLong();
            return result;
        }
    }

    /**
     * Free space is managed via segregated list. The minimum chunk size managed is minFreeChunkSize.
     */
    final FreeSpaceList [] freeChunkBins = new FreeSpaceList[10];

    /**
     * Short cut to first bin dedicated to TLAB refills.
     */
    private final FreeSpaceList  tlabFreeSpaceList;

    /**
     * Total space in free chunks. This doesn't include space of chunks allocated to heap space allocator.
     */
    long totalFreeChunkSpace;

    @INLINE
    private int binIndex(Size size) {
        final long l = size.unsignedShiftedRight(log2FirstBinSize).toLong();
        return  (l < freeChunkBins.length) ?  (int) l : (freeChunkBins.length - 1);
    }

    private synchronized Address binAllocateTLAB(Size size) {
        long requiredSpace = size.toLong();
        // First, try to allocate from the TLAB bin.
        if (tlabFreeSpaceList.totalSize > requiredSpace) {
            return tlabFreeSpaceList.allocateTLAB(size);
        }
        Address initialChunks = tlabFreeSpaceList.head;
        if (tlabFreeSpaceList.totalSize != 0) {
            totalFreeChunkSpace -= tlabFreeSpaceList.totalSize;
            size = size.minus(tlabFreeSpaceList.totalSize);
            tlabFreeSpaceList.head = Address.zero();
            tlabFreeSpaceList.totalSize = 0;
            tlabFreeSpaceList.totalChunks = 0;
            useTLABBin = false;
        }
        // Allocate additional space off higher free space bins.
        Address additionalChunks = binAllocate(1, size, true);
        if (additionalChunks.isZero()) {
            return initialChunks;
        }
        HeapFreeChunk.setFreeChunkNext(additionalChunks, initialChunks);
        return additionalChunks;
    }

    synchronized Address binAllocate(Size size) {
        return  binAllocate(binIndex(size), size, true);
    }

    /* For simplicity at the moment.
     */
    private static final OutOfMemoryError outOfMemoryError = new OutOfMemoryError();

    private Address binAllocate(int firstBinIndex, Size size, boolean exactFit) {
        // Search for a bin with a chunk large enough to satisfy this allocation.
        // Bin #0 contains chunks of any size between minReclaimableSpace and 1 << log2FirstBinSize,
        // so it needs to be scanned for a chunk big enough to hold the requested size.
        do {
            int index = binIndex(size);
            // Any chunks in bin larger or equal to index is large enough to contain the requested size.
            // We may have to re-enter the leftover into another bin.
            while (index <  freeChunkBins.length) {
                FreeSpaceList freelist = freeChunkBins[index];
                if (!freelist.head.isZero()) {
                    Address result = freelist.allocateFirstFit(size, exactFit);
                    if (!result.isZero()) {
                        return result;
                    }
                }
                index++;
            }
        } while (Heap.collectGarbage(size));
        // Not enough freed memory.
        throw  outOfMemoryError;
    }

    synchronized Address binRefill(Size refillSize, Pointer topAtRefill, Size spaceLeft) {
        // First, deal with the left-over.
        if  (spaceLeft.lessThan(minReclaimableSpace) && spaceLeft.greaterThan(0)) {
            HeapSchemeAdaptor.fillWithDeadObject(topAtRefill, topAtRefill.plus(spaceLeft));
        } else {
            recordFreeSpace(topAtRefill, spaceLeft);
        }
        return binAllocate(1, refillSize, false);
    }

    @INLINE
    private void recordFreeSpace(Address chunk, Size numBytes) {
        freeChunkBins[binIndex(numBytes)].append(chunk, numBytes);
        totalFreeChunkSpace += numBytes.toLong();
    }

    /**
     * Recording of free chunk of space.
     * Chunks are recording in different list depending on their size.
     * @param freeChunk
     * @param size
     */
    @Override
    public final void processDeadSpace(Address freeChunk, Size size) {
        recordFreeSpace(freeChunk, size);
    }

    /**
     * Minimum size to be considered reclaimable.
     */
    private Size minReclaimableSpace;
    /**
     * Counter of dark matter.
     */
    private Size darkMatter = Size.zero();

    /**
     * Pointer to the end of the last dead object notified by the sweeper. Used  for precise sweeping.
     */
    private Pointer endOfLastVisitedObject;

    @INLINE
    private Pointer setEndOfLastVisitedObject(Pointer cell) {
        final Pointer origin = Layout.cellToOrigin(cell);
        endOfLastVisitedObject = cell.plus(Layout.size(origin));
        return endOfLastVisitedObject;
    }

    @Override
    public Pointer processLiveObject(Pointer liveObject) {
        final Size deadSpace = liveObject.minus(endOfLastVisitedObject).asSize();
        if (deadSpace.greaterThan(minReclaimableSpace)) {
            recordFreeSpace(endOfLastVisitedObject, deadSpace);
        } else {
            darkMatter.plus(deadSpace);
        }
        endOfLastVisitedObject = liveObject.plus(Layout.size(Layout.cellToOrigin(liveObject)));
        return endOfLastVisitedObject;
    }

    private void printNotifiedGap(Pointer leftLiveObject, Pointer rightLiveObject, Pointer gapAddress, Size gapSize) {
        final boolean lockDisabledSafepoints = Log.lock();
        Log.print("Gap between [");
        Log.print(leftLiveObject);
        Log.print(", ");
        Log.print(rightLiveObject);
        Log.print("] = @");
        Log.print(gapAddress);
        Log.print("(");
        Log.print(gapSize.toLong());
        Log.print(")");

        if (gapSize.greaterEqual(minReclaimableSpace)) {
            Log.print(" => bin #");
            Log.println(binIndex(gapSize));
        } else {
            Log.println(" => dark matter");
        }
        Log.unlock(lockDisabledSafepoints);
    }

    @Override
    public Pointer processLargeGap(Pointer leftLiveObject, Pointer rightLiveObject) {
        Pointer endOfLeftObject = leftLiveObject.plus(Layout.size(Layout.cellToOrigin(leftLiveObject)));
        Size numDeadBytes = rightLiveObject.minus(endOfLeftObject).asSize();
        if (MaxineVM.isDebug() && TraceSweep) {
            printNotifiedGap(leftLiveObject, rightLiveObject, endOfLeftObject, numDeadBytes);
        }
        if (numDeadBytes.greaterEqual(minReclaimableSpace)) {
            recordFreeSpace(endOfLeftObject, numDeadBytes);
        } else {
            darkMatter = darkMatter.plus(numDeadBytes);
        }
        return rightLiveObject.plus(Layout.size(Layout.cellToOrigin(rightLiveObject)));
    }

    void print() {
        final boolean lockDisabledSafepoints = Log.lock();
        Log.print("Min reclaimable space: "); Log.println(minReclaimableSpace);
        Log.print("Dark matter: "); Log.println(darkMatter.toLong());
        for (int i = 0; i < freeChunkBins.length; i++) {
            Log.print("Bin ["); Log.print(i); Log.print("] (");
            Log.print(i << log2FirstBinSize); Log.print(" <= chunk size < "); Log.print((i + 1) << log2FirstBinSize);
            Log.print(") total chunks: "); Log.print(freeChunkBins[i].totalChunks);
            Log.print("   total space : "); Log.println(freeChunkBins[i].totalSize);
        }
        Log.unlock(lockDisabledSafepoints);
    }

    public RuntimeMemoryRegion committedHeapSpace() {
        return committedHeapSpace;
    }

    public FreeHeapSpaceManager() {
        committedHeapSpace = new LinearAllocationMemoryRegion("Heap");
        totalFreeChunkSpace = 0;
        for (int i = 0; i < freeChunkBins.length; i++) {
            freeChunkBins[i] = new FreeSpaceList();
        }
        tlabFreeSpaceList = freeChunkBins[0];
        smallObjectAllocator = new HeapSpaceAllocator("Small Objects Allocator");
    }

    public void initialize(Address start, Size initSize, Size maxSize) {
        committedHeapSpace.setStart(start);
        committedHeapSpace.mark.set(start.plus(initSize));
        committedHeapSpace.setSize(maxSize);
        // Round down to power of two.
        minLargeObjectSize = Size.fromInt(Integer.highestOneBit(largeObjectsMinSizeOption.getValue()));
        log2FirstBinSize = Integer.numberOfTrailingZeros(minLargeObjectSize.toInt());
        minReclaimableSpace = Size.fromInt(freeChunkMinSizeOption.getValue());
        doImpreciseSweep = doImpreciseSweepOption.getValue();
        TraceSweep = MaxineVM.isDebug() ? traceSweepingOption.getValue() : false;
        smallObjectAllocator.initialize(start, initSize, minLargeObjectSize, minLargeObjectSize);
        useTLABBin = false;
        InspectableHeapInfo.init(committedHeapSpace);
    }

    public void reclaim(TricolorHeapMarker heapMarker) {
        darkMatter = Size.zero();
        for (int i = 0; i < freeChunkBins.length; i++) {
            freeChunkBins[i].reset();
        }
        totalFreeChunkSpace = 0;

        if (doImpreciseSweep) {
            darkMatter =  darkMatter.plus(heapMarker.impreciseSweep(this, minReclaimableSpace));
        } else {
            endOfLastVisitedObject = committedHeapSpace.start().asPointer();
            heapMarker.sweep(this);
        }
        useTLABBin = tlabFreeSpaceList.totalSize > 0;
        if (MaxineVM.isDebug()) {
            print();
        }
    }

    public void makeParsable() {
        smallObjectAllocator.makeParsable();
    }

    /**
     * Estimated free space left.
     * @return
     */
    public synchronized Size freeSpaceLeft() {
        return Size.fromLong(totalFreeChunkSpace).plus(smallObjectAllocator.freeSpaceLeft());
    }

    /**
     * Allocation of zero-filled memory, ready to use for object allocation.
     * @param size
     * @return
     */
    @INLINE
    public final Pointer allocate(Size size) {
        return smallObjectAllocator.allocateCleared(size);
    }

    @INLINE
    public final Pointer allocateTLAB(Size size) {
        return useTLABBin ? binAllocateTLAB(size).asPointer() : smallObjectAllocator.allocateTLAB(size);
    }
}
