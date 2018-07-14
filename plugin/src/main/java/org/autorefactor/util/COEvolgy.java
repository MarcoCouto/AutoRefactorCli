package org.autorefactor.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.ASTHelper;
import org.autorefactor.refactoring.Refactorings;
import org.autorefactor.refactoring.rules.RefactoringContext;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

public class COEvolgy {
	
	public static final List<String> allJavaCollections = Arrays.asList(
			"LinkedHashSet", 
			"HashMap", 
			"AttributeList", 
			"IdentityHashMap", 
			"ArrayList", 
			"Attributes", 
			"TransferQueue", 
			"LinkedBlockingQueue", 
			"DelayQueue", 
			"ConcurrentHashMap.KeySetView", 
			"AbstractSequentialList", 
			"RoleUnresolvedList", 
			"LinkedHashMap", 
			"AbstractMap", 
			"ArrayBlockingQueue", 
			"TreeSet", 
			"BlockingDeque", 
			"SynchronousQueue", 
			"PrinterStateReasons", 
			"List", 
			"Vector", 
			"EnumMap", 
			"HashSet", 
			"BeanContextServices", 
			"AbstractList", 
			"BlockingQueue", 
			"UIDefaults", 
			"RoleList", 
			"ConcurrentMap", 
			"PriorityQueue", 
			"LinkedList", 
			"PriorityBlockingQueue", 
			"Stack", 
			"BeanContext", 
			"SimpleBindings", 
			"TabularDataSupport", 
			"SortedMap", 
			"ConcurrentLinkedQueue", 
			"CopyOnWriteArrayList", 
			"WeakHashMap", 
			"NavigableSet", 
			"LinkedBlockingDeque", 
			"Set", 
			"NavigableMap", 
			"AbstractCollection", 
			"CopyOnWriteArraySet", 
			"Properties", 
			"SortedSet", 
			"Bindings", 
			"LinkedTransferQueue", 
			"RenderingHints", 
			"BeanContextSupport", 
			"JobStateReasons", 
			"AbstractQueue", 
			"MessageContext", 
			"ConcurrentHashMap", 
			"Queue", 
			"ConcurrentNavigableMap", 
			"LogicalMessageContext", 
			"ConcurrentSkipListMap", 
			"ConcurrentSkipListSet", 
			"Hashtable", 
			"EnumSet", 
			"AbstractSet", 
			"AuthProvider", 
			"BeanContextServicesSupport", 
			"TreeMap", 
			"Deque", 
			"ArrayDeque", 
			"ConcurrentLinkedDeque", 
			"Provider", 
			"SOAPMessageContext");

