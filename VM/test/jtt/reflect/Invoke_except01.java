/*
 * Copyright (c) 2009, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jtt.reflect;

import java.lang.reflect.*;

/*
* @Harness: java
* @Runs: 0=0; 1=3; 2=!java.lang.reflect.InvocationTargetException; 3=!java.lang.IllegalArgumentException; 4=!java.lang.IllegalArgumentException
*/
public class Invoke_except01 {

    public static int test(int arg) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Object[] args;
        if (arg == 0) {
            args = new Object[] {new int[0]};
        } else if (arg == 1) {
            args = new Object[] {new int[3]};
        } else if (arg == 2) {
            args = new Object[] {null};
        } else if (arg == 3) {
            args = new Object[] {new char[3]};
        } else {
            args = null;
        }
        for (Method m : Invoke_except01.class.getDeclaredMethods()) {
            if ("method".equals(m.getName())) {
                return (Integer) m.invoke(null, args);
            }
        }
        return 42;
    }

    public static int method(int[] arg) {
        return arg.length;
    }
}
