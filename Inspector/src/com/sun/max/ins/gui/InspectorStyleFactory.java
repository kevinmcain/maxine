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

import com.sun.max.ins.*;


/**
 * A very simple provider of display styles, at this time only varying by font size.
 *
 * @author Michael Van De Vanter
 */
public class InspectorStyleFactory extends AbstractInspectionHolder {

    private final InspectorStyle _standard10;
    private final InspectorStyle _standard11;
    private final InspectorStyle _standard12;
    private final InspectorStyle[] _allStyles;

    public InspectorStyleFactory(Inspection inspection) {
        super(inspection);
        _standard10 = new StandardInspectorStyle(inspection, 10, 16);
        _standard11 = new StandardInspectorStyle(inspection, 11, 18);
        _standard12 = new StandardInspectorStyle(inspection, 12, 20);
        _allStyles = new InspectorStyle[] {_standard10, _standard11, _standard12};
    }

    /**
     * @return the default {@link InspectorStyle} to use when no preference specified.
     */
    public InspectorStyle defaultStyle() {
        return _standard12;
    }

    /**
     * @param name a style name
     * @return the style by that name, null if none exists.
     */
    public InspectorStyle findStyle(String name) {
        for (InspectorStyle style : _allStyles) {
            if (name.equals(style.name())) {
                return style;
            }
        }
        return null;
    }

    /**
     * @return all available styles.
     */
    public InspectorStyle[] allStyles() {
        return _allStyles;
    }

}