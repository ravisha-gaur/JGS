package analyzer.level1;

import de.unifreiburg.cs.proglang.jgs.instrumentation.Casts;
import soot.Local;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DefinedByValueOfCall extends ForwardFlowAnalysis<Unit, Set<Local>> {

    private static Value prevTarget;
    private static Value prevSource;
    private String methodName;
    public static boolean isFirstUnit;
    public static Set<String> independentVarsSet = new HashSet<String>();
    public static HashMap<String, List<String>> independentVarsMap = new HashMap<String, List<String>>();
    public static List<Value> identityTargets = new ArrayList<Value>();
    public static HashMap<String, List<HashMap<Integer, Value>>> identityTargetsMap = new HashMap<String, List<HashMap<Integer, Value>>>();
    public static HashMap<String, Boolean> dynInMethod = new HashMap<String, Boolean>();

    private static int index = 0;
    private static List identityList = new ArrayList();
    private static Casts casts;
    private static boolean staticDestination = false;

    public DefinedByValueOfCall(DirectedGraph<Unit> graph) {
        super(graph);
        doAnalysis();
    }

    @Override
    protected Set<Local> newInitialFlow() {
        return new HashSet<>();
    }

    @Override
    protected void merge(Set<Local> in1, Set<Local> in2, Set<Local> out) {
        out.clear();
        in1.stream().filter(in2::contains).forEach(out::add);
    }

    @Override
    protected void copy(Set<Local> in, Set<Local> out) {
        out.clear();
        out.addAll(in);
    }

    /**
     * This method is used to find dependent variable chains and independent variables
     * @param in
     * @param unit
     * @param out
     */
    @Override
    protected void flowThrough(Set<Local> in, Unit unit, Set<Local> out) {
        String methodName1 = "";
        copy(in, out);
        if(isFirstUnit){
            isFirstUnit = false;
            if(unit instanceof JIdentityStmt)
                prevTarget = ((JimpleLocalBox) ((JIdentityStmt) unit).leftBox).getValue();
            else if(unit instanceof JAssignStmt)
                prevTarget = ((JAssignStmt) unit).getLeftOp();
        }

        InvokeExpr invokeExpr1 = null;
        //handling method calls not in the main method
        if(BodyAnalyzer.methodCalls.contains(unit)){
            if(unit instanceof JAssignStmt)
                invokeExpr1 = (InvokeExpr) (((JAssignStmt) unit).getRightOp());
            else if(unit instanceof JInvokeStmt)
                invokeExpr1 = ((JInvokeStmt) unit).getInvokeExpr();
            String methodName2 = "";
            List<String> indVarList = new ArrayList<String>();
            for (int i = 0; i < unit.getUseBoxes().size(); i++) {
                if(unit.getUseBoxes().get(i) instanceof ImmediateBox) {
                    indVarList.add(unit.getUseBoxes().get(i).getValue().toString());
                }
                else {
                    if(null != invokeExpr1)
                        methodName2 = invokeExpr1.getMethod().getName();
                }
            }
            List<String> ind = new ArrayList<>(independentVarsSet);
            List<String> independentVarList = ind.stream().filter(indVarList::contains).collect(Collectors.toList());
            if(independentVarsMap.containsKey(methodName2)){
                independentVarList.addAll(independentVarsMap.get(methodName2));
            }
            independentVarsMap.put(methodName2, independentVarList);
        }

        if(unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt){
            identityList = new ArrayList();
            index = 0;
            independentVarsSet = new HashSet<String>();
        }

        // handling identity statements (or method params)
        if(unit instanceof IdentityStmt){
            identityTargets.add(((IdentityStmt) unit).getLeftOp());

            HashMap<Integer, Value> hm = new HashMap<Integer, Value>();
            hm.put(index, ((IdentityStmt) unit).getLeftOp());
            identityList.add(hm);
            identityTargetsMap.put(BodyAnalyzer.callingMethod, identityList);
            index += 1;
        }

        loop1:
        //handling assignment statements
        if (unit instanceof JAssignStmt) {
            JAssignStmt assignStmt = (JAssignStmt) unit;
            Value target = assignStmt.getLeftOp();
            Value source = assignStmt.getRightOp();
            if(null != prevTarget) {
                InvokeExpr invokeExpr;
                if( source instanceof InvokeExpr){
                    invokeExpr = (InvokeExpr) source;
                    methodName1 = invokeExpr.getMethod().getName();
                    if(BodyAnalyzer.methodNames.contains(methodName1))
                        methodName = methodName1;
                    if(Pattern.compile("\\bcast\\b").matcher(source.toString()).find()) {
                        Casts.ValueConversion conversion = casts.getValueCast(assignStmt);
                        if (!conversion.getSrcType().isDynamic() && conversion.getDestType().isDynamic()) {
                            staticDestination = false;
                            dynInMethod.put(BodyAnalyzer.callingMethod, true);
                        }
                        else if(conversion.getSrcType().isDynamic() && !conversion.getDestType().isDynamic()){
                            staticDestination = true;
                            break loop1;
                        }
                    }
                    if (target instanceof Local && Pattern.compile("\\b" + prevTarget.toString().replace("$","a") + "\\b").matcher(source.toString().replace("$","a")).find()) {
                        out.add((Local) prevTarget);
                        if(staticDestination){
                            independentVarsSet.add(target.toString());
                            break loop1;
                        }
                        if (!staticDestination && null != invokeExpr && invokeExpr.getMethod().getName().contains("intValue") || invokeExpr.getMethod().getName().contains("doubleValue") || invokeExpr.getMethod().getName().contains("floatValue")
                        || invokeExpr.getMethod().getName().contains("booleanValue") || invokeExpr.getMethod().getName().contains("charValue")){
                            out.add((Local)target);
                        };
                    }
                    if(!BodyAnalyzer.isArithmeticExpression(source.toString()) && !independentVarsSet.isEmpty() && independentVarsSet.contains(prevTarget.toString())
                            && null != invokeExpr && !BodyAnalyzer.methodNames.contains(invokeExpr.getMethod().getName()) && !(prevSource instanceof Constant))
                        independentVarsSet.remove(prevTarget.toString());
                }
                else if(source instanceof Constant){
                    independentVarsSet.add(target.toString());
                }
                else {
                    if(!out.contains(prevTarget) && !BodyAnalyzer.isArithmeticExpression(source.toString()))
                        independentVarsSet.add(prevTarget.toString());
                    out.clear();
                }
            }
            prevTarget = target;
            prevSource = source;
        }
    }

    static void getCasts(Casts c){
        casts = c;
    }

}
