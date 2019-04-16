package org.autorefactor.refactoring.rules;

import static org.autorefactor.util.COEvolgy.MEASURE;
import static org.autorefactor.util.COEvolgy.TRACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.ASTHelper;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;


public class HashMapUsageRefactoring extends AbstractRefactoringRule {
	
	public static final String TAG = "HashMapUsage";
	public static String fileName = "";
	
	private HashMap<String, String> instances;
	private List<String> integers;
	private static boolean foundArrayImport = false;
	private static boolean foundTracerImport = false;
	private static final String newMapClass = "ArrayMap";
	private static final String arrayMapImport = "android.support.v4.util.ArrayMap";
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	private static int operationFlag = MEASURE;
	
	public HashMapUsageRefactoring() {
		super();
		instances = new HashMap<>();
		integers = new ArrayList<>();
	}
	
	public HashMapUsageRefactoring(int flag) {
		super();
		operationFlag = flag;
		instances = new HashMap<>();
		integers = new ArrayList<>();
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
    
    public class UsageVisitor extends ASTVisitor {
    	
    	public boolean transformedCode;
    	private String currentVar;
    	
    	public UsageVisitor() {
    		transformedCode = false;
    		currentVar = "";
    	}
    	
    	private boolean argIsInteger(Expression exp) {
    		String argStr = exp.toString();
    		
    		if (exp.getNodeType() == ASTNode.METHOD_INVOCATION) {
    			MethodInvocation call = (MethodInvocation) exp;
    			if (call.resolveMethodBinding() == null) {
    				// if the argument is a method call, and we know nothing about it, 
    				// we assume it's a method returning an integer.
    				return true;
    			}
    			
    			IMethodBinding binding = call.resolveMethodBinding();
    			if (binding.getReturnType() == null) {
    				// if we can examine the method binding, but the return type in 
    				// unknown, it's more likely that it's not native (i.e., an integer).
    				return false;
    			}
    			
    			return (binding.getReturnType().getName().equals("int") || binding.getReturnType().getName().equals("Integer"));
    		} else if (integers.contains(argStr) || (exp.getNodeType() == ASTNode.NUMBER_LITERAL)) {
				return true;
    		}
    		
    		return false;
    	}
    	
    	@Override
    	public boolean visit(FieldDeclaration node) {
    		String nodeType = node.getType().toString();
    		if (nodeType.contains("HashMap")) {
    			transformedCode = true;
    			for (Object f : node.fragments()) {
    				currentVar = ((VariableDeclarationFragment) f).getName().getIdentifier();
    			}
    			return setDeclaration(node);
    		} else if (nodeType.equals("int") || nodeType.equals("Integer")) {
    			for (Object f : node.fragments()) {
    				VariableDeclarationFragment frag = (VariableDeclarationFragment) f; 
    				integers.add(frag.getName().getIdentifier());
    			}
    		}
    		
    		return ASTHelper.VISIT_SUBTREE;
    	}
    	

    	@Override
    	public boolean visit(VariableDeclarationStatement node) {
    		String nodeType = node.getType().toString();
    		if (nodeType.contains("HashMap")) {
    			transformedCode = true;
    			for (Object f : node.fragments()) {
    				currentVar = ((VariableDeclarationFragment) f).getName().getIdentifier();
    			}
    			return setDeclaration(node);
    		} else if (nodeType.equals("int") || nodeType.equals("Integer")) {
    			for (Object f : node.fragments()) {
    				VariableDeclarationFragment frag = (VariableDeclarationFragment) f; 
    				integers.add(frag.getName().getIdentifier());
    			}
    		}
    		
    		return ASTHelper.VISIT_SUBTREE;
    	}
    	
    	@Override
    	public boolean visit(Assignment node) {
    		Expression left = node.getLeftHandSide();
    		Expression right = node.getLeftHandSide();
    		
    		if (right.getNodeType() == ASTNode.METHOD_INVOCATION) {
    			String varName = "";
    			
    			switch (left.getNodeType()) {
    			
    				case ASTNode.FIELD_ACCESS:
    					varName = ((FieldAccess) left).getName().getIdentifier();
    					currentVar = varName;
    					break;
    					
    				case ASTNode.QUALIFIED_NAME:
    					varName = ((QualifiedName) left).getName().getIdentifier();
    					currentVar = varName;
    					break;
    					
    				case ASTNode.SIMPLE_NAME:
    					varName = ((SimpleName) left).getIdentifier();
    					currentVar = varName;
    					break;
    					
    				default:
    					break;
    					
    			}
    			if (instances.containsKey(varName)) {
    				final ASTBuilder b = ctx.getASTBuilder();
        			final Refactorings r = ctx.getRefactorings();
        			
        			Type castType = createGenericTypeCopy(newMapClass, instances.get(varName), b);
        			Expression newRight = b.cast(castType, b.copy(right));
        			if (operationFlag == TRACE) {
        				// insert something to trace the patterns execution
        				if (node.resolveTypeBinding() == null || !node.resolveTypeBinding().isNested()) {
        					ASTNode parentStatement = getParentStatement(node);
            				boolean insideFieldDecl = (parentStatement instanceof FieldDeclaration) ? true : false;
            				r.insertAfter(traceNode(insideFieldDecl), parentStatement);
        				}
        			}
        			COEvolgy.traceRefactoring(TAG);
        			r.replace(right, newRight);
        			return ASTHelper.DO_NOT_VISIT_SUBTREE;
    			}
    		}
    		
    		return ASTHelper.VISIT_SUBTREE;
    	}
    	
    	@Override
    	public boolean visit(ClassInstanceCreation node) {
    		String typeStr = node.getType().toString().replace(" ", "");
    		
    		if (typeStr.startsWith("HashMap")) {
    			final ASTBuilder b = ctx.getASTBuilder();
    			final Refactorings r = ctx.getRefactorings();
    			
    			Type newType = b.genericType(newMapClass);
    			
    			Expression[] args = new Expression[node.arguments().size()];
    			int i = 0;
    			for (Object a : node.arguments()) {
    				Expression exp = (Expression) a;
    				if (argIsInteger(exp)) { 
    					args[i] = b.copy(exp);
    				} else {
    					// This particular argument's type is a Map instance, 
    					// so we need to cast it.
    					String castTypeStr = instances.get(currentVar);
    					if (castTypeStr == null) {
    						Type castType = createGenericTypeCopy(newMapClass, typeStr, b);
    						args[i] = b.cast(castType, b.copy(exp));
    					} else {
    						Type castType = createGenericTypeCopy(newMapClass, castTypeStr, b);
    						args[i] = b.cast(castType, b.copy(exp));
    					}
    				}
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
    			transformedCode = true;
    			COEvolgy.traceRefactoring(TAG);
    			r.replace(node, replacement);
    			
    			return ASTHelper.DO_NOT_VISIT_SUBTREE;
    		} else if (typeStr.contains("<HashMap")	|| typeStr.contains(",HashMap")) {
    			final ASTBuilder b = ctx.getASTBuilder();
    			final Refactorings r = ctx.getRefactorings();
    			
    			Type newType = b.genericType(typeStr.split("<")[0]);

    			ClassInstanceCreation replacement;
    			replacement = b.new0(newType);
    			if (operationFlag == TRACE) {
    				// insert something to trace the patterns execution
    				ASTNode parentStatement = getParentStatement(node);
    				boolean insideFieldDecl = (parentStatement instanceof FieldDeclaration) ? true : false;
    				r.insertAfter(traceNode(insideFieldDecl), parentStatement);
    			}
    			transformedCode = true;
    			COEvolgy.traceRefactoring(TAG);
    			r.replace(node, replacement);
    			
    			return ASTHelper.DO_NOT_VISIT_SUBTREE;
    		}
    		
    		return ASTHelper.VISIT_SUBTREE;
    	}
    	
    	@Override
    	public boolean visit(CastExpression node) {
    		String typeStr = node.getType().toString().replace(" ", "");
    		
    		if (node.getType().isParameterizedType()) {
    			ParameterizedType paramType = (ParameterizedType) node.getType();
    			if (typeStr.contains("HashMap")) {
    				final ASTBuilder b = ctx.getASTBuilder();
    				final Refactorings r = ctx.getRefactorings();
    				
    				Type newType = createGenericTypeCopy(newMapClass, paramType.toString(), b);
    				
    				CastExpression replacement;
    				replacement = b.cast(newType, b.copy(node.getExpression()));
    				if (operationFlag == TRACE) {
    					// insert something to trace the patterns execution
    					ASTNode parentStatement = getParentStatement(node);
    					boolean insideFieldDecl = (parentStatement instanceof FieldDeclaration) ? true : false;
    					r.insertAfter(traceNode(insideFieldDecl), parentStatement);
    				}
    				transformedCode = true;
    				COEvolgy.traceRefactoring(TAG);
    				r.replace(node, replacement);
    				
    				return ASTHelper.DO_NOT_VISIT_SUBTREE;
    			}
    		}
    		
    		return ASTHelper.VISIT_SUBTREE;
    	}
    	
    	
    	@Override
    	public boolean visit(MethodDeclaration node) {
    		Type returnType = node.getReturnType2();
    		if (returnType != null) {
    			String returnTypeStr = returnType.toString();
    			if (returnTypeStr.contains("HashMap")) {
    				final ASTBuilder b = ctx.getASTBuilder();
			        final Refactorings r = ctx.getRefactorings();
			        
					Type newType = createGenericTypeCopy(newMapClass, returnTypeStr, b);
					
					r.replace(returnType, newType);
					return ASTHelper.DO_NOT_VISIT_SUBTREE;
    			}
    		}
    		
    		return ASTHelper.VISIT_SUBTREE;
    	}
    	
    	@Override
    	public boolean visit(SingleVariableDeclaration node) {
    		
    		if (node.getType().toString().contains("HashMap")) {
    			final ASTBuilder b = ctx.getASTBuilder();
    	        final Refactorings r = ctx.getRefactorings();
    	        
    			Type newType = createGenericTypeCopy(newMapClass, node.getType().toString(), b);
    			String varName = node.getName().getIdentifier();
    			SingleVariableDeclaration newArg = b.declareSingleVariable(varName, newType);
    			
    			r.replace(node, newArg);
    			transformedCode = true;
    			return ASTHelper.DO_NOT_VISIT_SUBTREE;
    		}
    		
        	return ASTHelper.VISIT_SUBTREE;
    	}
    }
    

	public class ImportVisitor extends ASTVisitor {
    	
    	@Override
    	public boolean visit(ImportDeclaration node) {
    		final ASTBuilder b = ctx.getASTBuilder();
    		final Refactorings r = ctx.getRefactorings();
    		boolean refactored = false;
    		if (!foundArrayImport) {
    			ImportDeclaration newImport = r.getAST().newImportDeclaration();
    			Name name = b.name(arrayMapImport.split("\\."));
    			newImport.setName(name);
    			r.insertBefore(newImport, node);
    			
    			foundArrayImport = true;
    			refactored = true;
    		}
    		
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
    	
    }

    @Override
    public boolean visit(CompilationUnit node) {
    	fileName = node.getJavaElement().getPath().toString();
    	List<ImportDeclaration> allImports = node.imports();
    	foundArrayImport = COEvolgy.isImportIncluded(allImports, arrayMapImport);
    	foundTracerImport = COEvolgy.isImportIncluded(allImports, tracerImport);
    	instances = new HashMap<>();
    	integers = new ArrayList<>();
    	
		UsageVisitor visitor = new UsageVisitor();
    	
    	node.accept(visitor);
    	if (visitor.transformedCode) {
    		ImportVisitor impVisitor = new ImportVisitor();
    		node.accept(impVisitor);
    	}
    	return ASTHelper.VISIT_SUBTREE;
    }
    
    
	/* AUXILIAR METHODS */

	private Type createGenericTypeCopy(String arrayMapTypeName, String type, ASTBuilder b) {
		String typeStr = type.replaceAll(" ", "");
		
		String mainType = typeStr.substring(0, typeStr.indexOf("<"));  // The main type string.
																	   // (Example: for "HashMap<Int, String>", it will return "HashMap" 
		String newTypeName = mainType;
		if (mainType.equals("HashMap")) newTypeName = arrayMapTypeName; 
        String argsStr = typeStr.substring(typeStr.indexOf("<")+1, typeStr.lastIndexOf(">"));  // The generic type argument string.
        																					   // (Example: for "HashMap<Int, String>", it will return "Int, String"
        int lessthanIndex = argsStr.indexOf("<");
        int commaIndex = argsStr.indexOf(",");
        String[] splittedArgs = argsStr.split(",", 2);
        if (lessthanIndex >= 0 && lessthanIndex < commaIndex) {
        	splittedArgs = new String[1];
        	splittedArgs[0] = argsStr;
        }
		
		Type[] typeArgs = new Type[splittedArgs.length];
		int i = 0;
		for (String name : splittedArgs) {
			Type t = null;
			if (name.contains("<")) t = createGenericTypeCopy(arrayMapTypeName, name, b);
			else t = b.type(name);
			
			typeArgs[i] = t;
			i++;
		}
		
		return b.genericType(newTypeName, typeArgs);
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
        
        Type newType = createGenericTypeCopy(newMapClass, node.getType().toString(), b);
		
		for (Object o : node.fragments()) {
			((ASTNode) o).accept(this);
			VariableDeclarationFragment frag = (VariableDeclarationFragment) o;
			instances.put(frag.getName().getIdentifier(), node.getType().toString());
			
			FieldDeclaration newNode = b.declareField(newType, b.copy(frag));
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
        
        Type newType = createGenericTypeCopy(newMapClass, node.getType().toString(), b);
		
		for (Object o : node.fragments()) {
			if (o instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) o;
				fragment.accept(this);
				instances.put(fragment.getName().getIdentifier(), node.getType().toString());
				SimpleName varName = b.copy(fragment.getName());
				Expression init = fragment.getInitializer() == null ? null : b.copy(fragment.getInitializer());
				if (fragment.getInitializer().getNodeType() == ASTNode.METHOD_INVOCATION) {
					init = b.cast(createGenericTypeCopy(newMapClass, node.getType().toString(), b), init);
				}
				
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
