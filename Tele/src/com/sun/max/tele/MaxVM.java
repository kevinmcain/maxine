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
package com.sun.max.tele;

import java.io.*;
import java.util.*;

import com.sun.max.collect.*;
import com.sun.max.memory.*;
import com.sun.max.tele.TeleViewModel.*;
import com.sun.max.tele.debug.*;
import com.sun.max.tele.debug.TeleBytecodeBreakpoint.*;
import com.sun.max.tele.debug.TeleProcess.*;
import com.sun.max.tele.field.*;
import com.sun.max.tele.interpreter.*;
import com.sun.max.tele.method.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.reference.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.prototype.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;


/**
 * The Inspectors access to an instance of the Maxine VM.
 * This could in the future be merged with the JDWP interface.
 *
 * @author Michael Van De Vanter
 */
public interface MaxVM {

    /**
     * @return the display name of the VM
     */
    String getName();

    /**
     * @return the display version of the VM
     */
    String getVersion();

    /**
     * @return a textual description of the VM
     */
    String getDescription();

    /**
     * @return description of the configuration for this instance of the VM.
     */
    VMConfiguration vmConfiguration();

    /**
     * @return size in bytes of a word in the VM.
     */
    int wordSize();

    /**
     * @return the boot image from which this VM instance was created.
     */
    BootImage bootImage();

    /**
     * @return the file from which the boot image for this VM was created.
     */
    File bootImageFile();

    File programFile();

    /**
     * Determines if this VM is read-only. The operations that try to write to the memory of a read-only VM
     * or change its execution state will result in a {@link TeleVMException}.
     */
    boolean isReadOnly();

    /**
     * Determines if the heap and code sections in the boot image have been relocated.
     */
    boolean isBootImageRelocated();

    /**
     * @return start location of the boot image in memory.
     */
    Pointer bootImageStart();

    /**
     * @return access to specific fields in VM heap objects.
     */
    TeleFields fields();

    /**
     * Activates two-way messaging with the VM, if not already active.
     * @return whether two-way messaging is operating.
     */
    boolean activateMessenger();

    /**
     * Status of garbage collection in the VM.
     * Assume for now that {@link Reference}s are invalid during a GC.
     * @return is a garbage collection underway in the VM?
     */
    boolean isInGC();

    /**
     * @return how much reliance is placed on the {@link TeleInterpreter} when
     * communicating with the VM (0=none, 1=some, etc)
     */
    int getInterpreterUseLevel();

    /**
     * Controls how much reliance is placed on the {@link TeleInterpreter} when
     * communicating with the VM.
     *
     * @param interpreterUseLevel  (0=none, 1=some, etc)
     */
    void setInterpreterUseLevel(int interpreterUseLevel);

    /**
     * @return current trace level in the VM
     * @see com.sun.max.program.Trace
     */
    int getVMTraceLevel();

    /**
     * Sets tracing level in the VM.
     *
     * @param newLevel
     * @see com.sun.max.program.Trace
     */
    void setVMTraceLevel(int newLevel);

    /**
     * @return current trace threshold in the VM
     * @see com.sun.max.program.Trace
     */
    long getVMTraceThreshold();

    /**
     * Sets tracing threshold in the VM.
     *
     * @param newLevel
     * @see com.sun.max.program.Trace
     */
    void setVMTraceThreshold(long newThreshold);

    /**
     * Visualizes a processor state registers in terms of flags.
     *
     * @param flags contents of a processor state register
     * @return a string interpreting the contents as a sequence of flags
     */
    String visualizeStateRegister(long flags);

    /**
     * Low-level read of a word from memory of the VM.
     */
    Word readWord(Address address);

    /**
     * Low-level read of a word from memory of the VM.
     */
    Word readWord(Address address, int offset);

    /**
     * Low-level read of bytes from memory of the VM.
     */
    void readFully(Address address, byte[] bytes);


