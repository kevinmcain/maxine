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
package com.sun.max.ins.debug;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.table.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.*;
import com.sun.max.tele.method.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;


/**
 * A table specialized for displaying the breakpoints in the {@link TeleVM}.
 *
 * @author Michael Van De Vanter
 */
public final class BreakpointsTable extends InspectorTable {

    private final Set<BreakpointData> _breakpoints = new TreeSet<BreakpointData>();

    private final BreakpointsTableModel _model;
    private BreakpointsColumnModel _columnModel;
    private final TableColumn[] _columns;

    public BreakpointsTable(Inspection inspection) {
        super(inspection);
        _model = new BreakpointsTableModel();
        _columns = new TableColumn[BreakpointsColumnKind.VALUES.length()];
        _columnModel = new BreakpointsColumnModel();

        setModel(_model);
        setColumnModel(_columnModel);
        setShowHorizontalLines(style().defaultTableShowHorizontalLines());
        setShowVerticalLines(style().defaultTableShowVerticalLines());
        setIntercellSpacing(style().defaultTableIntercellSpacing());
        setRowHeight(style().defaultTableRowHeight());
        setRowSelectionAllowed(true);
        setColumnSelectionAllowed(false);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        addMouseListener(new BreakpointInspectorMouseClickAdapter(inspection()));

        refresh(teleVM().epoch(), true);
        JTableColumnResizer.adjustColumnPreferredWidths(this);
    }

    private long _lastRefreshEpoch = -1;

    public void refresh(long epoch, boolean force) {
        if (epoch > _lastRefreshEpoch || force) {
            _lastRefreshEpoch = epoch;
            _model.refresh();
            for (TableColumn column : _columns) {
                final Prober prober = (Prober) column.getCellRenderer();
                if (prober != null) {
                    prober.refresh(epoch, force);
                }
            }
        }
    }

    public void redisplay() {
        for (TableColumn column : _columns) {
            final Prober prober = (Prober) column.getCellRenderer();
            prober.redisplay();
        }
        invalidate();
        repaint();
    }

    @Override
    protected JTableHeader createDefaultTableHeader() {
        return new JTableHeader(_columnModel) {
            @Override
            public String getToolTipText(MouseEvent mouseEvent) {
                final Point p = mouseEvent.getPoint();
                final int index = _columnModel.getColumnIndexAtX(p.x);
                final int modelIndex = _columnModel.getColumn(index).getModelIndex();
                return BreakpointsColumnKind.VALUES.get(modelIndex).toolTipText();
            }
        };
    }

    BreakpointsViewPreferences preferences() {
        return _columnModel.localPreferences();
    }

    private final class BreakpointsColumnModel extends DefaultTableColumnModel {

        private final BreakpointsViewPreferences _localPreferences;

        private BreakpointsColumnModel() {
            _localPreferences = new BreakpointsViewPreferences(BreakpointsViewPreferences.globalPreferences(inspection())) {
                @Override
                public void setIsVisible(BreakpointsColumnKind columnKind, boolean visible) {
                    super.setIsVisible(columnKind, visible);
                    final int col = columnKind.ordinal();
                    if (visible) {
                        addColumn(_columns[col]);
                    } else {
                        removeColumn(_columns[col]);
                    }
                    fireColumnPreferenceChanged();
                }
            };
            createColumn(BreakpointsColumnKind.TAG, new TagCellRenderer(inspection()), null);
            createColumn(BreakpointsColumnKind.ENABLED, null, new DefaultCellEditor(new JCheckBox()));
            createColumn(BreakpointsColumnKind.METHOD, new MethodCellRenderer(inspection()), null);
            createColumn(BreakpointsColumnKind.LOCATION, new LocationCellRenderer(inspection()), null);
            createColumn(BreakpointsColumnKind.CONDITION, new ConditionCellRenderer(), new DefaultCellEditor(new JTextField()));
            createColumn(BreakpointsColumnKind.TRIGGER_THREAD, new TriggerThreadCellRenderer(inspection()), null);
        }

        private BreakpointsViewPreferences localPreferences() {
            return _localPreferences;
        }

