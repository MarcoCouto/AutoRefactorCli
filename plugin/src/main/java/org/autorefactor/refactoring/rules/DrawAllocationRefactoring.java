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
import static org.autorefactor.refactoring.ASTHelper.isMethod;
import static org.autorefactor.util.COEvolgy.MEASURE;
import static org.autorefactor.util.COEvolgy.TRACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.ASTHelper;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;



/** See {@link #getDescription()} method. */
public class DrawAllocationRefactoring extends AbstractRefactoringRule {

	public static final String TAG = "DrawAllocation";
	
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	private static boolean foundTracerImport = false;
	
	private static CompilationUnit mainNode = null;
	private static int operationFlag = MEASURE;
	private static Set<String> fields;
	
	public DrawAllocationRefactoring() {
		super();
	}
	
	public DrawAllocationRefactoring(int flag) {
		super();
		operationFlag = flag;
	}

	
	@Override
	public String getDescription() {
		return "Optimization for Android applications to avoid the allocation of"
				+ " objects inside drawing routines. ";
	}

	@Override
	public String getName() {
		return "DrawAllocationRefactoring";
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
    	List<ImportDeclaration> allImports = node.imports();
		foundTracerImport = COEvolgy.isImportIncluded(allImports, tracerImport);
		mainNode = node;
		
		ClassVarsFinder varsFinder = new ClassVarsFinder();
		node.accept(varsFinder);
		fields = varsFinder.classVars;
		return VISIT_SUBTREE;
    }
    
    @Override
    public boolean visit(ImportDeclaration node) {
		final ASTBuilder b = ctx.getASTBuilder();
		final Refactorings r = ctx.getRefactorings();
		boolean refactored = false;

		if (operationFlag == TRACE && !foundTracerImport) {
			ImportDeclaration importTracer = r.getAST().newImportDeclaration();
			Name importName = b.name(tracerImport.split("\\."));
			importTracer.setName(importName);
			r.insertBefore(importTracer, node);
			
			foundTracerImport = true;
			refactored = true;
		}
		
		if (refactored) return ASTHelper.DO_NOT_VISIT_SUBTREE;
		
		else return ASTHelper.VISIT_SUBTREE;
	}
	
	public boolean visit(MethodDeclaration node) {
		
		if (isMethodonDraw(node, node.getName().getIdentifier(), ASTHelper.getEnclosingType(node))) {
			final ASTBuilder b = this.ctx.getASTBuilder();
			final Refactorings r = this.ctx.getRefactorings();
			
			node.accept(new OnDrawTransformer(this.ctx, node));
		}
		return VISIT_SUBTREE;
	}
	
	/* HELPER */
	private boolean isMethodonDraw(MethodDeclaration node, String methodName, ASTNode typeDeclaration) {
		IMethodBinding methodBinding = node.resolveBinding();
		
		boolean isViewExtended = false;
		if (!methodName.equals("onDraw")) {
			return false;
		}
		
		if (methodBinding != null) {
			return isMethod(methodBinding, "android.view.View", "onDraw", "android.graphics.Canvas");
			
		} else {
			List<ImportDeclaration> imports = mainNode.imports();
			List<String> typesToCheck = new ArrayList<>(COEvolgy.androidExtendables.get("android.view.View"));
			typesToCheck.add("android.view.View");
			return COEvolgy.isClassExtendedBy(typeDeclaration, typesToCheck, imports);
			
		}
		
	}
	
	static class OnDrawTransformer extends ASTVisitor{
		private RefactoringContext ctx;
		private MethodDeclaration onDrawDeclaration;
		private Map<String, ASTNode> variables;
		private List<String> movedVariables;
		
		public OnDrawTransformer(RefactoringContext ctx, MethodDeclaration onDrawDeclaration){
			this.ctx=ctx;
			this.onDrawDeclaration = onDrawDeclaration;
			this.variables = new HashMap<>();
			this.movedVariables = new ArrayList<>();
		}
		
		//recheck this -- getSupercLass is not working properly
		@Deprecated /*by @Marco Couto*/
		public boolean isTypeBindingSubclassOf(ITypeBinding typeBinding, List<String> superClassStrings){
			ITypeBinding superClass = typeBinding;
			while(superClass!= null && !superClass.equals(ctx.getAST().resolveWellKnownType("java.lang.Object"))){
				String className = superClass.getName();
				if(className.contains("<")){
					className = className.split("<",2)[0];
				}
				if(superClassStrings.contains(className)){
					return true;
				}
				superClass = superClass.getSuperclass();
			}
			return false;
		}
		
