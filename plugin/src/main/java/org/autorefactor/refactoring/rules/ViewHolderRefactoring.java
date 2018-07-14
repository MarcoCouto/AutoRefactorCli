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
import java.util.LinkedList;
import java.util.List;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

/* 
 * TODO when findViewById is reusing a local variable,
 * the viewholderitem will create a new field with duplicate name.
 * Possible solution: use the id names instead of var names
 */

/** See {@link #getDescription()} method. */
public class ViewHolderRefactoring extends AbstractRefactoringRule {

	public static final String TAG = "ViewHolder";
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	private static boolean foundTracerImport = false;
	
	private static CompilationUnit mainNode = null;
	private static int operationFlag = MEASURE;
	
	private List<VariableDeclaration> fields;
	private List<VariableDeclaration> variables;
	
	public ViewHolderRefactoring() {
		super();
	}
	
	public ViewHolderRefactoring(int flag) {
		super();
		operationFlag = flag;
	}

	
	@Override
	public String getDescription() {
		return "Optimization for Android applications to optimize getView routines. "
				+ "It allows reducing the calls to inflate and getViewById Android "
				+ "API methods.";
	}

	@Override
	public String getName() {
		return "ViewHolderRefactoring";
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
		
		this.fields = new ArrayList<>();
		this.variables = new ArrayList<>();
		
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
		
		if (refactored) return DO_NOT_VISIT_SUBTREE;
		
		else return VISIT_SUBTREE;
	}
    
    @Override
    public boolean visit(TypeDeclaration node) {
    	for (FieldDeclaration field : node.getFields()) {
    		for (Object o : field.fragments()) {
    			if (o instanceof VariableDeclarationFragment) {
    				this.fields.add((VariableDeclarationFragment) o);
    			}
    		}
    	}
    	return VISIT_SUBTREE;
    }
	