        private void createColumn(BreakpointsColumnKind columnKind, TableCellRenderer renderer, TableCellEditor editor) {
            final int col = columnKind.ordinal();
            _columns[col] = new TableColumn(col, 0, renderer, editor);
            _columns[col].setHeaderValue(columnKind.label());
            _columns[col].setMinWidth(columnKind.minWidth());
            if (_localPreferences.isVisible(columnKind)) {
                addColumn(_columns[col]);
            }
            _columns[col].setIdentifier(columnKind);
        }
    }

    private final class BreakpointsTableModel extends DefaultTableModel {

        BreakpointsTableModel() {
        }

        void refresh() {
            // Check for current and added breakpoints
            // Initially assume all deleted
            for (BreakpointData breakpointData : _breakpoints) {
                breakpointData.markDeleted(true);
            }
            // add new and mark previous as not deleted
            for (TeleTargetBreakpoint breakpoint : teleVM().targetBreakpoints()) {
                final BreakpointData breakpointData = findTargetBreakpoint(breakpoint.address());
                if (breakpointData == null) {
                    // new breakpoint in {@link TeleVM} since last refresh
                    _breakpoints.add(new TargetBreakpointData(breakpoint));
                    //fireTableDataChanged();
                } else {
                    // mark as not deleted
                    breakpointData.markDeleted(false);
                }
            }
            for (TeleBytecodeBreakpoint breakpoint : teleVM().bytecodeBreakpoints()) {
                final BreakpointData breakpointData = findBytecodeBreakpoint(breakpoint.key());
                if (breakpointData == null) {
                    // new breakpoint since last refresh
                    _breakpoints.add(new BytecodeBreakpointData(breakpoint));
                    //fireTableDataChanged();
                } else {
                    // mark as not deleted
                    breakpointData.markDeleted(false);
                }
            }
            // now remove the breakpoints that are still marked as deleted
            final Iterator iter = _breakpoints.iterator();
            while (iter.hasNext()) {
                final BreakpointData breakpointData = (BreakpointData) iter.next();
                if (breakpointData.isDeleted()) {
                    iter.remove();
                }
            }
            fireTableDataChanged();
            selectBreakpoint(focus().breakpoint());
        }

        @Override
        public int getColumnCount() {
            return BreakpointsColumnKind.VALUES.length();
        }

        @Override
        public int getRowCount() {
            return _breakpoints.size();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            switch (BreakpointsColumnKind.VALUES.get(col)) {
                case ENABLED:
                    return true;

                case CONDITION:
                    return get(row) instanceof TargetBreakpointData;

                default:
                    break;
            }
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            final BreakpointData breakpointData = get(row);
            switch (BreakpointsColumnKind.VALUES.get(col)) {
                case TAG:
                    return breakpointData.kindTag();
                case ENABLED:
                    return breakpointData.enabled();
                case METHOD:
                    return breakpointData.shortName();
                case LOCATION:
                    return breakpointData.location();
                case CONDITION:
                    return breakpointData.condition();
                case TRIGGER_THREAD:
                    return  breakpointData.triggerThreadName();
                default:
                    Problem.error("Unspected Breakpoint Data column");
            }
            return null;
        }

        @Override
        public Class< ? > getColumnClass(int c) {
            switch (BreakpointsColumnKind.VALUES.get(c)) {
                case TAG:
                    return String.class;
                case ENABLED:
                    return Boolean.class;
                case METHOD:
                    return String.class;
                case LOCATION:
                    return Number.class;
                case CONDITION:
                    return String.class;
                case TRIGGER_THREAD:
                    return String.class;
                default:
                    Problem.error("Unspected Breakpoint Data column");
            }
            return Object.class;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            final BreakpointData breakpointData = get(row);

            switch (BreakpointsColumnKind.VALUES.get(column)) {
                case ENABLED:
                    final Boolean newState = (Boolean) value;
                    if (breakpointData.setEnabled(newState)) {
                        inspection().settings().save();
                    }
                    break;

                case CONDITION:
                    final String conditionText = (String) value;
                    breakpointData.setCondition(conditionText);
                    inspection().settings().save();
                    break;

                default:
            }
        }
    }

    private final class TagCellRenderer extends PlainLabel implements TableCellRenderer {