		@Override
		public boolean visit(VariableDeclarationFragment node) {
			final ASTBuilder b = this.ctx.getASTBuilder();
			final Refactorings r = this.ctx.getRefactorings();
			
			Expression initializer = node.getInitializer();
			variables.put(node.getName().getIdentifier(), COEvolgy.getParentStatement(node));
			if(initializer != null){
				if(initializer.getNodeType() == ASTNode.CAST_EXPRESSION){
					initializer = ((CastExpression)initializer).getExpression();
				}
				InitializerVisitor initializerVisitor = new InitializerVisitor(this.variables.keySet());
				initializer.accept(initializerVisitor);
				if(initializerVisitor.initializerCanBeExtracted){
					if(initializer.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION || initializer.getNodeType() == ASTNode.METHOD_INVOCATION){
						Statement declarationStatement = (Statement) ASTNodes.getParent(node, ASTNode.VARIABLE_DECLARATION_STATEMENT);
						if(declarationStatement != null){
							// Deal with collections
							if (COEvolgy.isCollection(node)) {
								// allocate object outside onDraw
								for (String var : initializerVisitor.varsToExtract) {
									if (!movedVariables.contains(var) ) {
										ASTNode nodeToMove = this.variables.get(var);
										r.insertBefore(b.move(nodeToMove), onDrawDeclaration);
										this.movedVariables.add(var);
									}
								}
								r.insertBefore(b.move(declarationStatement), onDrawDeclaration);
								movedVariables.add(node.getName().getIdentifier());
								
								// [Insert trace statement]
								COEvolgy.traceRefactoring(TAG);
			    				if (operationFlag == TRACE) {
			    					COEvolgy helper = new COEvolgy(this.ctx, false);
			    					ASTNode traceNode = helper.buildTraceNode(TAG);
			    					r.insertAt(onDrawDeclaration.getBody(),
			        						Block.STATEMENTS_PROPERTY,
			        						traceNode,
			        						0
			        						);
			    				}
								// call collection.clear() in the end of onDraw
								ASTNode clearNode = b.getAST().newExpressionStatement(
									b.invoke(node.getName().getIdentifier(), "clear")
								);
								List bodyStatements = onDrawDeclaration.getBody().statements();
								Statement lastStatement = (Statement) bodyStatements.get(bodyStatements.size() - 1);
								int whereToInsertClearStatement = bodyStatements.size();
								if(ASTNode.RETURN_STATEMENT == lastStatement.getNodeType()){
									whereToInsertClearStatement -= 1;
								}
								r.insertAt(onDrawDeclaration.getBody(), Block.STATEMENTS_PROPERTY, clearNode, whereToInsertClearStatement);
							
							}
							else{
								for (String var : initializerVisitor.varsToExtract) {
									if (!movedVariables.contains(var) ) {
										ASTNode nodeToMove = this.variables.get(var);
										r.insertBefore(b.move(nodeToMove), onDrawDeclaration);
										this.movedVariables.add(var);
									}
								}
								r.insertBefore(b.move(declarationStatement), onDrawDeclaration);
								this.movedVariables.add(node.getName().getIdentifier());
								
								// [Insert trace statement]
								COEvolgy.traceRefactoring(TAG);
			    				if (operationFlag == TRACE) {
			    					COEvolgy helper = new COEvolgy(this.ctx, false);
			    					ASTNode traceNode = helper.buildTraceNode(TAG);
			    					r.insertAt(onDrawDeclaration.getBody(),
			        						Block.STATEMENTS_PROPERTY,
			        						traceNode,
			        						0
			        						);
			    				}
							}
						}
						//
						return DO_NOT_VISIT_SUBTREE;
					}
				} else {
					this.variables.remove(node.getName().getIdentifier());
				}
			}
			return VISIT_SUBTREE;
		}
		
