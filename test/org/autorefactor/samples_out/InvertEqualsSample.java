/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013 Jean-Noël Rouvignac - initial API and implementation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program under LICENSE-GNUGPL.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution under LICENSE-ECLIPSE, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.autorefactor.samples_out;

import org.autorefactor.samples_in.InvertEqualsSample.Itf;

public class InvertEqualsSample {

    public static interface Itf {
        String constant = "fkjfkjf";
        String nullConstant = null;
    }

    public static void main(String[] args) {
        {
            // Invert equals()
            boolean b1 = "".equals(args[0]);
            boolean b2 = Itf.constant.equals(args[0]);
            boolean b3 = "".equals(args[0]);
            boolean b4 = Itf.constant.equals(args[0]);
            // Do NOT invert equals()
            boolean b5 = args[0] != null && args[0].equals(Itf.nullConstant);
            boolean b6 = null != args[0] && args[0].equals(Itf.nullConstant);
        }
        {
            // Invert equals()
            boolean b1 = "".equalsIgnoreCase(args[0]);
            boolean b2 = Itf.constant.equalsIgnoreCase(args[0]);
            boolean b3 = "".equalsIgnoreCase(args[0]);
            boolean b4 = Itf.constant.equalsIgnoreCase(args[0]);
            // Do NOT invert equals()
            boolean b5 = args[0] != null
                    && args[0].equalsIgnoreCase(Itf.nullConstant);
            boolean b6 = null != args[0]
                    && args[0].equalsIgnoreCase(Itf.nullConstant);
        }
    }
}
