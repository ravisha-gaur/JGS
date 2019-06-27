package analyzer.level1;

import analyzer.level2.CurrentSecurityDomain;
import analyzer.level2.HandleStmt;
import de.unifreiburg.cs.proglang.jgs.instrumentation.Casts;
import de.unifreiburg.cs.proglang.jgs.instrumentation.CxTyping;
import de.unifreiburg.cs.proglang.jgs.instrumentation.Instantiation;
import de.unifreiburg.cs.proglang.jgs.instrumentation.VarTyping;
import scala.Option;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.ImmediateBox;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.util.Chain;
import util.dominator.DominatorFinder;
import util.exceptions.InternalAnalyzerException;
import util.jimple.JimpleFactory;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * JimpleInjector is handles the inserts of additional instructions in a methods
 * body, such that the Dynamic Checking is possible.
 *
 * @author Regina Koenig (2015), Karsten Fix (2017), fennell (2017)
 */
public class JimpleInjector {

    /** String for the HandleStmt class. */
    private static final String HANDLE_CLASS = HandleStmt.class.getName();

    /** Local which holds the object of HandleStmt. */
    private static Local hs = Jimple.v().newLocal("hs", RefType.v(HANDLE_CLASS));

    private static JimpleFactory fac = new JimpleFactory(HandleStmt.class, hs);


    // <editor-fold desc="Fields for Body Analysis">

    /** The body of the actually analyzed method. */
    private static Body b = Jimple.v().newBody();

    /** Chain with all units in the actual method-body.*/
    private static Chain<Unit> units = b.getUnits();

    /** Chain with all locals in the actual method-body. */
    private static Chain<Local> locals = b.getLocals();

    private static boolean ctxCastCalledFlag = false;

    private static Stack stack = new Stack();

    public static boolean dynLabelFlag = false;

    public static boolean arithmeticExpressionFlag = false;

    public static boolean checkConditionCalledFlag = false;

    public static boolean loopFlag = false;

    private static int count = 0;

    public static boolean methodCallFlag = false;

    public static boolean dynamicArgumentFlag = true;

    private static boolean dontSetLocalFlag = false;

    private static int argumentPosition = 0;

    public static ArrayList<String> signatureList = new ArrayList<String>();
    public static ArrayList<String> noTrackSignatureList = new ArrayList<String>();

    private static  boolean afterAssignArgumentToLocal = false;
    public static String prevMethodName = "";
    private static Unit prevUnit;


    public static boolean staticDestination = false;

    private static boolean staticCtxCast = false;

    /**
     * Stores the position of
     * <ul>
     *     <li>the last unit which was analyzed in the unit chain</li>
     *     <li><b>or</b> the last inserted unit</li>
     * </ul>
     * This is needed for further units, which have to be inserted after this
     * position.
     */
    // TODO: handling the "last positions" like this is absolutely horrible.
    // Cf. also the code in "addUnitsToChain". Instead of this mess, there should be two maps "stmt -> listof(stmt)",
    // mapping to statements-to-be-inserted before, and after a given original statement, respectively
    // It Should stay, because it is easier; at least for the Moment.
    private static Unit lastPos;
    public static Unit conditionPos;

    /** Logger */
    private static Logger logger = Logger.getLogger(JimpleInjector.class.getName());;


    private static Casts casts;

    /**
     * Stores the results of the static analysis. Use Level instead of Level because of conflicts with the LEVEL of the Logger.
     */
    private static VarTyping varTyping;
    private static CxTyping cxTyping;
    private static Instantiation instantiation;

    /**
     * The list of locals of the body *before* instrumentation.
     */
    private static List<Local> originalLocals;

    /**
     * See method with same name in HandleStatement.
     *
     * @param pos   Statement / Unit where to insert setReturnLevelAfterInvokeStmt
     */
    public static void setReturnLevelAfterInvokeStmt(Local l, Unit pos, String methodName) {
        Unit invoke = fac.createStmt("setReturnLevelAfterInvokeStmt", StringConstant.v(getSignatureForLocal(l)));
        if(!Pattern.compile("\\bstoreArgumentLevel\\b").matcher(units.toString()).find()){
            JAssignStmt assignStmt = (JAssignStmt) pos;
            Value source = assignStmt.getRightOp();
            String mn = ((InvokeExpr)source).getMethod().getName();
            String m = "";
            loop1:if(BodyAnalyzer.methodCallsInsideMethods.values().contains(mn)){
                InvokeExpr is = null;
                Unit unit = BodyAnalyzer.methodCallsInsideMethods.keySet().stream().findAny().get();
                if(unit instanceof JAssignStmt)
                    is = (InvokeExpr) (((JAssignStmt) unit).getRightOp());
                else if(unit instanceof JInvokeStmt)
                    break loop1;
                if(null != is) {
                    m = is.getMethod().getName();
                    if(DefinedByValueOfCall.dynInMethod.containsKey(m))
                        DefinedByValueOfCall.dynInMethod.put(mn, true);
                }
            }
            if(!DefinedByValueOfCall.dynInMethod.containsKey(mn))
            //if(!DefinedByValueOfCall.dynInMethod.containsKey(methodName))
                return;
        }
        // only add setReturnLevelAfterInvokeStmt if the left side is dynamic
        if (varTyping.getAfter(instantiation, (Stmt) pos, (Local) ((JAssignStmt) pos).leftBox.getValue() ).isDynamic() ) {
            units.insertAfter(invoke, pos);
            noTrackSignatureList.clear();
            //noTrackSignatureList.remove(getSignatureForLocal(l));
        }
    }

    /**
     * Initialization of JimpleInjector. Set all needed variables
     * and compute the start position for inserting new units.
     *
     * @param body The body of the analyzed method.
     */
    public static void setBody(Body body) {
        b = body;
        units = b.getUnits();
        locals = b.getLocals();
        originalLocals = new ArrayList<>(locals);

        lastPos = getUnitOf(units, getStartPos(body));
        fac.initialise();
    }

