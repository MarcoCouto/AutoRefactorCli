package org.autorefactor.refactoring.rules;

import static org.autorefactor.util.COEvolgy.MEASURE;
import static org.autorefactor.util.COEvolgy.TRACE;

import java.util.List;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.ASTHelper;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;


public class HashMapUsageRefactoring extends AbstractRefactoringRule {
	
	public static final String TAG = "HashMapUsage";

	private static boolean foundArrayImport = false;
	private static boolean foundTracerImport = false;
	private static final String newMapClass = "ArrayMap";
	private static final String arrayMapImport = "android.util.ArrayMap";
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	private static int operationFlag = MEASURE;
	
	public HashMapUsageRefactoring() {
		super();
	}
	
	public HashMapUsageRefactoring(int flag) {
		super();
		operationFlag = flag;
	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "HashMapUsageRefactoring";
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return "Using a `HashMap` data structure is discouraged. Most times they consume excessive " +
               "amounts of memory, a scarce resource os smartphones, which leads to an increase in " +
               "execution time and energy consumption.\n" +
               "\n" +
               "The use of a more lightweight alternative is advised: `ArrayMap`.";
	}

	/**
     * Get the reason.
     *
     * @return the reason.
     */
    public String getReason() {
        return "It improves the performance.";
    }
    
    /* VISITORS */

    @Override
    public boolean visit(CompilationUnit node) {    	
    	List<ImportDeclaration> allImports = node.imports();
    	foundArrayImport = COEvolgy.isImportIncluded(allImports, arrayMapImport);
    	foundTracerImport = COEvolgy.isImportIncluded(allImports, tracerImport);
		
	
    	return ASTHelper.VISIT_SUBTREE;
    }
    

	@Override
	public boolean visit(FieldDeclaration node) {
		if (node.getType().toString().startsWith("HashMap")) {
			return setDeclaration(node);
		}
		
		return ASTHelper.VISIT_SUBTREE;
	}
	

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		if (node.getType().toString().startsWith("HashMap")) {
			return setDeclaration(node);
		}
		
