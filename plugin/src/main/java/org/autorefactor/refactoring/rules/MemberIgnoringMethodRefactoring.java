package org.autorefactor.refactoring.rules;

import static org.autorefactor.util.COEvolgy.MEASURE;
import static org.autorefactor.util.COEvolgy.TRACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.ASTHelper;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.util.COEvolgy;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

public class MemberIgnoringMethodRefactoring extends AbstractRefactoringRule {

	public static final String TAG = "MemberIgnoringMethod";
	
	private static int operationFlag = MEASURE;
	private static boolean foundTracerImport = false;
	
	private static Map<String, Boolean> fields;
    private static Map<String, Set<String>> methods;
    private static Map<String, Boolean> methodsStatus;
    private static int methodsDepth = 0;
	
	private static final String tracerImport = "org.greenlab.coevolgy.util.Tracer";
	
	private static String mClassName = "";
	
	public MemberIgnoringMethodRefactoring() {
		super();
		
		fields = new HashMap<>();
        methods = new HashMap<>();
        methodsStatus = new HashMap<>();
	}
	
	public MemberIgnoringMethodRefactoring(int flag) {
		super();
		operationFlag = flag;
		
		fields = new HashMap<>();
        methods = new HashMap<>();
        methodsStatus = new HashMap<>();
	}
	
	@Override
	public String getName() {
		return "MemberIgnoringMethodRefactoring";
	}

	@Override
	public String getDescription() {
		return "non-static method that can be made static";
	}

	@Override
	public String getReason() {
		return "Methods that do not have a static modifier, yet never access a field or a non-static "+
				"method, can be converted to static.";
	}
	
	private static void isMIM(String methodName) {
        methodsStatus.put(methodName, true);
    }

    private static void notMIM(String methodName) {
        methodsStatus.put(methodName, false);
    }

    private static boolean isField(String qualifiedName) {
        if (fields.containsKey(qualifiedName)) return fields.get(qualifiedName);

        return false;
    }
    
    private ASTNode getParentClass(ASTNode node) {
    	if (node == null) return null;
    	
    	ASTNode parent = node.getParent();
    	if (parent == null) return null;
    	
    	if (parent instanceof TypeDeclaration) return parent;
    	
    	else if (parent instanceof AnonymousClassDeclaration) return parent;
    	
    	else return getParentClass(parent);
    }
    
    private void classifyMIM(String methodName) {
        if (methodsStatus.containsKey(methodName)) {
        	return;
        }

        if (! methods.containsKey(methodName)){
            // it's a method that we have no information about. Hence, it can either be a MIM or not.
            // since we MUST avoid having false positives, in this cases we assume is a non-MIM.
            notMIM(methodName);
            return;
        }

        for (String depend : methods.get(methodName)) {
            classifyMIM(depend);
            if (methodsStatus.containsKey(depend) && !methodsStatus.get(depend)) {
                notMIM(methodName);
                return;
            }
        }
        isMIM(methodName);
    }

    private void checkDependencies() {
        for (String method : methods.keySet()) {
            classifyMIM(method);
        }
    }
	
	public void debug() {
        System.out.println("\t << FIELDS >>");
        for (String f : fields.keySet()) {
            System.out.println("\t\t>> " + f + " [" + fields.get(f) + "]");
        }

        System.out.println("\t ** METHODS **");
        for (String m : methods.keySet()) {
            System.out.print("\t\t** " + m + " => [ ");
            for (String d :  methods.get(m)) {
                System.out.print(d + ", ");
            }
            System.out.println(" ]");
        }

        System.out.println("\t ## STATUS ##");
        for (String s : methodsStatus.keySet()) {
            System.out.println("\t\t## " + s + " [" + methodsStatus.get(s) + "]");
        }
        System.out.println();
    }
	
	
	@Override
    public boolean visit(TypeDeclaration node) {
		CompilationUnit unit = (CompilationUnit)ASTNodes.getParent(node, ASTNode.COMPILATION_UNIT);
		mClassName = node.resolveBinding().getQualifiedName();
		
		fields.clear();
		methods.clear();
		if (!node.isInterface() && !Modifier.isAbstract(node.getModifiers())) {
			// First check: fields
			FieldChecker checker = new FieldChecker();
			node.accept(checker);
			
			// Second check: method calls
			MemberIgnoringChecker mimChecker = new MemberIgnoringChecker();
			node.accept(mimChecker);
			
			checkDependencies();
			//debug();
			// Second check: refactor methods
			MemberIgnoringRefactor refactor = new MemberIgnoringRefactor();
			node.accept(refactor);
			
			// Final check: imports
			ImportVisitor importChecker = new ImportVisitor();
			node.accept(importChecker);
		}
		return super.visit(node);
    }

public class FieldChecker extends ASTVisitor{
	