    // <editor-fold desc="HandleStmt Related Methods">

    /**
     * Creates the Local hs in Jimple Code,
     * assigns "hs = new HandleStmt()" and invokes the Constructor within the
     * created Jimple Code.
     */
    static void invokeHS() {
        logger.info("Invoke HandleStmt in method " + b.getMethod().getName());

        locals.add(hs);
        Unit in = Jimple.v().newAssignStmt(hs, Jimple.v().newNewExpr(
                RefType.v(HANDLE_CLASS)));

        Unit inv = fac.createStmt(HandleStmt.class.getName());

        units.insertBefore(Arrays.asList(in, inv), lastPos);
        lastPos = inv;
    }

    /**
     * Inserts a call of {@link HandleStmt#initHandleStmtUtils()}
     * into the generated Jimple Code after the Last Position.
     */
    static void initHandleStmtUtils() {
        logger.info("Set Handle Stmt Utils and active/passive Mode of superfluous instrumentation checker");

        Unit inv = fac.createStmt("initHandleStmtUtils");

        units.insertAfter(inv, lastPos);
        lastPos = inv;
    }

    /**
     * Inserts {@link HandleStmt#init()}.
     */
    static void initHS() {
        logger.info("Initializing HandleStmt in method: " + b.getMethod().getName());

        Unit init = fac.createStmt("init");

        units.insertAfter(init, lastPos);
        lastPos = init;
    }

    /**
     * Injects the {@link HandleStmt#close()}, because it should be injected at the
     * end of every analyzed method.
     */
    static void closeHS() {
        logger.info("Closing HandleStmt in Method "+b.getMethod().getName());
        units.insertBefore(fac.createStmt("close"), units.getLast());
    }


    // </editor-fold>

    // <editor-fold desc="Local Related Methods">


    /**
     * Inserts a call of {@link HandleStmt#addLocal(String)}.
     *
     * @param local The Local, that shall be added. Its signature will be calculated
     * @see HandleStmt#addLocal(String)
     * @see JimpleInjector#getSignatureForLocal(Local)
     */
    public static void addLocal(Local local) {
        logger.info("Add Local " + getSignatureForLocal(local) + " in Method " + b.getMethod().getName());

        Unit add = fac.createStmt("addLocal", StringConstant.v(getSignatureForLocal(local)));
        units.insertAfter(add, lastPos);
        lastPos = add;
    }

    /**
     * Inserts {@link HandleStmt#setLocalFromString(String, String)} into the Jimple Code
     *
     * @param local local The Local with level shall be adjusted.
     * @param level the level to assign to the local
     * @param pos   position where to insert the created Stmt.
     */
    public static void makeLocal(Local local, String level, Unit pos) {
        logger.info("Setting " + local + "to new level " + level);

        String signature = getSignatureForLocal(local);
        dynamicArgumentFlag = true;
        dontSetLocalFlag = false;
        methodCallFlag = false;
        Unit setLevelOfL = fac.createStmt("setLocalFromString",
                StringConstant.v(signature),
                StringConstant.v(level));

        units.insertAfter(setLevelOfL, pos);
        lastPos = setLevelOfL;
    }

    // </editor-fold>

    // <editor-fold desc="Add To Object Map - Methods">

    /**
     * Inserts a call of {@link HandleStmt#addObjectToObjectMap(Object)}
     *
     * Add the instance of the actual class-object to the object map.
     * This is only done in "init".
     */
    static void addInstanceObjectToObjectMap() {
        logger.info("Add object "+units.getFirst().getUseBoxes().get(0).getValue()+" to ObjectMap in method "+ b.getMethod().getName());
        assureThisRef();
        Unit assignExpr = fac.createStmt("addObjectToObjectMap", units.getFirst().getDefBoxes().get(0).getValue());
        units.insertAfter(assignExpr, lastPos);
        lastPos = assignExpr;
    }

    /**
     * Inserts a call of {@link HandleStmt#addObjectToObjectMap(Object)}, what is
     * needed for static fields
     *
     * @param sc The SootClass that represents the Class that provides the static field
     */
    static void addClassObjectToObjectMap(SootClass sc) {
        logger.info("Add object "+sc.getName()+" to ObjectMap in method " + b.getMethod().getName());
        Unit assignExpr = fac.createStmt("addObjectToObjectMap", ClassConstant.v(sc.getName().replace(".", "/")));
        units.insertAfter(assignExpr, lastPos);
        lastPos = assignExpr;
    }

    /**
     * Inserts {@link HandleStmt#addFieldToObjectMap(Object, String)}
     *
     * @param field The Field that shall be added to the Object Map.
     */
    static void addInstanceFieldToObjectMap(SootField field) {
        logger.info("Adding field "+field.getSignature()+" to ObjectMap in method " + b.getMethod().getName());

        assureThisRef();

        String fieldSignature = getSignatureForField(field);
        Value tmpLocal =  units.getFirst().getDefBoxes().get(0).getValue();

        // Todo: is that the same? All tests passing...-> Write Test, that fails, because it is wrong...
        // tmpLocal = ClassConstant.v(field.getDeclaringClass().getName().replace(".", "/"));

        Unit assignExpr = fac.createStmt("addFieldToObjectMap", tmpLocal, StringConstant.v(fieldSignature));

        units.insertAfter(assignExpr, lastPos);
        lastPos = assignExpr;
    }

    /**
     * Inserts {@link HandleStmt#addFieldToObjectMap(Object, String)}
     *
     * @param field The Field that shall be added to the Object Map.
     */
    // Todo: May be the same as addInstanceField, difference could be figured out by field.isStatic() ?!
    static void addStaticFieldToObjectMap(SootField field) {
        logger.info("Adding static Field " + field + " to Object Map in method "+b.getMethod().getName());

        String signature = getSignatureForField(field);
        SootClass sc = field.getDeclaringClass();

        Unit assignExpr = fac.createStmt("addFieldToObjectMap",
                ClassConstant.v(sc.getName().replace(".", "/")),
                StringConstant.v(signature));

        units.insertAfter(assignExpr, lastPos);
        lastPos = assignExpr;
    }

