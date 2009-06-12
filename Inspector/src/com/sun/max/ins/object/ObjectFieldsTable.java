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
package com.sun.max.ins.object;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.table.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.type.*;
import com.sun.max.ins.value.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;


/**
 * A table that displays Maxine object fields; for use in an instance of {@link ObjectInspector}.
 *
 * @author Michael Van De Vanter
 */
public final class ObjectFieldsTable extends InspectorTable {

    private final ObjectInspector _objectInspector;
    private final Inspection _inspection;
    private final FieldActor[] _fieldActors;
    private final TeleObject _teleObject;
    private Pointer _objectOrigin;
    private final boolean _isTeleActor;

    /** an offset in bytes for the first field being displayed. */
    private final int _startOffset;
    /** an offset in bytes that is one past the last field being displayed.*/
    private final int _endOffset;

    private final ObjectFieldsTableModel _model;
    private final ObjectFieldsTableColumnModel _columnModel;
    private final TableColumn[] _columns;

    private MaxVMState _lastRefreshedState = null;

    /**
     * A {@link JTable} specialized to display Maxine object fields.
     *
     * @param objectInspector parent that contains this panel
     * @param fieldActors description of the fields to be displayed
     */
    public ObjectFieldsTable(final ObjectInspector objectInspector, Collection<FieldActor> fieldActors) {
        super(objectInspector.inspection());
        _objectInspector = objectInspector;
        _inspection = objectInspector.inspection();
        _fieldActors = new FieldActor[fieldActors.size()];
        _teleObject = objectInspector.teleObject();
        _isTeleActor = _teleObject instanceof TeleActor;

        // Sort fields by offset in object layout.
        fieldActors.toArray(_fieldActors);
        java.util.Arrays.sort(_fieldActors, new Comparator<FieldActor>() {
            public int compare(FieldActor a, FieldActor b) {
                final Integer aOffset = a.offset();
                return aOffset.compareTo(b.offset());
            }
        });
        if (fieldActors.size() > 0) {
            _startOffset =  _fieldActors[0].offset();
            final FieldActor lastFieldActor = _fieldActors[_fieldActors.length - 1];
            _endOffset = lastFieldActor.offset() + lastFieldActor.valueSize();
        } else {
            // moot if there aren't any field actors
            _startOffset = 0;
            _endOffset = 0;
        }

        _model = new ObjectFieldsTableModel();
        _columns = new TableColumn[ObjectFieldColumnKind.VALUES.length()];
        _columnModel = new ObjectFieldsTableColumnModel(_objectInspector);
        setModel(_model);
        setColumnModel(_columnModel);
        setFillsViewportHeight(true);
        setShowHorizontalLines(style().memoryTableShowHorizontalLines());
        setShowVerticalLines(style().memoryTableShowVerticalLines());
        setIntercellSpacing(style().memoryTableIntercellSpacing());
        setRowHeight(style().memoryTableRowHeight());
        setRowSelectionAllowed(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        addMouseListener(new TableCellMouseClickAdapter(_inspection, this) {
            @Override
            public void procedure(final MouseEvent mouseEvent) {
                // By the way we get this event, a left click will have already made a new row selection.
                final int selectedRow = getSelectedRow();
                final int selectedColumn = getSelectedColumn();
                if (selectedRow != -1 && selectedColumn != -1) {
                    // Left button selects a table cell; also cause an address selection at the row.
                    if (MaxineInspector.mouseButtonWithModifiers(mouseEvent) == MouseEvent.BUTTON1) {
                        _inspection.focus().setAddress(_model.rowToAddress(selectedRow));
                    }
                }
                if (MaxineInspector.mouseButtonWithModifiers(mouseEvent) == MouseEvent.BUTTON3) {
                    if (maxVM().watchpointsEnabled()) {
                        // So far, only watchpoint-related items on this popup menu.
                        final Point p = mouseEvent.getPoint();
                        final int hitRowIndex = rowAtPoint(p);
                        final int columnIndex = getColumnModel().getColumnIndexAtX(p.x);
                        final int modelIndex = getColumnModel().getColumn(columnIndex).getModelIndex();
                        if (modelIndex == ObjectFieldColumnKind.TAG.ordinal()) {
                            final InspectorMenu menu = new InspectorMenu();
                            final Address address = _model.rowToAddress(hitRowIndex);
                            menu.add(actions().setWordWatchpoint(address, "Watch this memory word"));
                            menu.add(actions().removeWatchpoint(address, "Un-watch this memory word"));
                            menu.popupMenu().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                        }
                    }
                }
                super.procedure(mouseEvent);
            }
        });
        refresh(true);
        JTableColumnResizer.adjustColumnPreferredWidths(this);
    }

    public void refresh(boolean force) {
        if (maxVMState().newerThan(_lastRefreshedState) || force) {
            _lastRefreshedState = maxVMState();
            _objectOrigin = _teleObject.getCurrentOrigin();
            final int oldSelectedRow = getSelectedRow();
            final int newRow = _model.addressToRow(focus().address());
            if (newRow >= 0) {
                getSelectionModel().setSelectionInterval(newRow, newRow);
            } else {
                if (oldSelectedRow >= 0) {
                    getSelectionModel().clearSelection();
                }
            }
            for (TableColumn column : _columns) {
                final Prober prober = (Prober) column.getCellRenderer();
                prober.refresh(force);
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
    public void paintChildren(Graphics g) {
        // Draw a box around the selected row in the table
        super.paintChildren(g);
        final int row = getSelectedRow();
        if (row >= 0) {
            g.setColor(style().memorySelectedAddressBorderColor());
            g.drawRect(0, row * getRowHeight(row), getWidth() - 1, getRowHeight(row) - 1);
        }
    }

    /**
     * Add tool tip text to the column headers, as specified by {@link ObjectFieldColumnKind}.
     *
     * @see javax.swing.JTable#createDefaultTableHeader()
     */
    @Override
    protected JTableHeader createDefaultTableHeader() {
        return new JTableHeader(_columnModel) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent mouseEvent) {
                final Point p = mouseEvent.getPoint();
                final int index = _columnModel.getColumnIndexAtX(p.x);
                final int modelIndex = _columnModel.getColumn(index).getModelIndex();
                return ObjectFieldColumnKind.VALUES.get(modelIndex).toolTipText();
            }
        };
    }

    /**
     * Models the fields/rows in a list of object fields;
     * the value of each cell is the {@link FieldActor} that describes the field.
     */
    private final class ObjectFieldsTableModel extends AbstractTableModel {

        public int getColumnCount() {
            return ObjectFieldColumnKind.VALUES.length();
        }

        public int getRowCount() {
            return _fieldActors.length;
        }

        public Object getValueAt(int row, int col) {
            return _fieldActors[row];
        }

        @Override
        public Class< ? > getColumnClass(int col) {
            return FieldActor.class;
        }

        public int rowToOffset(int row) {
            return _fieldActors[row].offset();
        }

        /**
         * @return the memory address of a specified row in the fields.
         */
        public Address rowToAddress(int row) {
            return _objectOrigin.plus(rowToOffset(row)).asAddress();
        }

        public TypeDescriptor rowToType(int row) {
            return _fieldActors[row].descriptor();
        }

        public String rowToName(int row) {
            return _fieldActors[row].name().string();
        }

        /**
         * @return the memory watchpoint, if any, that is active at a row
         */
        public MaxWatchpoint rowToWatchpoint(int row) {
            for (MaxWatchpoint watchpoint : maxVM().watchpoints()) {
                if (watchpoint.contains(rowToAddress(row))) {
                    return watchpoint;
                }
            }
            return null;
        }

        public int addressToRow(Address address) {
            if (!address.isZero()) {
                final int offset = address.minus(_objectOrigin).toInt();
                if (offset >= _startOffset && offset < _endOffset) {
                    int currentOffset = _startOffset;
                    for (int row = 0; row < _fieldActors.length; row++) {
                        final int nextOffset = currentOffset + _fieldActors[row].valueSize();
                        if (offset < nextOffset) {
                            return row;
                        }
                        currentOffset = nextOffset;
                    }
                }
            }
            return -1;
        }
    }

    /**
     * A column model for object headers, to be used in an {@link ObjectInspector}.
     * Column selection is driven by choices in the parent {@link ObjectInspector}.
     * This implementation cannot update column choices dynamically.
     */
    private final class ObjectFieldsTableColumnModel extends DefaultTableColumnModel {

        ObjectFieldsTableColumnModel(ObjectInspector objectInspector) {
            createColumn(ObjectFieldColumnKind.TAG, new TagRenderer(), true);
            createColumn(ObjectFieldColumnKind.ADDRESS, new AddressRenderer(), objectInspector.showAddresses());
            createColumn(ObjectFieldColumnKind.OFFSET, new PositionRenderer(), objectInspector.showOffsets());
            createColumn(ObjectFieldColumnKind.TYPE, new TypeRenderer(), objectInspector.showFieldTypes());
            createColumn(ObjectFieldColumnKind.NAME, new NameRenderer(), true);
            createColumn(ObjectFieldColumnKind.VALUE, new ValueRenderer(), true);
            createColumn(ObjectFieldColumnKind.REGION, new RegionRenderer(), objectInspector.showMemoryRegions());
        }

        private void createColumn(ObjectFieldColumnKind columnKind, TableCellRenderer renderer, boolean isVisible) {
            final int col = columnKind.ordinal();
            _columns[col] = new TableColumn(col, 0, renderer, null);
            _columns[col].setHeaderValue(columnKind.label());
            _columns[col].setMinWidth(columnKind.minWidth());
            if (isVisible) {
                addColumn(_columns[col]);
            }
            _columns[col].setIdentifier(columnKind);
        }
    }

    private final class TagRenderer extends MemoryTagTableCellRenderer implements TableCellRenderer {

        TagRenderer() {
            super(_inspection);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            return getRenderer(_model.rowToAddress(row), focus().thread(), _model.rowToWatchpoint(row));
        }

    }

    private final class AddressRenderer extends LocationLabel.AsAddressWithOffset implements TableCellRenderer {

        AddressRenderer() {
            super(_inspection);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setValue(_model.rowToOffset(row), _objectOrigin);
            return this;
        }
    }

    private final class PositionRenderer extends LocationLabel.AsOffset implements TableCellRenderer {

        public PositionRenderer() {
            super(_inspection);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setValue(_model.rowToOffset(row), _objectOrigin);
            return this;
        }
    }

    private final class TypeRenderer extends TypeLabel implements TableCellRenderer {

        public TypeRenderer() {
            super(_inspection);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setValue(_model.rowToType(row));
            return this;
        }
    }

    private final class NameRenderer extends FieldActorNameLabel implements TableCellRenderer {

        public NameRenderer() {
            super(_inspection);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final FieldActor fieldActor = (FieldActor) value;
            setValue(fieldActor);
            return this;
        }
    }

    private final class ValueRenderer implements TableCellRenderer, Prober {

        private InspectorLabel[] _labels = new InspectorLabel[_fieldActors.length];

        public void refresh(boolean force) {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.refresh(force);
                }
            }
        }

        public void redisplay() {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.redisplay();
                }
            }
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            InspectorLabel label = _labels[row];
            if (label == null) {
                final FieldActor fieldActor = (FieldActor) value;
                if (fieldActor.kind() == Kind.REFERENCE) {
                    label = new WordValueLabel(_inspection, WordValueLabel.ValueMode.REFERENCE) {
                        @Override
                        public Value fetchValue() {
                            return _teleObject.readFieldValue(fieldActor);
                        }
                    };
                } else if (fieldActor.kind() == Kind.WORD) {
                    label = new WordValueLabel(_inspection, WordValueLabel.ValueMode.WORD) {
                        @Override
                        public Value fetchValue() {
                            return _teleObject.readFieldValue(fieldActor);
                        }
                    };
                } else if (_isTeleActor && fieldActor.name().toString().equals("_flags")) {
                    final TeleActor teleActor = (TeleActor) _teleObject;
                    label = new ActorFlagsValueLabel(_inspection, teleActor);
                } else {
                    label = new PrimitiveValueLabel(_inspection, fieldActor.kind()) {
                        @Override
                        public Value fetchValue() {
                            return _teleObject.readFieldValue(fieldActor);
                        }
                    };
                }
                _labels[row] = label;
            }
            return label;
        }
    }

    private final class RegionRenderer implements TableCellRenderer, Prober {

        private InspectorLabel[] _labels = new InspectorLabel[_fieldActors.length];

        public void refresh(boolean force) {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.refresh(force);
                }
            }
        }

        public void redisplay() {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.redisplay();
                }
            }
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            InspectorLabel label = _labels[row];
            if (label == null) {
                final FieldActor fieldActor = (FieldActor) value;
                label = new MemoryRegionValueLabel(_inspection) {
                    @Override
                    public Value fetchValue() {
                        return _teleObject.readFieldValue(fieldActor);
                    }
                };
                _labels[row] = label;
            }
            return label;
        }
    }

}