	public static final Map<String, List<String>> androidExtendables;
	static {
		androidExtendables = new HashMap<>();
		//androidExtendables.put("android.os.Message", null);
		//androidExtendables.put("android.os.Parcel", null);
		//androidExtendables.put("android.content.ContentProviderClient", null);
		
		androidExtendables.put("android.database.sqlite.SQLiteClosable", Arrays.asList("android.database.CursorWindow", 
																					   "android.database.sqlite.SQLiteDatabase", 
																					   "android.database.sqlite.SQLiteProgram", 
																					   "android.database.sqlite.SQLiteQuery", 
																					   "android.database.sqlite.SQLiteStatement"));
		
		androidExtendables.put("android.view.InputEvent", Arrays.asList("android.view.MotionEvent", 
																		"android.view.InputEvent"));
		
		androidExtendables.put("android.content.ContentProvider", Arrays.asList("android.provider.DocumentsProvider", // FIXME: also abstracct. 
																				"android.test.mock.MockContentProvider",
																				"android.content.SearchRecentSuggestionsProvider",
																				"android.app.slice.SliceProvider")); // FIXME: also abstracct.
		
		androidExtendables.put("android.content.ContentResolver", Arrays.asList("android.test.mock.MockContentResolver"));
		
		androidExtendables.put("android.content.res.Reources", Arrays.asList("android.test.mock.MockResources"));
		
		androidExtendables.put("android.os.Handler", Arrays.asList("android.content.AsyncQueryHandler",
																   "android.content.AsyncQueryHandler.WorkerHandler",
																   "android.webkit.HttpAuthHandler",
																   "android.webkit.SslErrorHandler"));
		
		androidExtendables.put("android.content.Context", Arrays.asList("android.content.ContextWrapper",
																		"android.test.mock.MockContext",
																		"android.app.Application",
																		"android.app.backup.BackupAgent", 
																		"android.app.backup.BackupAgentHelper", 
																		"android.view.ContextThemeWrapper", 
																		"android.app.Activity", 
																		"android.accounts.AccountAuthenticatorActivity", 
																		"android.app.ActivityGroup", 
																		"android.app.AliasActivity", 
																		"android.app.ExpandableListActivity", 
																		"android.app.ListActivity", 
																		"android.app.LauncherActivity", 
																		"android.preference.PreferenceActivity", 
																		"android.app.NativeActivity", 
																		"android.app|android.support.v4.app.FragmentActivity", 
																		"android.app.TabActivity", 
																		"android.app.Fragment", 
																		"android.test.IsolatedContext", 
																		"android.content.MutableContextWrapper", 
																		"android.test.RenamingDelegatingContext", 
																		"android.app.Service", 
																		"android.inputmethodservice.AbstractInputMethodService", 
																		"android.accessibilityservice.AccessibilityService", 
																		"android.service.autofill.AutofillService", 
																		"android.telecom.CallScreeningService", 
																		"android.service.media.CameraPrewarmService", 
																		"android.service.carrier.CarrierMessagingService", 
																		"android.service.carrier.CarrierService", 
																		"android.service.chooser.ChooserTargetService", 
																		"android.service.notification.ConditionProviderService", 
																		"android.telecom.ConnectionService", 
																		"android.app.admin.DeviceAdminService", 
																		"android.service.dreams.DreamService", 
																		"android.nfc.cardemulation.HostApduService", 
																		"android.nfc.cardemulation.HostNfcFService", 
																		"android.telecom.InCallService", 
																		"android.app.IntentService", 
																		"android.app.job.JobService", 
																		"android.service.media.MediaBrowserService", 
																		"android.media.midi.MidiDeviceService", 
																		"android.service.notification.NotificationListenerService", 
																		"android.nfc.cardemulation.OffHostApduService", 
																		"android.printservice.PrintService", 
																		"android.speech.RecognitionService", 
																		"android.widget.RemoteViewsService", 
																		"android.location.SettingInjectorService", 
																		"android.service.textservice.SpellCheckerService", 
																		"android.speech.tts.TextToSpeechService", 
																		"android.service.quicksettings.TileService", 
																		"android.media.tv.TvInputService", 
																		"android.telephony.VisualVoicemailService", 
																		"android.service.voice.VoiceInteractionService", 
																		"android.service.voice.VoiceInteractionSessionService", 
																		"android.net.VpnService", 
																		"android.service.vr.VrListenerService", 
																		"android.service.wallpaper.WallpaperService", 
																		"android.inputmethodservice.InputMethodService"));
		
		androidExtendables.put("android.widget.Adapter", Arrays.asList("android.widget.ArrayAdapter",
																	   "android.widget.BaseAdapter",
																	   "android.widget.CursorAdapter",
																	   "android.widget.HeaderViewListAdapter",
																	   "android.widget.ListAdapter",
																	   "android.widget.ResourceCursorAdapter",
																	   "android.widget.SimpleAdapter",
																	   "android.widget.SimpleCursorAdapter",
																	   "android.widget.SpinnerAdapter",
																	   "android.widget.ThemedSpinnerAdapter",
																	   "android.widget.WrapperListAdapter"));
		
		androidExtendables.put("android.app.Activity", Arrays.asList("android.accounts.AccountAuthenticatorActivity", 
																	 "android.app.ActivityGroup", 
																	 "android.app.AliasActivity", 
																	 "android.app.ExpandableListActivity", 
																	 "android.app.ListActivity", 
																	 "android.app.LauncherActivity", 
																	 "android.preference.PreferenceActivity", 
																	 "android.app.NativeActivity", 
																	 "android.app|android.support.v4.app.FragmentActivity", 
																	 "android.app.TabActivity", 
																	 "android.app.Fragment"));
		
		androidExtendables.put("android.view.View", Arrays.asList("android.widget.AnalogClock", 
																"android.widget.ImageView", 
																"android.inputmethodservice.KeyboardView", 
																"android.app.MediaRouteButton", 
																"android.widget.ProgressBar", 
																"android.widget.Space", 
																"android.view.SurfaceView", 
																"android.widget.TextView", 
																"android.view.TextureView", 
																"android.view.ViewGroup", 
																"android.view.ViewStub", 
																"android.widget.AbsListView", 
																"android.widget.AbsSeekBar", 
																"android.widget.AbsSpinner", 
																"android.widget.AbsoluteLayout", 
																"android.widget.ActionMenuView", 
																"android.widget.AdapterView.html", 
																"android.widget.AdapterViewAnimator", 
																"android.widget.AdapterViewFlipper", 
																"android.appwidget.AppWidgetHostView", 
																"android.widget.AutoCompleteTextView", 
																"android.widget.Button", 
																"android.widget.CalendarView", 
																"android.widget.CheckBox", 
																"android.widget.CheckedTextView", 
																"android.widget.Chronometer", 
																"android.widget.CompoundButton", 
																"android.widget.DatePicker", 
																"android.widget.DialerFilter", 
																"android.widget.DigitalClock", 
																"android.widget.EditText", 
																"android.widget.ExpandableListView", 
																"android.inputmethodservice.ExtractEditText", 
																"android.app.FragmentBreadCrumbs", 
																"android.widget.FrameLayout", 
																"android.opengl.GLSurfaceView", 
																"android.widget.Gallery", 
																"android.gesture.GestureOverlayView", 
																"android.widget.GridLayout", 
																"android.widget.GridView", 
																"android.widget.HorizontalScrollView", 
																"android.widget.ImageButton", 
																"android.widget.ImageSwitcher", 
																"android.widget.LinearLayout", 
																"android.widget.ListView", 
																"android.widget.MediaController", 
																"android.widget.MultiAutoCompleteTextView", 
																"android.widget.NumberPicker", 
																"android.widget.QuickContactBadge", 
																"android.widget.RadioButton", 
																"android.widget.RadioGroup", 
																"android.widget.RatingBar", 
																"android.widget.RelativeLayout", 
																"android.widget.ScrollView", 
																"android.widget.SearchView", 
																"android.widget.SeekBar", 
																"android.widget.SlidingDrawer", 
																"android.widget.Spinner", 
																"android.widget.StackView", 
																"android.widget.Switch", 
																"android.widget.TabHost", 
																"android.widget.TabWidget", 
																"android.widget.TableLayout", 
																"android.widget.TableRow", 
																"android.widget.TextClock", 
																"android.widget.TextSwitcher", 
																"android.widget.TimePicker", 
																"android.widget.ToggleButton", 
																"android.widget.Toolbar", 
																"android.media.tv.TvView", 
																"android.widget.TwoLineListItem", 
																"android.widget.VideoView", 
																"android.widget.ViewAnimator", 
																"android.widget.ViewFlipper", 
																"android.widget.ViewSwitcher", 
																"android.webkit.WebView", 
																"android.widget.ZoomButton", 
																"android.widget.ZoomControls"));
	}
	