    /**
     * Inserts {@link HandleStmt#addArrayToObjectMap(Object[])} call into the Jimple Code.
     *
     * @param a   The Local where the array is stored.
     * @param pos Unit where the array occurs, after that position the invoke Stmt will be inserted.
     */
    public static void addArrayToObjectMap(Local a, Unit pos) {
        logger.info("Add array "+a+" with type "+a.getType()+" to ObjectMap in method " + b.getMethod().getName());
        Unit assignExpr = fac.createStmt("addArrayToObjectMap", a);
        units.insertAfter(assignExpr, pos);
        lastPos = assignExpr;
    }

    /**
     * Add the level of a local on the right side of an assign statement.
     * Inserts {@link HandleStmt#joinLevelOfLocalAndAssignmentLevel(String)} into the Jimple Code
     *
     * @param local Local
     * @param pos   Unit where the local occurs
     */
    public static void addLevelInAssignStmt(Local local, Unit pos) {
        if(staticCtxCast)
            return;

        // Checking, if current Policy is NSU, before performing the NSU_Check.
        // Means: If not, then we can break up here.
        if (DynamicPolicy.selected != DynamicPolicy.Policy.NSU_POLICY) {
            logger.info("Do not use NSU Policy.");
            return;
        }
        logger.info("Adding level of "+local+" in assign statement of Method: "+b.getMethod().getName());

        String signature = getSignatureForLocal(local);
        Unit invoke = fac.createStmt("joinLevelOfLocalAndAssignmentLevel", StringConstant.v(signature));

        if(afterAssignArgumentToLocal) {
            if (!signatureList.contains(signature)) {
                return;
            }
        }

        if(noTrackSignatureList.contains(signature))
            return;

        for(String methodName: BodyAnalyzer.methodNames){
            if(Pattern.compile("\\b" + methodName + "\\b").matcher(pos.toString()).find()){
                return;
            }
        }

        // only insert the joinLevelOfLocal.. stmt if local is in fact dynamically checked
        // TODO CX is irrelevant here?
        //if(varTyping.getBefore(instantiation, (Stmt) pos, local).isDynamic()){
        if (varTyping.getAfter(instantiation, (Stmt) pos, local).isDynamic()) {
            units.insertBefore(invoke, pos);
            lastPos = pos;
        }
    }

    /**
     * Add the level of a field of an object. It can be the field of the actually
     * analyzed object or the field
     * Inserts {@link HandleStmt#joinLevelOfFieldAndAssignmentLevel(Object, String)} into the Jimple Code
     *
     * @param f   Reference to the instance field
     * @param pos The statement where this field occurs
     */
    public static void addLevelInAssignStmt(InstanceFieldRef f, Unit pos) {
        if(staticCtxCast)
            return;

        logger.info("Adding level of field "+f.getField().getSignature()+" in assignStmt in method "+  b.getMethod().getName());

        String fieldSignature = getSignatureForField(f.getField());

        Unit assignExpr = fac.createStmt("joinLevelOfFieldAndAssignmentLevel", f.getBase(), StringConstant.v(fieldSignature));

        String[] tempArr = (pos.toString().split("=")[0]).split(" ");
        if(tempArr.length > 2) {
            String key = tempArr[2].replace(">", "");
            if (BodyAnalyzer.fieldVarMaps.get(key))
                staticDestination = false;
        }

        // TODO CANNOT CAST ..
        if(!staticDestination) {
            units.insertBefore(assignExpr, pos);
            lastPos = pos;
        }
    }

    

    /**
     * Inserts {@link HandleStmt#joinLevelOfFieldAndAssignmentLevel(Object, String)} into the Jimple Code
     *
     * @param f   the field
     * @param pos the position where to insert the statement
     */
    public static void addLevelInAssignStmt(StaticFieldRef f, Unit pos) {
        if(staticCtxCast)
            return;

        logger.info("Adding Level of static Field " + f + " in Method "+b.getMethod());

        SootField field = f.getField();
        String signature = getSignatureForField(field);

        SootClass sc = field.getDeclaringClass();

        Unit assignExpr = fac.createStmt("joinLevelOfFieldAndAssignmentLevel",
                ClassConstant.v(sc.getName().replace(".", "/")),
                StringConstant.v(signature)
        );

        String key = (pos.toString().split("=")[1]).trim().split(" ")[2].replace(">", "");
        if(!BodyAnalyzer.fieldVarMaps.isEmpty() && null != BodyAnalyzer.fieldVarMaps.get(key) && BodyAnalyzer.fieldVarMaps.get(key))
            staticDestination = false;


        // TODO cannot cast StaticFieldref to Local!
        if(!staticDestination) {
            units.insertBefore(assignExpr, pos);
            lastPos = pos;
        }
    }

    /**
     * Add the level of a read array field to the security-level-list.
     * Inserts {@link HandleStmt#joinLevelOfArrayFieldAndAssignmentLevel(Object, String)} into Jimple Code
     *
     * @param a   -ArrayRef- The referenced array field
     * @param pos -Unit- The position where this reference occurs
     */
    public static void addLevelInAssignStmt(ArrayRef a, Unit pos) {
        if(staticCtxCast)
            return;

        logger.info("Add Level of Array " + a + " in assign stmt: "+pos);

        String signature = getSignatureForArrayField(a);

        Unit assignExpr = fac.createStmt("joinLevelOfArrayFieldAndAssignmentLevel", a.getBase(), StringConstant.v(signature));

        // TODO CANNOT CAST ...
        units.insertBefore(assignExpr, pos);
        lastPos = pos;
    }

