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
package com.sun.max.vm.compiler.eir;

import com.sun.max.collect.*;
import com.sun.max.memory.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.eir.allocate.*;
import com.sun.max.vm.compiler.ir.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * @author Bernd Mathiske
 */
public abstract class EirMethodGeneration {

    private final EirGenerator _eirGenerator;

    public EirGenerator eirGenerator() {
        return _eirGenerator;
    }

    private final EirABI _abi;

    public EirABI abi() {
        return _abi;
    }

    private final MemoryModel _memoryModel;

    public MemoryModel memoryModel() {
        return _memoryModel;
    }

    private final EirValue[] _integerRegisterRoleValues;

    public EirValue integerRegisterRoleValue(VMRegister.Role registerRole) {
        return _integerRegisterRoleValues[registerRole.ordinal()];
    }

    private final EirValue[] _floatingPointRegisterRoleValues;

    public EirValue floatingPointRegisterRoleValue(VMRegister.Role registerRole) {
        return _floatingPointRegisterRoleValues[registerRole.ordinal()];
    }

    private EirVariable[] _registerVariables;

    public EirVariable makeRegisterVariable(EirRegister register) {
        final int index = register.serial();
        if (_registerVariables[index] == null) {
            _registerVariables[index] = createEirVariable(register.kind());
            //_registerVariables[index].fixLocation(register);
        }
        return _registerVariables[index];
    }

    protected EirMethodGeneration(EirGenerator eirGenerator, EirABI abi, boolean isTemplate) {
        _eirGenerator = eirGenerator;
        _abi = abi;
        _memoryModel = eirGenerator.compilerScheme().vmConfiguration().platform().processorKind().processorModel().memoryModel();
        _isTemplate = isTemplate;

        _integerRegisterRoleValues = new EirValue[VMRegister.Role.VALUES.length()];
        _floatingPointRegisterRoleValues = new EirValue[VMRegister.Role.VALUES.length()];
        for (VMRegister.Role role : VMRegister.Role.VALUES)  {
            _integerRegisterRoleValues[role.ordinal()] = preallocate(abi.integerRegisterActingAs(role), role.kind());
            _floatingPointRegisterRoleValues[role.ordinal()] = preallocate(abi.floatingPointRegisterActingAs(role), role.kind());
        }

        _registerVariables = new EirVariable[eirGenerator.eirABIsScheme().registerPool().length()];
    }

    public abstract MethodActor classMethodActor();

    public final void notifyBeforeTransformation(Object context, Object transform) {
        _eirGenerator.notifyBeforeTransformation(eirMethod(), context, transform);
    }

    public final void notifyAfterTransformation(Object context, Object transform) {
        _eirGenerator.notifyAfterTransformation(eirMethod(), context, transform);
    }

    private AppendableIndexedSequence<EirBlock> _eirBlocks = new ArrayListSequence<EirBlock>();

    private AppendableIndexedSequence<EirBlock> _result;

    public IndexedSequence<EirBlock> eirBlocks() {
        if (_result != null) {
            return _result;
        }
        return _eirBlocks;
    }

    private Pool<EirBlock> _eirBlockPool;

    public Pool<EirBlock> eirBlockPool() {
        return _eirBlockPool;
    }

    protected abstract EirMethod eirMethod();

    public EirBlock createEirBlock(IrBlock.Role role) {
        final int serial = _eirBlocks.length();
        final EirBlock eirBlock = new EirBlock(eirMethod(), role, serial);
        _eirBlocks.append(eirBlock);
        return eirBlock;
    }

    private final EirLiteralPool _literalPool = new EirLiteralPool();

    public EirLiteralPool literalPool() {
        return _literalPool;
    }

    /**
     * Slots in the frame of the method being generated. That is, the offsets of these slots are relative SP after it
     * has been adjusted upon entry to the method.
     */
    private final AppendableIndexedSequence<EirStackSlot> _localStackSlots = new ArrayListSequence<EirStackSlot>();

    /**
     * Slots in the frame of the caller of method being generated. That is, the offsets of these slots are relative to SP
     * before it has been adjusted upon entry to the method.
     */
    private final AppendableIndexedSequence<EirStackSlot> _parameterStackSlots = new ArrayListSequence<EirStackSlot>();

    public Sequence<EirStackSlot> allocatedStackSlots() {
        return _localStackSlots;
    }

    public EirStackSlot allocateSpillStackSlot() {
        final EirStackSlot stackSlot = new EirStackSlot(EirStackSlot.Purpose.LOCAL, _localStackSlots.length() * abi().stackSlotSize());
        _localStackSlots.append(stackSlot);
        return stackSlot;
    }

    /**
     * Gets the size of the stack frame currently allocated used for local variables.
     */
    public int frameSize() {
        return abi().frameSize(_localStackSlots.length());
    }