	public static final int MEASURE = 1000;
	public static final int TRACE = 2000;
	private static final String traceRefactoringFile = "refactorings.trace"; // "refactorings.cev"
	private static final boolean useBindings = false;
	
	private RefactoringContext ctx;
	private boolean insideStaticBlock;
	
	public COEvolgy(RefactoringContext ctx, boolean flag) {
		this.ctx = ctx;
		this.insideStaticBlock = flag;
	}
	
	
	public ASTNode buildTraceNode(String name) {
		final ASTBuilder b = this.ctx.getASTBuilder();
		final Refactorings r = this.ctx.getRefactorings();
		
		Expression exp = b.simpleName("Tracer");
		String methodName = "trace";
		MethodInvocation traceCall = b.invoke(exp, methodName, b.string(name));
		ExpressionStatement traceStmt = b.getAST().newExpressionStatement(traceCall);
		
		if (this.insideStaticBlock) {
			Initializer init = r.getAST().newInitializer();
			Block body = b.block(r.getAST().newExpressionStatement(traceCall));
			init.setBody(body);
			init.modifiers().add(b.static0());
			return init;
			
		} else {
			return traceStmt;
		}
		
	}
	
	public ASTNode buildTraceMethodNode(String qualifiedName) {
		final ASTBuilder b = this.ctx.getASTBuilder();
		final Refactorings r = this.ctx.getRefactorings();
		
		Expression exp = b.simpleName("Tracer");
		String methodName = "traceMethod";
		MethodInvocation traceCall = b.invoke(exp, methodName, b.string(qualifiedName));
		ExpressionStatement traceStmt = b.getAST().newExpressionStatement(traceCall);
		
		if (this.insideStaticBlock) {
			Initializer init = r.getAST().newInitializer();
			Block body = b.block(r.getAST().newExpressionStatement(traceCall));
			init.setBody(body);
			init.modifiers().add(b.static0());
			return init;
			
		} else {
			return traceStmt;
		}
		
	}
	
	
	public static ASTNode getParentStatement(ASTNode node) {
		
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
	
	
	public static Type copyType(Type type, ASTBuilder b) {
		if (type.isParameterizedType()) {
			String typeName = type.toString()
								  .split("<")[0];
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
		} else {
			return b.copy(type);
		}
	}
	
	
	public static String typeOf(String varName, List<VariableDeclaration> allVars) {
		
		String varType = "";
		for (VariableDeclaration decl : allVars) {
			String name = decl.getName().getIdentifier();
			if (name.equals(varName)) {
				if (decl.getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT || decl.getNodeType() == ASTNode.FIELD_DECLARATION) {
					ASTNode parent = getParentStatement(decl);
					if (parent.getNodeType() == ASTNode.FIELD_DECLARATION) {
						varType = ((FieldDeclaration) parent).getType().toString();
					} else if (parent.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
						varType = ((VariableDeclarationStatement) parent).getType().toString();
					}
				} else if (decl.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION) {
					varType = ((SingleVariableDeclaration) decl).getType().toString();
				}
				return varType;
			}
		}
		return "";
	}
	
	
	public static boolean isImportIncluded(List<ImportDeclaration> imports, String importName) {
		for (ImportDeclaration importDecl : imports) {
			if (importDecl.getName().toString().equals(importName)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isImportIncluded(List<ImportDeclaration> imports, List<String> importNames) {
		for (String pack : importNames) {
			if (isImportIncluded(imports, pack)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isClassExtendedBy(ASTNode typeDeclaration, String extendedClass, List<ImportDeclaration> imports) {
		// `extendedClass` must be a qualified name.
		// Example: org.greenlab.com.Class
		
		String superClass = "";
		int lastIndex = extendedClass.lastIndexOf(".") + 1;
		String extendedPackage = extendedClass.substring(0, lastIndex);
		String extendedClassName = extendedClass.substring(lastIndex);
		if (typeDeclaration instanceof TypeDeclaration) {
			Type superClassType = ((TypeDeclaration) typeDeclaration).getSuperclassType();
			
			if (superClassType != null) {
				superClass = superClassType.toString().split("<")[0];
				if (superClass.equals(extendedClassName) && isImportIncluded(imports, Arrays.asList(extendedClass, extendedPackage+"*"))) {
					return true;
				}
			}
			
		}
		return false;
	}
	
	public static boolean isClassExtendedBy(ASTNode typeDeclaration, Collection<String> extendedClasses, List<ImportDeclaration> imports) {
		for (String s : extendedClasses) {
			if (isClassExtendedBy(typeDeclaration, s, imports)) return true;
		}
		
		return false;
	}
	
	public static boolean isCollection(VariableDeclarationStatement node) {
		Type type = node.getType();
		
		if (type.isParameterizedType()) {
			String typeName = type.toString().split("<")[0];
			if (allJavaCollections.contains(typeName)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isCollection(VariableDeclarationFragment node) {
		VariableDeclarationStatement parent = (VariableDeclarationStatement) ASTNodes.getParent(node, ASTNode.VARIABLE_DECLARATION_STATEMENT);
		return isCollection(parent);
	}
	
	
	private static boolean isSameType(String typeName, String qualifiedTypeName, List<ImportDeclaration> imports) {
		for (ImportDeclaration i : imports) {
			String mainVarType = typeName.split("\\.")[0];
			String importName = i.getName().getFullyQualifiedName().replaceAll("\\.(\\*|"+mainVarType+")", ".");
			
			List<String> possibleTypes = new ArrayList<>();
			if (androidExtendables.containsKey(qualifiedTypeName)) {
				possibleTypes = new ArrayList<>(androidExtendables.get(qualifiedTypeName));
				possibleTypes.add(qualifiedTypeName);
			} else {
				possibleTypes.add(importName + typeName);
			}
			if (possibleTypes.contains(qualifiedTypeName)) {
				return true;
			}
		}
		return false;
	}
	
	
	/**
	 * Checks whether the provided variable has the same type as the provided qualified type name.
	 * Using the variable name, this method first get the variable's type by searching for it in 
	 * the list of declared variables. 
	 * Then, it concats each import qualified name with the variable type name.
	 * If the concatenation matches the qualifiedTypeName, it returns true.
	 * 
	 * @param varName The name of the variable under test.
	 * @param qualifiedTypeName The qualified type name.
	 * @param variables The list of variables declared so far.
	 * @param mainNode The CompilationUnit main node, from which to retrieve information about the imports.
	 * @return true if a match is found, false otherwise.
	 */
	private static boolean variableTypeMatches(String varName, String qualifiedTypeName, List<VariableDeclaration> variables, CompilationUnit mainNode) {
		if (varName.equals("")) return false;
		
		VariableDeclaration variable = null;
		for (VariableDeclaration var : variables) {
			if (var.getName().getIdentifier().equals(varName)) {
				variable = var;
				break;
			}
		}
		
		if (variable == null) {
			return false;
		} else {
			String varType = "";
			if (variable.getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT 
					|| variable.getNodeType() == ASTNode.FIELD_DECLARATION) {
				ASTNode parent = getParentStatement(variable);
				if (parent.getNodeType() == ASTNode.FIELD_DECLARATION) {
					varType = ((FieldDeclaration) parent).getType().toString();
				} else if (parent.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
					varType = ((VariableDeclarationStatement) parent).getType().toString();
				}
			} else if (variable.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION) {
				varType = ((SingleVariableDeclaration) variable).getType().toString();
			}
		
			if (varType.equals("")) {
				return false;
			}
			
			List<ImportDeclaration> imports = mainNode.imports();
			return isSameType(varType, qualifiedTypeName, imports);
		}
		
	}
	
	public static boolean isMethod(MethodInvocation node, CompilationUnit mainNode, List<VariableDeclaration> variables, String typeQualifiedName, String methodName, String ... args) {
		List<ImportDeclaration> imports = mainNode.imports();
		if (node.resolveMethodBinding() == null) {
			// Entering here means we can't rely on resolveMethodBinding.
			// That means we need to analyze the method by brute-force approach.
			String varName = node.getExpression() != null ? node.getExpression().toString() : "";
			if (!varName.equals("") 
					&& variableTypeMatches(varName, typeQualifiedName, variables, mainNode)
					&& node.getName().getIdentifier().equals(methodName)) 
			{
				// FIXME: For now, we will ignore the check on the method arguments.
				// We'll assume that if the name of the method and the type of the 
				// calling variable match, we're looking at the same method. 
				// It works for eBugs checks introduced in 2018.
				
				/*
				for (Object o : node.arguments()) {
					String name = getVarFromExpression((Expression) o);
					. . .
				}*/
				
				return true;
			}
			return false;
		} else {
			return ASTHelper.isMethod(node, typeQualifiedName, methodName, args);
		}
	}
	
	
	public static boolean isMethod(MethodDeclaration node, CompilationUnit mainNode, List<VariableDeclaration> variables, String typeQualifiedName, String methodName, String ... args) {
		ASTNode typeDecl = ASTNodes.getParent(node, ASTNode.TYPE_DECLARATION);
		String extendedClass = typeQualifiedName.substring(typeQualifiedName.lastIndexOf(".") + 1, 
														   typeQualifiedName.length());
		
		if (node.resolveBinding() == null || !useBindings) {
			// Entering here means we can't rely on resolveMethodBinding.
			// That means we need to analyze the method by brute-force approach.
			if (androidExtendables.containsKey(typeQualifiedName)) {
				List<String> classesToCheck = new ArrayList<>(androidExtendables.get(typeQualifiedName));
				classesToCheck.add(typeQualifiedName);
				return node.getName().getIdentifier().equals(methodName)
						&& isClassExtendedBy(typeDecl, classesToCheck, mainNode.imports());
				
			} else {
				return node.getName().getIdentifier().equals(methodName)
						&& isClassExtendedBy(typeDecl, typeQualifiedName, mainNode.imports());
			}
			
		} else {
			return ASTHelper.isMethod(node.resolveBinding(), typeQualifiedName, methodName, args);
		}
	}
	
	public static boolean instanceOf(MethodInvocation node, String typeQualifiedName, CompilationUnit mainNode, List<VariableDeclaration> variables) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		if (methodBinding == null || methodBinding.getDeclaringClass() == null) {
			// Bindings are down...
			String varName = node.getExpression() != null ? node.getExpression().toString() : "";
			return variableTypeMatches(varName, typeQualifiedName, variables, mainNode);
		} else {
			ITypeBinding declaringClazz = methodBinding.getDeclaringClass();
			return ASTHelper.instanceOf(declaringClazz, typeQualifiedName);
		}
		
	}
	
	
	public static boolean isSameLocalVariable(Expression exp1, Expression exp2) {
		if (exp1 == null || exp2 == null) return false;
		
		if (exp1.getNodeType() == ASTNode.SIMPLE_NAME && exp2.getNodeType() == ASTNode.SIMPLE_NAME) {
			
			SimpleName exp1Name = (SimpleName) exp1;
			SimpleName exp2Name = (SimpleName) exp2;
			if (exp1Name.resolveBinding() == null || exp2Name.resolveBinding() == null) {
				MethodDeclaration methodExp1 = (MethodDeclaration) ASTNodes.getParent(exp1, ASTNode.METHOD_DECLARATION);
				MethodDeclaration methodExp2 = (MethodDeclaration) ASTNodes.getParent(exp2, ASTNode.METHOD_DECLARATION);
				
				TypeDeclaration typeDeclExpr1 = (TypeDeclaration) ASTNodes.getParent(exp1, ASTNode.TYPE_DECLARATION);
				TypeDeclaration typeDeclExpr2 = (TypeDeclaration) ASTNodes.getParent(exp2, ASTNode.TYPE_DECLARATION);
				
				return (exp1Name.getIdentifier().equals(exp2Name.getIdentifier()) 
						&& (methodExp1.getName().equals(methodExp2.getName()))
						&& (typeDeclExpr1.getName().equals(typeDeclExpr2.getName()))
				);
			} else {
				return ASTHelper.isSameLocalVariable(exp1, exp2);
			}
		}
		
		return false;
	}
	
	
	public static boolean allConstants(List<ASTNode> nodes) {
		boolean res = false;
		for (ASTNode n : nodes) {
			switch (n.getNodeType()) {
				case ASTNode.CHARACTER_LITERAL:
					res = true;
					break;
				case ASTNode.BOOLEAN_LITERAL:
					res = true;
					break;
				case ASTNode.NULL_LITERAL:
					res = true;
					break;
				case ASTNode.NUMBER_LITERAL:
					res = true;
					break;
				case ASTNode.STRING_LITERAL:
					res = true;
					break;
				default:
					return false;
			}
		}
		return res;
	}
	
	public static String getVarFromExpression(MethodInvocation expression, Set<String> classVars) {
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

    public static String getVarFromExpression(SimpleName expression, Set<String> classVars) {
        String qualifiedName = expression.getIdentifier();

        if (qualifiedName.startsWith("this.") || classVars.contains(qualifiedName)) return "this";
        if (qualifiedName.contains(".")) {
            return qualifiedName.split("\\.")[0];
        }else{
            return qualifiedName;
        }
    }
    
    public static String getVarFromExpression(QualifiedName expression, Set<String> classVars) {
        String qualifiedName = expression.getFullyQualifiedName();

        if (qualifiedName.startsWith("this.") || classVars.contains(qualifiedName)) return "this";
        if (qualifiedName.contains(".")) {
            return qualifiedName.split("\\.")[0];
        }else{
            return qualifiedName;
        }
    }
    
    public static String getVarFromExpression(Expression expression, Set<String> classVars) {
    	String qualifiedName = "";
    	
    	if (expression instanceof SimpleName) {
    		qualifiedName = getVarFromExpression((SimpleName) expression, classVars);
    	} else if (expression instanceof QualifiedName) {
    		qualifiedName = getVarFromExpression((QualifiedName) expression, classVars);
    	} else if (expression instanceof MethodInvocation) {
    		qualifiedName = getVarFromExpression((MethodInvocation) expression, classVars);
    	} else if (expression instanceof ArrayAccess) {
    		ArrayAccess a = (ArrayAccess) expression;
    		Expression exp = a.getArray();
    		qualifiedName = getVarFromExpression(exp, classVars);
    	} else if (expression instanceof ParenthesizedExpression) {
    		ParenthesizedExpression exp = (ParenthesizedExpression) expression;
    		qualifiedName = getVarFromExpression(exp.getExpression(), classVars);
    	} else if (expression instanceof InfixExpression) {
    		InfixExpression exp = (InfixExpression) expression;
    		String nameLeft = getVarFromExpression(exp.getLeftOperand(), classVars);
    		String nameRight = getVarFromExpression(exp.getRightOperand(), classVars);
    		qualifiedName = nameLeft + ";" + nameRight;
    	} else {
    		qualifiedName = "";
    	}
    	
    	return qualifiedName;
    }
	
    public static String getMethodName(MethodInvocation node) {
    	String methodName = node.getName()
    							.getFullyQualifiedName()
    							.replace("this","");
    	return methodName;
	}
    
	public static int loadOperationFlag() {
		try {
			String operationStr = System.getenv("COEVOLOGY_OP");
			if (!operationStr.equals("")) {
				try {
					int op = Integer.parseInt(operationStr);
					return op;
				} catch (NumberFormatException e) {
					return MEASURE;
				}
			}
		} catch (NullPointerException e) {
			System.out.println("[W] No operation flag provided. Assuming: MEASURE (1000)");
		}
		
		return MEASURE;
	}
	
	public static void traceRefactoring(String tag) {
		FileWriter fw = null;
		BufferedWriter bw = null;
		
		try {
			FileWriter writer = new FileWriter(traceRefactoringFile, true);
	
			File file = new File(traceRefactoringFile);
	
			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
	
			// true = append file
			fw = new FileWriter(file.getAbsoluteFile(), true);
			bw = new BufferedWriter(fw);
	
			bw.write(tag+"\n");
			
		} catch (IOException e) {
			e.printStackTrace();
			
		} finally {
			try {
				if (bw != null)
					bw.close();
				if (fw != null)
					fw.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			
		}
	}



}
