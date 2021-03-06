/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2017 Fabrice Tiercelin - initial API and implementation
 * Copyright (C) 2017 Jean-Noël Rouvignac - minor changes
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
package org.autorefactor.refactoring.rules;

import static org.autorefactor.refactoring.ASTHelper.hasType;
import static org.autorefactor.refactoring.ASTHelper.isMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.Release;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;

/** See {@link #getDescription()} method. */
public class HashMapRatherThanHashtableRefactoring extends AbstractClassSubstituteRefactoring {
    private static Map<String, String[]> canBeCastedTo = new HashMap<String, String[]>();

    static {
        canBeCastedTo.put("java.lang.Object", new String[]{"java.lang.Object"});
        canBeCastedTo.put("java.lang.Cloneable", new String[]{"java.lang.Cloneable", "java.lang.Object"});
        canBeCastedTo.put("java.io.Serializable",
                new String[]{"java.io.Serializable", "java.lang.Object"});
        canBeCastedTo.put("java.util.Map", new String[]{"java.util.Map", "java.lang.Object"});
        canBeCastedTo.put("java.util.Hashtable",
                new String[]{"java.util.Hashtable", "java.io.Serializable", "java.util.Map",
                    "java.lang.Cloneable", "java.lang.Object"});
    }

    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return "HashMap rather than Hashtable";
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    public String getDescription() {
        return ""
            + "Replace Hashtable by HashMap when possible.";
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return "It improves the time performance.";
    }

    @Override
    public boolean isJavaVersionSupported(Release javaSeRelease) {
        return javaSeRelease.getMinorVersion() >= 2;
    }

    @Override
    protected boolean canBeSharedInOtherThread() {
        return false;
    }

    @Override
    protected String[] getExistingClassCanonicalName() {
        return new String[] {"java.util.Hashtable"};
    }

    @Override
    protected String getSubstitutingClassName(String origRawType) {
        if ("java.util.Hashtable".equals(origRawType)) {
            return "java.util.HashMap";
        } else {
            return null;
        }
    }

    @Override
    protected boolean canMethodBeRefactored(final MethodInvocation mi,
            final List<MethodInvocation> methodCallsToRefactor) {
        if (isMethod(mi, "java.util.Hashtable", "contains", "java.lang.Object")) {
            methodCallsToRefactor.add(mi);
        }
        return true;
    }

    @Override
    protected void refactorMethod(final ASTBuilder b, final MethodInvocation originalMi,
            final MethodInvocation refactoredMi) {
        refactoredMi.setName(b.simpleName("containsValue"));
    }

    @Override
    protected boolean isTypeCompatible(final ITypeBinding variableType,
            final ITypeBinding refType) {
        return super.isTypeCompatible(variableType, refType)
                || hasType(variableType, canBeCastedTo.getOrDefault(refType.getErasure().getQualifiedName(),
                        new String[0]));
    }
}