    public EirStackSlot localStackSlotFromIndex(int index) {
        if (index >= _localStackSlots.length()) {
            // Fill in the missing stack slots
            for (int i = _localStackSlots.length(); i <= index; i++) {
                _localStackSlots.append(new EirStackSlot(EirStackSlot.Purpose.LOCAL, i * abi().stackSlotSize()));
            }
        }
        return _localStackSlots.get(index);
    }

    public EirStackSlot spillStackSlotFromOffset(int offset) {
        return localStackSlotFromIndex(offset / abi().stackSlotSize());
    }

    private EirStackSlot canonicalizeStackSlot(EirStackSlot stackSlot, AppendableIndexedSequence<EirStackSlot> slots) {
        final int index = stackSlot.offset() / abi().stackSlotSize();
        if (index >= slots.length()) {
            // Fill in the missing stack slots
            for (int i = slots.length(); i < index; i++) {
                slots.append(new EirStackSlot(stackSlot.purpose(), i * abi().stackSlotSize()));
            }
            slots.append(stackSlot);
            return stackSlot;
        }
        return slots.get(index);
    }

    /**
     * Gets the canonical instance for a stack slot at a given offset.
     *
     * @param stackSlot specifies a stack slot offset
     * @return the canonical object representing the stack slot at {@code stackSlot.offset()}
     */
    public EirStackSlot canonicalizeStackSlot(EirStackSlot stackSlot) {
        final AppendableIndexedSequence<EirStackSlot> stackSlots = stackSlot.purpose() == EirStackSlot.Purpose.PARAMETER ? _parameterStackSlots : _localStackSlots;
        return canonicalizeStackSlot(stackSlot, stackSlots);
    }

    private final GrowableMapping<EirLocation, EirValue> _locationToValue = HashMapping.createEqualityMapping();

    public EirValue preallocate(EirLocation location, Kind kind) {
        if (location == null) {
            return null;
        }
        EirValue value = _locationToValue.get(location);
        if (value == null) {
            value = new EirValue.Preallocated(location, kind);
        }
        return value;
    }

    private final AppendableSequence<EirVariable> _variables = new ArrayListSequence<EirVariable>();

    private Pool<EirVariable> _variablePool;

    /**
     * Gets the {@linkplain #createEirVariable(Kind) allocated variables} as a pool.
     *
     * <b>This method must not be called before all variable allocation has been performed. That is, there must not be a
     * call to {@link EirMethodGeneration#createEirVariable(Kind)} after a call to this method.</b>
     */
    public Pool<EirVariable> variablePool() {
        if (_variablePool == null) {
            _variablePool = new ArrayPool<EirVariable>(Sequence.Static.toArray(_variables, EirVariable.class));
        }
        return _variablePool;
    }

    /**
     * Gets the set of variables that have been allocated.
     */
    public Sequence<EirVariable> variables() {
        return _variables;
    }

    public EirVariable createEirVariable(Kind kind) {
        assert _variablePool == null : "can't allocate EIR variables once a variable pool exists";
        final int serial = _variables.length();
        final EirVariable eirVariable = new EirVariable(eirGenerator().eirKind(kind), serial);
        _variables.append(eirVariable);
        return eirVariable;
    }

    private final AppendableSequence<EirConstant> _constants = new ArrayListSequence<EirConstant>();

    public Sequence<EirConstant> constants() {
        return _constants;
    }

    public EirConstant createEirConstant(Value value) {
        final EirConstant constant = (value.kind() == Kind.REFERENCE) ? new EirConstant.Reference(value, _constants.length()) : new EirConstant(value);
        _constants.append(constant);
        return constant;
    }

    public EirMethodValue createEirMethodValue(ClassMethodActor classMethodActor) {
        return new EirMethodValue(classMethodActor);
    }

    public EirValue stackPointerVariable() {
        return integerRegisterRoleValue(VMRegister.Role.ABI_STACK_POINTER);
    }

    public EirValue framePointerVariable() {
        return integerRegisterRoleValue(VMRegister.Role.ABI_FRAME_POINTER);
    }

    public EirValue safepointLatchVariable() {
        return integerRegisterRoleValue(VMRegister.Role.SAFEPOINT_LATCH);
    }

    public abstract EirCall createCall(EirBlock eirBlock, EirABI abi, EirValue result, EirLocation resultLocation,
                    EirValue function, EirValue[] arguments, EirLocation[] argumentLocations);

    public abstract EirCall createRuntimeCall(EirBlock eirBlock, EirABI abi, EirValue result, EirLocation resultLocation,
                                               EirValue function, EirValue[] arguments, EirLocation[] argumentLocations);

    public abstract EirInstruction createAssignment(EirBlock eirBlock, Kind kind, EirValue destination, EirValue source);

    public abstract EirSafepoint createSafepoint(EirBlock eirBlock);