    // </editor-fold>

    // <editor-fold desc="Set Level of Assign Stmt - Methods -> Interesting for LHS">

    public static void setLevelOfAssignStmt(Local l, Unit pos) {
        if(staticCtxCast)
            return;

        boolean checkDynArgs = false;

        for (int i = 0; i < pos.getUseBoxes().size(); i++) {
            if(pos.getUseBoxes().get(i) instanceof ImmediateBox) {
                checkDynArgs = false;
                Value param = pos.getUseBoxes().get(i).getValue();
                String sig = param.getType() + "_" + param;
                if(noTrackSignatureList.contains(sig)){
                    checkDynArgs = true;
                }
                else
                    break;
            }
        }
        if(checkDynArgs) {
            dontSetLocalFlag = true;
            noTrackSignatureList.add(getSignatureForLocal(l));
            return;
        }

        // Checking, if current Policy is NSU, before performing the NSU_Check.
        // Means: If not, then we can break up here.
        if (DynamicPolicy.selected != DynamicPolicy.Policy.NSU_POLICY) {
            logger.info("Do not use NSU Policy.");
            return;
        }
        logger.info("Setting level in assign statement");

        String signature = getSignatureForLocal(l);
        afterAssignArgumentToLocal = false;


        // insert setLocalToCurrentAssignmentLevel, which accumulates the PC and the right-hand side of the assign stmt.
        // The local's sec-value is then set to that sec-value.
        Stmt stmt = (Stmt) pos;
        for(String methodName: BodyAnalyzer.methodNames){
            if(Pattern.compile("\\b" + methodName + "\\b").matcher(pos.toString()).find()){
                return;
            }
        }

        if(!dynLabelFlag)  {  //&& !flag
            Unit invoke = fac.createStmt("setLocalToCurrentAssignmentLevel", StringConstant.v(signature));

            if (!ctxCastCalledFlag) {
                de.unifreiburg.cs.proglang.jgs.instrumentation.Type typeBefore = varTyping.getBefore(instantiation, stmt, l);
                Unit checkLocalPCExpr = typeBefore.isPublic()
                        ? fac.createStmt("checkNonSensitiveLocalPC")
                        : fac.createStmt("checkLocalPC", StringConstant.v(signature));

                if (varTyping.getAfter(instantiation, (Stmt) pos, l).isDynamic()) {
                    // insert NSU check only if PC is dynamic!
                    //if (cxTyping.get(instantiation, (Stmt) pos).isDynamic() && !levelOfConditionVarNotUpdated) {
                    if (cxTyping.get(instantiation, (Stmt) pos).isDynamic()) {
                        units.insertBefore(checkLocalPCExpr, pos);
                    }
                    if (!lastPos.toString().contains("setLocalFromString") && !arithmeticExpressionFlag)
                        units.insertBefore(invoke, pos);
                }
            } else {
                Unit checkLocalPCExpr = fac.createStmt("checkLocalPC", StringConstant.v(signature));
                units.insertBefore(checkLocalPCExpr, pos);
                units.insertBefore(invoke, pos);
            }

            // insert checkLocalPC to perform NSU check (aka check that level of local greater/equal level of lPC)
            // only needs to be done if CxTyping of Statement is Dynamic.
            // Also, if the variable to update is public, the PC should be "bottom"


            // TODO i did comment this out for some reason .. but why?
            // if variable l is not dynamic after stmt pos,
            // we do not need to call setLocalToCurrentAssignmentLevel at all,
            // and we especially do not need to perform a NSU check!
            lastPos = pos;
        }

    }

    /**
     * Set the level of a field of an object. It can be the field of the actually
     * analyzed object or the field
     *
     * @param f   Reference to the instance field
     * @param pos The statement where this field occurs
     */
    public static void setLevelOfAssignStmt(InstanceFieldRef f, Unit pos) {
        if(staticCtxCast)
            return;

        logger.info("Set level of field "+f.getField().getSignature() + " in assign Statement located in" + b.getMethod().getName());

        String fieldSignature = getSignatureForField(f.getField());

        // Retrieve the object it belongs to
        Local tmpLocal = (Local) f.getBase();

        // push and pop security level of instance to globalPC
        Unit pushInstanceLevelToGlobalPC = fac.createStmt("pushInstanceLevelToGlobalPC", StringConstant.v(getSignatureForLocal(tmpLocal)));

        Unit popGlobalPC = fac.createStmt("popGlobalPC");


        // insert: checkGlobalPC(Object, String)
        // why do we check the global PC? Because the field is possibly visible everywhere, check that sec-value of field is greater
        // or equal than the global PC.
        Unit checkGlobalPCExpr = fac.createStmt("checkGlobalPC", tmpLocal, StringConstant.v(fieldSignature));

        // insert setLevelOfField, which sets Level of Field to the join of gPC
        // and right-hand side of assign stmt sec-value join
        Unit assignExpr = fac.createStmt( "setLevelOfField", tmpLocal, StringConstant.v(fieldSignature));

        // pushInstanceLevelToGlobalPC and popGlobalPC take the instance, push to global pc; and pop afterwards.
        // see NSU_FieldAccess tests why this is needed
        units.insertBefore(pushInstanceLevelToGlobalPC, pos);

        if(ctxCastCalledFlag){
            units.insertBefore(checkGlobalPCExpr, pos);
        }
        else {
            // only if context ist dynamic / pc is dynamc
            if (cxTyping.get(instantiation, (Stmt) pos).isDynamic()) {
                units.insertBefore(checkGlobalPCExpr, pos);
            }
        }

        units.insertBefore(Arrays.asList(assignExpr, popGlobalPC), pos);
        lastPos = pos;
    }