    /**
     * Memory regions currently allocated remotely in the VM.
     *
     * <br>See:<ol>
     *   <li>{@link #memoryRegionContaining(Address)}</li>
     *   <li>{@link #contains(Address)}</li>
     *   </ol>
     *   <p>
     * The Maxine VM allocates memory regions for three purposes: <i>heap</i> (boot and dynamic regions),
     * <i>code</i> (boot and dynamic regions), and <i>threads</i> (stack and thread locals)
     *
     * <p><b>Heap</b><br>
     * Heap memory regions are allocated by the instance of {@link HeapScheme} built into the VM.
     * The VM boot image includes a special "boot heap" region.
     * <br>See also:<ol>
     *   <li>{@link HeapScheme}</li>
     *   <li>{@link #containsInHeap(Address)}</li>
     *   <li>{@link #teleBootHeapRegion()}</li>
     *   <li>{@link #teleHeapRegions()}</li>
     *   <li>{@link #isValidOrigin(Pointer)}</li>
     * </ol>
     *
     *
     * <p><b>Code</b><br>
     * Code memory regions are allocated by the singleton {@link CodeManager} in the VM,
     * whose local surrogate is an instance of {@link TeleCodeManager}.
     * <br>See also:<ol>
     *   <li>{@link CodeManager}</li>
     *   <li>{@link #containsInCode(Address)}</li>
     *   <li>{@link #teleBootCodeRegion()}</li>
     *   <li>{@link #teleCodeRegions()}</li>
     * </ol>
     *
     * <p><b>Threads</b><br>
     * Each thread is allocated a memory region for the thread's stack and also
     * thread-local storage.
     * <br>See also:<ol>
     *   <li>{@link TeleProcess}</li>
     *   <li>{@link TeleNativeStack}</li>
     *   <li>{@link #containsInThread(Address)}</li>
     * </ol>
     *
     * @return all allocated memory regions in the VM.
     */
    IndexedSequence<MemoryRegion> memoryRegions();

    /**
     * @param address a memory location in the VM
     * @return the allocated {@link MemoryRegion} containing the address, null if not in any known region.
     * @see #memoryRegions()
     */
    MemoryRegion memoryRegionContaining(Address address);

    /**
     * @param address a memory location in the VM.
     * @return whether the location is either in the object heap, the code
     *         regions, or a stack region of the VM.
     * @see #memoryRegions()
     */
    boolean contains(Address address);

    /**
     * @param address a memory address in the VM.
     * @return is the address within an allocated heap {@link MemoryRegion}?
     * @see #containsInDynamicHeap(Address)
     * @see #memoryRegions()
     */
    boolean containsInHeap(Address address);

    /**
     * @param address a memory address in the VM.
     * @return is the address within a dynamically allocated heap {@link MemoryRegion}?
     * @see #containsInHeap(Address)
     * @see #memoryRegions()
     */
    boolean containsInDynamicHeap(Address address);

    /**
     * @return surrogate for the special heap {@link MemoryRegion} in the {@link BootImage} of the VM.
     * @see #teleHeapRegions()
     * @see #memoryRegions()
     */
    TeleRuntimeMemoryRegion teleBootHeapRegion();

    /**
     * @return surrogates for all {@link MemoryRegion}s in the {@link Heap} of the VM.
     * Sorted in order of allocation.  Does not include the boot heap region.
     * @see #teleBootHeapRegion()
     * @see #memoryRegions()
     */
    IndexedSequence<TeleRuntimeMemoryRegion> teleHeapRegions();

    /**
     * @param address a memory address in the VM.
     * @return is the address within an allocated code {@link MemoryRegion}?
     * @see #memoryRegions()
     */
    boolean containsInCode(Address address);

    /**
     * @return surrogate for the special code {@link MemoryRegion} in the {@link BootImage} of the VM.
     * @see #teleCodeRegions()
     * @see #memoryRegions()
     */
    TeleCodeRegion teleBootCodeRegion();

