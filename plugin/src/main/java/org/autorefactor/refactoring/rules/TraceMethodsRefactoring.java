package org.autorefactor.refactoring.rules;

import static org.autorefactor.refactoring.ASTHelper.DO_NOT_VISIT_SUBTREE;
import static org.autorefactor.refactoring.ASTHelper.VISIT_SUBTREE;
import static org.autorefactor.util.COEvolgy.MEASURE;
import static org.autorefactor.util.COEvolgy.TRACE;

import java.util.ArrayList;
import java.util.List;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.ASTHelper;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class TraceMethodsRefactoring extends AbstractRefactoringRule {

	public static final String TAG = "ObsoleteLayoutParam";
	private static boolean foundTracerImport = false;
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	private static int operationFlag = MEASURE;
	
	private String packageName;
	private String fileName;
	private String className;
	private String qualifiedName;
	private List<String> fullMethodNames;
	
	private boolean insideMethod;
	private boolean insertedTraceNode;
	private boolean isMethodConstructor;
	
	public TraceMethodsRefactoring () {
		super();
		
		this.packageName = "";
		this.fileName = "";
		this.className = "";
		this.qualifiedName = "";
		this.fullMethodNames = new ArrayList<>();
		
		this.insideMethod = false;
		this.insertedTraceNode = false;
		this.isMethodConstructor = false;
	}
	
	public TraceMethodsRefactoring (int flag) {
		super();
		
		operationFlag = flag;
		
		this.packageName = "";
		this.fileName = "";
		this.className = "";
		this.qualifiedName = "";
		this.fullMethodNames = new ArrayList<>();
		
		this.insideMethod = false;
		this.insertedTraceNode = false;
		this.isMethodConstructor = false;
	}
	
	@Override
	public String getName() {
		return "TraceMethodsRefactoring";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	public String getReason() {
		return "";
	}
	
	
	@Override
    public boolean visit(CompilationUnit node) {
		List<ImportDeclaration> allImports = node.imports();
		fileName = node.getJavaElement().getElementName();
		packageName = node.getPackage().getName().getFullyQualifiedName();
		foundTracerImport = COEvolgy.isImportIncluded(allImports, tracerImport);
		
		return VISIT_SUBTREE;
	}
	
	@Override
	public boolean visit(TypeDeclaration node) {
		className = node.getName().getIdentifier();
		
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
	
	
	@Override
	public boolean visit(Block node) {
		final ASTBuilder b = this.ctx.getASTBuilder();
		final Refactorings r = this.ctx.getRefactorings();
		
		if (operationFlag == TRACE 
				&& this.insideMethod 
				&& !this.insertedTraceNode 
				&& !(this.fullMethodNames.contains(qualifiedName))
				&& foundTracerImport) {
			int insertIndex = 0;
			if (this.isMethodConstructor) {
				insertIndex = Math.min(1, node.statements().size());
			}
			COEvolgy helper = new COEvolgy(this.ctx, false);
			ASTNode traceMethodNode = helper.buildTraceMethodNode(this.qualifiedName);

			r.insertAt(node,
					Block.STATEMENTS_PROPERTY,
					traceMethodNode,
					insertIndex
					);
			this.insertedTraceNode = true;
			
			return ASTHelper.DO_NOT_VISIT_SUBTREE;
		}
		
		return ASTHelper.VISIT_SUBTREE;
	}
	
	@Override
	public boolean visit(MethodDeclaration node) {
		this.qualifiedName = this.packageName+"."
						   + this.fileName+":"
						   + this.className+"$" + node.getName().getIdentifier();
		
		this.insideMethod = true;
		this.isMethodConstructor = node.isConstructor();
		this.insertedTraceNode = false;
		
		return ASTHelper.VISIT_SUBTREE;
	}
	
	public void endVisit(MethodDeclaration node) {
		this.insideMethod = false;
		this.fullMethodNames.add(this.qualifiedName);
	}	
	

}
