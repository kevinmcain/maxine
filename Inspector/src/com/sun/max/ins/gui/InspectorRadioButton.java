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
package com.sun.max.ins.gui;

import javax.swing.*;

import com.sun.max.ins.*;


/**
 * A radio button specialized for use in the Maxine Inspector.
 *
 * @author Michael Van De Vanter
 */
public final class InspectorRadioButton extends JRadioButton {

    /**
     *  Creates a new {@JRadioButton} specialized for use in the Maxine Inspector.
     * @param inspection
     * @param text the text to appear in the label
     * @param selected whether the check box is currently selected.
     */
    public InspectorRadioButton(Inspection inspection, String text, String toolTipText) {
        super(text);
        setToolTipText(toolTipText);
        setOpaque(true);
        setFont(inspection.style().textLabelFont());
        setBackground(inspection.style().defaultBackgroundColor());
    }
}