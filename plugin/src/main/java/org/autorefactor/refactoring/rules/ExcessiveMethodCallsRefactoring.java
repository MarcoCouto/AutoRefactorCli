package org.autorefactor.refactoring.rules;

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
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class ExcessiveMethodCallsRefactoring extends AbstractRefactoringRule {
	
	public static final String TAG = "ExcessiveMethodCalls";
	private static boolean foundTracerImport = false;
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	
	private static int operationFlag = MEASURE;
	private static int phase = 0;
	private static String fileName = "";
	private static String packageName = "";
	
	private String visitingMethod;
	private Map<String, List<String>> methodCalls;
    private Map<String, Set<String>> conditionedVars;
    private Set<String> classVars;
	
	public ExcessiveMethodCallsRefactoring () {
		super();
		
		this.visitingMethod = "";
		this.methodCalls = new HashMap<>();
		this.conditionedVars = new HashMap<>();
		this.classVars = new HashSet<>();
	}
	
	public ExcessiveMethodCallsRefactoring (int flag) {
		super();
		
		operationFlag = flag;
		
		this.visitingMethod = "";
		this.methodCalls = new HashMap<>();
		this.conditionedVars = new HashMap<>();
		this.classVars = new HashSet<>();
	}

	@Override
	public String getName() {
		return "ExcessiveMethodCallsRefactoring";
	}

	@Override
	public String getDescription() {
		return "Calling methods inside loops, either in the loop condition or in the body, is " +
	            "usually a good optimization target." // TODO: improve explanation
	            ;
	}

	@Override
	public String getReason() {
		return "It improves the performance";
	}
	
    /* VISITORS */
	
    @Override
    public boolean visit(CompilationUnit node) {
    	
		this.phase++;
		fileName = node.getJavaElement().getElementName();
		packageName = node.getPackage().getName().getFullyQualifiedName();
		ASTVisitor visitor;
		
		// 1st Traversal : Get information about class variables.
		List<ImportDeclaration> allImports = node.imports();
		foundTracerImport = COEvolgy.isImportIncluded(allImports, tracerImport);
		visitor = new ClassVarsVisitor();
		node.accept(visitor);
		
		// 2nd Traversal: Check for variables updated inside the loop, and about ALL existing method calls.
		visitor = new LoopVisitor();
		node.accept(visitor);
		
		// 3rd Traversal: Check if previously located method calls can be passed outside the loop, and do it.
		visitor = new MethodCallVisitor();
		node.accept(visitor);

		fileName = "";
		packageName = "";
		
		this.classVars.clear();
		this.conditionedVars.clear();
		this.methodCalls.clear();
	
	
    	return ASTHelper.VISIT_SUBTREE;
    }
	
	/* HELPERS */
	
    private String getVarFromExpression(MethodInvocation expression) {
    	String exprString = expression.getExpression() != null ? expression.getExpression().toString() + "." : "";
    	String methodName = expression.getName()
    								  .getFullyQualifiedName()
    								  .replace("this","");
    	String qualifiedName = exprString + methodName ;

        if (qualifiedName.contains(".")) {
            String varName = qualifiedName.split("\\.")[0];
            if (classVars.contains(varName)) {
                return "this";
            }else{
                return varName;
            }
        }else{
            return "this";
        }
    }

    private String getVarFromExpression(SimpleName expression) {
        String qualifiedName = expression.getIdentifier(); //.getFullyQualifiedName();

        if (qualifiedName.startsWith("this.") || classVars.contains(qualifiedName)) return "this";
        if (qualifiedName.contains(".")) {
            return qualifiedName.split("\\.")[0];
        }else{
            return qualifiedName;
        }
    }
    
    private String getVarFromExpression(QualifiedName expression) {
        String qualifiedName = expression.getFullyQualifiedName();

        if (qualifiedName.startsWith("this.") || classVars.contains(qualifiedName)) return "this";
        if (qualifiedName.contains(".")) {
            return qualifiedName.split("\\.")[0];
        }else{
            return qualifiedName;
        }
    }
    
    private String getVarFromExpression(Expression expression) {
    	String qualifiedName = "";
    	
    	if (expression instanceof SimpleName) {
    		qualifiedName = getVarFromExpression((SimpleName) expression);
    	} else if (expression instanceof QualifiedName) {
    		qualifiedName = getVarFromExpression((QualifiedName) expression);
    	} else if (expression instanceof MethodInvocation) {
    		MethodInvocation call = (MethodInvocation) expression;
    		qualifiedName = getVarFromExpression(call);
    		if (call.arguments() != null && !call.arguments().isEmpty()) {
    			for (Object o : call.arguments()) qualifiedName += ";" + getVarFromExpression((Expression)o);
    		}
    	} else if (expression instanceof ArrayAccess) {
    		ArrayAccess a = (ArrayAccess) expression;
    		Expression exp = a.getArray();
    		qualifiedName = getVarFromExpression(exp);
    		String indexName = getVarFromExpression(a.getIndex());
    		if (!indexName.equals("")) qualifiedName += ";" + indexName;
    	} else if (expression instanceof ParenthesizedExpression) {
    		ParenthesizedExpression exp = (ParenthesizedExpression) expression;
    		qualifiedName = getVarFromExpression(exp.getExpression());
    	} else if (expression instanceof InfixExpression) {
    		InfixExpression exp = (InfixExpression) expression;
    		String nameLeft = getVarFromExpression(exp.getLeftOperand());
    		String nameRight = getVarFromExpression(exp.getRightOperand());
    		qualifiedName = nameLeft + ";" + nameRight;
    	} else {
    		qualifiedName = "";
    	}
    	
    	return qualifiedName;
    }
    
    private String nodeTypeName(VariableDeclarationFragment node) {
    	String typeName = "null";
    	IVariableBinding binding = node.resolveBinding();
    	if (binding != null) {
    		if (binding.getType() != null) {
    			typeName = binding.getType().getName();
    		}
    	}
    	return typeName;
    }
    
    private String nodeTypeName(Expression node) {
    	String typeName = "null";
    	if (node instanceof InfixExpression) {
    		InfixExpression exp = (InfixExpression) node;
    		ITypeBinding binding = exp.getLeftOperand().resolveTypeBinding();
    		if (binding != null) {
    			typeName = binding.getName();
    		} else {
    			binding = exp.getRightOperand().resolveTypeBinding();
    			if (binding != null) {
    				typeName = binding.getName();
    			}
    		}
    	} else if (node instanceof Assignment) {
    		Assignment exp = (Assignment) node;
    		ITypeBinding binding = exp.getLeftHandSide().resolveTypeBinding();
    		if (binding != null) {
    			typeName = binding.getName();
    		} else {
    			binding = exp.getRightHandSide().resolveTypeBinding();
    			if (binding != null) {
    				typeName = binding.getName();
    			}
    		}
    	} else {
    		typeName = "null";
    	}
    	return typeName;
    }
    
    private String expressionTag(Expression node) {
    	String location = ASTHelper.getSourceLocation(node);
    	return "["+location+"]" + node.toString();
    }
    
    private String expressionTag(MethodInvocation node) {
        String location = ASTHelper.getSourceLocation(node);
        return "["+location+"]" + node.getName().getFullyQualifiedName();
    }
	
    private void debug() {
		System.out.println("\t:: VARS ::");
		for (String v : this.classVars) {
			System.out.println("\t\t> " + v);
		}
		System.out.println();
		
		System.out.println("\t:: COND VARS ::");
        for (String method : this.conditionedVars.keySet()) {
            Set<String> set = this.conditionedVars.get(method);
            System.out.println("\t\t [ " + method + " ]");
            for (String var : set) {
                System.out.println("\t\t\t>> " + var);
            }
        }
        System.out.println();

        System.out.println("\t:: CALLS ::");
        for (String method : this.methodCalls.keySet()) {
            List<String> list = this.methodCalls.get(method);
            System.out.println("\t\t [ " + method + " ]");
            for (String exp : list) {
                System.out.println("\t\t\t>> " + exp);
            }
        }
        System.out.println("\n");
    }
	
	
	/** Visitor class for 1st traversal */
	private class ClassVarsVisitor extends ASTVisitor {

        public ClassVarsVisitor() {
            
        }
        
        @Override
		public boolean visit(FieldDeclaration node) {
        	for (Object o : node.fragments()) {
        		VariableDeclarationFragment varDecl = (VariableDeclarationFragment) o;
        		classVars.add(varDecl.getName().getIdentifier());
        	}
			
        	return ASTHelper.VISIT_SUBTREE;
		}

    }
	
	
	/** Visitor class for 2nd traversal */
 	private class LoopVisitor extends ASTVisitor {
        /*
            How to determine if a method call can be passed outside the loop?
            RULES: (1) When the left operand of an assignment is a method call AND the variable is
                       not in the loop condition.
                   (2) When variables involved in a 1-operand statement are not used in the loop
                       condition.

            (+) For both rules, when a method invoked inside the loop has no relation to  variables
                updated inside the loop, it can be passed outside.
            */

        private int insideLoop;
        private boolean insideLoopCondition;
        private boolean insideAssignment;
        private Stack<String> loopConditions;

        public LoopVisitor() {
            this.insideLoop = 0;
            this.insideLoopCondition = false;
            this.insideAssignment = false;
            this.loopConditions = new Stack<>();
        }
        
        private void addConditionedVar(String varName) {
            if (conditionedVars.containsKey(visitingMethod)) {
                conditionedVars.get(visitingMethod).add(varName);
            }else{
                Set<String> set = new HashSet<>();
                set.add(varName);
                conditionedVars.put(visitingMethod, set);
            }
        }

        private void addMethodCall(MethodInvocation expression) {
            if (methodCalls.containsKey(visitingMethod)) {
                methodCalls.get(visitingMethod).add(expressionTag(expression));
            }else{
                ArrayList<String> list = new ArrayList<>();
                list.add(expressionTag(expression));
                methodCalls.put(visitingMethod, list);
            }
        }
        
		@Override
		public boolean visit(MethodInvocation node) {
			if (this.insideLoop > 0) {
				String varName = getVarFromExpression(node);
                this.addMethodCall(node);
                
                if (this.insideLoopCondition && this.insideAssignment) this.addConditionedVar(varName);
                
                if (!this.insideLoopCondition) {
                    if (!this.insideAssignment) {
                        // Means this is a method call with potential state change.
                        // In other words, the variable used in this call cannot go outside the loop.
                        this.addConditionedVar(varName);
                    }
                }
			}
			return super.visit(node);
		}
		
		@Override
		public boolean visit(ForStatement node) {
			this.insideLoop++;
			String condition = expressionTag(node.getExpression());
			loopConditions.push(condition);
			return super.visit(node);
		}
		
		@Override
		public void endVisit(ForStatement node) {
			this.insideLoop--;
			this.loopConditions.pop();
			super.endVisit(node);
		}
		
		
		@Override
		public boolean visit(WhileStatement node) {
			this.insideLoop++;
			String condition = expressionTag(node.getExpression());
			loopConditions.push(condition);
			return super.visit(node);
		}
		
		@Override
		public void endVisit(WhileStatement node) {
			this.insideLoop--;
			this.loopConditions.pop();
			super.endVisit(node);
		}
		

		@Override
		public boolean visit(DoStatement node) {
			this.insideLoop++;
			String condition = expressionTag(node.getExpression());
			loopConditions.push(condition);
			return super.visit(node);
		}
		
		@Override
		public void endVisit(DoStatement node) {
			this.insideLoop--;
			this.loopConditions.pop();
			super.endVisit(node);
		}
		
        
        @Override
        public boolean visit(MethodDeclaration node) {
            visitingMethod = packageName + "." + fileName + "." + node.getName();
            return super.visit(node);
        }
		
		@Override
		public boolean visit(Assignment node) {
			if (this.insideLoop > 0) {
				this.insideAssignment = true;
				Expression exp = node.getLeftHandSide();
				if (exp instanceof SimpleName) {
					String varName = getVarFromExpression((SimpleName) exp);
					this.addConditionedVar(varName);
				} else if (exp instanceof MethodInvocation) {
					String varName = getVarFromExpression((MethodInvocation) exp);
					this.addConditionedVar(varName);
				}
			}
			return super.visit(node);
		}
		
		@Override
		public void endVisit(Assignment node) {
			if (this.insideLoop > 0) {
				this.insideAssignment = false;
			}
			super.endVisit(node);
		}
		
		@Override
		public boolean visit(InfixExpression node) {
			if (this.insideLoop > 0) {
				String actualLoopCondition = loopConditions.peek();
				String condition = expressionTag(node);
				if (condition.equals(actualLoopCondition)) {
					this.insideLoopCondition = true;
				}
			}
			return super.visit(node);
		}
		
		@Override
		public void endVisit(InfixExpression node) {
			if (this.insideLoopCondition) {
				this.insideLoopCondition = false;
			}
			super.endVisit(node);
		}
		

		@Override
		public boolean visit(PostfixExpression node) {
			if (this.insideLoop > 0) {
				Expression exp = node.getOperand();
				if (exp instanceof SimpleName) {
					String varName = getVarFromExpression((SimpleName) exp);
					this.addConditionedVar(varName);
				}
			}
			return super.visit(node);
		}
		
		
		@Override
		public boolean visit(PrefixExpression node) {
			if (this.insideLoop > 0) {
				Expression exp = node.getOperand();
				if (exp.getNodeType() == ASTNode.SIMPLE_NAME) {
					String varName = getVarFromExpression((SimpleName) exp);
					this.addConditionedVar(varName);
				}
			}
			return super.visit(node);
		}
		

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			if (this.insideLoop > 0) {
				this.insideAssignment = true;
				if (node.getInitializer() != null) {
					String varName = node.getName().getIdentifier();
					this.addConditionedVar(varName);
				}
			}
			return super.visit(node);
		}
		
		@Override
		public void endVisit(VariableDeclarationFragment node) {
			if (this.insideLoop > 0) {
				this.insideAssignment = false;
			}
			super.endVisit(node);
		}
		
        
    }
	
	
	/** Visitor class for 3rd traversal */
	
	private class MethodCallVisitor extends ASTVisitor {

		private static final String helperVarPrefix = "_coev__var_";
		
		private Stack<Statement> parentLoops;
		private String typeName;
		private int count;
		
        public MethodCallVisitor() {
            this.parentLoops = new Stack<>();
            this.typeName = "";
            this.count = 0;
        }
        
        /* HELPERS */
        private boolean argsConditioned(List<String> methodArgs) {
            for (String arg : methodArgs) {
                if (arg.equals("")) return true; // means we don't know anything about this var,
                                                 // so we assume it is conditioned.
                if (conditionedVars.get(visitingMethod).contains(arg)) return true;
            }

            return false;
        }
        
        private List<String> argNames(MethodInvocation call) {
        	List<String> methodArgs = new ArrayList<>();
        	methodArgs.addAll(argNames(call.arguments()));
        	
        	return methodArgs;
        }
        
        private List<String> argNames(List arguments) {
        	List<String> methodArgs = new ArrayList<>();
        	for (Object o : arguments) {
        		Expression expArg = (Expression) o;
        		String qualifiedName = getVarFromExpression(expArg);
        		if (expArg.getNodeType() == ASTNode.METHOD_INVOCATION) {
        			methodArgs.addAll(argNames((MethodInvocation) expArg));
        		}
        		if (qualifiedName.contains(";")) {
        			String[] nameSplit = qualifiedName.split(";");
        			for (String s : nameSplit) methodArgs.add(s);
        		} else {
        			methodArgs.add(qualifiedName);
        		}
        	}
        	return methodArgs;
        }
        
        private boolean alreadyRefactored(ASTNode node) {
        	ASTNode parentStmt = COEvolgy.getParentStatement(node);
            if (parentStmt instanceof VariableDeclarationStatement) {
            	VariableDeclarationStatement decl = (VariableDeclarationStatement) parentStmt;
            	for (Object o : decl.fragments()) {
            		VariableDeclarationFragment frag = (VariableDeclarationFragment) o;
            		if (frag.getName().getIdentifier().startsWith(helperVarPrefix)) {
            			return true;
            		}
            	}
            }
            
            return false;
        }
        
        private String getTypeOfExpression(MethodInvocation node) {
        	IMethodBinding binding = node.resolveMethodBinding();
        	if (node.toString().startsWith("Encoding.UTF_8.toString")) {
        	}
        	
        	if (binding == null || binding.getReturnType() == null) return null;
        	
        	return binding.getReturnType().getName(); 
        }
        
        /* VISITORS */
        @Override
        public boolean visit(MethodInvocation node) {
        	final ASTBuilder b = ctx.getASTBuilder();
            final Refactorings r = ctx.getRefactorings();
            
            // initial check: 
            //    when moving a method call outside a loop, it is possible that 
            //    the moved call will be re-visited (not sure why it happens).
            if (alreadyRefactored(node)) return ASTHelper.VISIT_SUBTREE;
            
            String name = getVarFromExpression(node);
            // all method calls inside loops, belonging to the method `visitingMethod`.
            List<String> calls = methodCalls.get(visitingMethod);
            if (calls != null && calls.contains(expressionTag(node))) {
                // the method call being examined exists in the inside loop `calls`.
                // if the call variable is not conditioned, it can be passed outside the loop (issue found).
                Set<String> vars = conditionedVars.get(visitingMethod);
                List<String> args = argNames(node.arguments());
                if (node.getExpression() != null && node.getExpression().getNodeType() == ASTNode.METHOD_INVOCATION) {
                	args.addAll(argNames((MethodInvocation) node.getExpression()));
                }
                
                if ((vars != null) && (!vars.contains(name)) && (!argsConditioned(args))) {
                	String currentType = getTypeOfExpression(node);
                	if (currentType == null) currentType = this.typeName;
                	
                	if (!(currentType.equals("null")) && !(currentType.equals(""))) {
	                	count++;
	                	String helperVar = helperVarPrefix + count;
	                	VariableDeclarationStatement newVar = b.declareStmt(b.type(currentType), b.simpleName(helperVar), b.copySubtree(node));
	                	
	                	if (operationFlag == TRACE) {
	        				// insert instruction to trace the patterns execution
	                		insertTraceNode(b, r, node);
	        			}
	                	
	                	r.insertBefore(newVar, this.parentLoops.peek());
	                	r.replace(node, b.simpleName(helperVar));
	                	
	                	COEvolgy.traceRefactoring(TAG);
	                		                	
	                	return ASTHelper.DO_NOT_VISIT_SUBTREE;
                	}
                }
            }
            
        	return super.visit(node);
        }
        
        
        @Override
        public boolean visit(MethodDeclaration node) {
            visitingMethod = packageName + "." + fileName + "." + node.getName();
            return super.visit(node);
        }
        
        @Override
        public boolean visit(ForStatement node) {
        	this.parentLoops.push(node);
        	return super.visit(node);
        }
        
        @Override
        public void endVisit(ForStatement node) {
        	this.parentLoops.pop();
        	super.endVisit(node);
        }
        
        
        @Override
        public boolean visit(WhileStatement node) {
        	this.parentLoops.push(node);
        	return super.visit(node);
        }
        
        @Override
        public void endVisit(WhileStatement node) {
        	this.parentLoops.pop();
        	super.endVisit(node);
        }
        
        
        @Override
        public boolean visit(DoStatement node) {
        	this.parentLoops.push(node);
        	return super.visit(node);
        }
        
        @Override
        public void endVisit(DoStatement node) {
        	this.parentLoops.pop();
        	super.endVisit(node);
        }

		@Override
		public boolean visit(Assignment node) {
			this.typeName = nodeTypeName(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(InfixExpression node) {
			this.typeName = nodeTypeName(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			this.typeName = nodeTypeName(node);
			return super.visit(node);
		}
		
		
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
		
		
		private void insertTraceNode(ASTBuilder b, Refactorings r, ASTNode node) {
			ASTNode parent = COEvolgy.getParentStatement(node);
    		ASTNode element = null;
    		Statement s = null;
    		if (parent instanceof ForStatement) {
    			s = ((ForStatement) parent).getBody();
    			if (s instanceof Block) {
    				element = (ASTNode) ((Block) s).statements().get(0);
    			} 
    		} else if (parent instanceof WhileStatement) {
    			s = ((WhileStatement) parent).getBody();
    			if (s instanceof Block) {
    				element = (ASTNode) ((Block) s).statements().get(0);
    			} 
    		} else if (parent instanceof DoStatement){
    			s = ((DoStatement) parent).getBody();
    			if (s instanceof Block) {
    				element = (ASTNode) ((Block) s).statements().get(0);
    			} 
    		} else {
    			element = parent;
    		}
    		
    		if (element == null) {
    			// the element is null because the parent statement
    			// is a loop with only one statement
    			Block block = b.block(b.copy(s));
                block.accept(this);
                r.replace(s, block);
    		}
    		
			r.insertAfter(traceNode(false), parent);
		}
		
		private ASTNode traceNode(boolean flag) {
			COEvolgy helper = new COEvolgy(ctx, flag);
			return helper.buildTraceNode(TAG);
		}
        
        
    }
    

}