	public FieldChecker() {
		
	}
	
	private void addField(FieldDeclaration field, TypeDeclaration containingClass, boolean isNonStatic) {
		for (Object o : field.fragments()) {
			VariableDeclarationFragment fragment = (VariableDeclarationFragment) o;
			String fullName = containingClass.resolveBinding().getQualifiedName() + "." + fragment.getName().getIdentifier();
			fields.put(fullName, isNonStatic);
		}
    }
	
	
	@Override
	public boolean visit(FieldDeclaration node) {
		boolean isNonStatic = true;
		for (Object o : node.modifiers()) {
			if (o instanceof Modifier) {
				Modifier modifier = (Modifier) o;
				if (modifier.isStatic()) {
					isNonStatic = false;
					break;
				}
			}
		}
		
		// a "Field" must allways be declared inside a Type Declaration
		addField(node, (TypeDeclaration)ASTNodes.getParent(node, ASTNode.TYPE_DECLARATION), isNonStatic);
		return super.visit(node);
	}
	
}
	
	
public class MemberIgnoringChecker extends ASTVisitor{
	
	public MemberIgnoringChecker() {
		
	}
	
    private void addDependency(MethodDeclaration parentMethod, String reference) {
    	TypeDeclaration parentClass = (TypeDeclaration)ASTNodes.getParent(parentMethod, ASTNode.TYPE_DECLARATION);
        String methodName = COEvolgy.getMethodQualifiedName(parentMethod);
        
        if (methodName != null && !methodName.equals(reference)) {
            if (methods.containsKey(methodName)) {
                methods.get(methodName).add(reference);
            } else {
                Set<String> refs = new HashSet<>();
                refs.add(reference);
                methods.put(methodName, refs);
            }
        }
    }
	

	@Override
	public boolean visit(ThisExpression node) {
		MethodDeclaration parentMethod = COEvolgy.getParentMethod(node);
		if (parentMethod != null) {
			String methodName = COEvolgy.getMethodQualifiedName(parentMethod);
			notMIM(methodName);
		}
		return super.visit(node);
	}	

	@Override
	public boolean visit(MethodDeclaration node) {
		if (!node.isConstructor()) {
			methodsDepth++;
			String methodName = COEvolgy.getMethodQualifiedName(node);
			if (methodName == null) {
				// the following code will be executed if a there are two methods with the same name.
				methodName = mClassName + "." + node.getName().getIdentifier() + "(";
				for (String m : methods.keySet()) {
					if (m.startsWith(methodName)) notMIM(m);
				}
				return super.visit(node); 
			}
			
			// check modifiers, and classify method as not MIM if it finds an '@Override'
			// NOTE: this check is necessary because methods inherited from 'Object' are not being detected with bindings.
			for (Object o : node.modifiers()) {
				if (o != null && (o.toString().equals("@Override") || o.toString().equals("@java.lang.Override"))) {
					notMIM(methodName);
				}
			}
			
			IMethodBinding binding = node.resolveBinding();
			Set<IMethodBinding> overridenMethods = ASTHelper.getOverridenMethods(binding);
						
			ASTNode parentClass = getParentClass(node);
			
			if (!methods.containsKey(methodName)) {
				methods.put(methodName, new HashSet<>());
			}
			
			boolean isStatic = Modifier.isStatic(node.resolveBinding().getModifiers());
			if (isStatic) {
				isMIM(methodName);
			} else if (parentClass instanceof AnonymousClassDeclaration) {
				notMIM(methodName);
			} else if (overridenMethods != null && !overridenMethods.isEmpty()) {
				notMIM(methodName);
				for (IMethodBinding mb : overridenMethods) {
					String superMethod = COEvolgy.getMethodQualifiedName(mb);
					if (superMethod != null) {
						addDependency(node, superMethod);
						notMIM(superMethod);
					}
				}
			}
			
		}
		return super.visit(node);
	}
	