    public static void setLevelOfAssignStmt(StaticFieldRef f, Unit pos) {
        if(staticCtxCast)
            return;

        logger.info("Set Level of static Field " + f.toString() + " in assign stmt");

        SootField field = f.getField();

        String signature = getSignatureForField(field);
        SootClass sc = field.getDeclaringClass();


        // insert: checkGlobalPC(Object, String)
        Unit checkGlobalPCExpr = fac.createStmt("checkGlobalPC",
                ClassConstant.v(sc.getName().replace(".", "/")),
                StringConstant.v(signature)
        );

        // Add setLevelOfField
        Unit assignExpr = fac.createStmt("setLevelOfField",
                ClassConstant.v(sc.getName().replace(".", "/")),
                StringConstant.v(signature)
        );

        if(ctxCastCalledFlag){
            units.insertBefore(checkGlobalPCExpr, pos);
        }
        else {
            if (cxTyping.get(instantiation, (Stmt) pos).isDynamic()) {
                units.insertBefore(checkGlobalPCExpr, pos);
            }
        }
        units.insertBefore(assignExpr, pos);
        lastPos = pos;
    }

    /**
     * Inject a method of HandleStmt to set the level of an array-field. This method
     * distinguishes two cases, one case where the index of the referenced array-field
     * is a constant number and the other case, where the index is stored in a local variable.
     * In the second case, the signature of the local variable also must be passed as an
     * argument to {@link analyzer.level2.HandleStmt#setLevelOfArrayField} .
     *
     * @param a   -ArrayRef. The reference to the array-field
     * @param pos -Unit- The assignStmt in the analyzed methodTypings body, where this
     *            reference appears.
     */
    public static void setLevelOfAssignStmt(ArrayRef a, Unit pos) {
        if(staticCtxCast)
            return;

        logger.info("Set level of array " + a.toString() + " in assign stmt");

        String signatureForField = getSignatureForArrayField(a);
        String signatureForObjectLocal = getSignatureForLocal((Local) a.getBase());

        // List for the arguments for HandleStmt.setLevelOfArrayField()
        List<Value> args = new ArrayList<>();
        args.add(a.getBase());

        // Store all string-arguments in locals for strings and assign the locals to the
        // argument list.




        args.add(StringConstant.v(signatureForField));
        args.add(StringConstant.v(signatureForObjectLocal));


        if (!(a.getIndex() instanceof Local)) {
            // Case where the index is a constant.
            // The needed arguments are "Object o, String field, String localForObject".

            logger.fine("Index value for the array field is a constant value");

        } else if (a.getIndex() instanceof Local) {
            // The index is a local and must be given as a parameter.
            // The needed arguments are
            // "Object o, String field, String localForObject, String localForIndex".

            logger.fine("Index value for the array field is stored in a local");
            // add a further parameter type for String localForIndex and
            // add it to the arguments-list.
            String localSignature = getSignatureForLocal((Local) a.getIndex());
            args.add(StringConstant.v(localSignature));
        }

        // checkArrayWithGlobalPC
        Unit checkArrayGlobalPCExpr = fac.createStmt("checkArrayWithGlobalPC", args.toArray(new Value[0]));

        // setLevelOfArrayField
        Unit assignExpr = fac.createStmt("setLevelOfArrayField", args.toArray(new Value[0]));

        units.insertBefore(Arrays.asList(checkArrayGlobalPCExpr, assignExpr), pos);
        lastPos = pos;
    }

    // </editor-fold>

    // <editor-fold desc="Assign Stmt - Method -> Interesting for Identity Stmt">

    /**
     * Note: Although method is not used by JimpleInjector, the corresponding handleStatement method is used in the manually instrumented tests.
     */
    @SuppressWarnings("unused")
    public static void assignReturnLevelToLocal(Local l, Unit pos) {
        logger.info("Assign return level of invoked method to local "+getSignatureForLocal(l));

        Unit assignExpr = fac.createStmt("assignReturnLevelToLocal", StringConstant.v(getSignatureForLocal(l)));
        units.insertAfter(assignExpr, pos);
        lastPos = assignExpr;
    }

    public static void assignArgumentToLocal(int posInArgList, Local local, String methodName, Unit pos) {
        afterAssignArgumentToLocal = true;

        if(!prevMethodName.isEmpty() && !prevMethodName.equals(methodName)) {
            signatureList = new ArrayList<String>();
            noTrackSignatureList = new ArrayList<String>();
            //
            argumentPosition = 0;
        }
        /*if(null != prevUnit && !prevUnit.equals(pos))
            argumentPosition = 0;*/

        List<HashMap<Integer, Boolean>> argList = BodyAnalyzer.argumentMap.get(methodName);
        if(null != argList && !argList.isEmpty()) {
            HashMap<Integer, Boolean> argMap = argList.get(posInArgList);
            if (argMap.get(posInArgList)) {
                logger.info("Assign argument level to local " + local);
                Unit assignExpr = fac.createStmt("assignArgumentToLocal", IntConstant.v(argumentPosition), StringConstant.v(getSignatureForLocal(local)));
                argumentPosition += 1;
                units.insertAfter(assignExpr, lastPos);
                lastPos = assignExpr;
                signatureList.add(getSignatureForLocal(local));
            } else {
                noTrackSignatureList.add(getSignatureForLocal(local));
            }
        }

        prevMethodName = methodName;
        prevUnit = pos;
    }


    //</editor-fold>

    // <editor-fold desc="Return Invokes">
    /**
     * Inserts an invoke of {@link HandleStmt#returnConstant()}
     * @param retStmt The invoke is inserted before the retStmt.
     */
    public static void returnConstant(Unit retStmt) {
        logger.info("Return a constant value");

        if (instantiation.getReturn().isDynamic()) {
            units.insertBefore(fac.createStmt("returnConstant"), retStmt);
        }
    }