        TagCellRenderer(Inspection inspection) {
            super(inspection, null);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final BreakpointData breakpointData = get(row);
            setText(breakpointData.kindTag());
            setToolTipText(breakpointData.kindName() + ", Enabled=" + (breakpointData.enabled() ? "true" : "false"));
            if (row == getSelectionModel().getMinSelectionIndex()) {
                setBackground(style().defaultCodeAlternateBackgroundColor());
            } else {
                setBackground(style().defaultTextBackgroundColor());
            }
            return this;
        }
    }

    private final class MethodCellRenderer extends JavaNameLabel implements TableCellRenderer {

        public MethodCellRenderer(Inspection inspection) {
            super(inspection, null);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final BreakpointData breakpointData = get(row);
            setValue(breakpointData.shortName(), breakpointData.longName());
            if (row == getSelectionModel().getMinSelectionIndex()) {
                setBackground(style().defaultCodeAlternateBackgroundColor());
            } else {
                setBackground(style().javaNameBackgroundColor());
            }
            return this;
        }
    }

    private final class LocationCellRenderer extends PlainLabel implements TableCellRenderer {

        public LocationCellRenderer(Inspection inspection) {
            super(inspection, null);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final BreakpointData breakpointData = get(row);
            setText(Integer.toString(breakpointData.location()));
            setToolTipText("Location: " + breakpointData.locationDescription());
            if (row == getSelectionModel().getMinSelectionIndex()) {
                setBackground(style().defaultCodeAlternateBackgroundColor());
            } else {
                setBackground(style().defaultTextBackgroundColor());
            }
            return this;
        }
    }

    private final class ConditionCellRenderer extends DefaultTableCellRenderer implements Prober {

        ConditionCellRenderer() {
            setFont(inspection().style().defaultFont());
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setToolTipText(get(row).conditionStatus());
            final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row == getSelectionModel().getMinSelectionIndex()) {
                component.setBackground(style().defaultCodeAlternateBackgroundColor());
            } else {
                component.setBackground(style().defaultTextBackgroundColor());
            }
            return component;
        }

        @Override
        public void redisplay() {
            setFont(inspection().style().defaultFont());
        }

