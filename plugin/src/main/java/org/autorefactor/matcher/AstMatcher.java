package org.autorefactor.matcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.autorefactor.refactoring.ASTHelper;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Matcher framework for Eclipse JDT dom AST.
 *
 * Heavily inspired by clang ast matchers (http://clang.llvm.org/docs/LibASTMatchersReference.html),
 * hamcrest matchers and assertj.
 *
 * @author cal
 */
public abstract class AstMatcher extends PrivateAstMatcher {

	/**
	 * Immutable list of binding maps.
	 */
	public static final class BoundNodes {
		static final BoundNodes EMPTY = new BoundNodes();

		// TODO: optimize map memory foot print? List for small sizes? optimized map config?
		private final List<Map<String,Object>> bindings;

		private BoundNodes() {
			this.bindings = Collections.emptyList();
		}

		private BoundNodes(List<Map<String,Object>> from) {
			this.bindings = from;
		}

		static BoundNodes create(BoundNodesMapRead map) {
			return map != null && !map.isEmpty() ? new BoundNodes(map.snapshot()) : EMPTY;
		}

        public boolean isEmpty() {
            return bindings.isEmpty();
        }

        // TODO: remove hack
        public Object getSingle(String id) {
            if (bindings.size() != 1) {
                throw new IllegalStateException("unexpected size: " + bindings.size()
                + "\nbindings: " + bindings);
            }
            return bindings.get(0).get(id);
        }

        // TODO: remove hack
        public Map<String,Object> getSingleMap() {
            if (bindings.size() != 1) {
                throw new IllegalStateException("unexpected size: " + bindings.size()
                + "\nbindings: " + bindings);
            }
            return new HashMap<String,Object>(bindings.get(0));
        }

        /**
         * Get bound node cast to given class.
         *
         * TODO: fix access for multiple maps!
         *
         * Use getAs or optionalAs if you need softer conditions...
         *
         * @throws ClassCastException if class does not match
         * @throws IllegalArgumentException if id is not bound
         */
        public <T> T castAs(String id, Class<T> clazz) {
            Object o = get(id);
            if (o == null) {
                throw new IllegalArgumentException("no binding for '" + id + "'");
            }
            return clazz.cast(o);
        }

        /* @Nullable */ public <T> T getAs(String id, Class<T> clazz) {
            Object o = get(id);
            return clazz.isInstance(o) ? clazz.cast(o) : null;
        }

        /* @Nullable */ public Object get(String id) {
            return getSingle(id);
        }

        public boolean containsBound(String id) {
            return get(id) != null;
        }

        @Override
        public String toString() {
            return bindings.toString();
        }
    }

    /**
     * Modifiable list of binding maps that collects bindings found
     * during match traversal.
     *
     * Implements a copy-on-write data structure to avoid extensive copying.
     */
    public static class BoundNodesBuilder {
        /** shared BindingMap instance for read-only access */
        private BoundNodesMapRead shared;
        /** own copy of bindings or null if unchanged compared to "shared" */
        private BoundNodesMap bindings;

        public BoundNodesBuilder() {
        }

        private BoundNodesBuilder(BoundNodesMapRead shared) {
            this.shared = shared;
        }


        public synchronized BoundNodesBuilder copy() {
            return new BoundNodesBuilder(share());
        }

        /** add bindings from other builder */
        public void addMatch(BoundNodesBuilder otherBuilder) {
            final /* @Nullable */ BoundNodesMapRead otherBindings = otherBuilder.effectiveBindings();
            synchronized (this) {
                if (otherBindings != effectiveBindings() && otherBindings != null && !otherBindings.isEmpty()) {
                    BoundNodesMap bindings = writableBindings();
                    // TODO: probably wrong
                    if (!bindings.isEmpty()) {
                        throw new IllegalArgumentException("adding second entries: \nother: " + otherBindings
                                + "\nto: " + bindings);
                    }
                    bindings.addAll(otherBindings);
                }
            }
        }

        private synchronized /* @Nullable */ BoundNodesMapRead effectiveBindings() {
            return bindings != null ? bindings : shared;
        }

        private synchronized BoundNodesMap writableBindings() {
            if (bindings == null) {
                bindings = shared != null ? shared.copy() : new BoundNodesMap();
            }
            return bindings;
        }

        private synchronized BoundNodesMapRead share() {
            if (bindings != null) {
                shared = bindings;
                bindings = null;
            }
            return shared;
        }

        /**
         * Set current content to given content.
         */
        public synchronized void set(BoundNodesBuilder other) {
            bindings = null;
            shared = other.share();
        }

        /**
         * Bind "id" to value "o".
         */
        public void put(String id, Object o) {
            writableBindings().put(id, o);
        }

        /**
         * Bind ids to values.
         */
        public void putAll(Map<String,Object> map) {
            writableBindings().putAll(map);
        }

        @Override
        public String toString() {
            return "BindingBuilder [" + effectiveBindings() + "]";
        }

        /**
         * Get bound node cast to given class.
         *
         * TODO: fix access for multiple maps!
         *
         * Use getAs or optionalAs if you need softer conditions...
         *
         * @throws ClassCastException if class does not match
         * @throws IllegalArgumentException if id is not bound
         */
        public <T> T castAs(String id, Class<T> clazz) {
            Object o = get(id);
            if (o == null) {
                throw new IllegalArgumentException("no binding for '" + id + "'");
            }
            return clazz.cast(o);
        }

        /* @Nullable */ public <T> T getAs(String id, Class<T> clazz) {
            Object o = get(id);
            return clazz.isInstance(o) ? clazz.cast(o) : null;
        }

        /* @Nullable */ public Object get(String id) {
            BoundNodesMapRead bounds = effectiveBindings();
            return bounds != null && !bounds.isEmpty() ? bounds.getSingle(id) : null;
        }

        public boolean hasBound(String id) {
            return get(id) != null;
        }

        public BoundNodes bindings() {
            return BoundNodes.create(effectiveBindings());
        }

        boolean isEmpty() {
            BoundNodesMapRead bounds = effectiveBindings();
            return bounds == null || bounds.isEmpty();
        }

        void clear() {
            shared = null;
            bindings = null;
        }
    }

    /** basic matcher interface for some ast node T */
    public interface Matcher<T extends ASTNode> {
        boolean match(ASTNode t, BoundNodesBuilder bounds);
    }

    /**
     * Matchers that allow binding the matched not to an id.
     * Typically node matchers.
     *
     * @param <T>	the matchable node type
     * @param <M>	the matcher itself for fluent interface
     */
    public interface BindableMatcher<T extends ASTNode, M extends Matcher<T>> extends Matcher<T> {
        M bind(String id);
    }

    /** matches every non null node */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public
    static <T extends ASTNode> BindableMatcher<T, Matcher<T>> anything() {
        return new CommonMatcher(ASTNode.class);
    }

    /**
     * One matcher must match. Matching is finished on first match.
     */
    @SafeVarargs
    public static <T extends ASTNode> AnyOfMatcher<T> anyOf(Matcher<? extends T>... matchers) {
        return new AnyOfMatcher<T>(matchers);
    }

    /**
     * All matchers must match. Matching is aborted on first fail.
     */
    @SuppressWarnings("unchecked")
    @SafeVarargs
    public static <T extends ASTNode> Matcher<T> allOf(Matcher<? extends T> firstMatcher, Matcher<? extends T>... matchers) {
        return matchers.length == 0 ? (Matcher<T>)firstMatcher : new CommonMatcher<>(ASTNode.class, matchers);
    }

    /**
     * Negates the given matcher. Matches if given matcher returns false. 
     */
	public static <T extends ASTNode> Matcher<T> unless(Matcher<? extends T> matcher) {
		return new Matcher<T>() {

			@Override
			public boolean match(ASTNode t, BoundNodesBuilder bounds) {
				// the sub matcher shall be able to read bounds but new binds are not 
				// returned
				return !matcher.match(t, bounds.copy());
			}
		};
	}

    /**
     * Matches if any of the descendants of node matches. 
     */
	public static <T extends ASTNode> Matcher<T> descendant(Matcher<?> matcher) {
		return new Matcher<T>() {

			@Override
			public boolean match(ASTNode t, BoundNodesBuilder bounds) {
	            DescendantFinderVisitor visitor = new DescendantFinderVisitor(matcher, bounds);
	            return visitor.findOrDefault(t, false);
			}
		};
	}
	
    /**
     * Matcher that returns true if expression is instance of given class or sub class.
     */
    public static Matcher<? extends Expression> isInstanceOf(String className) {
        return new IsInstanceOfMatcher(className);
    }

    /**
     * @see #statement()
     */
    public static Matchers.StatementMatcher stmt() {
        return new Matchers.StatementMatcher();
    }

    /* begin of generated code */
    
    /**
     * Create a matcher matching AST node BreakStatement.
     *
     * {@link org.eclipse.jdt.core.dom.BreakStatement}
     */
    public static Matchers.BreakStatementMatcher breakStatement() {
        return new Matchers.BreakStatementMatcher();
    }

    /**
     * Create a matcher matching AST node ArrayCreation.
     *
     * {@link org.eclipse.jdt.core.dom.ArrayCreation}
     */
    public static Matchers.ArrayCreationMatcher arrayCreation() {
        return new Matchers.ArrayCreationMatcher();
    }

    /**
     * Create a matcher matching AST node VariableDeclarationExpression.
     *
     * {@link org.eclipse.jdt.core.dom.VariableDeclarationExpression}
     */
    public static Matchers.VariableDeclarationExpressionMatcher variableDeclarationExpression() {
        return new Matchers.VariableDeclarationExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node CastExpression.
     *
     * {@link org.eclipse.jdt.core.dom.CastExpression}
     */
    public static Matchers.CastExpressionMatcher castExpression() {
        return new Matchers.CastExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node Annotation.
     *
     * {@link org.eclipse.jdt.core.dom.Annotation}
     */
    public static Matchers.AnnotationMatcher annotation() {
        return new Matchers.AnnotationMatcher();
    }

    /**
     * Create a matcher matching AST node InstanceofExpression.
     *
     * {@link org.eclipse.jdt.core.dom.InstanceofExpression}
     */
    public static Matchers.InstanceofExpressionMatcher instanceofExpression() {
        return new Matchers.InstanceofExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node ContinueStatement.
     *
     * {@link org.eclipse.jdt.core.dom.ContinueStatement}
     */
    public static Matchers.ContinueStatementMatcher continueStatement() {
        return new Matchers.ContinueStatementMatcher();
    }

    /**
     * Create a matcher matching AST node FieldDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.FieldDeclaration}
     */
    public static Matchers.FieldDeclarationMatcher fieldDeclaration() {
        return new Matchers.FieldDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node QualifiedType.
     *
     * {@link org.eclipse.jdt.core.dom.QualifiedType}
     */
    public static Matchers.QualifiedTypeMatcher qualifiedType() {
        return new Matchers.QualifiedTypeMatcher();
    }

    /**
     * Create a matcher matching AST node CatchClause.
     *
     * {@link org.eclipse.jdt.core.dom.CatchClause}
     */
    public static Matchers.CatchClauseMatcher catchClause() {
        return new Matchers.CatchClauseMatcher();
    }

    /**
     * Create a matcher matching AST node MemberValuePair.
     *
     * {@link org.eclipse.jdt.core.dom.MemberValuePair}
     */
    public static Matchers.MemberValuePairMatcher memberValuePair() {
        return new Matchers.MemberValuePairMatcher();
    }

    /**
     * Create a matcher matching AST node VariableDeclarationFragment.
     *
     * {@link org.eclipse.jdt.core.dom.VariableDeclarationFragment}
     */
    public static Matchers.VariableDeclarationFragmentMatcher variableDeclarationFragment() {
        return new Matchers.VariableDeclarationFragmentMatcher();
    }

    /**
     * Create a matcher matching AST node EnumDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.EnumDeclaration}
     */
    public static Matchers.EnumDeclarationMatcher enumDeclaration() {
        return new Matchers.EnumDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node BlockComment.
     *
     * {@link org.eclipse.jdt.core.dom.BlockComment}
     */
    public static Matchers.BlockCommentMatcher blockComment() {
        return new Matchers.BlockCommentMatcher();
    }

    /**
     * Create a matcher matching AST node ConditionalExpression.
     *
     * {@link org.eclipse.jdt.core.dom.ConditionalExpression}
     */
    public static Matchers.ConditionalExpressionMatcher conditionalExpression() {
        return new Matchers.ConditionalExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node ASTNode.
     *
     * {@link org.eclipse.jdt.core.dom.ASTNode}
     */
    public static Matchers.ASTNodeMatcher node() {
        return new Matchers.ASTNodeMatcher();
    }

    /**
     * Create a matcher matching AST node BooleanLiteral.
     *
     * {@link org.eclipse.jdt.core.dom.BooleanLiteral}
     */
    public static Matchers.BooleanLiteralMatcher booleanLiteral() {
        return new Matchers.BooleanLiteralMatcher();
    }

    /**
     * Create a matcher matching AST node Name.
     *
     * {@link org.eclipse.jdt.core.dom.Name}
     */
    public static Matchers.NameMatcher name() {
        return new Matchers.NameMatcher();
    }

    /**
     * Create a matcher matching AST node ArrayType.
     *
     * {@link org.eclipse.jdt.core.dom.ArrayType}
     */
    public static Matchers.ArrayTypeMatcher arrayType() {
        return new Matchers.ArrayTypeMatcher();
    }

    /**
     * Create a matcher matching AST node Javadoc.
     *
     * {@link org.eclipse.jdt.core.dom.Javadoc}
     */
    public static Matchers.JavadocMatcher javadoc() {
        return new Matchers.JavadocMatcher();
    }

    /**
     * Create a matcher matching AST node ClassInstanceCreation.
     *
     * {@link org.eclipse.jdt.core.dom.ClassInstanceCreation}
     */
    public static Matchers.ClassInstanceCreationMatcher classInstanceCreation() {
        return new Matchers.ClassInstanceCreationMatcher();
    }

    /**
     * Create a matcher matching AST node EnumConstantDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.EnumConstantDeclaration}
     */
    public static Matchers.EnumConstantDeclarationMatcher enumConstantDeclaration() {
        return new Matchers.EnumConstantDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node TryStatement.
     *
     * {@link org.eclipse.jdt.core.dom.TryStatement}
     */
    public static Matchers.TryStatementMatcher tryStatement() {
        return new Matchers.TryStatementMatcher();
    }

    /**
     * Create a matcher matching AST node AnnotatableType.
     *
     * {@link org.eclipse.jdt.core.dom.AnnotatableType}
     */
    public static Matchers.AnnotatableTypeMatcher annotatableType() {
        return new Matchers.AnnotatableTypeMatcher();
    }

    /**
     * Create a matcher matching AST node ReturnStatement.
     *
     * {@link org.eclipse.jdt.core.dom.ReturnStatement}
     */
    public static Matchers.ReturnStatementMatcher returnStatement() {
        return new Matchers.ReturnStatementMatcher();
    }

    /**
     * Create a matcher matching AST node BodyDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.BodyDeclaration}
     */
    public static Matchers.BodyDeclarationMatcher bodyDeclaration() {
        return new Matchers.BodyDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node SingleMemberAnnotation.
     *
     * {@link org.eclipse.jdt.core.dom.SingleMemberAnnotation}
     */
    public static Matchers.SingleMemberAnnotationMatcher singleMemberAnnotation() {
        return new Matchers.SingleMemberAnnotationMatcher();
    }

    /**
     * Create a matcher matching AST node TypeLiteral.
     *
     * {@link org.eclipse.jdt.core.dom.TypeLiteral}
     */
    public static Matchers.TypeLiteralMatcher typeLiteral() {
        return new Matchers.TypeLiteralMatcher();
    }

    /**
     * Create a matcher matching AST node VariableDeclarationStatement.
     *
     * {@link org.eclipse.jdt.core.dom.VariableDeclarationStatement}
     */
    public static Matchers.VariableDeclarationStatementMatcher variableDeclarationStatement() {
        return new Matchers.VariableDeclarationStatementMatcher();
    }

    /**
     * Create a matcher matching AST node AnonymousClassDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.AnonymousClassDeclaration}
     */
    public static Matchers.AnonymousClassDeclarationMatcher anonymousClassDeclaration() {
        return new Matchers.AnonymousClassDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node VariableDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.VariableDeclaration}
     */
    public static Matchers.VariableDeclarationMatcher variableDeclaration() {
        return new Matchers.VariableDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node EmptyStatement.
     *
     * {@link org.eclipse.jdt.core.dom.EmptyStatement}
     */
    public static Matchers.EmptyStatementMatcher emptyStatement() {
        return new Matchers.EmptyStatementMatcher();
    }

    /**
     * Create a matcher matching AST node Initializer.
     *
     * {@link org.eclipse.jdt.core.dom.Initializer}
     */
    public static Matchers.InitializerMatcher initializer() {
        return new Matchers.InitializerMatcher();
    }

    /**
     * Create a matcher matching AST node TypeDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.TypeDeclaration}
     */
    public static Matchers.TypeDeclarationMatcher typeDeclaration() {
        return new Matchers.TypeDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node ConstructorInvocation.
     *
     * {@link org.eclipse.jdt.core.dom.ConstructorInvocation}
     */
    public static Matchers.ConstructorInvocationMatcher constructorInvocation() {
        return new Matchers.ConstructorInvocationMatcher();
    }

    /**
     * Create a matcher matching AST node QualifiedName.
     *
     * {@link org.eclipse.jdt.core.dom.QualifiedName}
     */
    public static Matchers.QualifiedNameMatcher qualifiedName() {
        return new Matchers.QualifiedNameMatcher();
    }

    /**
     * Create a matcher matching AST node UnionType.
     *
     * {@link org.eclipse.jdt.core.dom.UnionType}
     */
    public static Matchers.UnionTypeMatcher unionType() {
        return new Matchers.UnionTypeMatcher();
    }

    /**
     * Create a matcher matching AST node ThisExpression.
     *
     * {@link org.eclipse.jdt.core.dom.ThisExpression}
     */
    public static Matchers.ThisExpressionMatcher thisExpression() {
        return new Matchers.ThisExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node EnhancedForStatement.
     *
     * {@link org.eclipse.jdt.core.dom.EnhancedForStatement}
     */
    public static Matchers.EnhancedForStatementMatcher enhancedForStatement() {
        return new Matchers.EnhancedForStatementMatcher();
    }

    /**
     * Create a matcher matching AST node NormalAnnotation.
     *
     * {@link org.eclipse.jdt.core.dom.NormalAnnotation}
     */
    public static Matchers.NormalAnnotationMatcher normalAnnotation() {
        return new Matchers.NormalAnnotationMatcher();
    }

    /**
     * Create a matcher matching AST node IntersectionType.
     *
     * {@link org.eclipse.jdt.core.dom.IntersectionType}
     */
    public static Matchers.IntersectionTypeMatcher intersectionType() {
        return new Matchers.IntersectionTypeMatcher();
    }

    /**
     * Create a matcher matching AST node SimpleType.
     *
     * {@link org.eclipse.jdt.core.dom.SimpleType}
     */
    public static Matchers.SimpleTypeMatcher simpleType() {
        return new Matchers.SimpleTypeMatcher();
    }

    /**
     * Create a matcher matching AST node AssertStatement.
     *
     * {@link org.eclipse.jdt.core.dom.AssertStatement}
     */
    public static Matchers.AssertStatementMatcher assertStatement() {
        return new Matchers.AssertStatementMatcher();
    }

    /**
     * Create a matcher matching AST node SynchronizedStatement.
     *
     * {@link org.eclipse.jdt.core.dom.SynchronizedStatement}
     */
    public static Matchers.SynchronizedStatementMatcher synchronizedStatement() {
        return new Matchers.SynchronizedStatementMatcher();
    }

    /**
     * Create a matcher matching AST node DoStatement.
     *
     * {@link org.eclipse.jdt.core.dom.DoStatement}
     */
    public static Matchers.DoStatementMatcher doStatement() {
        return new Matchers.DoStatementMatcher();
    }

    /**
     * Create a matcher matching AST node NumberLiteral.
     *
     * {@link org.eclipse.jdt.core.dom.NumberLiteral}
     */
    public static Matchers.NumberLiteralMatcher numberLiteral() {
        return new Matchers.NumberLiteralMatcher();
    }

    /**
     * Create a matcher matching AST node Expression.
     *
     * {@link org.eclipse.jdt.core.dom.Expression}
     */
    public static Matchers.ExpressionMatcher expression() {
        return new Matchers.ExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node Dimension.
     *
     * {@link org.eclipse.jdt.core.dom.Dimension}
     */
    public static Matchers.DimensionMatcher dimension() {
        return new Matchers.DimensionMatcher();
    }

    /**
     * Create a matcher matching AST node NullLiteral.
     *
     * {@link org.eclipse.jdt.core.dom.NullLiteral}
     */
    public static Matchers.NullLiteralMatcher nullLiteral() {
        return new Matchers.NullLiteralMatcher();
    }

    /**
     * Create a matcher matching AST node MemberRef.
     *
     * {@link org.eclipse.jdt.core.dom.MemberRef}
     */
    public static Matchers.MemberRefMatcher memberRef() {
        return new Matchers.MemberRefMatcher();
    }

    /**
     * Create a matcher matching AST node ExpressionMethodReference.
     *
     * {@link org.eclipse.jdt.core.dom.ExpressionMethodReference}
     */
    public static Matchers.ExpressionMethodReferenceMatcher expressionMethodReference() {
        return new Matchers.ExpressionMethodReferenceMatcher();
    }

    /**
     * Create a matcher matching AST node TypeParameter.
     *
     * {@link org.eclipse.jdt.core.dom.TypeParameter}
     */
    public static Matchers.TypeParameterMatcher typeParameter() {
        return new Matchers.TypeParameterMatcher();
    }

    /**
     * Create a matcher matching AST node MethodRef.
     *
     * {@link org.eclipse.jdt.core.dom.MethodRef}
     */
    public static Matchers.MethodRefMatcher methodRef() {
        return new Matchers.MethodRefMatcher();
    }

    /**
     * Create a matcher matching AST node SuperConstructorInvocation.
     *
     * {@link org.eclipse.jdt.core.dom.SuperConstructorInvocation}
     */
    public static Matchers.SuperConstructorInvocationMatcher superConstructorInvocation() {
        return new Matchers.SuperConstructorInvocationMatcher();
    }

    /**
     * Create a matcher matching AST node WildcardType.
     *
     * {@link org.eclipse.jdt.core.dom.WildcardType}
     */
    public static Matchers.WildcardTypeMatcher wildcardType() {
        return new Matchers.WildcardTypeMatcher();
    }

    /**
     * Create a matcher matching AST node ExpressionStatement.
     *
     * {@link org.eclipse.jdt.core.dom.ExpressionStatement}
     */
    public static Matchers.ExpressionStatementMatcher expressionStatement() {
        return new Matchers.ExpressionStatementMatcher();
    }

    /**
     * Create a matcher matching AST node CharacterLiteral.
     *
     * {@link org.eclipse.jdt.core.dom.CharacterLiteral}
     */
    public static Matchers.CharacterLiteralMatcher characterLiteral() {
        return new Matchers.CharacterLiteralMatcher();
    }

    /**
     * Create a matcher matching AST node LambdaExpression.
     *
     * {@link org.eclipse.jdt.core.dom.LambdaExpression}
     */
    public static Matchers.LambdaExpressionMatcher lambdaExpression() {
        return new Matchers.LambdaExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node WhileStatement.
     *
     * {@link org.eclipse.jdt.core.dom.WhileStatement}
     */
    public static Matchers.WhileStatementMatcher whileStatement() {
        return new Matchers.WhileStatementMatcher();
    }

    /**
     * Create a matcher matching AST node SingleVariableDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.SingleVariableDeclaration}
     */
    public static Matchers.SingleVariableDeclarationMatcher singleVariableDeclaration() {
        return new Matchers.SingleVariableDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node ThrowStatement.
     *
     * {@link org.eclipse.jdt.core.dom.ThrowStatement}
     */
    public static Matchers.ThrowStatementMatcher throwStatement() {
        return new Matchers.ThrowStatementMatcher();
    }

    /**
     * Create a matcher matching AST node SuperFieldAccess.
     *
     * {@link org.eclipse.jdt.core.dom.SuperFieldAccess}
     */
    public static Matchers.SuperFieldAccessMatcher superFieldAccess() {
        return new Matchers.SuperFieldAccessMatcher();
    }

    /**
     * Create a matcher matching AST node MarkerAnnotation.
     *
     * {@link org.eclipse.jdt.core.dom.MarkerAnnotation}
     */
    public static Matchers.MarkerAnnotationMatcher markerAnnotation() {
        return new Matchers.MarkerAnnotationMatcher();
    }

    /**
     * Create a matcher matching AST node AnnotationTypeDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.AnnotationTypeDeclaration}
     */
    public static Matchers.AnnotationTypeDeclarationMatcher annotationTypeDeclaration() {
        return new Matchers.AnnotationTypeDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node Type.
     *
     * {@link org.eclipse.jdt.core.dom.Type}
     */
    public static Matchers.TypeMatcher type() {
        return new Matchers.TypeMatcher();
    }

    /**
     * Create a matcher matching AST node MethodInvocation.
     *
     * {@link org.eclipse.jdt.core.dom.MethodInvocation}
     */
    public static Matchers.MethodInvocationMatcher methodInvocation() {
        return new Matchers.MethodInvocationMatcher();
    }

    /**
     * Create a matcher matching AST node MethodDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.MethodDeclaration}
     */
    public static Matchers.MethodDeclarationMatcher methodDeclaration() {
        return new Matchers.MethodDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node AbstractTypeDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.AbstractTypeDeclaration}
     */
    public static Matchers.AbstractTypeDeclarationMatcher abstractTypeDeclaration() {
        return new Matchers.AbstractTypeDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node TextElement.
     *
     * {@link org.eclipse.jdt.core.dom.TextElement}
     */
    public static Matchers.TextElementMatcher textElement() {
        return new Matchers.TextElementMatcher();
    }

    /**
     * Create a matcher matching AST node Modifier.
     *
     * {@link org.eclipse.jdt.core.dom.Modifier}
     */
    public static Matchers.ModifierMatcher modifier() {
        return new Matchers.ModifierMatcher();
    }

    /**
     * Create a matcher matching AST node SwitchCase.
     *
     * {@link org.eclipse.jdt.core.dom.SwitchCase}
     */
    public static Matchers.SwitchCaseMatcher switchCase() {
        return new Matchers.SwitchCaseMatcher();
    }

    /**
     * Create a matcher matching AST node ArrayAccess.
     *
     * {@link org.eclipse.jdt.core.dom.ArrayAccess}
     */
    public static Matchers.ArrayAccessMatcher arrayAccess() {
        return new Matchers.ArrayAccessMatcher();
    }

    /**
     * Create a matcher matching AST node ParenthesizedExpression.
     *
     * {@link org.eclipse.jdt.core.dom.ParenthesizedExpression}
     */
    public static Matchers.ParenthesizedExpressionMatcher parenthesizedExpression() {
        return new Matchers.ParenthesizedExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node LabeledStatement.
     *
     * {@link org.eclipse.jdt.core.dom.LabeledStatement}
     */
    public static Matchers.LabeledStatementMatcher labeledStatement() {
        return new Matchers.LabeledStatementMatcher();
    }

    /**
     * Create a matcher matching AST node SwitchStatement.
     *
     * {@link org.eclipse.jdt.core.dom.SwitchStatement}
     */
    public static Matchers.SwitchStatementMatcher switchStatement() {
        return new Matchers.SwitchStatementMatcher();
    }

    /**
     * Create a matcher matching AST node MethodRefParameter.
     *
     * {@link org.eclipse.jdt.core.dom.MethodRefParameter}
     */
    public static Matchers.MethodRefParameterMatcher methodRefParameter() {
        return new Matchers.MethodRefParameterMatcher();
    }

    /**
     * Create a matcher matching AST node FieldAccess.
     *
     * {@link org.eclipse.jdt.core.dom.FieldAccess}
     */
    public static Matchers.FieldAccessMatcher fieldAccess() {
        return new Matchers.FieldAccessMatcher();
    }

    /**
     * Create a matcher matching AST node ParameterizedType.
     *
     * {@link org.eclipse.jdt.core.dom.ParameterizedType}
     */
    public static Matchers.ParameterizedTypeMatcher parameterizedType() {
        return new Matchers.ParameterizedTypeMatcher();
    }

    /**
     * Create a matcher matching AST node TypeDeclarationStatement.
     *
     * {@link org.eclipse.jdt.core.dom.TypeDeclarationStatement}
     */
    public static Matchers.TypeDeclarationStatementMatcher typeDeclarationStatement() {
        return new Matchers.TypeDeclarationStatementMatcher();
    }

    /**
     * Create a matcher matching AST node PrimitiveType.
     *
     * {@link org.eclipse.jdt.core.dom.PrimitiveType}
     */
    public static Matchers.PrimitiveTypeMatcher primitiveType() {
        return new Matchers.PrimitiveTypeMatcher();
    }

    /**
     * Create a matcher matching AST node LineComment.
     *
     * {@link org.eclipse.jdt.core.dom.LineComment}
     */
    public static Matchers.LineCommentMatcher lineComment() {
        return new Matchers.LineCommentMatcher();
    }

    /**
     * Create a matcher matching AST node CreationReference.
     *
     * {@link org.eclipse.jdt.core.dom.CreationReference}
     */
    public static Matchers.CreationReferenceMatcher creationReference() {
        return new Matchers.CreationReferenceMatcher();
    }

    /**
     * Create a matcher matching AST node ImportDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.ImportDeclaration}
     */
    public static Matchers.ImportDeclarationMatcher importDeclaration() {
        return new Matchers.ImportDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node IfStatement.
     *
     * {@link org.eclipse.jdt.core.dom.IfStatement}
     */
    public static Matchers.IfStatementMatcher ifStatement() {
        return new Matchers.IfStatementMatcher();
    }

    /**
     * Create a matcher matching AST node SimpleName.
     *
     * {@link org.eclipse.jdt.core.dom.SimpleName}
     */
    public static Matchers.SimpleNameMatcher simpleName() {
        return new Matchers.SimpleNameMatcher();
    }

    /**
     * Create a matcher matching AST node CompilationUnit.
     *
     * {@link org.eclipse.jdt.core.dom.CompilationUnit}
     */
    public static Matchers.CompilationUnitMatcher compilationUnit() {
        return new Matchers.CompilationUnitMatcher();
    }

    /**
     * Create a matcher matching AST node PostfixExpression.
     *
     * {@link org.eclipse.jdt.core.dom.PostfixExpression}
     */
    public static Matchers.PostfixExpressionMatcher postfixExpression() {
        return new Matchers.PostfixExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node TagElement.
     *
     * {@link org.eclipse.jdt.core.dom.TagElement}
     */
    public static Matchers.TagElementMatcher tagElement() {
        return new Matchers.TagElementMatcher();
    }

    /**
     * Create a matcher matching AST node Block.
     *
     * {@link org.eclipse.jdt.core.dom.Block}
     */
    public static Matchers.BlockMatcher block() {
        return new Matchers.BlockMatcher();
    }

    /**
     * Create a matcher matching AST node NameQualifiedType.
     *
     * {@link org.eclipse.jdt.core.dom.NameQualifiedType}
     */
    public static Matchers.NameQualifiedTypeMatcher nameQualifiedType() {
        return new Matchers.NameQualifiedTypeMatcher();
    }

    /**
     * Create a matcher matching AST node AnnotationTypeMemberDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration}
     */
    public static Matchers.AnnotationTypeMemberDeclarationMatcher annotationTypeMemberDeclaration() {
        return new Matchers.AnnotationTypeMemberDeclarationMatcher();
    }

    /**
     * Create a matcher matching AST node Statement.
     *
     * {@link org.eclipse.jdt.core.dom.Statement}
     */
    public static Matchers.StatementMatcher statement() {
        return new Matchers.StatementMatcher();
    }

    /**
     * Create a matcher matching AST node StringLiteral.
     *
     * {@link org.eclipse.jdt.core.dom.StringLiteral}
     */
    public static Matchers.StringLiteralMatcher stringLiteral() {
        return new Matchers.StringLiteralMatcher();
    }

    /**
     * Create a matcher matching AST node ForStatement.
     *
     * {@link org.eclipse.jdt.core.dom.ForStatement}
     */
    public static Matchers.ForStatementMatcher forStatement() {
        return new Matchers.ForStatementMatcher();
    }

    /**
     * Create a matcher matching AST node PrefixExpression.
     *
     * {@link org.eclipse.jdt.core.dom.PrefixExpression}
     */
    public static Matchers.PrefixExpressionMatcher prefixExpression() {
        return new Matchers.PrefixExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node ArrayInitializer.
     *
     * {@link org.eclipse.jdt.core.dom.ArrayInitializer}
     */
    public static Matchers.ArrayInitializerMatcher arrayInitializer() {
        return new Matchers.ArrayInitializerMatcher();
    }

    /**
     * Create a matcher matching AST node SuperMethodInvocation.
     *
     * {@link org.eclipse.jdt.core.dom.SuperMethodInvocation}
     */
    public static Matchers.SuperMethodInvocationMatcher superMethodInvocation() {
        return new Matchers.SuperMethodInvocationMatcher();
    }

    /**
     * Create a matcher matching AST node SuperMethodReference.
     *
     * {@link org.eclipse.jdt.core.dom.SuperMethodReference}
     */
    public static Matchers.SuperMethodReferenceMatcher superMethodReference() {
        return new Matchers.SuperMethodReferenceMatcher();
    }

    /**
     * Create a matcher matching AST node TypeMethodReference.
     *
     * {@link org.eclipse.jdt.core.dom.TypeMethodReference}
     */
    public static Matchers.TypeMethodReferenceMatcher typeMethodReference() {
        return new Matchers.TypeMethodReferenceMatcher();
    }

    /**
     * Create a matcher matching AST node InfixExpression.
     *
     * {@link org.eclipse.jdt.core.dom.InfixExpression}
     */
    public static Matchers.InfixExpressionMatcher infixExpression() {
        return new Matchers.InfixExpressionMatcher();
    }

    /**
     * Create a matcher matching AST node Assignment.
     *
     * {@link org.eclipse.jdt.core.dom.Assignment}
     */
    public static Matchers.AssignmentMatcher assignment() {
        return new Matchers.AssignmentMatcher();
    }

    /**
     * Create a matcher matching AST node PackageDeclaration.
     *
     * {@link org.eclipse.jdt.core.dom.PackageDeclaration}
     */
    public static Matchers.PackageDeclarationMatcher packageDeclaration() {
        return new Matchers.PackageDeclarationMatcher();
    }

    /** end of generated code */
    /**
     * Helper factory method for matching nodeClass dependent expression.
     *
     * @param name name to give matcher for printing and debugging
     */
    @SafeVarargs
    static <T extends ASTNode, E extends ASTNode> Matcher<T> internalAllOf(String name,
            Class<T> nodeClass,
            Function<T,E> valueSupplier,
            /** matchers to apply on value */
            Matcher<? extends E>... innerMatchers) {
        return new InternalMatcherUtil.ComputedExpressionMatcher<T, E>(name, nodeClass, valueSupplier, innerMatchers);
    }

    private static class PBoundedMatcherImpl extends PBoundedMatcher implements Matcher<ASTNode>{

        private String id;
        private Matcher<?>[] matchers;

        PBoundedMatcherImpl(String id, Matcher<?>[] matchers) {
            this.id = id;
            this.matchers = matchers;
        }

        public boolean match(ASTNode t, BoundNodesBuilder resultBounds) {
            Object node = resultBounds.get(id);
            return node instanceof ASTNode && InternalMatcherUtil.matchAllOf((ASTNode) node, resultBounds, matchers);
        }

    }

    @SafeVarargs
    public static <T extends ASTNode> PBoundedMatcher hasBoundPM(String id, Matcher<? extends T>... matchers) {
        return new PBoundedMatcherImpl(id, matchers);
    }

    // TODO: rethink if hasBound makes sense
    @SafeVarargs
    public static <T extends ASTNode> Matcher<T> hasBound(String id, Matcher<?>... matchers) {
        return new BoundedMatcher<T>(id, matchers);
    }

    public static <T extends ASTNode> Matcher<T> isEqualToBoundNode(String id) {
        return new Matcher<T>() {

            @Override
            public boolean match(ASTNode t, BoundNodesBuilder bounds) {
                Object other = bounds.get(id);
                boolean isEqual = t != null && other != null ? t.equals(other) : false;
                /*
                System.out.println("equalsBoundNode.match = " + isEqual
                        + "\n    node:  " + t
                        + "\n    bound: " + other);
                        */
                return isEqual;
            }
        };
    }

    public static <T extends ASTNode> Matcher<T> matchesBoundNode(String id, BiPredicate<T,ASTNode> predicate) {
        return new Matcher<T>() {

            @SuppressWarnings("unchecked")
            @Override
            public boolean match(ASTNode t, BoundNodesBuilder bounds) {
                Object other = bounds.get(id);
                // TODO: find way to protect cast to T
                return t != null && other != null ? predicate.test((T) t,  (ASTNode) other) : false;
            }
        };
    }

    public static <T extends ASTNode> Matcher<T> matchesAstOfBoundNode(String id) {
        return new Matcher<T>() {

            @Override
            public boolean match(ASTNode t, BoundNodesBuilder bounds) {
                Object other = bounds.get(id);
                return t != null && other instanceof ASTNode
                                ? ASTHelper.match(new ASTMatcher(), t, (ASTNode) other)
                                : false;
            }
        };
    }

    public static <T extends Expression> Matcher<T> isSameVariableAsBoundNode(String id) {
    	if (id == null) {
    		throw new NullPointerException();
    	}
        return new Matcher<T>() {

            @Override
            public boolean match(ASTNode t, BoundNodesBuilder bounds) {
            	Expression other = bounds.getAs(id, Expression.class);
                return t instanceof Expression && other != null
                                ? ASTHelper.isSameVariable(t, other)
                                : false;
            }
        };
    }

    // TODO: how to support same matcher for VariableDeclaration without loosing explicit typing
    public static <T extends Expression> Matcher<T> isSameLocalVariableAsBoundNode(String id) {
    	if (id == null) {
    		throw new NullPointerException();
    	}
        return new Matcher<T>() {

            @Override
            public boolean match(ASTNode t, BoundNodesBuilder bounds) {
            	Expression other = bounds.getAs(id, Expression.class);
                return t instanceof Expression && other != null
                                ? ASTHelper.isSameLocalVariable((Expression) t, other)
                                : false;
            }
        };
    }

    /**
     * Creates a matcher that accepts parameters that can be checked against node matchers.
     */
    public static ParameterizedMatcher parameterizedMatcher(List<String> parameterNames) {
        return new ParameterizedMatcher(parameterNames);
    }

    /**
     * Creates a matcher that accepts parameters that can be checked against node matchers.
     */
    public static ParameterizedMatcher parameterizedMatcher(String... parameterNames) {
        return parameterizedMatcher(Arrays.asList(parameterNames));
    }

    /**
	 * Strip blocks from statement and interpret as virtual list.
	 */
	public static StatementListMatcher asStmtList() {
	    return new StatementListMatcher();
	}

	/**
	 * Strip blocks from statement and match if result is a single statement.
	 */
	public static Matcher<? extends Statement> uniqueStatement(Matcher<? extends Statement> m) {
	    return new UniqueStatementMatcher(m);
	}

	/**
	 * Ignore parentheses on match.
	 */
	@SafeVarargs
	public static BindableMatcher<Expression, Matcher<Expression>> ignoreParentheses(Matcher<? extends Expression>... matchers) {
	    return new InternalCommonMatcher<Expression>(Expression.class, matchers) {
	
	        @Override
	        public boolean match(ASTNode t, BoundNodesBuilder bounds) {
	            return t instanceof Expression && super.match(ASTHelper.removeParentheses((Expression) t), bounds);
	        }
	    };
	}
}