    public static void returnLocal(Local l, Unit pos) {
        if(dontSetLocalFlag)
            return;

        logger.info("Return Local "+ getSignatureForLocal(l));

        Stmt returnL = fac.createStmt("returnLocal", StringConstant.v(getSignatureForLocal(l)));

        if (instantiation.getReturn().isDynamic()) {
            units.insertBefore(returnL, pos);
            lastPos = pos;
        }
    }


    /**
     * Store the levels of all arguments in a list in ObjectMap. If an
     * argument is a constant, then the argument is stored as "DEFAULT_LOW".
     *
     * @param pos        position of actual statement
     * @param lArguments list of arguments
     */
    public static void storeArgumentLevels(Unit pos, Local... lArguments) {
        logger.info("Store Arguments for next method in method " + b.getMethod().getName());
        for (int i = 0; i < lArguments.length; i++) {
            String signature = "";
            if (lArguments[i] != null) {
                InvokeExpr is = null;
                if(pos instanceof JAssignStmt)
                    is = (InvokeExpr) (((JAssignStmt) pos).getRightOp());
                else if(pos instanceof JInvokeStmt)
                    is = ((JInvokeStmt) pos).getInvokeExpr();
                if(null != is) {
                    String mn = is.getMethod().getName();
                    List<String> independentVars = DefinedByValueOfCall.independentVarsMap.get(mn);
                    if (independentVars.contains(lArguments[i].toString()))
                        continue;
                }
                signature = getSignatureForLocal(lArguments[i]);
                Unit invoke = fac.createStmt("storeArgumentLevel", StringConstant.v(signature));
                units.insertBefore(invoke, pos);
                noTrackSignatureList.remove(signature);
            }
            lastPos = pos;
        }
    }


    public static void checkThatLe(Local l, String level, Unit pos) {
        checkThatLe(l, level, pos, "checkThatLe");
    }

    /**
     * Insert the following check: If Local l is high, throw new IFCError
     */
    public static void checkThatLe(Local l, String level, Unit pos, String methodName) {
        if(staticCtxCast)
            return;

        logger.info("Check that " + l + " is not high");

        if (l == null) {
            throw new InternalAnalyzerException("Argument is null");
        }

        ArrayList<Type> paramTypes = new ArrayList<>();
        paramTypes.add(RefType.v("java.lang.String"));
        paramTypes.add(RefType.v("java.lang.String"));

        String signature = getSignatureForLocal(l);

        Expr invokeSetLevel = Jimple.v().newVirtualInvokeExpr(
                hs, Scene.v().makeMethodRef(Scene.v().getSootClass(HANDLE_CLASS),
                        methodName, paramTypes, VoidType.v(), false),
                StringConstant.v(signature), StringConstant.v(level));
        Unit invoke = Jimple.v().newInvokeStmt(invokeSetLevel);

        // TODO: why check for isDynamic here?
            units.insertBefore(invoke, pos);
            lastPos = pos;
    }

    /**
     * Method to check that PC is not of specified level, or above. Called when calling System.out.println(),
     * which must always be called in low context because of its side effects.
     *
     * @param level level that the PC must not exceed
     * @param pos   position where to insert statement
     */
    public static void checkThatPCLe(String level, Unit pos, boolean ctxCastFlag) {
        logger.info("Check that context is " + level + " or above");

        if (pos == null) {
            throw new InternalAnalyzerException("Position is Null");
        }

        ArrayList<Type> paramTypes = new ArrayList<>();
        paramTypes.add(RefType.v("java.lang.String"));

        Expr checkPC = Jimple.v().newVirtualInvokeExpr(
                hs, Scene.v().makeMethodRef(Scene.v().getSootClass(HANDLE_CLASS),
                        "checkThatPCLe", paramTypes, VoidType.v(), false), StringConstant.v(level));
        Unit invoke = Jimple.v().newInvokeStmt(checkPC);

        if(ctxCastFlag || ctxCastCalledFlag){
            units.insertBefore(invoke, pos);
            lastPos = pos;
        }
        else
        {
            // only if PC is dynamic
            if (cxTyping.get(instantiation, (Stmt) pos).isDynamic()) {
                units.insertBefore(invoke, pos);
                lastPos = pos;
            }
        }

    }


    /**
     * Check condition of if statements. Needed parameters are all locals (no constants)
     * which occur in the if statement. If the result is high, then the lpc of the if-context
     * is set to high.
     *
     * @param pos    Position of the ifStmt in the method body.
     * @param locals An array of all locals which appear in the condition.
     */
    public static void checkCondition(Unit pos, Local... locals) {
        if(staticCtxCast)
            return;

        logger.info("Check condition in method " + b.getMethod()+ " IfStmt: " + pos);

        int numberOfLocals = locals.length;
        // Add hashvalue for immediate dominator
        String domIdentity = DominatorFinder.getImmediateDominatorIdentity(pos);
        logger.info("Identity of Dominator of \"" + pos.toString() + "\" is " + domIdentity);

        if(numberOfLocals < 1)
            throw new InternalAnalyzerException("Argument is null");

        if(!loopFlag) {
            if (count != 1) {
                callJoinLevel(numberOfLocals, false, locals);
                Unit invoke = fac.createStmt("checkCondition", StringConstant.v(domIdentity));
                checkConditionCalledFlag = true;
                count += 1;
                units.insertAfter(invoke, lastPos);
                lastPos = invoke;
            } else {
                callJoinLevel(numberOfLocals, true, locals);
                Unit invokeUpdate = fac.createStmt("updateCondition", StringConstant.v(domIdentity));
                units.insertAfter(invokeUpdate, lastPos);
                lastPos = invokeUpdate;
            }
        }
        else {
            if(!lastPos.toString().contains("exitInnerScope")) {
                callJoinLevel(numberOfLocals, false, locals);
                Unit invoke = fac.createStmt("checkCondition", StringConstant.v(domIdentity));
                units.insertAfter(invoke, lastPos);
            }

            callJoinLevel(numberOfLocals, true, locals);

            Unit invokeUpdate = fac.createStmt("updateCondition", StringConstant.v(domIdentity));
            units.insertAfter(invokeUpdate, lastPos);
            lastPos = invokeUpdate;
        }
    }

