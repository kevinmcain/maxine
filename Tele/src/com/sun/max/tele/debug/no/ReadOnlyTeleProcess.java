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
package com.sun.max.tele.debug.no;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel.*;

import com.sun.max.collect.*;
import com.sun.max.platform.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.prototype.*;
import com.sun.max.vm.prototype.BootImage.*;

/**
 * A null process that "contains" the boot image for inspection, as if it were a {@link TeleVM}.
 *
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public final class ReadOnlyTeleProcess extends TeleProcess {

    private final DataAccess dataAccess;
    private final Pointer heap;

    @Override
    public DataAccess dataAccess() {
        return dataAccess;
    }

    public ReadOnlyTeleProcess(TeleVM teleVM, Platform platform, File programFile) throws BootImageException {
        super(teleVM, platform, ProcessState.NO_PROCESS);
        heap = Pointer.zero();
        try {
            dataAccess = map(teleVM.bootImageFile(), teleVM.bootImage());
        } catch (IOException ioException) {
            throw new BootImageException("Error mapping in boot image", ioException);
        }
    }

    public Pointer heap() {
        return heap;
    }

    /**
     * Maps the heap and code sections of the boot image in a given file into memory.
     *
     * @param bootImageFile the file containing the heap and code sections to map into memory
     * @return a {@link DataAccess} object that can be used to access the mapped sections
     * @throws IOException if an IO error occurs while performing the memory mapping
     */
    public DataAccess map(File bootImageFile, BootImage bootImage) throws IOException {
        final RandomAccessFile randomAccessFile = new RandomAccessFile(bootImageFile, "r");
        final Header header = bootImage.header;
        int heapOffset = bootImage.heapOffset();
        int heapAndCodeSize = header.heapSize + header.codeSize;
        final MappedByteBuffer bootImageBuffer = randomAccessFile.getChannel().map(MapMode.READ_ONLY, heapOffset, heapAndCodeSize);
        bootImageBuffer.order(bootImage.vmConfiguration.platform().processorKind.dataModel.endianness.asByteOrder());
        randomAccessFile.close();
        return new MappedByteBufferDataAccess(bootImageBuffer, heap, header.wordWidth());
    }

    private static final String FAIL_MESSAGE = "Attempt to run/write/modify a read-only bootimage VM with no live process";

    @Override
    protected void gatherThreads(AppendableSequence<TeleNativeThread> threads) {
        ProgramError.unexpected(FAIL_MESSAGE);
    }

    @Override
    protected TeleNativeThread createTeleNativeThread(int id, long handle, long stackBase, long stackSize) {
        ProgramError.unexpected(FAIL_MESSAGE);
        return null;
    }

    @Override
    protected int read0(Address address, ByteBuffer buffer, int offset, int length) {
        return dataAccess.read(address, buffer, offset, length);
    }

    @Override
    protected int write0(ByteBuffer buffer, int offset, int length, Address address) {
        ProgramError.unexpected(FAIL_MESSAGE);
        return 0;
    }

    @Override
    protected void kill() throws OSExecutionRequestException {
        ProgramError.unexpected(FAIL_MESSAGE);
    }

    @Override
    protected void resume() throws OSExecutionRequestException {
        ProgramError.unexpected(FAIL_MESSAGE);
    }

    @Override
    protected void suspend() throws OSExecutionRequestException {
        ProgramError.unexpected(FAIL_MESSAGE);
    }

    @Override
    protected boolean waitUntilStopped() {
        ProgramError.unexpected(FAIL_MESSAGE);
        return false;
    }
}