    public EirGuardpoint createGuardpoint(EirBlock eirBlock) {
        return new EirGuardpoint(eirBlock);
    }

    protected abstract EirAllocator createAllocator(EirMethodGeneration methodGeneration);

    protected abstract EirEpilogue createEpilogue(EirBlock eirBlock);

    // Used when one wants generated code to perform a jump at the end of
    // the generated code region instead of a return instruction. This is most
    // useful for generating templates of a JIT or an interpreter.
    private EirBlock _eirEpilogueBlock;

    public EirBlock eirEpilogueBlock() {
        if (_eirEpilogueBlock == null) {
            _eirEpilogueBlock = createEirBlock(IrBlock.Role.NORMAL);
        }
        return _eirEpilogueBlock;
    }

    private EirEpilogue _eirEpilogue;

    public void addResultValue(EirValue resultValue) {
        makeEpilogue();
        _eirEpilogue.addResultValue(resultValue);
    }

    public void addEpilogueUse(EirValue useValue) {
        makeEpilogue();
        _eirEpilogue.addUse(useValue);
    }

    public abstract EirEpilogue createEpilogueAndReturn(EirBlock eirBlock);

    public EirBlock makeEpilogue() {
        if (_eirEpilogue == null) {
            _eirEpilogue = createEpilogueAndReturn(eirEpilogueBlock());
        }
        return eirEpilogueBlock();
    }

    private EirBlock selectSuccessor(EirBlock block, PoolSet<EirBlock> rest) {
        final EirInstruction<?, ?> instruction = block.instructions().last();
        return instruction.selectSuccessorBlock(rest);
    }

    private EirBlock gatherUnconditionalSuccessors(EirBlock eirBlock, PoolSet<EirBlock> rest, AppendableSequence<EirBlock> result) {
        EirBlock block = eirBlock;
        while (rest.contains(block)) {
            rest.remove(block);
            result.append(block);
            if (block.instructions().last() instanceof EirJump) {
                final EirJump jump = (EirJump) block.instructions().last();
                block = jump.target();
            } else {
                return block;
            }
        }
        return selectSuccessor(block, rest);
    }

    private EirBlock selectUnconditionalPredecessor(EirBlock block, final PoolSet<EirBlock> rest) {
        for (EirBlock predecessor : block.predecessors()) {
            if (rest.contains(predecessor)) {
                if (predecessor.instructions().last() instanceof EirJump) {
                    return predecessor;
                }
            }
        }
        return null;
    }

    private void gatherUnconditionalPredecessors(EirBlock eirBlock, PoolSet<EirBlock> rest, PrependableSequence<EirBlock> result) {
        EirBlock block = eirBlock;
        while (rest.contains(eirBlock)) {
            result.prepend(block);
            rest.remove(block);
            block = selectUnconditionalPredecessor(block, rest);
        }
    }

    private void gatherSuccessors(EirBlock eirBlock, PoolSet<EirBlock> rest, AppendableSequence<EirBlock> result) {
        EirBlock block = eirBlock;
        while (rest.contains(block)) {
            rest.remove(block);
            result.append(block);
            block = selectSuccessor(block, rest);
        }
    }

    protected void rearrangeBlocks() {
        final int eirBlocksLength = _eirBlocks.length();
        final Pool<EirBlock> eirBlockPool = new IndexedSequencePool<EirBlock>(_eirBlocks);
        final PoolSet<EirBlock> rest = PoolSet.noneOf(eirBlockPool);
        _result = new ArrayListSequence<EirBlock>();
        rest.addAll();

        final EirBlock head = gatherUnconditionalSuccessors(_eirBlocks.first(), rest, _result);

        PrependableSequence<EirBlock> tail = null;
        if (_eirEpilogueBlock != null) {
            tail = new ArrayListSequence<EirBlock>();
            gatherUnconditionalPredecessors(_eirEpilogueBlock, rest, tail);
        }
        gatherSuccessors(head, rest, _result);

        for (EirBlock block : _eirBlocks) {
            if (block.role() == IrBlock.Role.EXCEPTION_DISPATCHER) {
                final EirBlock last = gatherUnconditionalSuccessors(block, rest, _result);
                gatherSuccessors(last, rest, _result);
            }
        }
        for (EirBlock block : _eirBlocks) {
            if (block.role() != IrBlock.Role.EXCEPTION_DISPATCHER) {
                final EirBlock last = gatherUnconditionalSuccessors(block, rest, _result);
                gatherSuccessors(last, rest, _result);
            }
        }

        if (tail != null) {
            AppendableSequence.Static.appendAll(_result, tail);
        }

        int serial = 0;
        for (EirBlock block : _result) {
            block.setSerial(serial);
            serial++;
        }
        assert eirBlocksLength == _eirBlocks.length();
        _eirBlocks = null;
    }

    private final boolean _isTemplate;

    public boolean isTemplate() {
        return _isTemplate;
    }

}