    private static void callJoinLevel(int numberOfLocals, Boolean beforeFlag, Local... locals){
        Unit invokeJoin = null;
        for (int i = 0; i < numberOfLocals; i++) {
            String signature = getSignatureForLocal(locals[i]);
            invokeJoin = fac.createStmt("joinLevelOfLocalAndAssignmentLevel", StringConstant.v(signature));
            if(!beforeFlag)
                units.insertAfter(invokeJoin, lastPos);
            else
                units.insertBefore(invokeJoin, conditionPos);
        }
        lastPos = invokeJoin;
    }

    /**
     * If a stmt is a postdominator of an ifStmt then the if-context ends before this stmt.
     * The method exitInnerScope pops the localPCs for all ifStmts which end here.
     *
     * @param pos The position of this stmt.
     */
    public static void exitInnerScope(Unit pos) {
        logger.info("Exit inner scope in method " + b.getMethod().getName());

        ArrayList<Type> paramTypes = new ArrayList<>();
        paramTypes.add(RefType.v("java.lang.String"));

        String domIdentity = DominatorFinder.getIdentityForUnit(pos);
        logger.info("Dominator \"" + pos.toString()
                + "\" has identity " + domIdentity);

        Expr specialIn = Jimple.v().newVirtualInvokeExpr(
                hs, Scene.v().makeMethodRef(Scene.v().getSootClass(HANDLE_CLASS),
                        "exitInnerScope", paramTypes, VoidType.v(), false),
                StringConstant.v(domIdentity));

        Unit inv = Jimple.v().newInvokeStmt(specialIn);

        units.insertBefore(inv, pos);
        //lastPos = pos;
        lastPos = inv;
    }


    public static void exitCtxCastScope(Unit pos) {
        logger.info("Exit context cast scope in method " + b.getMethod().getName());

        staticCtxCast = false;

        ArrayList<Type> paramTypes = new ArrayList<>();
        paramTypes.add(RefType.v("java.lang.String"));

        if(!stack.isEmpty()) {
            String domIdentity = stack.pop().toString();
            logger.info("Dominator \"" + pos.toString() + "\" has identity " + domIdentity);

            Expr specialIn = Jimple.v().newVirtualInvokeExpr(
                    hs, Scene.v().makeMethodRef(Scene.v().getSootClass(HANDLE_CLASS),
                            "exitInnerScope", paramTypes, VoidType.v(), false),
                    StringConstant.v(domIdentity));

            Unit inv = Jimple.v().newInvokeStmt(specialIn);

            units.insertBefore(inv, pos);
            lastPos = pos;
        }
    }

    /*
     * Internal methodTypings
     */

    /**
     * Assures that a "@this" reference is present
     * @throws InternalAnalyzerException if not present.
     */
    private static void assureThisRef() {
        Unit first = units.getFirst();
        Value obj = first.getUseBoxes().get(0).getValue();
        // Check if the first unit is a reference to the actual object
        if (!( first instanceof IdentityStmt ) || !( obj instanceof ThisRef )) {
            throw new InternalAnalyzerException("Expected @this reference");
        }
    }

    /**
     *
     */
    static void addUnitsToChain() {
        b.validate();
    }

    /**
     * Add all locals which are needed from JimpleInjector to store values
     * of parameters for invoked methodTypings.
     */
    // Todo: Remove, when ready
    /*static void addNeededLocals() {
        locals.add(local_for_String_Arrays);

        b.validate();
    }*/

    // <editor-fold desc="Signature Calculation Methods">

    /**
     * Calculates and returns the Signature of a given Local
     * @param l the local which signature is to be retrieved
     * @return corresponding signature
     * @see Local#getType()
     * @see Local#getName()
     */
    private static String getSignatureForLocal(Local l) {
        return l.getType() + "_" + l.getName();
    }

    /**
     * Calculates and returns the Signature of given SootField
     * @param f The Field of which the signature is required.
     * @return The signature of the SootField
     * @see SootField#getSignature()
     */
    private static String getSignatureForField(SootField f) {
        return f.getSignature();
    }

    /**
     * Creates the signature of an array-field based on the index.
     * It simply returns the int-value as string.
     *
     * @param a The ArrayRef of which the Signature is required.
     * @return The signature for the array-field.
     * @throws InternalAnalyzerException if the index type is not int.
     */
    private static String getSignatureForArrayField(ArrayRef a) {
        if (!Objects.equals(a.getIndex().getType().toString(), "int")) {
            throw new InternalAnalyzerException("Unexpected type of index");
        }
        return a.getIndex().toString();
    }

    // </editor-fold>

    // <editor-fold desc="Jimple Helper Methods">

    /**
     * Calculates the start position for inserting further units.
     * @param b The Body, that may contain Units.
     * @return the start position of the first Unit.
     */
    private static int getStartPos(Body b) {
        // Getting the Method, that is currently calculated.
        SootMethod m = b.getMethod();

        /* Depending on the number of arguments and whether it is a static method
         * or a Constructor the position is calculated.
         * Because there are statements, which shouldn't be preceded by other
         * statements.
         * */

        int startPos = (!m.isStatic()) ? 1 : 0;
        startPos = (m.isConstructor()) ? startPos + 1 : startPos;
        startPos += m.getParameterCount();

        logger.fine("Calculated start position: " + startPos + " of " + b.getMethod().getName());
        return startPos;
    }