		return ASTHelper.VISIT_SUBTREE;
	}
	
	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (node.getType().toString().startsWith("HashMap")) {
			final ASTBuilder b = this.ctx.getASTBuilder();
			final Refactorings r = this.ctx.getRefactorings();
			
			Type newType = b.genericType(newMapClass);
			
			Expression[] args = new Expression[node.arguments().size()];
			int i = 0;
			for (Object a : node.arguments()) {
				args[i] = b.copy((Expression) a);
				i++;
			}

			ClassInstanceCreation replacement;
			replacement = b.new0(newType, args);
			if (operationFlag == TRACE) {
				// insert something to trace the patterns execution
				ASTNode parentStatement = getParentStatement(node);
				boolean insideFieldDecl = (parentStatement instanceof FieldDeclaration) ? true : false;
				r.insertAfter(traceNode(insideFieldDecl), parentStatement);
			}
			COEvolgy.traceRefactoring(TAG);
			r.replace(node, replacement);
			
			return ASTHelper.DO_NOT_VISIT_SUBTREE;
		}
		
		return ASTHelper.VISIT_SUBTREE;
	}
	
	@Override
	public boolean visit(ImportDeclaration node) {
		final ASTBuilder b = this.ctx.getASTBuilder();
		final Refactorings r = this.ctx.getRefactorings();
		boolean refactored = false;
		if (!foundArrayImport) {
			ImportDeclaration newImport = r.getAST().newImportDeclaration();
			Name name = b.name(arrayMapImport.split("\\."));
			newImport.setName(name);
			r.insertBefore(newImport, node);
			
			foundArrayImport = true;
			refactored = true;
		}
		
		if (!foundTracerImport) {
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
	public boolean visit(MethodDeclaration node) {
		for (Object o : node.parameters()) {
			ASTNode arg = (ASTNode) o;
			if (arg.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION) {
				SingleVariableDeclaration argVar = (SingleVariableDeclaration) arg;
				if (argVar.getType().toString().startsWith("HashMap")) {
					final ASTBuilder b = this.ctx.getASTBuilder();
			        final Refactorings r = this.ctx.getRefactorings();
			        
					Type newType = createGenericTypeCopy(newMapClass, argVar.getType(), b);
					String varName = argVar.getName().getIdentifier();
					SingleVariableDeclaration newArg = b.declareSingleVariable(varName, newType);
					
					r.replace(argVar, newArg);
				}
			}
		}
		
		return ASTHelper.VISIT_SUBTREE;
	}
	
	/* AUXILIAR METHODS */

	private Type createGenericTypeCopy(String typeName, Type type, ASTBuilder b) {
		String[] typeArgsStr = type.toString()
								   .replaceAll(".+<(.+)>", "$1")
								   .replaceAll(" ",  "")
								   .split(",");
		
		Type[] typeArgs = new Type[typeArgsStr.length];
		int i = 0;
		for (String name : typeArgsStr) {
			typeArgs[i] = b.type(name);
			i++;
		}
		
		return b.genericType(typeName, typeArgs);
	}
	
	private void copyModifiers(List<Modifier> source, List<Modifier> dest, ASTBuilder b) {
		for (Modifier modifier : source) {
			if (modifier.isFinal()) {
				dest.add(b.final0());
			} else if (modifier.isStatic()) {
				dest.add(b.static0());
			} else if (modifier.isPrivate()) {
				dest.add(b.private0());
			} else if (modifier.isPublic()) {
				dest.add(b.private0());
			} else if (modifier.isProtected()) {
				dest.add(b.protected0());
			} 
		}
	}
	
	private boolean setDeclaration(FieldDeclaration node) {
        final ASTBuilder b = this.ctx.getASTBuilder();
        final Refactorings r = this.ctx.getRefactorings();
        
        Type newType = createGenericTypeCopy(newMapClass, node.getType(), b);
		
		for (Object o : node.fragments()) {
			((ASTNode) o).accept(this);
			FieldDeclaration newNode = b.declareField(newType, b.copy((VariableDeclarationFragment) o));
			List<Modifier> listMod = node.modifiers();
			List<Modifier> newListMod = newNode.modifiers();

			copyModifiers(listMod, newListMod, b);

			r.insertBefore(newNode, node);
		}
		r.remove(node);

        return ASTHelper.DO_NOT_VISIT_SUBTREE;
    }
	
	private boolean setDeclaration(VariableDeclarationStatement node) {
        final ASTBuilder b = this.ctx.getASTBuilder();
        final Refactorings r = this.ctx.getRefactorings();
        
        Type newType = createGenericTypeCopy(newMapClass, node.getType(), b);
		
		for (Object o : node.fragments()) {
			if (o instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) o;
				fragment.accept(this);
				SimpleName varName = b.copy(fragment.getName());
				Expression init = fragment.getInitializer() == null ? null : b.copy(fragment.getInitializer());
				
				VariableDeclarationStatement newNode = b.declareStmt(newType, varName, init);
				List<Modifier> listMod = node.modifiers();
				List<Modifier> newListMod = newNode.modifiers();
	
				copyModifiers(listMod, newListMod, b);
	
				r.insertBefore(newNode, node);
			} else {
				return ASTHelper.VISIT_SUBTREE;
			}
		}
		r.remove(node);

        return ASTHelper.DO_NOT_VISIT_SUBTREE;
    }
	
	private ASTNode getParentStatement(ASTNode node) {
		if (node == null) return null; // FIXME: add try-catch; should never enter here
		
		if (node instanceof Statement) {
			return node;
		}
		else if (node instanceof FieldDeclaration) {
			return node;
		}
		else {
			return getParentStatement(node.getParent());
		}
	}
	
	private ASTNode traceNode(boolean flag) {
		COEvolgy helper = new COEvolgy(this.ctx, flag);
		return helper.buildTraceNode(TAG);
	}

}
