package analyzer.level1;

import analyzer.level2.HandleStmt;
import de.unifreiburg.cs.proglang.jgs.constraints.TypeViews;
import de.unifreiburg.cs.proglang.jgs.instrumentation.Casts;
import de.unifreiburg.cs.proglang.jgs.instrumentation.MethodTypings;
import de.unifreiburg.cs.proglang.jgs.signatures.SignatureTable;
import soot.*;
import soot.jimple.Constant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.JAssignStmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import util.dominator.DominatorFinder;
import util.visitor.AnnotationStmtSwitch;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This Analyzer extends the Body Transformer of the Soot Framework,
 * so the {@link BodyAnalyzer#internalTransform(Body, String, Map)} is called
 * from Soot for every Methods Body of the analyzed Code.
 *
 * The BodyAnalyzer decides for every body what shall be inserted into
 * the Body.
 * E.g for each constructor, an new Object and Field Map is inserted into the ObjectMap.
 * For each method, a HandleStmt object is inserted (which contains a local Map
 * for the Locals and the localPC).
 * Then every Local is inserted into this map.
 * At least it iterates over all Units and calls the appropriate operation
 * At the end (but before the return statement) it calls HandleStmt.close()
 * @author koenigr, Karsten Fix (2017)
 *
 */
public class BodyAnalyzer<Level> extends BodyTransformer {

	private MethodTypings<Level> methodTypings;
	private Casts<Level> casts;
	private SignatureTable<Level> signatureTable;

	private List<Unit> successorStmt = new ArrayList<Unit>();
	private List<Unit> nextSuccessorStmt = new ArrayList<Unit>();
	private List<String> unitRhsList = new ArrayList<String>();
	private List<String> unitStmtsListString = new ArrayList<String>();
	private UnitGraph g;

	private Logger logger = Logger.getLogger(this.getClass().getName());

	private static boolean containsFieldsVarsFlag = false;
    public static HashMap<String, Boolean> fieldVarMaps = new HashMap<String, Boolean>();
    public static Set<String> methodNames = new HashSet<String>();
	public static List<String> methodNamesList = new ArrayList<>();
	public static HashMap<String, List<HashMap<Integer, Boolean>>> argumentMap = new HashMap<String, List<HashMap<Integer, Boolean>>>();
	public static List<Unit> methodCalls = new ArrayList<Unit>();
	public static HashMap<Unit,String> methodCallsInsideMethods = new HashMap<Unit,String>();
	public static String callingMethod = "main";
	public static List<String> independentVars = new ArrayList<String>();
	private static String prevClassName = "";

	/**
	 * Constructs an new BodyAnalyzer with the given
	 * @param m
	 * @param c
	 */
	public BodyAnalyzer(MethodTypings<Level> m, Casts<Level> c, SignatureTable<Level> signatureTable) {
		methodTypings = m;
		casts = c;
		this.signatureTable = signatureTable;
	}


	/**
	 * This method collects all the user defined method names.
	 * @param sootMethods
	 * @return
	 */
	private static Set<String> getMethodNames(List<SootMethod> sootMethods){
		Set<String> methodNames = new HashSet<String>();
		for (SootMethod m : Scene.v().getMainClass().getMethods()) {
			if(!m.isMain() && !m.getName().equals("<init>") && !m.getName().equals("<clinit>")) {
				methodNames.add(m.getName());
			}
		}
		return methodNames;
	}

	/**
	 * This Method is called from the Soot Framework. In this Specific Implementation
	 * it inserts some invokes of the {@link analyzer.level2.HandleStmt}.
	 *
	 * @param body The Body of the Method, that is analyzed and may be instrumented.
	 * @param s1 The Phase Name of this Soot Phase, in this case it should be "jtp.analyzer"
	 * @param map A Map of Options, that could be defined and passed to this Method. These
	 *            Options can be passed as command line arguments. As defined by Soot.
	 */
	@Override
	protected void internalTransform(Body body, String s1, Map<String, String> map) {
		logger.info(" Analyze of :" + body.getMethod().getName() + " started.");

		List<SootMethod> allMethods = Scene.v().getMainClass().getMethods();

		methodNames = getMethodNames(allMethods);
		methodNamesList = new ArrayList<>(methodNames);
		HashMap<String, HashMap<Integer, Boolean>> mp = new HashMap<String, HashMap<Integer, Boolean>>();

		String className = body.getMethod().getDeclaringClass().getName();

		if(!prevClassName.isEmpty() && !prevClassName.equals(className)){
			methodCalls = new ArrayList<Unit>();
			methodCallsInsideMethods = new HashMap<Unit,String>();
			argumentMap = new HashMap<String, List<HashMap<Integer, Boolean>>>();
			JimpleInjector.prevMethodName = "";
			JimpleInjector.signatureList = new ArrayList<String>();
			JimpleInjector.noTrackSignatureList = new ArrayList<String>();
			fieldVarMaps = new HashMap<String, Boolean>();
			DefinedByValueOfCall.independentVarsMap = new HashMap<String, List<String>>();
			DefinedByValueOfCall.identityTargetsMap = new HashMap<String, List<HashMap<Integer, Value>>>();
			DefinedByValueOfCall.identityTargets = new ArrayList<Value>();
		}

		if (body.getMethod().getName().equals("<init>")){
			for (SootMethod sootMethod : allMethods) {
				if (!sootMethod.getName().equals("<init>") && !sootMethod.getName().equals("<clinit>")) {
					Chain<Unit> units = sootMethod.getActiveBody().getUnits();
					ArrayList<Unit> unmodifiedStmts = new ArrayList<>(units);
					for (Unit unit : unmodifiedStmts) {
						for (String methodName : methodNames) {
							if (unit.toString().contains(methodName)) {
								methodCalls.add(unit);
								if(!sootMethod.isMain())
									methodCallsInsideMethods.put(unit, sootMethod.getName()); // Find calls to methods no in the main method
							}
						}
					}
				}
			}

			for (SootMethod sootMethod : allMethods) {
				if (!sootMethod.getName().equals("<init>") && !sootMethod.getName().equals("<clinit>")) {
					UnitGraph unitGraph = new BriefUnitGraph(sootMethod.getActiveBody());

					DefinedByValueOfCall.isFirstUnit = true;
					callingMethod = sootMethod.getName();
					// to find dependent variable chains and independent variables
					DefinedByValueOfCall.getCasts(casts);
					DefinedByValueOfCall defininedByValueOfCall = new DefinedByValueOfCall(unitGraph);
				}
			}


			HashMap<String, List<String>> independentVarsMap = DefinedByValueOfCall.independentVarsMap;

			// for calls to methods not in main method, check if parent is dynamically tracked or not
            for(Map.Entry<Unit, String> e: methodCallsInsideMethods.entrySet()){
                String methodName1 = e.getValue();
                Unit unit = e.getKey();
                for (int i = 0; i < unit.getUseBoxes().size(); i++) {
                    int idx = 0;
                    Value value = unit.getUseBoxes().get(i).getValue();
                    String methodName2 = BodyAnalyzer.methodNames.stream().findAny().get();
                    if (!BodyAnalyzer.methodNames.stream().anyMatch(value.toString()::contains)) {
                        if(DefinedByValueOfCall.identityTargets.contains(value)){
                            InvokeExpr invokeExpr;
                            for(Unit s : methodCalls){
                                if(s instanceof JAssignStmt) {
                                    JAssignStmt assignStmt = (JAssignStmt) s;
                                    Value source = assignStmt.getRightOp();
                                    invokeExpr = (InvokeExpr) source;
                                }
                                else {
                                    InvokeStmt st = (InvokeStmt) s;
                                    invokeExpr = st.getInvokeExpr();
                                }

                                if(methodName1.equals(invokeExpr.getMethod().getName())){
                                    independentVars = independentVarsMap.get(methodName1);
                                    List tempList = new ArrayList();
                                    for (int j = 0; j < s.getUseBoxes().size(); j++) {
                                        Value val = s.getUseBoxes().get(j).getValue();
                                        if (!BodyAnalyzer.methodNames.stream().anyMatch(val.toString()::contains)) {
                                            if(null != independentVars) {
                                                if (val instanceof Constant || independentVars.contains(val.toString())) {
                                                    List<HashMap<Integer, Value>> tempList2 = DefinedByValueOfCall.identityTargetsMap.get(methodName1);
                                                    HashMap<Integer, Value> tempMap = tempList2.get(idx);
                                                    tempList.add(tempMap.get(idx).toString());
                                                }
                                            }
                                            idx += 1;
                                        }
                                    }
                                    if(independentVarsMap.containsKey(methodName2)){
                                        List finalIndVarList = independentVarsMap.get(methodName2);
                                        finalIndVarList.addAll(tempList);
                                        independentVarsMap.put(methodName2, finalIndVarList);
                                    }
                                }
                            }
                        }
                    }
                }

            }

            // Create "argumentMap" for each argument of each method - 0 -> don't track; 1 -> track
            if(argumentMap.isEmpty()) {
                String methodName = "";
                for (Unit unit: methodCalls) {
                    int argPosition = 0;
                    List argumentsList = new ArrayList();
                    InvokeExpr invokeExpr;
                    if (unit instanceof JAssignStmt) {
                        Value source = ((JAssignStmt) unit).getRightOp();
                        if (source instanceof InvokeExpr) {
                            invokeExpr = (InvokeExpr) source;
                            methodName = invokeExpr.getMethod().getName();
                        }
                    }
                    else if(unit instanceof InvokeStmt){
                        methodName = ((InvokeStmt) unit).getInvokeExpr().getMethod().getName();
                    }

                    if (!methodName.isEmpty()) {
                        for (int i = 0; i < unit.getUseBoxes().size(); i++) {
                            Value value = unit.getUseBoxes().get(i).getValue();
                            if (!methodNames.stream().anyMatch(value.toString()::contains)) {
                                independentVars = independentVarsMap.get(methodName);
                                // argument is not a constant - eg: add(7,7) or int x = 7 and add(x, x)
                                if (!(value instanceof Constant) && !independentVars.contains(value.toString())) {
                                    HashMap<Integer, Boolean> tempMap = new HashMap<Integer, Boolean>();
                                    tempMap.put(argPosition, true);
                                    argumentsList.add(tempMap);
                                    argPosition += 1;
                                } else {
                                    if (null == argumentMap.get(methodName)) {
                                        HashMap<Integer, Boolean> tempMap = new HashMap<Integer, Boolean>();
                                        tempMap.put(argPosition, false);
                                        argumentsList.add(tempMap);
                                        argPosition += 1;
                                    }
                                    //if the method is called multiple times, update the argument map for that method i.e if for eg: add(5, x) and (x, 5) argument map for add will have true for argPositions 0 and 1
                                    else if (null != argumentMap.get(methodName)) {
                                        List<HashMap<Integer, Boolean>> argList = argumentMap.get(methodName);
                                        if(!argList.isEmpty()) {
                                            HashMap<Integer, Boolean> argMap = argList.get(argPosition);
                                            if (null != argMap.get(argPosition) && argMap.get(argPosition)) {
                                                HashMap<Integer, Boolean> tempMap = new HashMap<Integer, Boolean>();
                                                tempMap.put(argPosition, true);
                                                argumentsList.add(tempMap);
                                                argPosition += 1;
                                            }
                                            else {
                                                HashMap<Integer, Boolean> tempMap = new HashMap<Integer, Boolean>();
                                                tempMap.put(argPosition, false);
                                                argumentsList.add(tempMap);
                                                argPosition += 1;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    argumentMap.put(methodName, argumentsList);
                }
            }

            // Print argumentMap - debugging
            /*for(Map.Entry<String, List<HashMap<Integer, Boolean>>> e: argumentMap.entrySet()){
                System.out.print(e.getKey());
                List<HashMap<Integer, Boolean>> l = e.getValue();
                for(HashMap<Integer, Boolean> m : l){
                    for(Map.Entry e1 :m.entrySet()){
                        System.out.print(e1.getKey() + ": " + e1.getValue());
                    }
                }
                System.out.println();
            }*/

		}

		SootMethod sootMethod = body.getMethod();
		Chain<Unit> units  = body.getUnits();

		AnnotationStmtSwitch stmtSwitch = new AnnotationStmtSwitch(body, casts);
		Chain<SootField> fields = sootMethod.getDeclaringClass().getFields();

		// Using a copy, such that JimpleInjector could inject directly.
		ArrayList<Unit> unmodifiedStmts = new ArrayList<>(units);

		DominatorFinder.init(body);

		// The JimpleInjector actually inserts the invokes, that we decide to insert.
		// In order, that the righteous body got the inserts, we have to set up the
		// Body for the Injections.
		JimpleInjector.setBody(body);

		UnitGraph unitGraph = new BriefUnitGraph(body);

		// hand over exactly those Maps that contain Instantiation, Statement and Locals for the currently analyzed method
		JimpleInjector.setStaticAnalaysisResults(methodTypings.getVarTyping(sootMethod),
				methodTypings.getCxTyping(sootMethod),
				// We set the default type to dyn; our RT-system is able to handle untracked variables.
				methodTypings.getSingleInstantiation(sootMethod, new TypeViews.Dyn<>()),
				casts);



		// invokeHS should be at the beginning of every method-body.
		// It creates a map for locals.
		JimpleInjector.invokeHS();
		//JimpleInjector.addNeededLocals();

		// We have to initialize the run-time system at the very beginning.
		// That is, either
		// - at the beginning of the clinit of the Main class (which is // the class that contains the main method), *if it exists*, or
		// - if there is no clinit, at the beginning of main
		// TODO: the run-time system should inititalize itself lazily, perhaps (i.e., on-demand)
		if (isFirstApplicationMethodToRun(sootMethod)) {
			JimpleInjector.initHS();
		}

		JimpleInjector.initHandleStmtUtils();
		ArrayList<String> fieldVars = new ArrayList<String>();

		// <editor-fold desc="Add Fields to Object Map, either static or instance; determined by Method name">

		/*
		 * If the method is the constructor, the newly created object
		 * has to be added to the ObjectMap and its fields are added to the
		 * new object
		 */
		if (sootMethod.getName().equals("<init>")) {
		    boolean nonStaticFieldsFlag = false;
            for (SootField f : fields) {
                containsFieldsVarsFlag = true;
                fieldVars.add(f.getName());
                if (!f.isStatic()) {
                    nonStaticFieldsFlag = true;
                }
            }
            if(fields.size() > 0 && nonStaticFieldsFlag){
                JimpleInjector.addInstanceObjectToObjectMap();
            }

			// Add all instance fields to ObjectMap
			for (SootField f : fields) {
				if (!f.isStatic()) {
					JimpleInjector.addInstanceFieldToObjectMap(f);
				}
			}

		} else if (sootMethod.getName().equals("<clinit>")) {
            SootClass sc = sootMethod.getDeclaringClass();
            JimpleInjector.addClassObjectToObjectMap(sc);

			// Add all static fields to ObjectMap
			for (SootField f : fields) {
				if (f.isStatic()) {
					JimpleInjector.addStaticFieldToObjectMap(f);
				}
			}
		}

		Iterator<Unit> stmtsIt = body.getUnits().iterator();
		g = new ExceptionalUnitGraph(body);
		MHGDominatorsFinder a = new MHGDominatorsFinder(g);
		HashMap<Stmt, List<Stmt>> loops = new HashMap<Stmt, List<Stmt>>();
		while (stmtsIt.hasNext()){
			Stmt s = (Stmt)stmtsIt.next();

			List<Unit> succs = g.getSuccsOf(s);
			Collection<Unit> dominaters = (Collection<Unit>)a.getDominators(s);

			ArrayList<Stmt> headers = new ArrayList<Stmt>();

			Iterator<Unit> succsIt = succs.iterator();
			while (succsIt.hasNext()){
				Stmt succ = (Stmt)succsIt.next();
				if (dominaters.contains(succ)){
					//header succeeds and dominates s, we have a loop
					headers.add(succ);
				}
			}

			Iterator<Stmt> headersIt = headers.iterator();
			while (headersIt.hasNext()){
				JimpleInjector.loopFlag = true;
				Stmt header = headersIt.next();
				List<Stmt> loopBody = getLoopBodyFor(header, s);

				// for now just print out loops as sets of stmts
				System.out.println("FOUND LOOP: Header: " + header + " Body: " + loopBody);
				/*if (loops.containsKey(header)){
					// merge bodies
					List<Stmt> lb1 = loops.get(header);
					loops.put(header, union(lb1, loopBody));
				}
				else {
					loops.put(header, loopBody);
				}*/
			}
		}

		// </editor-fold>

		List<Unit> successorStmt = new ArrayList<Unit>();
		List<String> unitRhsList = new ArrayList<String>(); // contains the RHS of the units in string
		List<String> unitRhsListString = new ArrayList<String>(); // contains the RHS of the units in string - need separate lists to deal with separate data type params
		List<String> unitStmtsListString = new ArrayList<String>(); // contains the units in string

		PatchingChain<Unit> mainStmts = (PatchingChain)units;
        for (SootMethod m : allMethods) {
            if (m.isMain()) {
                mainStmts = m.getActiveBody().getUnits();
                for (Unit unit : mainStmts) {
                    for (String fieldVar : fieldVars) {
                        if (Pattern.compile("\\b" + fieldVar + "\\b").matcher(unit.toString()).find()) {
                            fieldVarMaps.put(fieldVar, true);
                        }
                    }
                }
            }
        }

		for (Unit unit: unmodifiedStmts) {
			unitStmtsListString.add(unit.toString());
			if (unit.toString().contains("=")) {
				unitRhsList.add(unit.toString().split("=")[1]);
				unitRhsListString.add(unit.toString().split("=")[1]);
			}
			else {
				unitRhsList.add(unit.toString());
				unitRhsListString.add(unit.toString());
			}
		}

		this.unitRhsList = new ArrayList<>(unitRhsList);
		this.unitStmtsListString = new ArrayList<>(unitStmtsListString);

		// Analyzing Every Statement, step by step.
		boolean isNotStringFlag = false;
		boolean isStringFlag = false;
		int stmtIndex = 0;
		for (Unit unit: unmodifiedStmts) {
			boolean dynLabelFlag = false;
			String unitLhsString = "";
			String unitRhsString = "";

			if(isArithmeticExpression(unit.toString())) {
				successorStmt = unitGraph.getSuccsOf(unit);
				//if(!successorStmt.get(0).toString().contains("return") && !successorStmt.get(0).toString().contains("goto"))
				if(!successorStmt.get(0).toString().contains("return"))
					JimpleInjector.arithmeticExpressionFlag = true;
				successorStmt = new ArrayList<Unit>();
			}

			/*if(unit.toString().contains("goto")){
				int index = unitStmtsListString.indexOf(unit.toString());
				for(int i = index; i < unitStmtsListString.size(); i++){
					if(!(unitStmtsListString.toString().contains("makeHigh") || unitStmtsListString.toString().contains("makeLow") ||
							Pattern.compile("\"\\\\b\"+cast+\"\\\\b\"").matcher(unitStmtsListString.toString()).find())) {
						JimpleInjector.levelOfConditionVarNotUpdated = true;
					}
				}
			}*/


            if(unit.toString().contains("makeHigh") || unit.toString().contains("makeLow") || Pattern.compile("\\bcast\\b").matcher(unit.toString()).find()){
				dynLabelFlag = true;
			}
            if(containsFieldsVarsFlag && dynLabelFlag){
                List<Unit> succ = unitGraph.getSuccsOf(unit);
                succ = unitGraph.getSuccsOf(succ.get(0));
                if(succ.size() > 0 && (succ.get(0).toString().contains("intValue") || succ.get(0).toString().contains("doubleValue") ||
							succ.get(0).toString().contains("floatValue") || succ.get(0).toString().contains("booleanValue") || succ.get(0).toString().contains("charValue")))
                	succ = unitGraph.getSuccsOf(succ.get(0));
                if(succ.size() > 0 && succ.get(0).toString().contains("=")) {
                	String tempVar = (succ.get(0).toString().split("=")[0]);
                	if(tempVar.contains(" ")) {
						String[] tempArr = tempVar.split(" ");
						if (tempArr.length > 2) {
							String key = (succ.get(0).toString().split("=")[0]).split(" ")[2].replace(">", "");
							if (fieldVarMaps.containsKey(key)) {
								dynLabelFlag = false;
							}
						}
					}
				}
            }

			if(dynLabelFlag){
				// for int, double, boolean, float, char params
				for(int i = 0; i < unitRhsList.size(); i++) {
					if (unitRhsList.get(i).contains("intValue") || unitRhsList.get(i).contains("doubleValue") || unitRhsList.get(i).contains("floatValue")
							|| unitRhsList.get(i).contains("booleanValue") || unitRhsList.get(i).contains("charValue")) {

						if(stmtIndex + 2 == i) {
							isNotStringFlag = true;
							isStringFlag = false;
							unitLhsString = unitStmtsListString.get(i).split("=")[0];
							//int index = i + 1; // to escape the following assignment statement
							int index = i;
							List<Unit> tempSuccessorList = new ArrayList<Unit>();
							tempSuccessorList.add(unit);

							int u = unmodifiedStmts.size() - unmodifiedStmts.indexOf(unit) - 1;

							tempSuccessorList.set(0, unit);
							if(u > 4)
								u = 4;
							// TODO: get rid of hardcoded 4 !!
							for (int m = 0; m < u; m++) {
								tempSuccessorList.set(0, (unitGraph.getSuccsOf(tempSuccessorList.get(0))).get(0));
							}
							if (tempSuccessorList.get(0).toString().contains("makeHigh") || tempSuccessorList.get(0).toString().contains("makeLow"))
								index = index + 2; // to escape the following assignment and makeHigh/makeLow statements

							for (int j = 0; j <= index; j++) {
								unitRhsList.remove(0);
								unitStmtsListString.remove(0);
							}
							boolean varExistsFlag = varExists(unitRhsList, unitLhsString);
							if(!varExistsFlag){
								JimpleInjector.dynLabelFlag = true;
								successorStmt = unitGraph.getSuccsOf(unit);
								this.successorStmt = successorStmt;
							}
							unitRhsList = new ArrayList<>(this.unitRhsList);
							unitStmtsListString = new ArrayList<>(this.unitStmtsListString);
							break;
						}
					}
				}
				// for string, stringbuilder, object params
				if(!isNotStringFlag) {
					isStringFlag = true;
					successorStmt = unitGraph.getSuccsOf(unit);
					this.successorStmt = successorStmt;
					if(successorStmt.get(0).toString().contains("=")) {
						unitLhsString = successorStmt.get(0).toString().split("=")[0];
						unitRhsString = successorStmt.get(0).toString().split("=")[1];
						int index = unitRhsListString.indexOf(unitRhsString);
						successorStmt = unitGraph.getSuccsOf(successorStmt.get(0));
						if(successorStmt.get(0).toString().contains("makeHigh") || successorStmt.get(0).toString().contains("makeLow"))
							index += 1; // to escape the following makeHigh/makeLow statement
						for (int k = 0; k <= index; k++) {
							unitRhsListString.remove(0);
						}
						boolean varExistsFlag = varExists(unitRhsListString, unitLhsString);
						if(!varExistsFlag){
							JimpleInjector.dynLabelFlag = true;
						}
					}
				}
			}

			if(isStringFlag){
				// check if var is used in code. If not, the tracing for the assignment statement can be skipped
				if(this.successorStmt.size() > 0 && this.successorStmt.get(0).toString().equals(unit.toString())) {
					unitLhsString = this.successorStmt.get(0).toString().split("=")[0];
					boolean varExistsFlag = varExists(unitRhsListString, unitLhsString);
					if(!varExistsFlag){
						JimpleInjector.dynLabelFlag = true;
					}
				}
			}
			else {
				// check if var is used in code. If not, the tracing for the assignment statement can be skipped
				if (this.successorStmt.size() > 0 && this.successorStmt.get(0).toString().equals(unit.toString())) {
					JimpleInjector.dynLabelFlag = true;
					nextSuccessorStmt = unitGraph.getSuccsOf(unit);
				}
			}
			// remove extra tracing for methods like intValue
			if (nextSuccessorStmt.size() > 0 && (nextSuccessorStmt.get(0).toString().contains("intValue") || nextSuccessorStmt.get(0).toString().contains("doubleValue") || nextSuccessorStmt.get(0).toString().contains("booleanValue")
					|| nextSuccessorStmt.get(0).toString().contains("floatValue") || nextSuccessorStmt.get(0).toString().contains("charValue"))
					&& nextSuccessorStmt.get(0).toString().equals(unit.toString())) {
				JimpleInjector.dynLabelFlag = true;
				nextSuccessorStmt = unitGraph.getSuccsOf(unit);
			}

			// remove extra tracing for methods like valueOf
			//if (nextSuccessorStmt.size() > 0 && nextSuccessorStmt.get(0).toString().contains("valueOf") && nextSuccessorStmt.get(0).toString().equals(unit.toString())) {
			if(unit.toString().contains("valueOf"))	{
				nextSuccessorStmt = unitGraph.getSuccsOf(unit);
				if(nextSuccessorStmt.get(0).toString().contains("makeHigh") || nextSuccessorStmt.get(0).toString().contains("makeLow"))
					JimpleInjector.dynLabelFlag = true;
			}

			// Check if the statements is a postdominator for an IfStmt.
			if (DominatorFinder.containsStmt(unit)) {
				JimpleInjector.exitInnerScope(unit);
				logger.info("Exit inner scope with identity" +	DominatorFinder.getIdentityForUnit(unit));
				DominatorFinder.removeStmt(unit);
			}

			// Add further statements using JimpleInjector.
			unit.apply(stmtSwitch);
			JimpleInjector.dynLabelFlag = false;
			JimpleInjector.arithmeticExpressionFlag = false;
			//JimpleInjector.loopFlag = false;
			stmtIndex += 1;
		}

		prevClassName = className;


		// Apply all changes.
		JimpleInjector.addUnitsToChain();
		JimpleInjector.closeHS();
	}

	// Reference: https://searchcode.com/codesearch/view/17073873/
	// To differentiate if from while and for
	private  List<Stmt> getLoopBodyFor(Stmt header, Stmt node){

		ArrayList<Stmt> loopBody = new ArrayList<Stmt>();
		Stack<Unit> stack = new Stack<Unit>();

		loopBody.add(header);
		stack.push(node);

		while (!stack.isEmpty()){
			Stmt next = (Stmt)stack.pop();
			if (!loopBody.contains(next)){
				// add next to loop body
				loopBody.add(0, next);
				// put all preds of next on stack
				Iterator<Unit> it = g.getPredsOf(next).iterator();
				while (it.hasNext()){
					stack.push(it.next());
				}
			}
		}

		assert (node==header && loopBody.size()==1) || loopBody.get(loopBody.size()-2)==node;
		assert loopBody.get(loopBody.size()-1)==header;

		return loopBody;
	}

	/*private  List<Stmt> union(List<Stmt> l1, List<Stmt> l2){
		Iterator<Stmt> it = l2.iterator();
		while (it.hasNext()){
			Stmt next = it.next();
			if (!l1.contains(next)){
				l1.add(next);
			}
		}
		return l1;
	}*/


	// find if the var is used again in code. If not, it does not need to be tracked explicitly
	private static boolean varExists(List<String> unitRhsListString, String unitLhsString){
		boolean varExistsFlag = false;
		for(String a : unitRhsListString){    // source : https://stackoverflow.com/questions/25417363/java-string-contains-matches-exact-word
			String pattern = "\\b"+unitLhsString.trim()+"\\b";
			Pattern p = Pattern.compile(pattern);
			Matcher m = p.matcher(a);
			varExistsFlag = m.find();
			if(varExistsFlag)
				break;
		}
		return varExistsFlag;
	}

	// check if the unit string is an arithmetic expression
	public static boolean isArithmeticExpression(String s){

		if(Pattern.compile("[-+*/]").matcher(s).find()){
			return true;
		}

		return false;
	}

	/**
	 * Specifies, if the given Method is the First Application Method,
	 * such that {@link HandleStmt#init()} is not inserted to much, it is enough
	 * to call it at the very begging.
	 * @param method The Method, that is tested.
	 * @return true, iff the given Method is the first Application Method.
	 */
	private boolean isFirstApplicationMethodToRun(SootMethod method) {
		if (method.isMain()) {
			for (SootMethod m : method.getDeclaringClass().getMethods()) {
				if (m.getName().equals("<clinit>")) {
					return false;
				}
			}
			// there is no clinit in the main-class
			return true;
		} else if (method.getName().equals("<clinit>")){
			for (SootMethod m : method.getDeclaringClass().getMethods()) {
				if (m.isMain()) {
					return true; // we are clinit, and in the main class
				}
			}
			// we are another clinit, not in the main class
			return false;
		} else {
			return false; // neither clinit nor main method
		}
	}
}
