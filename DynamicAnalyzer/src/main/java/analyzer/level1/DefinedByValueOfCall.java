package analyzer.level1;

import de.unifreiburg.cs.proglang.jgs.instrumentation.Casts;
import soot.Local;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JimpleLocalBox;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.*;
import java.util.regex.Pattern;

public class DefinedByValueOfCall extends ForwardFlowAnalysis<Unit, Set<Local>> {

    private static Value prevTarget;
    public static boolean isFirstUnit;
    public static Set<String> independentVarsSet = new HashSet<String>();
    public static List<Value> identityTargets = new ArrayList<Value>();
    public static HashMap<String, List<HashMap<Integer, Value>>> identityTargetsMap = new HashMap<String, List<HashMap<Integer, Value>>>();
    public static HashMap<String, Boolean> dynInMethod = new HashMap<String, Boolean>();

    private static int index = 0;
    private static List l = new ArrayList();
    private static Casts casts;
    //private static String callingMethod;
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
        copy(in, out);
        if(isFirstUnit){
            isFirstUnit = false;
            if(unit instanceof JIdentityStmt)
                prevTarget = ((JimpleLocalBox) ((JIdentityStmt) unit).leftBox).getValue();
            else if(unit instanceof JAssignStmt)
                prevTarget = ((JAssignStmt) unit).getLeftOp();

            //callingMethod = BodyAnalyzer.callingMethod;
        }
        if(unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt){
            l = new ArrayList();
            index = 0;
        }


        // handling identity statements (or method params)
        if(unit instanceof IdentityStmt){
            identityTargets.add(((IdentityStmt) unit).getLeftOp());

            HashMap<Integer, Value> hm = new HashMap<Integer, Value>();
            hm.put(index, ((IdentityStmt) unit).getLeftOp());
            l.add(hm);
            identityTargetsMap.put(BodyAnalyzer.callingMethod, l);
            index += 1;
        }
        /*for(Unit u: BodyAnalyzer.methodCalls){
            if(u.equals(unit)){
                for (int i = 0; i < unit.getUseBoxes().size(); i++) {
                    Value param = unit.getUseBoxes().get(i).getValue();
                    String s = unit.getUseBoxes().get(i).getValue().toString();
                    if (!BodyAnalyzer.methodNames.stream().anyMatch(s::contains)) {
                        if(!identityTargets.isEmpty() && identityTargets.contains(param)){
                            independentVarsSet.add(param.toString());
                        }
                    }
                }
            }
        }*/

        // TODO: instanceof is used for demonstration.. it is better to use a StmtSwitch for the real implementation
        //handling assignment statements
        loop1:
        if (unit instanceof JAssignStmt) {
            JAssignStmt assignStmt = (JAssignStmt) unit;
            Value target = assignStmt.getLeftOp();
            Value source = assignStmt.getRightOp();
            /*if (target instanceof  Local && source instanceof  Local && in.contains(source)) {
               out.add((Local) target);
            } else if (target instanceof  Local && source instanceof InvokeExpr) {
                InvokeExpr invokeExpr = (InvokeExpr) source;
                if (invokeExpr.getMethod().getName().contains("valueOf")){
                   out.add((Local)target);
                };
            }
            else*/
            if(null != prevTarget) {
                InvokeExpr invokeExpr;
                if( source instanceof InvokeExpr){
                    invokeExpr = (InvokeExpr) source;
                    if(Pattern.compile("\\bcast\\b").matcher(source.toString()).find()) {
                        Casts.ValueConversion conversion = casts.getValueCast(assignStmt);
                        if (!conversion.getSrcType().isDynamic() && conversion.getDestType().isDynamic())
                            dynInMethod.put(BodyAnalyzer.callingMethod, true);
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
                            && null != invokeExpr && !BodyAnalyzer.methodNames.contains(invokeExpr.getMethod().getName()))
                        independentVarsSet.remove(prevTarget.toString());
                }
                else if(source instanceof Constant){
                    independentVarsSet.add(target.toString());
                }
                //else if(BodyAnalyzer.isArithmeticExpression(source.toString())) {
                    //String[] tempArray = source.toString().split("[-+*/]");
                    //independentVarsSet.add(tempArray[0].trim()); // Three address code - source will have only two vars
                   // independentVarsSet.add(tempArray[1].trim());
                //}
                else {
                    if(!out.contains(prevTarget) && !BodyAnalyzer.isArithmeticExpression(source.toString()))
                        independentVarsSet.add(prevTarget.toString());
                    out.clear();
                }
            }
            lab:prevTarget = target;
        }
    }

    static void getCasts(Casts c){
        casts = c;
    }

}