    @Override
    public boolean visit(MethodDeclaration node) {
		final ASTBuilder b = this.ctx.getASTBuilder();
		final Refactorings r = this.ctx.getRefactorings();
		List<VariableDeclaration> allVars = new ArrayList<>(fields);
		allVars.addAll(variables);
		
		if(COEvolgy.isMethod(
					node,
					mainNode,
					allVars,
					"android.widget.Adapter",
					"getView",
					"int","android.view.View","android.view.ViewGroup"
			)
		){
			GetViewVisitor visitor = new GetViewVisitor();
			VariablesVisitor gatherer = new VariablesVisitor();
			// First of all, collect information about the variables in this methods.
			node.accept(gatherer);
			allVars = new ArrayList<>(fields);
			allVars.addAll(variables);
			// Now, look at the contents of it, to look for places where to use the ViewHolder pattern.
			Block body = node.getBody();
			if(body != null){
				body.accept(visitor);
				if(!visitor.usesConvertView &&
					visitor.viewVariable != null &&
					!visitor.isInflateInsideIf()){
					
					// Transform tree
					
					//Create If statement
			    	IfStatement ifStatement = b.getAST().newIfStatement();
			    	// test-clause
			    	InfixExpression infixExpression = b.getAST().newInfixExpression();
			    	infixExpression.setOperator(InfixExpression.Operator.EQUALS);
			    	infixExpression.setLeftOperand(b.simpleName("convertView"));
			    	infixExpression.setRightOperand(b.getAST().newNullLiteral());
					ifStatement.setExpression(infixExpression);
					//then
					Assignment assignment = b.assign(b.simpleName("convertView"), Assignment.Operator.ASSIGN, b.copy(visitor.getInflateExpression()));
					Block thenBlock = b.block(b.getAST().newExpressionStatement(assignment));
					ifStatement.setThenStatement(thenBlock);
					
					// [Insert trace statement]
					COEvolgy.traceRefactoring(TAG);
					if (operationFlag == TRACE) {
						COEvolgy helper = new COEvolgy(this.ctx, false);
    					ASTNode traceNode = helper.buildTraceNode(TAG);
						r.insertBefore(traceNode, visitor.viewAssignmentStatement);
					}
					r.insertBefore(ifStatement, visitor.viewAssignmentStatement);
					
					// assign to local view variable when necessary
					if(!"convertView".equals(visitor.viewVariable.getIdentifier())){
						Statement assignConvertViewToView = null;
						if(visitor.viewVariableDeclarationFragment != null){
							assignConvertViewToView = b.declareStmt(b.type(COEvolgy.typeOf(visitor.viewVariable.getIdentifier(), allVars)), b.copy(visitor.viewVariable), b.simpleName("convertView"));
						}
						else if(visitor.viewVariableAssignment != null){
							assignConvertViewToView = b.getAST().newExpressionStatement(b.assign(b.copy(visitor.viewVariable), Assignment.Operator.ASSIGN, b.simpleName("convertView")));
						}
						if(assignConvertViewToView != null){
							r.insertBefore(assignConvertViewToView, visitor.viewAssignmentStatement);
						}
					}
					
					// make sure method returns the view to be reused DELETEME
					if(visitor.returnStatement != null){
						r.insertAfter(b.return0(b.copy(visitor.viewVariable)), visitor.returnStatement);
						r.remove(visitor.returnStatement);
					}
					
					//Optimize findViewById calls
					FindViewByIdVisitor findViewByIdVisitor = new FindViewByIdVisitor();
					body.accept(findViewByIdVisitor);
					if(findViewByIdVisitor.items.size() > 0){
						//create ViewHolderItem class
						TypeDeclaration viewHolderItemDeclaration = b.getAST().newTypeDeclaration();
						viewHolderItemDeclaration.setName(b.simpleName("ViewHolderItem"));
						List<ASTNode> viewItemsDeclarations = viewHolderItemDeclaration.bodyDeclarations();
						for(FindViewByIdVisitor.FindViewByIdItem item : findViewByIdVisitor.items){
							
							VariableDeclarationFragment declarationFragment = b.getAST().newVariableDeclarationFragment();
							SimpleName simpleName = b.simpleName(item.variable.getIdentifier());
							declarationFragment.setName(simpleName);
							FieldDeclaration fieldDeclaration = b.getAST().newFieldDeclaration(declarationFragment);
							fieldDeclaration.setType(
								b.getAST().newSimpleType(
									b.simpleName(COEvolgy.typeOf(item.variable.getIdentifier(), allVars))
								)
							);
							viewItemsDeclarations.add(
								fieldDeclaration
							);
						}
						viewHolderItemDeclaration.modifiers().add(
							b.getAST().newModifier(ModifierKeyword.STATIC_KEYWORD)
						);
						r.insertBefore(viewHolderItemDeclaration, node);
						// create viewhHolderItem object
						VariableDeclarationStatement viewHolderItemVariableDeclaration = b.declareStmt(b.type("ViewHolderItem"), b.simpleName("viewHolderItem"), null);
						r.insertAt(body, Block.STATEMENTS_PROPERTY, viewHolderItemVariableDeclaration, 0);
						//initialize viewHolderItem
						Assignment viewHolderItemInitialization = b.assign(b.simpleName("viewHolderItem"), Assignment.Operator.ASSIGN, b.new0("ViewHolderItem"));
						thenBlock.statements().add(b.getAST().newExpressionStatement(viewHolderItemInitialization));
						//  Assign findViewById to ViewHolderItem
						for(FindViewByIdVisitor.FindViewByIdItem item : findViewByIdVisitor.items){
							//ensure we are accessing to convertView object
							QualifiedName qualifiedName = b.getAST().newQualifiedName(b.name("viewHolderItem"), b.simpleName(item.variable.getIdentifier()));
							item.findViewByIdInvocation.setExpression(b.simpleName("convertView"));
							Assignment itemAssignment = b.assign(qualifiedName, Assignment.Operator.ASSIGN, (Expression)ASTNode.copySubtree(b.getAST(), item.findViewByIdExpression));
							
							thenBlock.statements().add(b.getAST().newExpressionStatement(itemAssignment));
							
							//replace previous fidnviewbyid with accesses to viewHolderItem
							QualifiedName viewHolderItemFieldAccessQualifiedName = b.getAST().newQualifiedName(b.name("viewHolderItem"), b.simpleName(item.variable.getIdentifier()));
							r.replace(item.findViewByIdExpression, b.copy(qualifiedName));
						}
						//store viewHolderItem in convertView
						MethodInvocation setTagInvocation = b.invoke("convertView", "setTag", b.simpleName("viewHolderItem"));
						thenBlock.statements().add(b.getAST().newExpressionStatement(setTagInvocation));
						
						//retrieve viewHolderItem from convertView
						ifStatement.setElseStatement(b.block(b.getAST().newExpressionStatement(b.assign(
								b.simpleName("viewHolderItem"),
								Assignment.Operator.ASSIGN,
								b.cast(b.type("ViewHolderItem"), b.invoke("convertView", "getTag"))
						))));
						
					}
					r.remove(visitor.viewAssignmentStatement);
					return DO_NOT_VISIT_SUBTREE;
				}
			}
		}
    	return VISIT_SUBTREE;
    }
    