    /**
     * @return surrogates for all code {@link MemoryRegion}s in the VM, including those not yet allocated.
     * Sorted in order of allocation.  Does not include the boot code region.
     * @see #teleBootCodeRegion()
     * @see #memoryRegions()
     */
    IndexedSequence<TeleCodeRegion> teleCodeRegions();

    /**
     * @param address a memory address in the VM.
     * @return is the address within a {@link MemoryRegion} allocated to a thread?
     * @see #memoryRegions()
     * @see TeleNativeStack
     */
    boolean containsInThread(Address address);

    /**
     * @param origin current absolute location of the beginning of a heap object's memory in the VM,
     * subject to relocation by GC.
     * @return a {@link Reference} to the object.
     */
    Reference originToReference(final Pointer origin);

    /**
     * @param reference a heap object in the VM.
     * @return a pointer to the current absolute memory  location for the object.
     */
    Pointer referenceToCell(Reference reference);

    /**
     * @param cell a pointer to the current absolute memory  location for a heap object in the VM.
     * @return a reference to the object.
     */
    Reference cellToReference(Pointer cell);

    /**
     * @return a reference to the {@link ClassRegistry} in the boot heap of the VM.
     */
    Reference bootClassRegistryReference();

    /**
     * @param origin an absolute memory location in the VM.
     * @return whether there is a heap object with that origin.
     */
    boolean isValidOrigin(Pointer origin);

    /**
     * @param reference a {@link Reference} to memory in the VM.
     * @return whether there is a heap object at that location.
     */
    boolean isValidReference(Reference reference);

    /**
     * @param word contents of a memory word from the VM.
     * @return the word interpreted as a memory location, wrapped in a Reference.
     */
    Reference wordToReference(Word word);

    /**
     * @param reference a {@link Reference} to memory in the VM.
     * @return a {@link Value} that wraps a {@link Reference} to a memory location in the VM.
     */
    ReferenceValue createReferenceValue(Reference reference);

    /**
     * Gets a canonical local {@classActor} corresponding to the type of a heap object in the targetVM, creating one if
     * needed by loading the class using the {@link PrototypeClassLoader#PROTOTYPE_CLASS_LOADER} from either the
     * classpath, or if not found on the classpath, by copying the classfile from the VM.
     *
     * @param objectReference An {@link Object} in  VM heap.
     * @return Local {@link ClassActor} representing the type of the object.
     * @throws InvalidReferenceException
     */
    ClassActor makeClassActorForTypeOf(Reference objectReference)  throws InvalidReferenceException;

    /**
     * Fetches a primitive value from an array in the memory of the VM.
     *
     * @param kind identifies one of the basic VM value types
     * @param reference memory location in the VM of an array origin
     * @param index offset into the array
     * @return a value of the specified kind
     * @throws InvalidReferenceException
     */
    Value getElementValue(Kind kind, Reference reference, int index) throws InvalidReferenceException;

    /**
     * Factory for TeleObjects, local surrogates for objects in the heap of the VM, which
     * provide access to object contents and specialized methods that encapsulate
     * knowledge of the heap's design.
     * Special subclasses are created for Maxine implementation objects of special interest,
     *  and for other objects for which special treatment is needed.
     *
     * @param reference a heap object in the VM;
     * @return a canonical local surrogate for the object, null for the distinguished zero {@link Reference}.
     */
    TeleObject makeTeleObject(Reference reference);

    /**
     * @param id an id assigned to each heap object in the VM as needed, unique for the duration of a VM execution.
     * @return an accessor for the specified heap object.
     */
    TeleObject findObjectByOID(long id);

    /**
     * @param id  Class ID of a {@link ClassActor} in the VM.
     * @return surrogate for the {@link ClassActor} in the VM, null if not known.
     * @see ClassActor
     */
    TeleClassActor findTeleClassActor(int id);

    /**
     * @param typeDescriptor A local {@link TypeDescriptor}.
     * @return surrogate for the equivalent {@link ClassActor} in the VM, null if not known.
     * @see ClassActor
     */
    TeleClassActor findTeleClassActor(TypeDescriptor typeDescriptor);