    /**
     * Gets the Unit, that is stored in the given units at the given Position.
     * @param units The Chain of Units, that contains severall Units
     * @param pos The position of the Unit, that is wanted to be extracted.
     * @return The unit at the given position.
     * @throws IndexOutOfBoundsException if the Position is greater the number of
     * units in the given Unit chain or lower 0.
     */
    private static Unit getUnitOf(Chain<Unit> units, int pos) {
        if (pos < 0 || pos >= units.size())
            throw new IndexOutOfBoundsException("No legal index: "+pos);
        int idx = 0;
        for (Unit u : units) {
            if (idx == pos) return u;
            idx++;
        }
        return null;
    }

    // </editor-fold>

    /**
     * This method is only for debugging purposes.
     */
    @SuppressWarnings("unused")
    private static void printUnits() {
        Iterator<Unit> uIt = units.iterator();
        int i = 0;
        System.out.println("Actual method: " + b.getMethod().toString());
        while (uIt.hasNext()) {
            System.out.println(i + " " + uIt.next().toString());
            i++;
        }
    }

    static <Level> void setStaticAnalaysisResults(VarTyping<Level> varTy, CxTyping<Level> cxTy, Instantiation<Level> inst,
                                                  Casts c) {
        varTyping = varTy;
        cxTyping = cxTy;
        instantiation = inst;
        casts = c;
    }



    /**
     * Handle Casts.cast(String s, T local) method
     * @param aStmt         Jimple assign statement whose right-hand side is the cast
     */
    public static void handleCast(AssignStmt aStmt) {

        if (casts.isValueCast(aStmt)) {
            Casts.ValueConversion conversion = casts.getValueCast(aStmt);
            logger.fine("Found value cast: " + conversion);

            if (conversion.getSrcType().isDynamic() && !conversion.getDestType().isDynamic()) {
                // Check eines Security Wert: x = (? => LOW) y
                logger.fine("Convertion is: dynamic->static");
                Option<Value> srcValue = conversion.getSrcValue();
                if (srcValue.isDefined()) {
                    Local rightHandLocal = (Local) srcValue.get();

                    Object destLevel = conversion.getDestType().getLevel();
                    logger.fine( "Inserting check: "
                            + getSignatureForLocal(rightHandLocal)
                            + " <= "
                            + destLevel);
                    checkThatLe(rightHandLocal, destLevel.toString(), aStmt, "checkCastToStatic");

                    logger.fine("Setting destination variable to: " + destLevel);
                    staticDestination = true;
                    //makeLocal((Local) aStmt.getLeftOp(), destLevel.toString(), aStmt);
                } else {
                    logger.info("Source value is pubilc. Not inserting checks.");
                }
            } else if ( !conversion.getSrcType().isDynamic() && conversion.getDestType().isDynamic()) {
                // Initialisierung eines Security Wert: x = (H => ? ) y
                logger.fine("Conversion is: static->dynamic");
                Object srcLevel;
                de.unifreiburg.cs.proglang.jgs.instrumentation.Type type = conversion.getSrcType();
                if (type.isPublic()) {
                    srcLevel = CurrentSecurityDomain.bottom();
                } else {
                    srcLevel = type.getLevel();
                }
                logger.fine("Setting destination variable to: " + srcLevel);
                makeLocal((Local) aStmt.getLeftOp(), srcLevel.toString(), aStmt);
                staticDestination = false;
            } else if ( conversion.getSrcType().isDynamic() && conversion.getDestType().isDynamic()) {
                logger.fine("Conversion is: dynamic->dynamic");
                logger.fine("Ignoring trivial conversion.");
                // TODO: casts should be run on the rhs and set the "assignment level" here.
            } else {
                logger.fine("Conversion is: static->static");
                logger.fine("Ignoring trivial conversion.");
                // TODO: casts should be run on the rhs and set the "assignment level" here.
                // TODO: if for some reason the type analysis is not available, we should check that the conversion is correct here
            }
        }
    }

    public static void handleCtxCast(Stmt stmt, Local[] args){
        if(casts.isCxCastStart(stmt)){
            Casts.Conversion conversion = casts.getCxCast(stmt);
            logger.fine("Found context cast: " + conversion);
            ctxCastCalledFlag = true;
            if (conversion.getSrcType().isDynamic() && !conversion.getDestType().isDynamic()) {
                logger.fine("Conversion is: dynamic->static");
                Object destLevel = conversion.getDestType().getLevel();
                checkThatPCLe(destLevel.toString(), stmt, true);
                logger.fine("Setting destination variable to: " + destLevel);
                staticCtxCast = true;
            }
            else if ( !conversion.getSrcType().isDynamic() && conversion.getDestType().isDynamic()) {
                logger.fine("Conversion is: static->dynamic");
                Object srcLevel = conversion.getSrcType().getLevel();
                ctxCastStToDyn(stmt, srcLevel.toString());
            }

            else if ( conversion.getSrcType().isDynamic() && conversion.getDestType().isDynamic()) {
                logger.fine("Conversion is: dynamic->dynamic");
                logger.fine("Ignoring trivial conversion.");
            } else {
                logger.fine("Conversion is: static->static");
                logger.fine("Ignoring trivial conversion.");
            }
        }
    }


    public static void ctxCastStToDyn(Unit pos, String srcLevel){
        ArrayList<Type> paramTypes = new ArrayList<>();
        paramTypes.add(RefType.v("java.lang.String"));
        paramTypes.add(ArrayType.v(RefType.v("java.lang.String"), 1));

        Random randomNumber = new Random();
        String domIdentity = String.valueOf(randomNumber.nextInt());
        stack.push(domIdentity);

        Unit invoke = fac.createStmt("ctxCastStToDyn", StringConstant.v(domIdentity), StringConstant.v(srcLevel));

        units.insertAfter(invoke, pos);
        lastPos = invoke;
    }

    /**
     * Insert "stopTrackingLocal" call.
     */
    public static void stopTrackingLocal(Local l, Stmt callStmt) {
        units.insertBefore(fac.createStmt("stopTrackingLocal", StringConstant.v(getSignatureForLocal(l))), callStmt);
    }
}