		@Override
		public boolean visit(Assignment node) {
			final ASTBuilder b = this.ctx.getASTBuilder();
			final Refactorings r = this.ctx.getRefactorings();
			
			String varLeftHS = COEvolgy.getVarFromExpression(node.getLeftHandSide(), fields);
			Expression right = node.getRightHandSide();
			
			if (this.variables.containsKey(varLeftHS)) {
				InitializerVisitor initializerVisitor = new InitializerVisitor(variables.keySet());
				right.accept(initializerVisitor);
				if (initializerVisitor.initializerCanBeExtracted) {
					if (right.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION || right.getNodeType() == ASTNode.METHOD_INVOCATION) {
						Statement statement = (Statement) variables.get(varLeftHS);
						if(statement!= null && statement.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT){
							VariableDeclarationStatement declarationStatement = (VariableDeclarationStatement) statement;
							//Deal with collections
							if (COEvolgy.isCollection(declarationStatement)) {
								//allocate object outside onDraw
								for (String var : initializerVisitor.varsToExtract) {
									if (!movedVariables.contains(var)) {
										ASTNode nodeToMove = this.variables.get(var);
										r.insertBefore(b.move(nodeToMove), onDrawDeclaration);
										this.movedVariables.add(var);
									}
								}
								Type type = COEvolgy.copyType(declarationStatement.getType(), b);
								VariableDeclarationStatement newDecl = b.declareStmt(type, b.simpleName(varLeftHS), b.copy(right));
								r.insertBefore(newDecl, onDrawDeclaration);
								r.remove(declarationStatement);
								r.remove(node);
								this.movedVariables.add(varLeftHS);
								
								// [Insert trace statement]
								COEvolgy.traceRefactoring(TAG);
			    				if (operationFlag == TRACE) {
			    					COEvolgy helper = new COEvolgy(this.ctx, false);
			    					ASTNode traceNode = helper.buildTraceNode(TAG);
			    					r.insertAt(onDrawDeclaration.getBody(),
			        						Block.STATEMENTS_PROPERTY,
			        						traceNode,
			        						0
			        						);
			    				}
								// call collection.clear() in the end of onDraw
								ASTNode clearNode = b.getAST().newExpressionStatement(
									b.invoke(b.simpleName(varLeftHS), "clear")
								);
								List bodyStatements = onDrawDeclaration.getBody().statements();
								Statement lastStatement = (Statement) bodyStatements.get(bodyStatements.size() - 1);
								int whereToInsertClearStatement = bodyStatements.size();
								if(ASTNode.RETURN_STATEMENT == lastStatement.getNodeType()){
									whereToInsertClearStatement -= 1;
								}
								r.insertAt(onDrawDeclaration.getBody(), Block.STATEMENTS_PROPERTY, clearNode, whereToInsertClearStatement);
							
							}
							else{
								for (String var : initializerVisitor.varsToExtract) {
									if (!movedVariables.contains(var) ) {
										ASTNode nodeToMove = this.variables.get(var);
										r.insertBefore(b.move(nodeToMove), onDrawDeclaration);
										this.movedVariables.add(var);
									}
								}
								Type type = COEvolgy.copyType(declarationStatement.getType(), b);
								VariableDeclarationStatement newDecl = b.declareStmt(type, b.simpleName(varLeftHS), b.copy(right));
								r.insertBefore(newDecl, onDrawDeclaration);
								r.remove(declarationStatement);
								r.remove(node);
								this.movedVariables.add(varLeftHS);
								
								// [Insert trace statement]
								COEvolgy.traceRefactoring(TAG);
			    				if (operationFlag == TRACE) {
			    					COEvolgy helper = new COEvolgy(this.ctx, false);
			    					ASTNode traceNode = helper.buildTraceNode(TAG);
			    					r.insertAt(onDrawDeclaration.getBody(),
			        						Block.STATEMENTS_PROPERTY,
			        						traceNode,
			        						0
			        						);
			    				}
							}
						}
						//
						return DO_NOT_VISIT_SUBTREE;
					}
				}
			}
			
			return VISIT_SUBTREE;
		}
	}
	
	static class InitializerVisitor extends ASTVisitor{
		public boolean initializerCanBeExtracted=true;
		private Set<String> localVariables;
		public Set<String> varsToExtract;
		private Stack<String> methodNames;
		
		public InitializerVisitor(Set<String> variables) {
			this.localVariables = variables;
			this.varsToExtract = new HashSet<>();
			this.methodNames = new Stack<>();
		}
		
		@Override
		public boolean visit(MethodInvocation node) {
			String varName = COEvolgy.getVarFromExpression(node, fields);
			methodNames.push(COEvolgy.getMethodName(node));
			
			if (Character.isUpperCase(varName.charAt(0))) {
				// this means the method call is a static one, 
				// which means the variable can be extracted.
				
				return VISIT_SUBTREE;
			} else if (this.localVariables.contains(varName)) {
				// the variable on which the method is called 
				// is a local one, so everything is good!
				// we just need to mark it for extraction. 
				varsToExtract.add(varName);
				return VISIT_SUBTREE;
			} else {
				// if it enters here, it means one of two things:
				//    - the variable of the method call is a "field" (varName == "this")
				//    - the method call is a method from the same class
				initializerCanBeExtracted=false;
				return DO_NOT_VISIT_SUBTREE;
			}
		}
		
		@Override
		public void endVisit(MethodInvocation node) {
			this.methodNames.pop();
		}
		
		
		@Override
		public boolean visit(SimpleType node) {
			return DO_NOT_VISIT_SUBTREE;
		}
		
		@Override
		public boolean visit(SimpleName node) {
			String varName = COEvolgy.getVarFromExpression(node, fields);
			if (this.localVariables.contains(varName)) {
				this.varsToExtract.add(varName);
				return VISIT_SUBTREE;
			} else if (Character.isUpperCase(varName.charAt(0))) {
				// we're looking at the name of a class, 
				// certainly used for calling a static method, 
				// or to create an object (`new Obj()`).
				return VISIT_SUBTREE;
			} else if (!this.methodNames.isEmpty() && this.methodNames.peek().equals(varName)) {
				return VISIT_SUBTREE;
			} else {
				initializerCanBeExtracted=false;
				return DO_NOT_VISIT_SUBTREE;
			}
		}
	}
	
	static class ClassVarsFinder extends ASTVisitor{
		public boolean initializerCanBeExtracted=true;
		public Set<String> classVars;
		
		public ClassVarsFinder() {
			this.classVars = new HashSet<>();
		}
		
		@Override
		public boolean visit(FieldDeclaration node) {
        	for (Object o : node.fragments()) {
        		VariableDeclarationFragment varDecl = (VariableDeclarationFragment) o;
        		classVars.add(varDecl.getName().getIdentifier());
        	}
			
        	return VISIT_SUBTREE;
		}
	}
    
    
}