        @Override
        public void refresh(long epoch, boolean force) {
        }
    }

    private final class TriggerThreadCellRenderer extends PlainLabel implements TableCellRenderer {

        TriggerThreadCellRenderer(Inspection inspection) {
            super(inspection, null);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final BreakpointData breakpointData = get(row);
            if (breakpointData.triggerThread() != null) {
                setText(breakpointData.triggerThreadName());
                setToolTipText("Thread \"" + breakpointData.triggerThreadName() + "\" stopped at this breakpoint");
            } else {
                setText("");
                setToolTipText("No Thread stopped at this breakpoint");
            }
            if (row == getSelectionModel().getMinSelectionIndex()) {
                setBackground(style().defaultCodeAlternateBackgroundColor());
            } else {
                setBackground(style().defaultTextBackgroundColor());
            }
            return this;
        }
    }

    private final class BreakpointMenuItems implements InspectorMenuItems {

        private final Inspection _inspection;
        private final BreakpointData _breakpointData;

        BreakpointMenuItems(Inspection inspection, BreakpointData breakpointData) {
            _inspection = inspection;
            _breakpointData = breakpointData;
        }

        public void addTo(InspectorMenu menu) {
            final String shortName = _breakpointData.shortName();
            menu.add(inspection().actions().removeBreakpoint(_breakpointData.teleBreakpoint(), "Remove: " + shortName));
            if (_breakpointData.enabled()) {
                menu.add(inspection().actions().disableBreakpoint(_breakpointData.teleBreakpoint(), "Disable: " + shortName));
            } else {
                menu.add(inspection().actions().enableBreakpoint(_breakpointData.teleBreakpoint(), "Enable: " + shortName));
            }
        }

        public Inspection inspection() {
            return _inspection;
        }

        public void refresh(long epoch, boolean force) {
        }

        public void redisplay() {
        }
    }

    /**
     * @param breakpointData a breakpoint in the {@link TeleVM}.
     * @return a menu of actions, some of which are specific to the specified breakpoint
     */
    private InspectorMenu getButton3Menu(BreakpointData breakpointData) {
        final InspectorMenu menu = new InspectorMenu();
        menu.add(new BreakpointMenuItems(inspection(), breakpointData));
        menu.addSeparator();
        final JMenu methodEntryBreakpoints = new JMenu("Break at Method Entry");
        methodEntryBreakpoints.add(inspection().actions().setTargetCodeBreakpointAtMethodEntriesByName());
        methodEntryBreakpoints.add(inspection().actions().setBytecodeBreakpointAtMethodEntryByName());
        methodEntryBreakpoints.add(inspection().actions().setBytecodeBreakpointAtMethodEntryByKey());
        menu.add(methodEntryBreakpoints);
        menu.add(inspection().actions().setTargetCodeBreakpointAtObjectInitializer());
        menu.add(inspection().actions().removeAllBreakpoints());
        menu.addSeparator();
        menu.add(inspection().actions().removeAllTargetCodeBreakpoints());
        menu.addSeparator();
        menu.add(inspection().actions().removeAllBytecodeBreakpoints());
        return menu;
    }

    private final class BreakpointInspectorMouseClickAdapter extends InspectorMouseClickAdapter {

        BreakpointInspectorMouseClickAdapter(Inspection inspection) {
            super(inspection);
        }

        @Override
        public void procedure(final MouseEvent mouseEvent) {
            switch (MaxineInspector.mouseButtonWithModifiers(mouseEvent)) {
                case MouseEvent.BUTTON1: {
                    final int row = getSelectedRow();
                    final int column = getSelectedColumn();
                    final BreakpointData breakpointData = get(row);
                    switch (BreakpointsColumnKind.VALUES.get(column)) {
                        case TAG:
                        case METHOD:
                        case LOCATION:
                        case CONDITION:
                        case TRIGGER_THREAD:
                            focus().setBreakpoint(breakpointData.teleBreakpoint());
                            break;
                        case ENABLED:
                            break;
                    }
                    break;
                }
                case MouseEvent.BUTTON3: {
                    final Point p = mouseEvent.getPoint();
                    final int row = rowAtPoint(p);
                    final BreakpointData breakpointData = get(row);
                    final InspectorMenu menu = getButton3Menu(breakpointData);
                    menu.popupMenu().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                    break;
                }
            }
        }
    };

    /**
     * Global breakpoint focus has changed; revise selection in inspector if needed.
     */
    void selectBreakpoint(TeleBreakpoint teleBreakpoint) {
        TeleBreakpoint selectedBreakpoint = null;
        final int selectedRow = getSelectedRow();
        if (selectedRow >= 0) {
            selectedBreakpoint = get(selectedRow).teleBreakpoint();
        }
        if (selectedBreakpoint != teleBreakpoint) {
            final int row = breakpointToRow(teleBreakpoint);
            if (row >= 0) {
                getSelectionModel().setSelectionInterval(row, row);
            } else {
                getSelectionModel().clearSelection();
            }
        }
    }


    /**
     * Locates a target code breakpoint already known to the inspector.
     */
    private TargetBreakpointData findTargetBreakpoint(Address address) {
        for (BreakpointData breakpointData : _breakpoints) {
            if (breakpointData instanceof TargetBreakpointData) {
                final TargetBreakpointData targetBreakpointData = (TargetBreakpointData) breakpointData;
                if (targetBreakpointData.address().toLong() == address.toLong()) {
                    return targetBreakpointData;
                }
            }
        }
        return null;
    }

    /**
     * Locates a bytecode breakpoint already known to the inspector.
     */
    private BytecodeBreakpointData findBytecodeBreakpoint(TeleBytecodeBreakpoint.Key key) {
        for (BreakpointData breakpointData : _breakpoints) {
            if (breakpointData instanceof BytecodeBreakpointData) {
                final BytecodeBreakpointData bytecodeBreakpointData = (BytecodeBreakpointData) breakpointData;
                if (bytecodeBreakpointData.key() == key) {
                    return bytecodeBreakpointData;
                }
            }
        }
        return null;
    }

    private BreakpointData get(int row) {
        int count = 0;
        for (BreakpointData breakpointData : _breakpoints) {
            if (count == row) {
                return breakpointData;
            }
            count++;
        }
        Problem.error("BreakpointsInspector.get(" + row + ") failed");
        return null;
    }

    /**
     * Return the table row in which a breakpoint is displayed.
     */
    private int breakpointToRow(TeleBreakpoint breakpoint) {
        int row = 0;
        for (BreakpointData breakpointData : _breakpoints) {
            if (breakpointData.teleBreakpoint() == breakpoint) {
                return row;
            }
            row++;
        }
        return -1;
    }

    /**
     * Summary of information about a breakpoint that is useful for inspection.
     *
     * @author Michael Van De Vanter
     */
    private abstract class BreakpointData implements Comparable{

        /**
         * @return the breakpoint in the {@link TeleVM} being described
         */
        abstract TeleBreakpoint teleBreakpoint();

        /**
         * @return the location of the breakpoint in the {@link TeleVM} in a standard format.
         */
        TeleCodeLocation teleCodeLocation() {
            return teleBreakpoint().teleCodeLocation();
        }

        /**
         * @return short string identifying the kind of this breakpoint
         */
        abstract String kindTag();

        /**
         * @return longer string identifying the kind of this breakpoint
         */
        abstract String kindName();

        /**
         * @return name of the breakpoint, suitable for display in a table cell
         */
        abstract String shortName();

        /**
         * @return longer textual description suitable for tool tip
         */
        abstract String longName();

        /**
         * @return difference between the breakpoint and the beginning of the method,
         * described in units appropriate to each kind of breakpoint.
         */
        abstract int location();

        /**
         * @return a description of the breakpoint location in the {@link TeleVM}, specifying units
         */
        abstract String locationDescription();

        /**
         * @return is this breakpoint currently enabled in the {@link TeleVM}?
         */
        boolean enabled() {
            return teleBreakpoint().isEnabled();
        }

        /**
         * Updates the enabled state of this breakpoint.
         *
         * @param enabled new state for this breakpoint
         * @return true if the state was actually changed
         */
        public boolean setEnabled(boolean enabled) {
            return teleBreakpoint().setEnabled(enabled);
        }

         /**
         * @return textual expression of the condition associated with this breakpoint, if any.
         */
        String condition() {
            return teleBreakpoint().condition() == null ? "" : teleBreakpoint().condition().toString();
        }

        abstract void setCondition(String conditionText);

        /**
         * @return message describing the status of the condition, if any, assocaited with this breakpoint;
         * suitable for a ToolTip.
         */
        abstract String conditionStatus();

        /**
         * @return the thread in the {@link TeleVM}, if any, that is currently stopped at this breakpoint.
         */
        TeleNativeThread triggerThread() {
            for (TeleNativeThread thread : teleVM().threads()) {
                if (thread.breakpoint() == teleBreakpoint()) {
                    return thread;
                }
            }
            return null;
        }

        /**
         * @return display name of the thread, if any, that is currently stopped at this breakpoint.
         */
        String triggerThreadName() {
            return inspection().nameDisplay().longName(triggerThread());
        }

        private boolean _deleted = false;

        /**
         * @return whether this breakpoint is still marked deleted after the most recent sweep
         */
        boolean isDeleted() {
            return _deleted;
        }

        /**
         * sets the "deleted" state, used to update the list of breakpoints in the {@link TeleVM}.
         */
        void markDeleted(boolean deleted) {
            _deleted = deleted;
        }

        @Override
        public String toString() {
            return shortName();
        }

        public int compareTo(Object o) {
            // per {@link TreeSet}, comparison must be consistent with equals
            int result = 0;
            if (!this.equals(o)) {
                if (o instanceof BreakpointData) {
                    final BreakpointData breakpointData = (BreakpointData) o;
                    result = shortName().compareTo(breakpointData.shortName());
                    if (result == 0) {
                        result = longName().compareTo(breakpointData.longName());
                    }
                }
                if (result == 0) {
                    result = 1;
                }
            }
            return result;
        }
    }

    private final class TargetBreakpointData extends BreakpointData {

        private final TeleTargetBreakpoint _teleTargetBreakpoint;
        private Address _codeStart;
        private int _location = 0;
        private String _shortName;
        private String _longName;

        TargetBreakpointData(TeleTargetBreakpoint teleTargetBreakpoint) {
            _teleTargetBreakpoint = teleTargetBreakpoint;
            final Address address = teleTargetBreakpoint.address();
            final TeleTargetMethod teleTargetMethod = TeleTargetMethod.make(teleVM(), address);
            if (teleTargetMethod != null) {
                _shortName = inspection().nameDisplay().shortName(teleTargetMethod);
                _longName = inspection().nameDisplay().longName(teleTargetMethod, address);
                _codeStart = teleTargetMethod.codeStart();
                _location = address.minus(_codeStart.asAddress()).toInt();
            } else {
                final TeleRuntimeStub teleRuntimeStub = TeleRuntimeStub.make(teleVM(), address);
                if (teleRuntimeStub != null) {
                    _codeStart = teleRuntimeStub.runtimeStub().start();
                    _location = address.minus(_codeStart).toInt();
                    _shortName = "runtime stub[0x" + _codeStart + "]";
                    _longName = _shortName;
                } else {
                    final TeleNativeTargetRoutine teleNativeTargetRoutine = TeleNativeTargetRoutine.make(teleVM(), address);
                    if (teleNativeTargetRoutine != null) {
                        _codeStart = teleNativeTargetRoutine.codeStart();
                        _location = address.minus(_codeStart.asAddress()).toInt();
                        _shortName = inspection().nameDisplay().shortName(teleNativeTargetRoutine);
                        _longName = inspection().nameDisplay().longName(teleNativeTargetRoutine);
                    } else {
                        // Must be an address in an unknown area of native code
                        _shortName = "0x" + address.toHexString();
                        _longName = "native code at 0x" + address.toHexString();
                        _codeStart = address;
                        _location = 0;
                    }
                }
            }
        }

        @Override
        TeleBreakpoint teleBreakpoint() {
            return _teleTargetBreakpoint;
        }

        @Override
        String kindTag() {
            return "T";
        }

        @Override
        String kindName() {
            return "Target Code breakpoint";
        }

        @Override
        String shortName() {
            return _shortName;
        }

        @Override
        String longName() {
            return _longName;
        }

        @Override
        int location() {
            return _location;
        }

        @Override
        String locationDescription() {
            return "Offset=" + (_location > 0 ? "+" : "") + _location + ", Address=" + _teleTargetBreakpoint.address().toHexString();
        }

        @Override
        void setCondition(String condition) {
            try {
                _teleTargetBreakpoint.setCondition(new BreakpointCondition(teleVM(), condition));
                inspection().settings().save();
            } catch (BreakpointCondition.ExpressionException expressionException) {
                inspection().errorMessage(String.format("Error parsing saved breakpoint condition:%n  expression: %s%n       error: " + condition, expressionException.getMessage()), "Breakpoint Condition Error");
            }
        }

        @Override
        String conditionStatus() {
            final String condition = condition();
            if (!condition.equals("")) {
                return "Breakpoint condition= \"" + condition + "\"";
            }
            return "No breakpoint condition set";
        }

        Address address() {
            return _teleTargetBreakpoint.address();
        }
    }

    private final class BytecodeBreakpointData extends BreakpointData {

        private final TeleBytecodeBreakpoint _teleBytecodeBreakpoint;
        private final TeleBytecodeBreakpoint.Key _key;
        String _shortName;
        String _longName;

        BytecodeBreakpointData(TeleBytecodeBreakpoint teleBytecodeBreakpoint) {
            _teleBytecodeBreakpoint = teleBytecodeBreakpoint;
            _key = teleBytecodeBreakpoint.key();
            _shortName = _key.holder().toJavaString(false) + "." + _key.name().toString() + _key.signature().toJavaString(false,  false);

            _longName = _key.signature().getResultDescriptor().toJavaString(false) + " " + _key.name().toString() + _key.signature().toJavaString(false,  false);
            if (_key.position() > 0) {
                _longName += " + " + _key.position();
            }
            _longName = _longName + " in " + _key.holder().toJavaString();
        }

        @Override
        TeleBreakpoint teleBreakpoint() {
            return _teleBytecodeBreakpoint;
        }

        @Override
        String kindTag() {
            return "B";
        }

        @Override
        String kindName() {
            return "Bytecode breakpoint";
        }

        @Override
        String shortName() {
            return _shortName;
        }

        @Override
        String longName() {
            return _longName;
        }

        @Override
        int location() {
            return _key.position();
        }

        @Override
        String locationDescription() {
            return "Bytecode position=" + _key.position();
        }

        @Override
        void setCondition(String conditionText) {
            Problem.unimplemented("Conditional bytecode breakpoints not supported yet");
        }

        @Override
        String conditionStatus() {
            return "Bytecode breakpoint conditions not supported yet";
        }

        TeleBytecodeBreakpoint.Key key() {
            return _key;
        }
    }
}