    @Override
    public void endVisit(MethodDeclaration node) {
    	this.variables.clear();
    	super.endVisit(node);
    }    
    
    public boolean isInflateMethod(MethodInvocation node){
    	List<VariableDeclaration> allVars = new ArrayList<>(fields);
		allVars.addAll(variables);
		
    	return  COEvolgy.isMethod(node, mainNode, allVars, "android.view.LayoutInflater", "inflate", "int", "android.view.ViewGroup")||
    			COEvolgy.isMethod(node, mainNode, allVars, "android.view.LayoutInflater", "inflate", "int", "android.view.ViewGroup","boolean")||
    			COEvolgy.isMethod(node, mainNode, allVars, "android.view.LayoutInflater", "inflate", "org.xmlpull.v1.XmlPullParser", "android.view.ViewGroup")||
    			COEvolgy.isMethod(node, mainNode, allVars, "android.view.LayoutInflater", "inflate", "org.xmlpull.v1.XmlPullParser", "android.view.ViewGroup", "boolean");
	}
    
	public class GetViewVisitor extends ASTVisitor {
		public boolean usesConvertView= false;
		public SimpleName viewVariable = null;
		public Statement viewAssignmentStatement; 
		public VariableDeclarationFragment viewVariableDeclarationFragment = null;
		public Assignment viewVariableAssignment = null;
		public  ReturnStatement returnStatement = null;
		GetViewVisitor(){
		}
		
		@Override
        public boolean visit(SimpleName node) {
			if (node.getIdentifier() == "convertView" &&
				ASTNodes.getParent(node, ASTNode.RETURN_STATEMENT) == null
			){
				this.usesConvertView = true;
				return DO_NOT_VISIT_SUBTREE;
			}
			return VISIT_SUBTREE;
		}
		
		public boolean visit(MethodInvocation node) {
			if(isInflateMethod(node)){
				this.viewVariableDeclarationFragment = (VariableDeclarationFragment) ASTNodes.getParent(node, ASTNode.VARIABLE_DECLARATION_FRAGMENT);
				if(viewVariableDeclarationFragment != null){
					this.viewVariable = viewVariableDeclarationFragment.getName();
					this.viewAssignmentStatement = (Statement) ASTNodes.getParent(viewVariableDeclarationFragment, ASTNode.VARIABLE_DECLARATION_STATEMENT);
				}
				else{
					this.viewVariableAssignment = (Assignment) ASTNodes.getParent(node, ASTNode.ASSIGNMENT);
					if(viewVariableAssignment!=null){
						this.viewVariable = (SimpleName) viewVariableAssignment.getLeftHandSide();
						this.viewAssignmentStatement = (ExpressionStatement) ASTNodes.getParent(viewVariableAssignment, ASTNode.EXPRESSION_STATEMENT);
					}
				}
				return DO_NOT_VISIT_SUBTREE;
			}
			return VISIT_SUBTREE;
		}
		
