package analyzer.level1;

import analyzer.level2.HandleStmt;
import de.unifreiburg.cs.proglang.jgs.constraints.TypeViews;
import de.unifreiburg.cs.proglang.jgs.instrumentation.Casts;
import de.unifreiburg.cs.proglang.jgs.instrumentation.MethodTypings;
import de.unifreiburg.cs.proglang.jgs.signatures.SigConstraint;
import de.unifreiburg.cs.proglang.jgs.signatures.SignatureTable;
import de.unifreiburg.cs.proglang.jgs.signatures.Symbol;
import scala.collection.JavaConversions;
import scala.collection.immutable.Set;
import soot.*;
import soot.baf.internal.BSpecialInvokeInst;
import soot.baf.internal.BVirtualInvokeInst;
import soot.jimple.Constant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.ImmediateBox;
import soot.jimple.internal.JAssignStmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import util.CommonUtil;
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
	private List<String> methodNamesList = new ArrayList<>();
	private boolean containsFieldsVarsFlag = false;
	private HashMap<String, Boolean> fieldVarMaps = new HashMap<String, Boolean>();
	private java.util.Set<String> methodNames = new HashSet<String>();
	private HashMap<String, List<HashMap<Integer, Boolean>>> argumentMap = new HashMap<String, List<HashMap<Integer, Boolean>>>();
	private List<Unit> methodCalls = new ArrayList<Unit>();
	private HashMap<Unit,String> methodCallsInsideMethods = new HashMap<Unit,String>();
	private List<String> independentVars = new ArrayList<String>();
	private String callingMethod = "main";
	private String prevClassName = "";
	private int count = 0;
	private Chain<SootField> fields;
	private boolean nonStaticFieldsFlag = false;

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
	private java.util.Set<String> getMethodNames(List<SootMethod> sootMethods){
		java.util.Set<String> methodNames = new HashSet<String>();
		for (SootMethod m : Scene.v().getMainClass().getMethods()) {
			if(!m.isMain()) {
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
		count += 1;
		logger.info(" Analyze of :" + body.getMethod().getName() + " started.");

		List<SootMethod> allMethods = Scene.v().getMainClass().getMethods();

		methodNames = getMethodNames(allMethods);
		methodNamesList = new ArrayList<>(methodNames);
		ArrayList<String> fieldVars = new ArrayList<String>();

		String className = body.getMethod().getDeclaringClass().getName();

		if(!prevClassName.isEmpty() && !prevClassName.equals(className)){
			reinitializeObjects();
		}

		if(count == 1){
			for (SootMethod sootMethod : allMethods) {
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

				fields = sootMethod.getDeclaringClass().getFields();

				if (sootMethod.getName().equals("<init>")) {
					for (SootField f : fields) {
						containsFieldsVarsFlag = true;
						fieldVars.add(f.getName());
						if (!f.isStatic()) {
							nonStaticFieldsFlag = true;
						}
					}
				}
			}

			HashMap<String, List<String>> independentVarsMap = new HashMap<String, List<String>>();
			DefinedByValueOfCall.setBodyAnalyzerObjects(methodNames, methodCalls);

			for (SootMethod sootMethod : allMethods) {
				UnitGraph unitGraph = new BriefUnitGraph(sootMethod.getActiveBody());

				DefinedByValueOfCall.isFirstUnit = true;
				callingMethod = sootMethod.getName();
				DefinedByValueOfCall.setCallingMethod(callingMethod);
				// to find dependent variable chains and independent variables
				DefinedByValueOfCall.getCasts(casts);
				new DefinedByValueOfCall(unitGraph);

			}


			independentVarsMap = DefinedByValueOfCall.getIndependentVarsMap();

			// for calls to methods not in main method, check if parent is dynamically tracked or not
            for(Map.Entry<Unit, String> e: methodCallsInsideMethods.entrySet()){
                String methodName1 = e.getValue();
                Unit unit = e.getKey();
                for (int i = 0; i < unit.getUseBoxes().size(); i++) {
                    int idx = 0;
                    Value value = unit.getUseBoxes().get(i).getValue();
                    String methodName2 = methodNames.stream().findAny().get();
                    if (!methodNames.stream().anyMatch(value.toString()::contains)) {
                        if(DefinedByValueOfCall.identityTargets.contains(value)){
                            InvokeExpr invokeExpr;
                            for(Unit s : methodCalls){
                            	if (s instanceof BSpecialInvokeInst || s instanceof BVirtualInvokeInst)
                            		continue;
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
                                        if (!methodNames.stream().anyMatch(val.toString()::contains)) {
                                            if(null != independentVars) {
                                                if (val instanceof Constant || independentVars.contains(val.toString())) {
                                                    List<HashMap<Integer, Value>> tempList2 = DefinedByValueOfCall.identityTargetsMap.get(methodName1);
                                                    if(null != tempList2) {
														HashMap<Integer, Value> tempMap = tempList2.get(idx);
														tempList.add(tempMap.get(idx).toString());
													}
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
                            if (!methodNames.stream().anyMatch(value.toString()::contains) && unit.getUseBoxes().get(i) instanceof ImmediateBox) {
                                independentVars = independentVarsMap.get(methodName);
                                // argument is not a constant - eg: add(7,7) or int x = 7 and add(x, x)
                                if (!(value instanceof Constant) && null != independentVars && !independentVars.contains(value.toString())) {
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
                                        if(!argList.isEmpty() && argList.size() > argPosition) {
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
                    if(!argumentsList.isEmpty()) // specialinvoke r0.<java.lang.Object: void <init>()>(); - always empty
                    	argumentMap.put(methodName, argumentsList);
                }
            }

            // Print argumentMap - debugging
            for(Map.Entry<String, List<HashMap<Integer, Boolean>>> e: argumentMap.entrySet()){
                System.out.print(e.getKey() + " -> ");
                List<HashMap<Integer, Boolean>> l = e.getValue();
                for(HashMap<Integer, Boolean> m : l){
                    for(Map.Entry e1 :m.entrySet()){
                        System.out.print(e1.getKey() + ": " + e1.getValue() + ", ");
                    }
                }
                System.out.println();
            }

		}

		SootMethod sootMethod = body.getMethod();
		Chain<Unit> units  = body.getUnits();

		if(!sootMethod.isMain() && !sootMethod.getName().equals("<init>") && !sootMethod.getName().equals("<clinit>"))
			analyseMethodSignatures(sootMethod);

		AnnotationStmtSwitch stmtSwitch = new AnnotationStmtSwitch(body, casts);

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

		JimpleInjector.setBodyAnalyzerObjects(fieldVarMaps, methodNames, argumentMap, methodCalls, methodCallsInsideMethods, independentVars);


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

		// <editor-fold desc="Add Fields to Object Map, either static or instance; determined by Method name">

		/*
		 * If the method is the constructor, the newly created object
		 * has to be added to the ObjectMap and its fields are added to the
		 * new object
		 */
		if (sootMethod.getName().equals("<init>")) {
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
			String unitString = unit.toString();
			unitStmtsListString.add(unitString);
			/*if(unitString.contains(">="))
				unitString = unit.toString().replace(">=", "GE");
			if(unitString.contains("<="))
				unitString = unit.toString().replace("<=", "LE");*/

			if (unitString.contains("=") && !unitString.contains("if")) {
				unitRhsList.add(unitString.split("=")[1]);
				unitRhsListString.add(unitString.split("=")[1]);
			}
			else {
				unitRhsList.add(unitString);
				unitRhsListString.add(unitString);
			}
		}

		this.unitRhsList = new ArrayList<>(unitRhsList);
		this.unitStmtsListString = new ArrayList<>(unitStmtsListString);

		// Analyzing Every Statement, step by step.
		boolean isNotStringFlag = false;
		boolean isStringFlag = false;
		int stmtIndex = 0;
		for (Unit unit: unmodifiedStmts) {
			//int stmtIndex = 0;
			boolean dynLabelFlag = false;
			String unitLhsString = "";
			String unitRhsString = "";

			if(CommonUtil.isArithmeticExpression(unit.toString())) {
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

				String tempString;
				//SootClass sc = field.getDeclaringClass();

				if(succ.size() > 0 && succ.get(0).toString().contains("=")) {
					String[] tempArr1 = succ.get(0).toString().split("=");
					if(tempArr1[0].contains(className))
						tempString = tempArr1[0];
					else
						tempString = tempArr1[1];
					String[] tempArr = tempString.split(" ");
					String key = tempArr[tempArr.length - 1].replace(">", "");
					if (null != fieldVarMaps && !fieldVarMaps.isEmpty()) {
						if (fieldVarMaps.containsKey(key) && fieldVarMaps.get(key))
							dynLabelFlag = false;
					}
				}




                /*if(succ.size() > 0 && succ.get(0).toString().contains("=")) {
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
				}*/
            }

			if(dynLabelFlag){
				unitRhsList = new ArrayList<>(this.unitRhsList);
				unitRhsListString = new ArrayList<>(this.unitRhsList);
				unitStmtsListString = new ArrayList<>(this.unitStmtsListString);
				// for int, double, boolean, float, char params
				for(int i = 0; i < unitRhsList.size(); i++) {
					if (unitRhsList.get(i).contains("intValue") || unitRhsList.get(i).contains("doubleValue") || unitRhsList.get(i).contains("floatValue")
							|| unitRhsList.get(i).contains("booleanValue") || unitRhsList.get(i).contains("charValue")) {

						if(stmtIndex + 2 == i) {
							isNotStringFlag = true;
							isStringFlag = false;
							if(unitStmtsListString.size() > i)
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

							if(containsFieldsVarsFlag)
								//index += 1;
							for (int j = 0; j <= index; j++) {
								unitRhsList.remove(0);
								unitStmtsListString.remove(0);
							}
							boolean varExistsFlag = true;
							//boolean varExistsFlag = varExists(unitRhsList, unitLhsString);
							if(!unitLhsString.isEmpty())
								varExistsFlag = varExists(unitStmtsListString, unitLhsString);
							if(!varExistsFlag){
								JimpleInjector.dynLabelFlag = true;
								successorStmt = unitGraph.getSuccsOf(unit);
								this.successorStmt = successorStmt;
							}
							//unitRhsList = new ArrayList<>(this.unitRhsList);
							//unitStmtsListString = new ArrayList<>(this.unitStmtsListString);
							break;
						}
						else {
							isNotStringFlag = false;
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
						if(containsFieldsVarsFlag)
							//index += 1;
						for (int k = 0; k <= index; k++) {
							unitRhsListString.remove(0);
							unitStmtsListString.remove(0);
						}
						//boolean varExistsFlag = varExists(unitRhsListString, unitLhsString);
						boolean varExistsFlag = varExists(unitStmtsListString, unitLhsString);
						if(!varExistsFlag){
							JimpleInjector.dynLabelFlag = true;
						}
						else {
							this.successorStmt.clear();
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
					else {
						this.successorStmt.clear();
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
		if(unitLhsString.contains("$"))
			unitLhsString = unitLhsString.replace("$","dol");
		for(String unitRhsString : unitRhsListString){    // source : https://stackoverflow.com/questions/25417363/java-string-contains-matches-exact-word
			if(unitRhsString.contains("$"))
				unitRhsString = unitRhsString.replace("$","dol");
			String pattern = "\\b"+unitLhsString.trim()+"\\b";
			Pattern p = Pattern.compile(pattern);
			Matcher m = p.matcher(unitRhsString);
			varExistsFlag = m.find();
			if(varExistsFlag)
				break;
		}
		return varExistsFlag;
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

	private void reinitializeObjects(){
		methodCalls = new ArrayList<Unit>();
		methodCallsInsideMethods = new HashMap<Unit,String>();
		argumentMap = new HashMap<String, List<HashMap<Integer, Boolean>>>();
		JimpleInjector.prevMethodName = "";
		JimpleInjector.signatureList = new ArrayList<String>();
		JimpleInjector.noTrackSignatureList = new ArrayList<String>();
		fieldVarMaps = new HashMap<String, Boolean>();
		//DefinedByValueOfCall.independentVarsMap = new HashMap<String, List<String>>();
		DefinedByValueOfCall.identityTargetsMap = new HashMap<String, List<HashMap<Integer, Value>>>();
		DefinedByValueOfCall.identityTargets = new ArrayList<Value>();
		count = 1;
		fields.clear();
	}


	/*
	* Based on the Method Constraints(irrespective of the type of arguments with which the method call has been made, analyse if method calls can be made with static arguments, dynamic arguments or both)
	* eg: 1) @Constraints({"@0 <= @ret", "@1 <= @ret", "@1 <= ?", "LOW <= @ret"})
	* 		 public static void add(int x, int y) - calls possible with public(constant) arguments
	*        i.e : add(int pub0, int pub1)
	* 	  2) @Constraints({"@0 <= @ret", "@1 <= @ret"})
	*        public static void add(int x, int y) - calls possible with both static and dynamic arguments
	*        i.e : add(int static0, int static1) and add(int dynamic0, int dynamic1)
	*     3) @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= LOW/HIGH"})
	*        public static void add(int x, int y) - calls possible with static arguments
	*        i.e : add(int static0, int static1)
	*     4) @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= ?"})
	*        public static void add(int x, int y) - calls possible with dynamic arguments
	*        i.e : add(int dynamic0, int dynamic1)
	*/
	private void analyseMethodSignatures(SootMethod sootMethod) {

		Set constraintsSet = null;
		//Get method constraints
		if (null != signatureTable){
			constraintsSet = signatureTable.get(sootMethod).get().constraints.sigSet();
		}

		if (null != constraintsSet) {
			java.util.Set constraints = JavaConversions.setAsJavaSet(constraintsSet);
			if (!constraints.isEmpty()) {
				List<SigConstraint> constraintsList = new ArrayList(constraints);
				List<SigConstraint> updatedConstraintsList = new ArrayList();
				int independentArgCount = 0;

				//Omit redundant constraints like @return LE @return or @param2 ~ @param2
				for (SigConstraint constraint : constraintsList) {
					if (!constraint.lhs.equals(constraint.rhs))
						updatedConstraintsList.add(constraint);
					if(constraint.lhs.toString().contains("@param") && constraint.rhs.toString().contains("@ret"))
						independentArgCount += 1;
				}

				String staticArgs = "";
				String dynArgs = "";
				String publicArgs = "";
				String args = "";

				loop1:
				if (!updatedConstraintsList.isEmpty()) {
					if (sootMethod.getParameterCount() > 0) {
						System.out.println();
						System.out.println("Analysing method constraints for method : " + sootMethod.getName());
						String methodSignature = sootMethod.getReturnType() + " " + sootMethod.getName() + "(" ;

						// handle unusual methods where args are independent of each other / return var
						if(independentArgCount != sootMethod.getParameterCount()){
							HashMap<Integer, Symbol> argEffectMap = getIndependentArgs(updatedConstraintsList);
							int argPosn = getArgPosition(updatedConstraintsList);
							String effectString = "";
							if(argPosn != Integer.MAX_VALUE) {
								Symbol symbol = argEffectMap.get(argPosn);
								effectString = getEffectString(symbol);
							}
							if(argEffectMap.isEmpty()){
								for(int m = 0; m < sootMethod.getParameterCount(); m++) {
									args += sootMethod.getParameterTypes().get(m) + " static" + m + "/dynamic" + m + "/public" + m + ", ";
								}
								System.out.println("Allowed method calls : (1)" + methodSignature + args.substring(0, args.lastIndexOf(",")) + ")");
								System.out.println("Allowed return type(s) : static/dynamic/public");
								break loop1;
							}
							for(int m = 0; m < sootMethod.getParameterCount(); m++){
								if(!argEffectMap.keySet().contains(m)){
									staticArgs += sootMethod.getParameterTypes().get(m) + " " + "static" + m + "/public" + m + ", ";
								}
								else {
									staticArgs += sootMethod.getParameterTypes().get(m) + " " + getEffectString(argEffectMap.get(m)) + m + ", ";
								}
								if(!argEffectMap.keySet().contains(m)){
									dynArgs += sootMethod.getParameterTypes().get(m) + " " + "dynamic" + m + "/public" + m + ", ";
								}
								else {
									dynArgs += sootMethod.getParameterTypes().get(m) + " " + getEffectString(argEffectMap.get(m)) + m + ", ";
								}
							}
							System.out.println("Allowed method calls : (1)" + methodSignature + staticArgs.substring(0, staticArgs.lastIndexOf(",")) + ")");
							if(!staticArgs.equals(dynArgs)) {
								System.out.println("(2)" + methodSignature + dynArgs.substring(0, dynArgs.lastIndexOf(",")) + ")");
							}
							if(!effectString.isEmpty())
								System.out.println("Allowed return type(s) : " + effectString);
							System.out.println();
							break loop1;
						}



						for (int i = 0; i < sootMethod.getParameterCount(); i++) {
							staticArgs += sootMethod.getParameterTypes().get(i) + " static" + i + ", ";
							dynArgs += sootMethod.getParameterTypes().get(i) + " dynamic" + i + ", ";
							publicArgs += sootMethod.getParameterTypes().get(i) + " public" + i + ", ";
						}
						// eg 1 in comments above
						if (updatedConstraintsList.toString().contains("Dyn()") && (updatedConstraintsList.toString().contains("Lit(HIGH)") || updatedConstraintsList.toString().contains("Lit(LOW)"))) {
							System.out.println("Allowed method call : (1)" + methodSignature + publicArgs.substring(0, publicArgs.lastIndexOf(",")) + ")");
							System.out.println("Allowed return type(s) : public");
						}
						// eg 2
						else if (!(updatedConstraintsList.toString().contains("Lit(HIGH)") || updatedConstraintsList.toString().contains("Lit(LOW)") || updatedConstraintsList.toString().contains("Dyn()"))) {
							System.out.println("Allowed method calls : (1)" + methodSignature + staticArgs.substring(0, staticArgs.lastIndexOf(",")) + ")");
							System.out.println("Allowed return type(s) : static");
							System.out.println("(2)" + methodSignature + dynArgs.substring(0, dynArgs.lastIndexOf(",")) + ")");
							System.out.println("Allowed return type(s) : dynamic");
						}
						// eg 3
						else if (updatedConstraintsList.toString().contains("Lit(LOW)") || updatedConstraintsList.toString().contains("Lit(HIGH)")) {
							System.out.println("Allowed method call : (1)" + methodSignature + staticArgs.substring(0, staticArgs.lastIndexOf(",")) + ")");
							System.out.println("Allowed return type(s) : static");
						}
						// eg 4
						else if (updatedConstraintsList.toString().contains("Dyn()")) {
							System.out.println("Allowed method call : (1)" + methodSignature + dynArgs.substring(0, dynArgs.lastIndexOf(",")) + ")");
							System.out.println("Allowed return type(s) : dynamic");
						}
					}
					// method with no arguments
					else {
						String effectString = getReturnConstraint(updatedConstraintsList);
						if(effectString.isEmpty())
							effectString = "dynamic/ static";
						System.out.println("Analysing method constraints for method : " + sootMethod.getName());
						System.out.println("Allowed return type(s) for method " + sootMethod.getName() + "() : " + effectString);
					}
				}
				// method with no arguments
				else {
					String effectString = getReturnConstraint(updatedConstraintsList);
					if(effectString.isEmpty())
						effectString = "dynamic/ static";
					System.out.println("Analysing method constraints for method : " + sootMethod.getName());
					System.out.println("Allowed return type(s) for method " + sootMethod.getName() + "() : " + effectString);
				}
			}
		}
	}

	/**
	 * convert symbol to string
	 * @param symbol
	 * @return
	 */
	private static String getEffectString(Symbol symbol){
		String effectString = "";
		if(symbol.toString().equals("Dyn()"))
			effectString = "dynamic";
		else if(symbol.toString().equals("Lit(HIGH)") || symbol.toString().contains("Lit(LOW)"))
			effectString = "static";
		return effectString;
	}

	/**
	 * get map of arguments independent from the return var / independent of each other
	 * @param updatedConstraintsList
	 * @return
	 */
	private static HashMap getIndependentArgs(List<SigConstraint> updatedConstraintsList){
		int argPosition = Integer.MAX_VALUE;
		HashMap argEffectMap = new HashMap();
		for (SigConstraint constraint : updatedConstraintsList) {
			String constraintString = constraint.lhs.toString();
			Symbol effectString = constraint.rhs;
			if (effectString.toString().contains("Lit(HIGH)") || effectString.toString().contains("Lit(LOW)") || effectString.toString().contains("Dyn()")){
				for (int i = 0; i < constraintString.length(); i++) {
					if (Character.isDigit(constraintString.charAt(i))) {
						argPosition = constraintString.charAt(i);
						argEffectMap.put(Character.getNumericValue(argPosition), effectString);
					}
				}
			}
		}
		return argEffectMap;
	}


	/**
	 * get param on which the return var is dependent
	 * @param updatedConstraintsList
	 * @return
	 */
	private static int getArgPosition(List<SigConstraint> updatedConstraintsList){
		int argPosition = Integer.MAX_VALUE;
		for (SigConstraint constraint : updatedConstraintsList) {
			String constraintString = constraint.lhs.toString();
			Symbol rhs = constraint.rhs;
			if (rhs.toString().contains("@ret")){
				for (int i = 0; i < constraintString.length(); i++) {
					if (Character.isDigit(constraintString.charAt(i))) {
						argPosition = Character.getNumericValue(constraintString.charAt(i));
					}
				}
			}
		}
		return argPosition;
	}

	/**
	 * get return constraint
	 * @param updatedConstraintsList
	 * @return
	 */
	private static String getReturnConstraint(List<SigConstraint> updatedConstraintsList){
		String effectString = "";
		for (SigConstraint constraint : updatedConstraintsList) {
			String lhsString = constraint.lhs.toString();
			if(!lhsString.contains("@ret"))
				effectString = getEffectString(constraint.lhs);
			else
				effectString = getEffectString(constraint.rhs);
		}
		return effectString;
	}
}