	@Override
	public void endVisit(MethodDeclaration node) {
		if (!node.isConstructor()) {
			methodsDepth--;
		}
		super.endVisit(node);
	}
	
	
	@Override
	public boolean visit(MethodInvocation node) {
		String methodName = COEvolgy.getMethodQualifiedName(node);
		MethodDeclaration parentMethod = COEvolgy.getParentMethod(node);
		if (methodName == null && parentMethod != null) {
			notMIM(COEvolgy.getMethodQualifiedName(parentMethod));
		}
		if (methodsDepth > 0 && parentMethod != null && !parentMethod.isConstructor()) {
			Expression exp = node.getExpression();
			if (exp != null) {
				// a method call that was either accessed through a variable, 
				// or directly (like 'a().b().n()').
				String callVar = exp.toString().replace("this.", "");
				if (Character.isUpperCase(callVar.charAt(0))
						|| callVar.contains("(")) {
					// reaching this point means either that:
					//     (1) something accessed directly through the class (like 'Class.method' or 'Class.field'), 
					//         and we assume that 'Class' is NOT A FIELD.
					//     (2) we're not looking at the top method (in 'a().b()', this would be 'b()').
					return super.visit(node);
				}

					// a method call that was accessed through a variable.
					String varQualifiedName = mClassName + "." + callVar;
					if (isField(varQualifiedName)) {
						notMIM(COEvolgy.getMethodQualifiedName(parentMethod));
					}
				
			} else {
				// a method call that was directly accessed (of the form 'method1()').
				addDependency(parentMethod, methodName);
				
				boolean isStatic = false;
				if (node.resolveMethodBinding() != null) {
					isStatic = Modifier.isStatic(node.resolveMethodBinding().getModifiers());
				}
				
				if (isStatic) {
					isMIM(methodName);
					
					if (!methods.containsKey(methodName)) {
						methods.put(methodName, new HashSet<>());
					}
				}
				
			}
		}
		return super.visit(node);
	}
	
	@Override
	public boolean visit(SimpleName node) {
		if (methodsDepth > 0) {
			MethodDeclaration parentMethod = COEvolgy.getParentMethod(node);
			if (parentMethod != null) {
				String methodName = COEvolgy.getMethodQualifiedName(parentMethod);
				if (methodName != null) {
					TypeDeclaration parentType = (TypeDeclaration) ASTNodes.getParent(node, ASTNode.TYPE_DECLARATION);
					String qualifiedName = parentType.resolveBinding().getQualifiedName() + "." + node.getIdentifier();
					if (node.resolveBinding() != null && node.resolveBinding().getKind() == IBinding.VARIABLE) {
						if (isField(qualifiedName)) {
							notMIM(COEvolgy.getMethodQualifiedName(parentMethod));
						}
					} else if (isField(qualifiedName)){
						notMIM(COEvolgy.getMethodQualifiedName(parentMethod));
					}
				} else {
					// 'methodName == null' means we can't be sure what is happening in this method.
					// Hence, we assume it is "not MIM".
					methodName = mClassName + "." + parentMethod.getName().getIdentifier() + "(";
					for (String m : methods.keySet()) {
						if (m.startsWith(methodName)) {
							notMIM(m);
						}
					}
				}
			}
		}
		return super.visit(node);
	}
	
}

public class MemberIgnoringRefactor extends ASTVisitor {
	
	@Override
	public boolean visit(MethodDeclaration node) {
		String methodName = COEvolgy.getMethodQualifiedName(node);
		ASTNode parentClass = getParentClass(node);
		
		boolean isStatic = node.resolveBinding() == null
							|| Modifier.isStatic(node.resolveBinding().getModifiers());
		boolean emptyBody = node.getBody() == null
							|| node.getBody().statements() == null
							|| node.getBody().statements().isEmpty();
		
		boolean ignorable = isStatic
							|| emptyBody
							|| (parentClass instanceof AnonymousClassDeclaration);
		
		if (ignorable) {
			return super.visit(node);
		}
		
		if (methodsStatus.containsKey(methodName) && methodsStatus.get(methodName)) {
			// MIM found!
			final ASTBuilder b = ctx.getASTBuilder();
			final Refactorings r = ctx.getRefactorings();
			COEvolgy helper = new COEvolgy(ctx, false);
			
			MethodDeclaration newMethod = b.copySubtree(node);
			
			if (newMethod.modifiers() != null) {
				newMethod.modifiers().add(b.static0());
				
				if (operationFlag == TRACE) {
					ASTNode traceNode = helper.buildTraceNode(TAG);
					r.insertAt(newMethod.getBody(),
							Block.STATEMENTS_PROPERTY,
							traceNode,
							0
					);
				}
				r.replace(node, newMethod);
				
				COEvolgy.traceRefactoring(TAG);
				
				return ASTHelper.DO_NOT_VISIT_SUBTREE;
			} else {
				System.out.println("\t\t $$ NO MODIFIERS IN METHOD: " + methodName);
			}
			
			return ASTHelper.VISIT_SUBTREE;
			
		}
		
		return ASTHelper.VISIT_SUBTREE;
	}
}

public class ImportVisitor extends ASTVisitor {
		
		@Override
		public boolean visit(TypeDeclaration node) {
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
		
	}

}
