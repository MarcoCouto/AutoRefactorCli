/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2016 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2016 Fabrice Tiercelin - Make sure we do not visit again modified nodes
 * Copyright (C) 2016 Luis Cruz - Android Refactoring Rules
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

import static org.autorefactor.refactoring.ASTHelper.DO_NOT_VISIT_SUBTREE;
import static org.autorefactor.refactoring.ASTHelper.VISIT_SUBTREE;
import static org.autorefactor.util.COEvolgy.MEASURE;
import static org.autorefactor.util.COEvolgy.TRACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

/* 
 * TODO when the last use of resource is as arg of a method invocation,
 * it should be assumed that the given method will take care of the release.  
 * TODO Track local variables. E.g., when a TypedArray a is assigned to variable b,
 * release() should be called only in one variable. 
 * TODO (low priority) check whether resources are being used after release.
 * TODO add support for FragmentTransaction.beginTransaction(). It can use method
 * chaining (which means local variable might not be present) and it can be released
 * by two methods: commit() and commitAllowingStateLoss()
 * 
 * FIXME: (medium priority) fix the infinite loop cycle.
 * This rule has a strange issue: when a refactor is made, it goes through the contents 
 * of the same file all over again. For now, after analyzing a file it stores its path, and 
 * once it starts again the path of the file being analyzed is compared to the prevvious one.
 * If they match, the rule is forced to not do anything. Sloppy solution, but it works for now.
 */

/** See {@link #getDescription()} method. */
public class RecycleRefactoring extends AbstractRefactoringRule {
	
	public static final String TAG = "Recycle";
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	private static boolean foundTracerImport = false;
	
	private static CompilationUnit mainNode = null;
	private static int operationFlag = MEASURE;
	
	private List<VariableDeclaration> fields;
	private List<VariableDeclaration> variables;
	
	private static String lastVisitedCU = "";
	private static boolean alreadyVisitedCU = false;
	
	private Map<SimpleName, String> recycles;
	private int countRecycles;
	
	public RecycleRefactoring() {
		super();
		this.recycles = new HashMap<>();
		this.countRecycles = 0;
	}
	
	public RecycleRefactoring(int flag) {
		super();
		this.recycles = new HashMap<>();
		this.countRecycles = 0;
		operationFlag = flag;
	}

	@Override
	public String getDescription() {
		return "Many resources, such as TypedArrays, VelocityTrackers, etc., should be "
				+ "recycled (with a recycle()/close() call) after use. "
				+ "Inspired from "
				+ "https://android.googlesource.com/platform/tools/base/+/master/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/CleanupDetector.java";
	}

	@Override
	public String getName() {
		return "RecycleRefactoring";
	}
	
