/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2017 Fabrice Tiercelin - initial API and implementation
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
package org.autorefactor.refactoring.rules.samples_in;

import java.util.Date;
import java.util.Stack;
import java.util.List;

public class ArrayDequeRatherThanStackSample {

    public void replaceStackInstanceCreation() {
        // Keep this comment
        String[] stringArray = new Stack<String>().toArray(null);
        // Keep this comment too
        int size = new Stack<String>().size();
    }

    public void replaceRawStack() {
        // Keep this comment
        Object[] objectArray = new Stack().toArray(null);
        // Keep this comment too
        int size = new Stack().size();
    }

    public void replaceFullyQualifiedStack() {
        // Keep this comment
        Date[] dateArray = new java.util.Stack<Date>().toArray(null);
        // Keep this comment too
        int size = new java.util.Stack().size();
    }

    public void replaceStackVariableDeclaration() {
        // Keep this comment
        Stack<String> queue = new Stack<String>();
    }

    public void doNotReplaceInterface() {
        // Keep this comment
        List<String> queue = new Stack<String>();
    }

    public void replaceStackVariableUse() {
        // Keep this comment
        Stack<String> queue = new Stack<String>();
        // Keep this comment too
        queue.add("bar");
    }

    public void refactorMethod() {
        // Keep this comment
        Stack<String> queue = new Stack<String>();
        // Keep this comment too
        queue.toArray();
    }

    public String replaceStackWithLoop(List<Date> dates) {
        // Keep this comment
        Stack<Date> queue = new Stack<Date>();
        for (Date date : dates) {
            queue.add(date);
        }

        return queue.toString();
    }

    public void replaceStackWithModifier() {
        // Keep this comment
        final Stack<String> queue = new Stack<String>();
        queue.add("bar");
    }

    public void replaceStackWithParameter() {
        // Keep this comment
        Stack<String> queue = new Stack<String>();
        queue.add("bar");
    }

    public String[] replaceReassignedStack() {
        // Keep this comment
        Stack<String> queue1 = new Stack<String>();
        queue1.add("FOO");

        // Keep this comment too
        Stack<String> queue2 = queue1;
        queue2.add("BAR");

        return queue2.toArray(null);
    }

    public void doNotReplaceStackParameter(Stack<String> aStack) {
        Stack<String> stack = aStack;
        stack.add("bar");
    }

    public void doNotReplaceStackPassedToAMethod() {
        String p3 = String.valueOf(new Stack<String>());
    }

    public Stack<Date> doNotReplaceReturnedStack() {
        return new Stack<Date>();
    }

    public void doNotReplaceReassignedVariable() {
        Stack<String> stack = new Stack<String>();
        stack = new Stack<String>();
    }

    public void replaceOldMethod() {
        Stack<Integer> queue = new Stack<Integer>();
        // Keep this comment
        queue.addElement(42);
        queue.copyInto(new Object[10]);
        queue.removeElement(123);
        queue.removeAllElements();
        queue.firstElement();
        queue.lastElement();
        queue.empty();
    }

    public String doNotReplaceSpecificMethod() {
        Stack<String> stack = new Stack<String>();
        stack.removeElementAt(1);
        return stack.elementAt(0);
    }

    public void replaceStackWithRunnable() {
        // Keep this comment
        final Stack<String> queue = new Stack<String>();
        new Runnable() {

            @Override
            public void run() {
                final Stack<String> localQueue = new Stack<String>();
                localQueue.add("Local, it's safe.");
            }
        };
    }

    public void doNotReplaceThreadSharedStack() {
        final Stack<String> stack = new Stack<String>();
        new Runnable() {

            @Override
            public void run() {
                stack.add("No conflict please");
            }
        };
    }
}