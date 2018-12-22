package org.autorefactor.refactoring.rules;

import static org.autorefactor.refactoring.ASTHelper.VISIT_SUBTREE;
import static org.autorefactor.util.COEvolgy.MEASURE;
import static org.autorefactor.util.COEvolgy.TRACE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.ASTHelper;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class MemoizationChanceRefactoring extends AbstractRefactoringRule {
	
	private static final boolean genericApproach = false;
	
	public static final String TAG = "MemoizationChance";
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	private static final String memoizerImport = "org.greenlab.memoization.Memoizer";
	private static final String mapImport = "java.util.Hashtable";

	private static int operationFlag = MEASURE;
	
	private HashMap<String, Boolean> classVars;
    private HashMap<String, Boolean> classMethods;
    private HashMap<String, Set<String>> methodCalls;
    private HashMap<String, HashSet<String>> methodLocalVars;
    private String className;
    private String packageName;
    private static boolean foundMemoizerImport = false;
	private static boolean foundTracerImport = false;
	private static boolean foundMapImport = false;
	
	public MemoizationChanceRefactoring() {
		super();
		this.classVars = new HashMap<>();
		this.classMethods = new HashMap<>();
		this.methodCalls = new HashMap<>();
		this.methodLocalVars = new HashMap<>();
		this.className = "";
		this.packageName = "";
	}
	
	public MemoizationChanceRefactoring(int flag) {
		super();
		operationFlag = flag;
		this.classVars = new HashMap<>();
		this.classMethods = new HashMap<>();
		this.methodCalls = new HashMap<>();
		this.methodLocalVars = new HashMap<>();
		this.className = "";
		this.packageName = "";
	}
	
	@Override
	public String getName() {
		return "MemoizationChanceRefactoring";
	}

	@Override
	public String getDescription() {
		return "Transformation for Android applications to take advantage "
				+ "of memoization. Methods than are prone to memoization will "
				+ "have mechanism that will only execute the method if its "
				+ "return value for the given arguments was not calculated already.";
		//TODO: improve this description.
	}

	@Override
	public String getReason() {
		return "It improves the performance";
	}
	
	/* Helper Methods */
    private void checkInnerCalls() {
        for (String m : methodCalls.keySet()) {
            Boolean check = classMethods.get(m);
            if (check != null && check) {
                // Only verify the method calls inside a method IF the memoization test
                // didn't fail for any other reason.
                boolean allCallsMemoizable = true;

                for (String call : methodCalls.get(m)) {
                    if (!allCallsMemoizable) break;

                    if (classMethods.containsKey(call)) {
                        // if a called method is memoizable, the callee method also is.
                        allCallsMemoizable = classMethods.get(call);
                    } else if (methodLocalVars.get(m) != null && methodLocalVars.get(m).contains(call)) {
                        allCallsMemoizable = true;
                    } else {
                        allCallsMemoizable = false;
                    }
                }

                classMethods.put(m, allCallsMemoizable);
            }
        }
    }
    
    private boolean hasMemoizableMethods() {
    	for (String method : classMethods.keySet()) {
    		if (classMethods.get(method)) return true;
    	}
    	
    	return false;
    }
    
    private void debug() {
        System.out.println("{ VARS }");
        for (String var : this.classVars.keySet()) {
            System.out.println("\t [ " + var + " ]: " + classVars.get(var));
        }
        System.out.println("");
        
        System.out.println("{ METHODS }");
        for (String method : this.classMethods.keySet()) {
            System.out.println("\t [ " + method + " ]: " + classMethods.get(method));
        }
        System.out.println("");

        System.out.println("{ METHOD VARS }");
        for (String method : this.methodLocalVars.keySet()) {
            Set<String> set = this.methodLocalVars.get(method);
            for (String exp : set) {
                System.out.println("\t>> " + method + " : "+ exp);
            }
        }
        System.out.println("");
        
        System.out.println("{ CALLS }");
        for (String method : this.methodCalls.keySet()) {
            Set<String> set = this.methodCalls.get(method);
            for (String exp : set) {
                System.out.println("\t>> " + method + " : "+ exp);
            }
        }
        System.out.println("");
        
        System.out.println("\n\n");
    }

	@Override
    public boolean visit(CompilationUnit node) {		
		className = node.getJavaElement().getElementName().replace(".java", "");
		packageName = node.getPackage().getName().getFullyQualifiedName();
		
		List<ImportDeclaration> allImports = node.imports();
		foundTracerImport = COEvolgy.isImportIncluded(allImports, tracerImport);
		foundMemoizerImport = COEvolgy.isImportIncluded(allImports, memoizerImport);
		foundMapImport = COEvolgy.isImportIncluded(allImports, mapImport);
		
		// Phase #1: Collect class variables.
		FieldsVisitor gatherer = new FieldsVisitor(packageName, className);
		node.accept(gatherer);
		
		// Phase #2: Classify methods regarding memoization.
		MethodVisitor analyzer = new MethodVisitor(packageName, className);
		node.accept(analyzer);
		checkInnerCalls();
		
		// Phase #3: Refactor memoizable methods.
		ReturnVisitor returnRefactor = new ReturnVisitor(packageName, className);
		node.accept(returnRefactor);
		
		MemoizationVisitor methodRefactor = new MemoizationVisitor(packageName, className);
		node.accept(methodRefactor);
		
		// Final check: imports
		ImportVisitor importChecker = new ImportVisitor();
		node.accept(importChecker);
	
		return super.visit(node);
    }
	
	public class FieldsVisitor extends ASTVisitor {
		
		private String packageName;
		private String className;
		
		public FieldsVisitor(String packageName, String filename) {
			this.packageName = packageName;
			this.className = filename;
		}
		
		private void addLocalVar(String methodName, String varName) {
            if (methodLocalVars.containsKey(methodName)) {
                methodLocalVars.get(methodName).add(varName);
            } else {
                HashSet<String> calls = new HashSet<>();
                calls.add(varName);
                methodLocalVars.put(methodName, calls);
            }
        }

		@Override
		public boolean visit(FieldDeclaration node) {
			String className = this.packageName + "." + this.className;
			
			boolean isFinal = false;
			for (Object m : node.modifiers()) {
				if (m.toString().equals("final")) isFinal = true;
			}

			for (Object f : node.fragments()) {
				String varName = ((VariableDeclarationFragment) f).getName().getIdentifier();
				classVars.put(className + "." + varName, isFinal);
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(VariableDeclarationStatement node) {
			String parentMethod = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			if (!parentMethod.equals("")) {
				for (Object f : node.fragments()) {
					String varName = ((VariableDeclarationFragment) f).getName().getIdentifier();
					addLocalVar(parentMethod, varName);
				}
            }
			return super.visit(node);
		}
		
		@Override
		public boolean visit(SingleVariableDeclaration node) {
			String parentMethod = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			if (!parentMethod.equals("")) {
				addLocalVar(parentMethod, node.getName().getIdentifier());
            }
			return super.visit(node);
		}
		
		@Override
		public boolean visit(VariableDeclarationExpression node) {
			String parentMethod = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			if (!parentMethod.equals("")) {
				for (Object f : node.fragments()) {
					String varName = ((VariableDeclarationFragment) f).getName().getIdentifier();
					addLocalVar(parentMethod, varName);
				}
            }
			return super.visit(node);
		}
		
		@Override
		public boolean visit(MethodDeclaration node) {
			String visitingMethod = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			if (!node.isConstructor() && !visitingMethod.equals("")) {
				classMethods.put(visitingMethod, true);
			}
			return super.visit(node);
		}
		
	}

	public class MethodVisitor extends ASTVisitor {
		
		private String packageName;
		private String className;
		
		public MethodVisitor(String packageName, String filename) {
			this.packageName = packageName;
			this.className = filename;
		}
		
		
		/* Helper methods */
		
        private boolean areParametersNative(List lst) {
            if (lst.isEmpty()) return false;

            for (Object obj : lst) {
            	if (obj instanceof SingleVariableDeclaration) {
            		SingleVariableDeclaration var = (SingleVariableDeclaration) obj;
            		return COEvolgy.isTypeNative(var.getType());
            	} else {
            		// It's not supposed to enter here. All methods parameters are, as far as 
            		// we know, represented as a `SingleVariableDeclaration` in the AST.
            		// If for some reason this `else` is reached, we will assume the method 
            		// is not memoizable.
            		return false;
            	}
            	
            }

            return true;
        }

        private void addMethodCall(String calleeMethod, String call) {
            if (methodCalls.containsKey(calleeMethod)) {
                methodCalls.get(calleeMethod).add(call);
            } else {
                HashSet<String> calls = new HashSet<>();
                calls.add(call);
                methodCalls.put(calleeMethod, calls);
            }
        }
        
        @Override
		public boolean visit(MethodDeclaration node) {
			String visitingMethod = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			if (!node.isConstructor() && !visitingMethod.equals("")) {
                boolean goFurther = node.getReturnType2() != null &&
                					COEvolgy.isTypeNative(node.getReturnType2()) &&
                                    areParametersNative(node.parameters());
				classMethods.put(visitingMethod, goFurther); //
            }
			return super.visit(node);
		}

		
		@Override
		public boolean visit(SimpleName node) {
			String visitingMethod = COEvolgy.getParentMethodName(node, packageName, className);
			Boolean checkScope = !(node.getParent() instanceof SimpleType) && 
					!(node.getParent() instanceof MethodDeclaration);
			Boolean check = classMethods.get(visitingMethod);
			if(checkScope && !visitingMethod.equals("") && check != null && check) {
				// Reaching this point means that the method has
                // native return type and native arguments.
				String qualifiedVarName = packageName + "." + className + "." + node.getFullyQualifiedName();
				String varName = node.getFullyQualifiedName();
				Set<String> methodVars = methodLocalVars.get(visitingMethod);
				
				boolean isLocalVar = methodVars != null && methodVars.contains(varName);
				boolean isFinalVar = classVars.keySet().contains(qualifiedVarName) && classVars.get(qualifiedVarName);
				boolean isMethodCall = classMethods.containsKey(qualifiedVarName);
				if (!isLocalVar && !isFinalVar && !isMethodCall) {
                    classMethods.put(visitingMethod, false);        
                }
			}
			
			return super.visit(node);
		}
		
		@Override
		public boolean visit(MethodInvocation node) {
			String visitingMethod = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			if (!visitingMethod.equals("")) {
				Expression callVarExp = node.getExpression();
				String callVariable = "";
				if (callVarExp != null) {
					callVariable = callVarExp.toString().replace("this", "");
				}
				
				String call = this.packageName + "." + this.className + "." + node.getName().getFullyQualifiedName();
				if (!callVariable.equals("")) {
					call = callVariable;
				}
				
				addMethodCall(visitingMethod, call);
				
			}
			return super.visit(node);
		}
        
	}
	
	public class MemoizationVisitor extends ASTVisitor{
		
		private String packageName;
		private String className;
		
		public MemoizationVisitor(String packageName, String filename) {
			this.packageName = packageName;
			this.className = filename;
		}
		
		private ASTNode getParentTypeOrInstanceCreation(ASTNode node) {
			
			if (node == null) return null;
			
			if (node instanceof TypeDeclaration) return node;
			
			if (node instanceof ClassInstanceCreation) return node;
			
			return getParentTypeOrInstanceCreation(node.getParent());
		}
		
		private boolean refactorMethod(MethodDeclaration node, String name) {
			final ASTBuilder b = ctx.getASTBuilder();
			final Refactorings r = ctx.getRefactorings();
			COEvolgy helper = new COEvolgy(ctx, false);
			
			String returnType = "";
			if (node.getReturnType2().resolveBinding() != null)
				returnType = node.getReturnType2().resolveBinding().getName();
			
			if (returnType.equals("")) {
				System.out.println("Stupid binding error...");
				return ASTHelper.VISIT_SUBTREE;
			} 
			
			if (node.parameters() == null || node.parameters().size() == 0) {
				System.out.println("# of parameters should be > 0, and it's not");
				return ASTHelper.VISIT_SUBTREE;
			}
			
			int paramSize = node.parameters().size();
			String[] params = new String[paramSize];
			for (int i = 0; i < paramSize; i++) {
				if (!(node.parameters().get(i) instanceof SingleVariableDeclaration)) {
					// This block should never be reached.
					// If it does, something unpredictable is going on...
					System.out.println("Parameter is not a `SingleVariableDeclaration`");
					return ASTHelper.VISIT_SUBTREE;
				}
				SingleVariableDeclaration arg = (SingleVariableDeclaration)node.parameters().get(i);
				params[i] = arg.getName().getIdentifier();
			}

			IfStatement checkMemoization = helper.buildMemoizationCheck(name, returnType, genericApproach, params);
			
			r.insertAt(node.getBody(),
					Block.STATEMENTS_PROPERTY,
					checkMemoization,
					0
			);
			
			if (!genericApproach) {
				FieldDeclaration classVar = helper.declareLookupTable(name, returnType);
				ASTNode parent = getParentTypeOrInstanceCreation(node);
				
				ASTNode ref = null;
				if (parent == null) {
					ref = node;
					System.out.println("\t\tNull parent: " + name);
				} else if (parent instanceof TypeDeclaration) {
					ref = node;
				} else {
					ref = COEvolgy.getParentStatement(parent);
				}
				
				r.insertBefore(classVar, ref);
			} else {
				VariableDeclarationStatement storedValue = helper.buildGetStoredValueVar(name, returnType, genericApproach, params);
				r.insertAt(node.getBody(),
						Block.STATEMENTS_PROPERTY,
						storedValue,
						0
				);
			}
		
			if (operationFlag == TRACE) {
				ASTNode traceNode = helper.buildTraceNode(TAG);
				r.insertAt(node.getBody(),
						Block.STATEMENTS_PROPERTY,
						traceNode,
						0
				);
			}
			
			COEvolgy.traceRefactoring(TAG);
			
			return ASTHelper.DO_NOT_VISIT_SUBTREE;
		}

		@Override
		public boolean visit(MethodDeclaration node) {
			if (node.isConstructor()) return super.visit(node);
			
			String methodName = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			
            Boolean containsBody = node.getBody() != null && 
            		node.getBody().statements() != null &&
            		node.getBody().statements().size() > 1;
                                
            Boolean check = classMethods.containsKey(methodName) && classMethods.get(methodName);
            
            if (!methodName.equals("") && containsBody && check) {
            	String methodID = genericApproach ? methodName : className + "_" + node.getName().getIdentifier();
            	return refactorMethod(node, methodID);
            }
			return super.visit(node);
		}
		
	}
	
	public class ReturnVisitor extends ASTVisitor{
		private String packageName;
		private String className;
		
		public ReturnVisitor(String packageName, String filename) {
			this.packageName = packageName;
			this.className = filename;
		}
		
		@Override
		public boolean visit(ReturnStatement node) {
			MethodDeclaration parentMethod = COEvolgy.getParentMethod(node);
			String methodName = COEvolgy.getParentMethodName(node, this.packageName, this.className);
			
			Boolean containsBody = parentMethod.getBody() != null && 
            		parentMethod.getBody().statements() != null &&
            		parentMethod.getBody().statements().size() > 1;
			
            Boolean check = classMethods.containsKey(methodName) && classMethods.get(methodName);
            
            if (!methodName.equals("") && containsBody && check) {
            	String returnType = "";
    			if (parentMethod.getReturnType2().resolveBinding() != null)
    				returnType = parentMethod.getReturnType2().resolveBinding().getName();
    			
    			if (returnType.equals("")) {
    				System.out.println("Stupid binding error...");
    				return ASTHelper.VISIT_SUBTREE;
    			} 
    			
    			if (parentMethod.parameters() == null || parentMethod.parameters().size() == 0) {
    				System.out.println("# of parameters should be > 0, and it's not");
    				return ASTHelper.VISIT_SUBTREE;
    			}
    			
    			final ASTBuilder b = ctx.getASTBuilder();
    			final Refactorings r = ctx.getRefactorings();
    			COEvolgy helper = new COEvolgy(ctx, false);
    			
    			int paramSize = parentMethod.parameters().size();
    			String[] params = new String[paramSize];
    			for (int i = 0; i < paramSize; i++) {
    				if (!(parentMethod.parameters().get(i) instanceof SingleVariableDeclaration)) {
    					// This block should never be reached.
    					// If it does, something unpredictable is going on...
    					System.out.println("Parameter is not a `SingleVariableDeclaration`");
    					return ASTHelper.VISIT_SUBTREE;
    				}
    				SingleVariableDeclaration arg = (SingleVariableDeclaration)parentMethod.parameters().get(i);
    				params[i] = arg.getName().getIdentifier();
    			}
    			
    			String methodID = genericApproach ? methodName : className + "_" + parentMethod.getName().getIdentifier();
            	ExpressionStatement memoizationStmt = 
            			helper.buildMemoizationStatement(methodID, node.getExpression(), genericApproach, params);
            	
            	r.insertBefore(memoizationStmt, node);
            	return ASTHelper.DO_NOT_VISIT_SUBTREE;
            }
            
			return super.visit(node);
		}
		
	}
	
	public class ImportVisitor extends ASTVisitor{
		
		@Override
		public boolean visit(TypeDeclaration node) {
			final ASTBuilder b = ctx.getASTBuilder();
			final Refactorings r = ctx.getRefactorings();
			boolean refactored = false;
			
			if (!foundMemoizerImport && hasMemoizableMethods()) {
				ImportDeclaration newImport = r.getAST().newImportDeclaration();
				Name name = b.name(memoizerImport.split("\\."));
				newImport.setName(name);
				r.insertBefore(newImport, node);
				
				foundMemoizerImport = true;
				refactored = true;
			}
			
			if (!foundMapImport && hasMemoizableMethods()) {
				ImportDeclaration mapImportStmt = r.getAST().newImportDeclaration();
				Name mapImportName = b.name(mapImport.split("\\."));
				mapImportStmt.setName(mapImportName);
				r.insertBefore(mapImportStmt, node);
				
				foundMapImport = true;
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
	
}