    /**
     * @param type a local class instance
     * @return surrogate for the equivalent {@link ClassActor} in the VM, null if not known.
     */
    TeleClassActor findTeleClassActor(Class type);

    /**
     * @return  {@link TypeDescriptor}s for all classes loaded in the VM.
     */
    Set<TypeDescriptor> typeDescriptors();


    /**
     * @return an ordered set of {@link TypeDescriptor}s for classes loaded in
     *         the VM, plus classes found on the class path.
     */
    Iterable<TypeDescriptor> loadableTypeDescriptors();

    /**
     * Updates the set of types that are available by scanning the class path. This
     * scan will be performed automatically the first time
     * {@link #loadableTypeDescriptors()} is called. However, it should also be
     * performed any time the set of classes available on the class path may
     * have changed.
     */
    void updateLoadableTypeDescriptorsFromClasspath();

    /**
     * Get the TeleTargetMethod, newly created if needed, that contains a given address in the VM.
     *
     * @param address address in target code memory in the VM
     * @return a possibly newly created target method whose code contains the address.
     */
    TeleTargetMethod makeTeleTargetMethod(Address address);

    /**
     * Get the TeleRuntimeStub, newly created if needed, that contains a given address in the VM.
     *
     * @param address address in target code memory in the VM
     * @return a possibly newly created runtime stub whose code contains the address.
     */
    TeleRuntimeStub makeTeleRuntimeStub(Address address);

    /**
     * Create a new TeleNativeTargetRoutine for a block of native code in the VM that has not yet been registered.
     *
     * @param codeStart starting address of the code in VM memory
     * @param codeSize presumed size of the code
     * @param name a name to be assigned to the block of code; a simple address-based name used if null.
     * @return a newly created TeleNativeTargetRoutine
     */
    TeleNativeTargetRoutine createTeleNativeTargetRoutine(Address codeStart, Size codeSize, String name);

    /**
     * Gets the existing TeleTargetRoutine, if registered, that contains a given address in the VM, possibly filtering by subtype.
     *
     * @param <TeleTargetRoutine_Type> the type of the requested TeleTargetRoutine
     * @param teleTargetRoutineType the {@link Class} instance representing {@code TeleTargetRoutine_Type}
     * @param address the look up address
     * @return the tele target routine of type {@code TeleTargetRoutine_Type} in this registry that contains {@code
     *         address} or null if no such tele target routine of the requested type exists
     */
    <TeleTargetRoutine_Type extends TeleTargetRoutine> TeleTargetRoutine_Type findTeleTargetRoutine(Class<TeleTargetRoutine_Type> teleTargetRoutineType, Address address);

    /**
     * Finds the remote {@link MethodActor} corresponding to a local one.
     *
     * @param <TeleMethodActor_Type> the type of the requested TeleMethodActor
     * @param teleMethodActorType the {@link Class} instance representing {@code TeleMethodActor_Type}
     * @param methodActor the local {@link MethodActor} describing the method
     * @return surrogate for the {@link MethodActor} of type {@code TeleMethodActor_Type} in the VM.
     */
    <TeleMethodActor_Type extends TeleMethodActor> TeleMethodActor_Type findTeleMethodActor(Class<TeleMethodActor_Type> teleMethodActorType, MethodActor methodActor);

    /**
     * Writes a textual summary describing all  instances of {@link TeleTargetRoutine} known to the VM.
     */
    void describeTeleTargetRoutines(PrintStream printStream);

    /**
     * @return VM process state
     */
    State state();

    /**
     * @param listener will be notified of changes to {@link state()}.
     */
    void addStateListener(StateTransitionListener listener);

    /**
     * @return the number of discrete execution steps of the VM process since it was created.
     */
    long epoch();

    /**
     * @return a collection of all current threads in the VM, ordered by threadID.
     */
    IterableWithLength<TeleNativeThread> threads();

    //TODO (mlvdv) bogus
    /**
     * @return threads created since the previous {@link #epoch()}.
     */
    IterableWithLength<TeleNativeThread> recentlyCreatedThreads();

