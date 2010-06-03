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

import javax.swing.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;

/**
 * Persistent preferences for the table viewing breakpoints in the VM.
 *
 * @author Michael Van De Vanter
  */
public final class BreakpointsViewPreferences extends TableColumnVisibilityPreferences<BreakpointsColumnKind> {

    private static BreakpointsViewPreferences globalPreferences;

    /**
     * @return the global, persistent set of user preferences for viewing a table of breakpoints.
     */
    static BreakpointsViewPreferences globalPreferences(Inspection inspection) {
        if (globalPreferences == null) {
            globalPreferences = new BreakpointsViewPreferences(inspection);
        }
        return globalPreferences;
    }

    // Prefix for all persistent column preferences in view
    private static final String BREAKPOINTS_COLUMN_PREFERENCE = "breakpointsViewColumn";

    /**
     * @return a GUI panel suitable for setting global preferences for this kind of view.
     */
    public static JPanel globalPreferencesPanel(Inspection inspection) {
        return globalPreferences(inspection).getPanel();
    }

    /**
    * Creates a set of preferences specified for use by singleton instances, where local and
    * persistent global choices are identical.
    */
    private BreakpointsViewPreferences(Inspection inspection) {
        super(inspection, BREAKPOINTS_COLUMN_PREFERENCE, BreakpointsColumnKind.values());
        // There are no view preferences beyond the column choices, so no additional machinery needed here.
    }
}