	/**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return "It improves the performance.";
    }
    
    @Override
    public boolean visit(CompilationUnit node) {
    	if (node.getJavaElement().getPath().toString().equals(lastVisitedCU)) {
    		alreadyVisitedCU = true;
    		return VISIT_SUBTREE;
    	}
    	alreadyVisitedCU = false;
    	List<ImportDeclaration> allImports = node.imports();
		foundTracerImport = COEvolgy.isImportIncluded(allImports, tracerImport);
		
		mainNode = node;
		
		this.fields = new ArrayList<>();
		this.variables = new ArrayList<>();
		
		return VISIT_SUBTREE;
    }
    
    public void endVisit(CompilationUnit node) {
    	lastVisitedCU = node.getJavaElement().getPath().toString();
    }
    
    @Override
    public boolean visit(ImportDeclaration node) {
		final ASTBuilder b = ctx.getASTBuilder();
		final Refactorings r = ctx.getRefactorings();
		boolean refactored = false;

		if (!foundTracerImport) {
			ImportDeclaration importTracer = r.getAST().newImportDeclaration();
			Name importName = b.name(tracerImport.split("\\."));
			importTracer.setName(importName);
			r.insertBefore(importTracer, node);
			
			foundTracerImport = true;
			refactored = true;
		}
		
		if (refactored) return DO_NOT_VISIT_SUBTREE;
		
		else return VISIT_SUBTREE;
	}
    
    @Override
    public boolean visit(MethodDeclaration node) {
    	this.recycles.clear();
    	return true;
    }
    
    @Override
    public void endVisit(MethodDeclaration node) {
    	this.variables.clear();
    	super.endVisit(node);
    }
    
    
    @Override
   	public boolean visit(SingleVariableDeclaration node) {
       	this.variables.add(node);
   		return super.visit(node);
   	}

   	@Override
   	public boolean visit(VariableDeclarationFragment node) {
   		this.variables.add(node);
   		return super.visit(node);
   	}
	
	private boolean isMethodIgnoringParameters(MethodInvocation node, String typeQualifiedName, String methodName){
		if (node == null) {
            return false;
        }
		
		
        if (!methodName.equals(node.getName().getIdentifier())){
        	return false;
        }
        
        List<VariableDeclaration> allVars = new ArrayList<>(fields);
		allVars.addAll(variables);
		
        return COEvolgy.instanceOf(node, typeQualifiedName, mainNode, allVars);
	}
	
	private boolean isMethodIgnoringParameters(MethodInvocation node, String typeQualifiedName, String[] methodNames){
		boolean isSameMethod;
		
		for(String methodName: methodNames){
			isSameMethod = isMethodIgnoringParameters(node, typeQualifiedName, methodName);
			if(isSameMethod){
				return true;
			}
		}
		
		return false;
	}

	private String methodNameToCleanupResource(MethodInvocation node){
		List<VariableDeclaration> allVars = new ArrayList<>(fields);
		allVars.addAll(variables);
		
		if(isMethodIgnoringParameters(
			node,
			"android.database.sqlite.SQLiteDatabase",
			new String[]{"query","rawQuery","queryWithFactory","rawQueryWithFactory"})
		){
			return "close";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.content.ContentProvider",
			new String[]{"query","rawQuery","queryWithFactory","rawQueryWithFactory"})
		){
			return "close";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.content.ContentResolver",
			new String[]{"query","rawQuery","queryWithFactory","rawQueryWithFactory"})
		){
			return "close";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.content.ContentProviderClient",
			new String[]{"query","rawQuery","queryWithFactory","rawQueryWithFactory"})
		){
			return "close";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.content.Context",
			"obtainStyledAttributes")
		){
			return "recycle";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.content.res.Resources",
			new String[]{"obtainTypedArray","obtainAttributes","obtainStyledAttributes"})
		){
			return "recycle";
		}
		else if(COEvolgy.isMethod(
			node,
			mainNode,
			allVars,
			"android.view.VelocityTracker",
			"obtain")
		){
			return "recycle";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.os.Handler",
			"obtainMessage")
		){
			return "recycle";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.os.Message",
			"obtain")
		){
			return "recycle";
		}
		else if(COEvolgy.isMethod(
			node,
			mainNode,
			allVars,
			"android.view.MotionEvent",
			"obtainNoHistory", "android.view.MotionEvent")
		){
			return "recycle";
		}
		else if(isMethodIgnoringParameters(
			node,
			"android.view.MotionEvent",
			"obtain")
		){
			return "recycle";
		}
		else if(COEvolgy.isMethod(
			node,
			mainNode,
			allVars,
			"android.os.Parcel",
			"obtain")
		){
			return "recycle";
		}

		else if(isMethodIgnoringParameters(
			node,
			"android.content.ContentResolver",
			"acquireContentProviderClient")
		){
			return "release";
		}
		return null;
	}
	
	@Override
	public boolean visit(ReturnStatement node) {
		if (alreadyVisitedCU) return VISIT_SUBTREE;
		
		final ASTBuilder b = this.ctx.getASTBuilder();
		final Refactorings r = this.ctx.getRefactorings();
		
		for (SimpleName cursorExpression : this.recycles.keySet()) {
			String recycleCallName = this.recycles.get(cursorExpression);
			MethodInvocation closeInvocation = b.getAST().newMethodInvocation();
			
			closeInvocation.setName(b.simpleName(recycleCallName));
			closeInvocation.setExpression(b.copy(cursorExpression));
			ExpressionStatement expressionStatement = b.getAST().newExpressionStatement(closeInvocation);
			MethodDeclaration methodDecl = (MethodDeclaration) ASTNodes.getParent(node, ASTNode.METHOD_DECLARATION);
		
			countRecycles++;
			if (node.getExpression().getNodeType() == ASTNode.METHOD_INVOCATION 
					&& COEvolgy.isSameLocalVariable(((MethodInvocation)node.getExpression()).getExpression(), cursorExpression) ) {
				Type returnType = methodDecl.getReturnType2();
				SimpleName auxVarName = b.simpleName("__aux_" + countRecycles);
				VariableDeclarationStatement auxVarDecl = b.declareStmt(b.copy(returnType), 
																		auxVarName, 
																		b.copy(node.getExpression()));
				// [Insert trace statement]
				if (operationFlag == TRACE) {
					COEvolgy helper = new COEvolgy(this.ctx, false);
					ASTNode traceNode = helper.buildTraceNode(TAG);
					r.insertBefore(traceNode, node);
				}
				r.insertBefore(auxVarDecl, node);
				r.insertBefore(expressionStatement, node);
				r.insertBefore(b.return0(b.copy(auxVarName)), node);
				
				r.remove(node);
			} else {
				// [Insert trace statement]
				if (operationFlag == TRACE) {
					COEvolgy helper = new COEvolgy(this.ctx, false);
					ASTNode traceNode = helper.buildTraceNode(TAG);
					r.insertBefore(traceNode, node);
				}
				r.insertBefore(expressionStatement, node);
				
				return DO_NOT_VISIT_SUBTREE;
    			
			}
			COEvolgy.traceRefactoring(TAG);
		
			return DO_NOT_VISIT_SUBTREE;
		}
		return VISIT_SUBTREE;
	}
	
    @Override
    public boolean visit(MethodInvocation node) {
    	if (alreadyVisitedCU) return VISIT_SUBTREE;
    	
		String recycleCallName = methodNameToCleanupResource(node);
		if(recycleCallName != null){
			ASTNode variableAssignmentNode = null;
			VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) ASTNodes.getParent(node, ASTNode.VARIABLE_DECLARATION_FRAGMENT);
			SimpleName cursorExpression;
			if(variableDeclarationFragment!=null){
				cursorExpression = variableDeclarationFragment.getName();
				variableAssignmentNode = variableDeclarationFragment;
			}
			else{
				Assignment variableAssignment = (Assignment) ASTNodes.getParent(node, ASTNode.ASSIGNMENT);
				cursorExpression = (SimpleName) variableAssignment.getLeftHandSide();
				variableAssignmentNode = variableAssignment;
			}
			// Check whether it has been closed
			ClosePresenceChecker closePresenceChecker = new ClosePresenceChecker(cursorExpression, recycleCallName);
			VisitorDecorator visitor = new VisitorDecorator(variableAssignmentNode, cursorExpression, closePresenceChecker);
    		Block block = (Block) ASTNodes.getParent(node, ASTNode.BLOCK);
    		block.accept(visitor);
    		if(!closePresenceChecker.closePresent){
    			this.recycles.put(cursorExpression, recycleCallName);
        		/*if(lastCursorAccess.getNodeType() != ASTNode.RETURN_STATEMENT){
        			expressionStatement.accept(this);
        			r.insertAfter(expressionStatement, lastCursorAccess);
        			
        			// [Insert trace statement]
        			COEvolgy.traceRefactoring(TAG);
    				if (operationFlag == TRACE) {
    					COEvolgy helper = new COEvolgy(this.ctx, false);
    					ASTNode traceNode = helper.buildTraceNode(TAG);
    					r.insertAfter(traceNode, lastCursorAccess);
    				}
        			return DO_NOT_VISIT_SUBTREE;
        		}*/
    		}
    	}
    	return VISIT_SUBTREE;
    }

	public class ClosePresenceChecker extends ASTVisitor {
    	public boolean closePresent;
    	private SimpleName lastCursorUse;
    	private SimpleName cursorSimpleName;
		private String recycleMethodName;
    	
    	
    	public ClosePresenceChecker(SimpleName cursorSimpleName, String recycleMethodName) {
			super();
			this.closePresent = false;
			this.lastCursorUse = null;
			this.cursorSimpleName = cursorSimpleName;
			this.recycleMethodName = recycleMethodName;
		}
    	
    	/* Returns the last statement in the block where a variable
    	 * was accessed before being assigned again or destroyed. 
         */
    	public Statement getLastCursorStatementInBlock(Block block){
    		ASTNode lastCursorStatement = this.lastCursorUse.getParent();
    		while(lastCursorStatement!=null &&
    			!block.statements().contains(lastCursorStatement)
    		){
    			lastCursorStatement = lastCursorStatement.getParent();
    		}
    		return (Statement) lastCursorStatement;
    	}
    	
    	
    	public Statement getLastCursorStatementInBlock(MethodDeclaration node) {
    		Block block = node.getBody();
    		return getLastCursorStatementInBlock(block);
    	}


		@Override
        public boolean visit(MethodInvocation node) {
    		if(COEvolgy.isSameLocalVariable(cursorSimpleName, node.getExpression())){
    			if(this.recycleMethodName.equals(node.getName().getIdentifier())){
    				this.closePresent=true;
    				return DO_NOT_VISIT_SUBTREE;    				
    			}
   			}
    		return VISIT_SUBTREE;
    	}
		
		@Override
        public boolean visit(SimpleName node) {
    		if(COEvolgy.isSameLocalVariable(node, cursorSimpleName)){        			
    			this.lastCursorUse = node;
    		}
    		return VISIT_SUBTREE;
    	}
		
		@Override
        public boolean visit(Assignment node) {
			if(COEvolgy.isSameLocalVariable(node.getLeftHandSide(), cursorSimpleName)){       
				return DO_NOT_VISIT_SUBTREE;
			}
			return VISIT_SUBTREE;
		}
		
		
    }
	
	/*
	 * This visitor selects a partial part of the block to make the visit
	 * I.e., it will only analyze the visitor from the variable assignment
	 *  until the next assignment or end of the block
	 *  startNode is a Assignment or VariableDeclarationFragment
	 */
	public class VisitorDecorator extends ASTVisitor {
		private ASTVisitor visitor;
		private SimpleName cursorSimpleName;
		public ASTVisitor specialVisitor;
		private ASTNode startNode;
		
		public class NopVisitor extends ASTVisitor{}
		
		VisitorDecorator(ASTNode startNode, SimpleName cursorSimpleName, ASTVisitor specialVisitor){
			this.cursorSimpleName = cursorSimpleName;
			this.specialVisitor = specialVisitor;
			this.startNode = startNode;
			this.visitor = new NopVisitor();
		}
		
		@Override
        public boolean visit(Assignment node) {
			if(node.equals(startNode)){ 
				visitor = specialVisitor;
			}
			else if(visitor != null && COEvolgy.isSameLocalVariable(node.getLeftHandSide(), cursorSimpleName)){
				visitor = new NopVisitor();
			}
			return visitor.visit(node);
		}
		
		@Override
        public boolean visit(VariableDeclarationFragment node) {
			if(node.equals(startNode)){ 
				visitor = specialVisitor;
			}
			return visitor.visit(node);
		}
		
		@Override
        public boolean visit(SimpleName node) {
			return visitor.visit(node);
		}
		
		@Override
        public boolean visit(MethodInvocation node) {
			return visitor.visit(node);
		}
	}
	

}