    //TODO (mlvdv) bogus
    /**
     * @return threads died since the previous {@link #epoch()}.
     */
    IterableWithLength<TeleNativeThread> recentlyDiedThreads();

    /**
     * @param threadID
     * @return the thread associated with the id, null if none exists.
     */
    TeleNativeThread getThread(long threadID);

    /**
     * @param address an address in the VM
     * @return thread whose stack contains the address, null if none.
     */
    TeleNativeThread threadContaining(Address address);

    /**
     * Creates a code location in the VM based on a memory address,
     * and thus anchored at a specific compilation.
     *
     * @param address an address in the VM, presumably in a body of target code.
     * @return a new location
     */
    TeleCodeLocation createCodeLocation(Address address);

    /**
     * Creates a code location in the VM based on method bytecode position, with
     * compiled location unspecified.
     *
     * @param teleClassMethodActor surrogate for a {@link ClassMethodActor} in the VM that identifies a method.
     * @param position offset into the method's bytecodes
     * @return a new location
     */
    TeleCodeLocation createCodeLocation(TeleClassMethodActor teleClassMethodActor, int position);

    /**
     * Creates a code location in the VM based on both a bytecode position and a
     * memory address that (presumably) points to the corresponding location in
     * a compilation of the method.
     *
     * @param address an address in the VM, presumably in a body of target code.
     * @param teleClassMethodActor surrogate for a {@link ClassMethodActor} in the VM that identifies a method.
     * @param position offset into the method's bytecodes
     * @return a new location
     */
    TeleCodeLocation createCodeLocation(Address address, TeleClassMethodActor teleClassMethodActor, int position);

    /**
     * Adds a listener for breakpoint changes in the VM.
     *
     * @param listener will be notified whenever breakpoints in VM change.
     */
    void addBreakpointListener(Listener listener);

    /**
     * All existing target code breakpoints.
     *
     * @return all existing target code breakpoints in the VM, ignoring transients.
     * Modification safe against breakpoint removal.
     */
    Iterable<TeleTargetBreakpoint> targetBreakpoints();

    /**
     * @return the number of target code breakpoints in the VM, ignoring transients
     */
    int targetBreakpointCount();

    /**
     * Gets a target code breakpoint in the VM, newly created if needed.
     *
     * @param address a code address in the VM.
     * @return a possibly new, non-transient, target code breakpoint at the address.
     */
    TeleTargetBreakpoint makeTargetBreakpoint(Address address);

    /**
     * Finds a target code breakpoint in the VM.
     *
     * @param address an address in the VM.
     * @return an ordinary, non-transient target code breakpoint at the address in VM, null if none exists.
     */
    TeleTargetBreakpoint getTargetBreakpoint(Address address);

    /**
     * All existing bytecode breakpoints.
     *
     * @return all existing bytecode breakpoints in the VM.
     * Modification safe against breakpoint removal.
     */
    Iterable<TeleBytecodeBreakpoint> bytecodeBreakpoints();

    /**
     * @return the number of bytecode breakpoints in the VM.
     */
    int bytecodeBreakpointCount();

    /**
     * Gets a bytecode breakpoint in the VM, newly created if needed.
     *
     * @param key description of a bytecode position in a method
     * @return a possibly new, non-transient, enabled bytecode breakpoint at the location.
     */
    TeleBytecodeBreakpoint makeBytecodeBreakpoint(Key key);

    /**
     * Finds a bytecode breakpoint in the VM.
     *
     * @param key description of a bytecode position in a method
     * @return an ordinary, non-transient bytecode breakpoint, null if doesn't exist
     */
    TeleBytecodeBreakpoint getBytecodeBreakpoint(Key key);

    /**
     * Creates a new memory watchpoint in the VM.
     *
     * @param memoryRegion a memory region in the VM
     * @return a possibly new memory watchpoint
     */
    TeleWatchpoint makeWatchpoint(MemoryRegion memoryRegion);