		public boolean visit(ReturnStatement node) {
			this.returnStatement = node;
			return VISIT_SUBTREE;
		}
		
		public boolean isInflateInsideIf(){
			if(this.viewAssignmentStatement != null){
				if(ASTNodes.getParent(this.viewAssignmentStatement, ASTNode.IF_STATEMENT) != null){
					return true;
				}
				else if(ASTNodes.getParent(this.viewAssignmentStatement, ASTNode.SWITCH_STATEMENT) != null){
					return true;
				}
				else{
					//check whether inflate is inside a conditional assignment
					Expression inflateExpression = this.getInflateExpression();
					if(inflateExpression!=null && inflateExpression.getNodeType() == ASTNode.CONDITIONAL_EXPRESSION){
						return true;
					}
				}
			}
			return false;
		}
		
		public Expression getInflateExpression(){
			if(this.viewVariableDeclarationFragment != null){
				return this.viewVariableDeclarationFragment.getInitializer();
			}
			else if(this.viewVariableAssignment != null){
				return this.viewVariableAssignment.getRightHandSide();
			}
			return null;
		}
		
	}
	
	
	private class FindViewByIdVisitor extends ASTVisitor{
		public List<FindViewByIdItem> items = new LinkedList<FindViewByIdItem>();
		
		FindViewByIdVisitor(){
		}
		
		private class FindViewByIdItem{
			SimpleName variable;
			Expression findViewByIdExpression;
			Statement findViewByIdAssignment;
			VariableDeclarationFragment findViewByIdDeclarationFragment;
			Assignment findViewByIdVariableAssignment;
			MethodInvocation findViewByIdInvocation;
			
			FindViewByIdItem(MethodInvocation node){
				this.setAssignment(node);
			}
			
			public void setAssignment(MethodInvocation node){
				this.findViewByIdInvocation = node;
				this.findViewByIdDeclarationFragment = (VariableDeclarationFragment) ASTNodes.getParent(node, ASTNode.VARIABLE_DECLARATION_FRAGMENT);
				if(this.findViewByIdDeclarationFragment!=null){
					this.variable = this.findViewByIdDeclarationFragment.getName();
					this.findViewByIdAssignment = (Statement) ASTNodes.getParent(this.findViewByIdDeclarationFragment, ASTNode.VARIABLE_DECLARATION_STATEMENT);
					this.findViewByIdExpression = this.findViewByIdDeclarationFragment.getInitializer();
				}
				else{
					this.findViewByIdVariableAssignment = (Assignment) ASTNodes.getParent(node, ASTNode.ASSIGNMENT);
					if(this.findViewByIdVariableAssignment!=null){
						this.variable = (SimpleName) this.findViewByIdVariableAssignment.getLeftHandSide();
						this.findViewByIdAssignment = (ExpressionStatement) ASTNodes.getParent(this.findViewByIdVariableAssignment, ASTNode.EXPRESSION_STATEMENT);
						this.findViewByIdExpression = this.findViewByIdVariableAssignment.getRightHandSide();
					}
				}
			}
		}
		
	    @Override
	    public boolean visit(MethodInvocation node) {
	    	List<VariableDeclaration> allVars = new ArrayList<>(fields);
			allVars.addAll(variables);
			
			if(COEvolgy.isMethod(node, mainNode, allVars, "android.view.View", "findViewById", "int")){
				FindViewByIdItem item = new FindViewByIdItem(node);
				items.add(item);
			}
			
			return VISIT_SUBTREE;
	    }
	}
	
	
	private class VariablesVisitor extends ASTVisitor {
		
		@Override
	   	public boolean visit(SingleVariableDeclaration node) {
	       	variables.add(node);
	   		return super.visit(node);
	   	}

	   	@Override
	   	public boolean visit(VariableDeclarationFragment node) {
	   		variables.add(node);
	   		return super.visit(node);
	   	}
	}
    
}