    /**
     * Sets debugging trace level for the transport
     * mechanism that communicates with the VM.
     *
     * @param level new level: 0=none, 1=some, etc
     */
    void setTransportDebugLevel(int level);

    /**
     * Debugging trace level for the transport
     * mechanism that communicates with the VM.
     *
     * @return current level: 0=none, 1=some, etc
     */
    int transportDebugLevel();

    /**
     * Relocates the boot image, assuming that the inspector was invoked
     * with the option {@link MaxineInspector#suspendingBeforeRelocating()} set.
     *
     * @throws IOException
     */
    void advanceToJavaEntryPoint() throws IOException;

    /**
     * Interprets a method invocation in the context of the VM.
     *
     * @param classMethodActor method to interpret
     * @param arguments method arguments encapsulated as values
     * @return result of method invocation wrapped as a Value
     * @throws TeleInterpreterException  if an uncaught exception occurs during execution of the method
     */
    Value interpretMethod(ClassMethodActor classMethodActor, Value... arguments) throws TeleInterpreterException;

    /**
     * Resumes execution of the VM.
     *
     * @param synchronous should the call wait for the execution to complete.
     * @param disableBreakpoints should existing breakpoints be disabled for the duration of the execution.
     * @throws InvalidProcessRequestException execution not permissible in current VM state.
     * @throws OSExecutionRequestException execution failed in OS.
     */
    void resume(final boolean synchronous, final boolean disableBreakpoints) throws InvalidProcessRequestException, OSExecutionRequestException;

    /**
     * Single steps a thread in the VM.
     *
     * @param thread a thread in the VM
     * @param synchronous should the call wait for the execution to complete.
     * @throws InvalidProcessRequestException execution not permissible in current VM state.
     * @throws OSExecutionRequestException execution failed in OS.
     */
    void singleStep(final TeleNativeThread thread, boolean synchronous) throws InvalidProcessRequestException, OSExecutionRequestException;

    /**
     * Single steps a thread in the VM; if the instruction is a call, then resume VM execution until call returns.
     *
     * @param thread a thread in the VM.
     * @param synchronous should the call wait for the execution to complete.
     * @param disableBreakpoints should existing breakpoints be disabled for the duration of the execution.
     * @throws InvalidProcessRequestException execution not permissible in current VM state.
     * @throws OSExecutionRequestException execution failed in OS.
     */
    void stepOver(final TeleNativeThread thread, boolean synchronous, final boolean disableBreakpoints) throws InvalidProcessRequestException, OSExecutionRequestException;

    /**
     * Resumes execution of the VM with a temporary breakpoint set.
     *
     * @param instructionPointer location where temporary breakpoint should be set.
     * @param synchronous should the call wait for the execution to complete.
     * @param disableBreakpoints should existing breakpoints be disabled for the duration of the execution.
     * @throws OSExecutionRequestException execution failed in OS.
     * @throws InvalidProcessRequestException execution not permissible in current VM state.
     */
    void runToInstruction(final Address instructionPointer, final boolean synchronous, final boolean disableBreakpoints) throws OSExecutionRequestException, InvalidProcessRequestException;

    /**
     * Pauses the running VM.
     *
     * @throws InvalidProcessRequestException execution not permissible in current VM state.
     * @throws OSExecutionRequestException execution failed in OS.
     */
    void pause() throws InvalidProcessRequestException, OSExecutionRequestException;

    /**
     * Shuts down the VM process.
     */
    void terminate() throws Exception;

    /**
     * Uses the configured source path in the VM to search for a source file corresponding to a class.
     *
     * @param classActor  the class for which a source file is to be found
     * @return the source file corresponding to {@code classActor} or null if so such source file can be found
     */
    File findJavaSourceFile(ClassActor classActor);

    /**
     * Runs commands from a specified file, supporting only "break <method signature>" commands at present.
     *
     * @param fileName name of a file containing commands.
     */
    void executeCommandsFromFile(String fileName);

}

