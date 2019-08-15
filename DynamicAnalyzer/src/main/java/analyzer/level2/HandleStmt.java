package analyzer.level2;

import analyzer.level2.storage.LPCDominatorPair;
import analyzer.level2.storage.LocalMap;
import analyzer.level2.storage.ObjectMap;
import util.exceptions.IllegalFlowError;
import util.exceptions.InternalAnalyzerException;
import util.exceptions.NSUError;
import util.logging.L2Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static analyzer.level2.HandleStmtUtils.NSU_ERROR_MESSAGE;

/**
 * MethodTypings for JGS' run-time enforcement. This class makes up the main
 * part of the run-time system.
 *
 * @author Regina Koenig (2015)
 */
@SuppressWarnings("ALL")
public class HandleStmt {

    /**
     * Logger used by the run-time enforcement. Verbose message are available by
     * setting the environment variable JGS_VERBOSE_LOGGING=1.
     */
    private static final Logger logger = L2Logger.getLogger();

    private LocalMap localMap;
    private static ObjectMap objectMap;
    private HandleStmtUtils handleStatementUtils;
    PassivController controller;

    /**
     * This must be called at the beginning of every method in the analyzed
     * code. It creates a new LocalMap for the method and adjusts the
     * {@link CurrentSecurityDomain} of the globalPC
     */
    public HandleStmt() {
        localMap = new LocalMap();
        objectMap = ObjectMap.getInstance();
        logger.setLevel(Level.ALL);
    }

    @SuppressWarnings("unused")
    /**
     * Initialise the HandleStmtUtils. Use also to specify if, and what kind
     * of exception we expect
     */
    public void initHandleStmtUtils() {
        handleStatementUtils = new HandleStmtUtils(localMap, objectMap);
        objectMap.pushGlobalPC(handleStatementUtils.joinLevels(objectMap.getGlobalPC(), localMap.getLocalPC()));
    }

    /**
     * This method must be called just once at the beginning of the main
     * method.
     * It triggers the setup of the logger.
     */
    public static void init() {
        try {
            L2Logger.setup();
        } catch (IOException e) {
            logger.warning("Set up of logger failed");
            e.printStackTrace();
        }
        if (objectMap == null) {
            objectMap = ObjectMap.getInstance();
        }
        objectMap.flush();
        objectMap.clearAssignmentLevel();
    }

    /**
     * This must be called at the end of every method in the analyzed code. It
     * resets the globalPC to its initial value
     */
    public void close() {
        logger.log(Level.INFO, "Close HandleStatement");
        objectMap.popGlobalPC();
        localMap.checkisLPCStackEmpty();
    }

    /**
     * Set the return level.
     *
     * @param securityLevel The securitylevel of actual return-statement.
     * @return The new security-level.
     */
    protected Object setActualReturnLevel(Object securityLevel) {
        return objectMap.setActualReturnLevel(securityLevel);
    }

    /**
     * Get the actual return level. The level is not changed in this operation.
     *
     * @return The securitylevel of the last return-statement.
     */
    protected Object getActualReturnLevel() {
        return objectMap.getActualReturnLevel();
    }

    /**
     * Add an object to the ObjectMap.
     *
     * @param object object
     */
    public void addObjectToObjectMap(Object object) {
        logger.log(Level.INFO, "Insert Object {0} to ObjectMap", object);
        objectMap.insertNewObject(object);
        if (!objectMap.containsObject(object)) {
            throw new InternalAnalyzerException("Add object " + object + " to ObjectMap failed.");
        }
    }

    /**
     * Add a field of an object to ObjectMap.
     *
     * @param object    an object
     * @param signature signature of the field of the object
     * @return SecurityLevel of the newly set field
     */
    public Object addFieldToObjectMap(Object object, String signature) {
        logger.log(Level.INFO, "Add Field {0} to object {1}", new Object[]{signature, object});
        handleStatementUtils.checkIfObjectExists(object);
        Object fieldLevel = objectMap.setField(object, signature, CurrentSecurityDomain.bottom());
        if (!objectMap.containsField(object, signature)) {
            throw new InternalAnalyzerException("Add field " + signature + " to ObjectMap failed");
        }
        return fieldLevel;
    }

    /**
     * Add an array to ObjectMap.
     *
     * @param array array
     */
    public void addArrayToObjectMap(Object[] array) {
        logger.log(Level.INFO, "Add Array {0} to ObjectMap", array.toString());
        logger.info("Array length " + array.length);

        addObjectToObjectMap(array);
        for (int i = 0; i < array.length; i++) {
            addFieldToObjectMap(array, Integer.toString(i));
        }

        if (!containsObjectInObjectMap(array)) {
            throw new InternalAnalyzerException("Add Object " + array
                    + " to ObjectMap failed");
        }
    }

    /**
     * Check if an object is contained in ObjectMap.
     *
     * @param object object
     * @return true, if object is found in ObjectMap
     */
    protected boolean containsObjectInObjectMap(Object object) {
        return objectMap.containsObject(object);
    }

    /**
     * Check if a field is contained in ObjectMap.
     *
     * @param object    object of the field
     * @param signature signature of the field
     * @return true, if field is found in ObjectMap
     */
    protected boolean containsFieldInObjectMap(Object object, String
            signature) {
        handleStatementUtils.checkIfObjectExists(object);
        return objectMap.containsField(object, signature);
    }

    /**
     * Get the number of elements in ObjectMap.
     *
     * @return number of elements
     */
    protected int getNumberOfElementsInObjectMap() {
        return objectMap.getNumberOfElements();
    }

    /**
     * Get the number of fields for an object in ObjectMap.
     *
     * @param bject object
     * @return number of fields for given object
     */
    protected int getNumberOfFieldsInObjectMap(Object bject) {
        handleStatementUtils.checkIfObjectExists(bject);
        return objectMap.getNumberOfFields(bject);
    }

    /**
     * Get the SecurityLevel for a field in ObjectMap.
     *
     * @param object    object of the field
     * @param signature signature of the field
     * @return SecurityLevel
     */
    protected Object getFieldLevel(Object object, String signature) {
        handleStatementUtils.checkIfObjectExists(object);
        return objectMap.getFieldLevel(object, signature);
    }

    /**
     * Set the level of a field to SecurityLevel.top().
     *
     * @param object    object containing the field
     * @param signature signature of the field
     */
    public void makeFieldHigh(Object object, String signature) {
        logger.log(Level.INFO, "Set SecurityLevel of field {0} to HIGH",
                signature);
        handleStatementUtils.checkIfObjectExists(object);
        objectMap.setField(object, signature, CurrentSecurityDomain.top());
    }

    /**
     * Set the level of a field to LOW.
     *
     * @param object    object containing the field
     * @param signature signature of the field
     */
    public void makeFieldLow(Object object, String signature) {
        logger.log(Level.INFO, "Set SecurityLevel of field {0} to LOW",
                signature);
        handleStatementUtils.checkIfObjectExists(object);
        objectMap.setField(object, signature, CurrentSecurityDomain.bottom());
    }

    /**
     * Add a local to LocalMap.
     *
     * @param signature signature of the local
     * @param level     SecurityLevel for the new local
     */
    public void addLocal(String signature, Object level) {
        logger.log(Level.INFO, "Insert Local {0} with Level {1} to LocalMap",
                new Object[]{signature, level});
        handleStatementUtils.checkThatLocalDoesNotExist(signature);
        localMap.setLevel(signature, level);
    }

    /**
     * Add an uninitialized local to LocalMap with default SecurityLevel LOW.
     * Used for declaration, e.g: "int i;"
     *
     * An uninitialized local is an untracked local, so this method should
     * not be used at all.
     *
     * @param signature signature of the local
     */
    @Deprecated
    public void addLocal(String signature) {
        logger.log(Level.INFO,
                "Add Local {0} with SecurityLevel.bottom() to LocalMap",
                signature);
        handleStatementUtils.checkThatLocalDoesNotExist(signature);
    }

    /**
     * Get the SecurityLevel of a local.
     *
     * @param signature signature of the local
     * @return SecurityLevel
     */
    protected Object getLocalLevel(String signature) {
        return localMap.getLevel(signature);
    }

    public void setLocalFromString(String signature, String level) {
        logger.info("Set level of local " + signature + " to " + level);
        localMap.setLevel(signature, CurrentSecurityDomain.readLevel(level));
    }


//
//
//	/**
//	 * Set SecurityLevel of given local to HIGH. This one is
//	 * actually in use (by the AnnotationSwitchStmt)
//	 *
//	 * @param signature
//	 *            signature of local
//	 */
//	public void makeLocalHigh(String signature) {
//		logger.info("Set level of local " + signature
//				+ " to SecurityLevel.top()");
//		localMap.initializeLocal(signature);
//		localMap.setLevel(signature, SecurityLevel.top());
//		logger.info("New securitylevel of local " + signature + " is "
//				+ localMap.getLevel(signature));
//	}
//
//	/**
//	 * Set SecurityLevel of given local to LOW. This one is
//	 * actually in use (by the AnnotationSwitchStmt).
//	 * Same as above probably.
//	 *
//	 * @param signature
//	 *            signature of local
//	 */
//	public void makeLocalLow(String signature) {
//		logger.info("Set level of " + signature + " to SecurityLevel.bottom
// ()");
//		localMap.setLevel(signature, SecurityLevel.bottom());
//		logger.info("New securitylevel: " + localMap.getLevel(signature));
//	}

    /**
     * Push the level of a given instance to the globalPC (e.g. on top of its
     * stack)
     *
     * @param localSignature singature of local to be pushed onto the stack
     */
    public void pushInstanceLevelToGlobalPC(String localSignature) {
        // get instance level of localSignature, push to globalPC (which calcs
        // the max of all its stack elements)

        Object secLevel = getLocalLevel(localSignature);
        pushGlobalPC(handleStatementUtils.joinWithGPC(secLevel));

    }

    // make PopGlobalPC public

    /**
     * Push a new localPC and the indentity for its corresponding postdominator
     * unit to the LPCList.
     *
     * @param securityLevel     The new securitylevel.
     * @param dominatorIdentity The identity of the postdominator.
     * @return The new localPC.
     */
    protected Object pushLocalPC(Object securityLevel, int dominatorIdentity) {
        localMap.pushLocalPC(securityLevel, dominatorIdentity);
        logger.info("New LPC: " + localMap.getLocalPC().toString());
        return localMap.getLocalPC();
    }

    /**
     * Remove the first element of the localPC list. The indentity value is used
     * to check whether the first element belongs to the actual dominator.
     *
     * @param dominatorIdentity Hidentity for actual dominator.
     */
    protected void popLocalPC(int dominatorIdentity) {
        logger.info("Pop local pc.");
        localMap.popLocalPC(dominatorIdentity);
    }

    /**
     * Get actual localPC without removing it from the list.
     *
     * @return localPC
     */
    protected Object getLocalPC() {
        return localMap.getLocalPC();
    }

    /**
     * Add a globalPC to GPC-stack.
     *
     * @param securityLevel SecurityLevel of GPC.
     * @return new SecurityLevel
     */
    protected Object pushGlobalPC(Object securityLevel) {
        logger.log(Level.INFO, "Set globalPC to {0}", securityLevel);
        objectMap.pushGlobalPC(securityLevel);
        return objectMap.getGlobalPC();
    }

    /**
     * Get first element of globalPC stack without removing it.
     *
     * @return SecurityLevel
     */
    protected Object getGlobalPC() {
        return objectMap.getGlobalPC();
    }

    /**
     * Remove the first element of globalPC-stack.
     *
     * @return SecurityLevel of last GPC
     */
    public Object popGlobalPC() {
        return objectMap.popGlobalPC();
    }

    /**
     * Assign argument at given position to local.
     *
     * @param pos       position of argument
     * @param signature signature of local
     * @return new SecurityLevel of local
     */
    public Object assignArgumentToLocal(int pos, String signature) {

        // In case somebody wonders: we do not need to check the local pc
        // here. In Jimple, argument-to-local assignments (JIdentityStmt) are always
        // the beginning of the method, where the context is public
        if(!objectMap.getActualArguments().isEmpty()) {
            localMap.setLevel(signature, handleStatementUtils.joinWithLPC(objectMap.getArgLevelAt(0)));
            objectMap.getActualArguments().remove(0);
        }
        return localMap.getLevel(signature);
    }


    /**
     * Assign actual returnlevel to local. The returnlevel must be set again to
     * HIGH because the standard return level is SecurityLevel.top() for all
     * external methods.
     *
     * @param signature signature of local
     */
    public void assignReturnLevelToLocal(String signature) {
        Object returnLevel = objectMap.getActualReturnLevel();

        checkLocalPC(signature);
        setLocal(signature, returnLevel);
        objectMap.setActualReturnLevel(CurrentSecurityDomain.top());
    }

    /**
     * Set Returnlevel to SecurityLevel.bottom().
     */
    public void returnConstant() {
        logger.log(Level.INFO, "Return a constant value.");

        objectMap.setActualReturnLevel(handleStatementUtils
                .joinWithLPC
                        (CurrentSecurityDomain
                                .bottom
                                        ()));
        logger.info("Actual return level is: "
                + handleStatementUtils.joinWithLPC(CurrentSecurityDomain
                .bottom())
                .toString());
    }

    /**
     * Set returnlevel to the level of local.
     *
     * @param signature signature of local
     */
    public void returnLocal(String signature) {
        Object level = localMap.getLevel(signature);
        logger.log(Level.INFO, "Return Local {0} with level {1}", new Object[]{signature, level});
        objectMap.setActualReturnLevel(level);
    }

    /**
     * Store the levels of the arguments in a list in LocalMap.
     *
     * @param arguments List of arguments
     */
    public void storeArgumentLevel(String signature) {
        ArrayList<Object> levelArr = objectMap.getActualArguments();
        levelArr.add(localMap.getLevel(signature));
        objectMap.setActualArguments(levelArr);
    }

    /**
     * Check condition of an if-statement. This operation merges the security-
     * levels of all locals with the actual localPC and stores them together
     * with the identity value of the corresponding postdominator in the
     * localMap.
     *
     * @param dominatorIdentity identity of the postdominator.
     * @param args              List of signatore-string of all locals.
     */
    public void checkCondition(String dominatorIdentity) {
        logger.info("Check condition of ifStmt");
        localMap.pushLocalPC(handleStatementUtils.joinWithLPC(objectMap.getAssignmentLevel()), Integer.valueOf(dominatorIdentity));
        objectMap.pushGlobalPC(handleStatementUtils.joinWithGPC(localMap.getLocalPC()));
        logger.info("New LPC is " + localMap.getLocalPC().toString());
    }

    public void updateCondition(String dominatorIdentity) {
        logger.info("Update condition of ifStmt");
        LinkedList<LPCDominatorPair> localPC = new LinkedList<LPCDominatorPair>();
        localPC.push(new LPCDominatorPair(CurrentSecurityDomain.bottom(), -1));
        Object securityLevel = handleStatementUtils.joinWithLPC(objectMap.getAssignmentLevel());
        localPC.push(new LPCDominatorPair(securityLevel, Integer.valueOf(dominatorIdentity)));
        localMap.setLocalPC(localPC);
        LinkedList<Object> globalPC = new LinkedList<>();
        globalPC.push(handleStatementUtils.joinWithGPC(localMap.getLocalPC()));
        objectMap.setGlobalPC(globalPC);
        logger.info("New LPC is " + localMap.getLocalPC().toString());
    }

    public void ctxCastStToDyn(String dominatorIdentity, String level) {
        logger.info("level : " + level);
        localMap.pushLocalPC(handleStatementUtils.joinLocalLevel(level), Integer.valueOf(dominatorIdentity));
        objectMap.pushGlobalPC(handleStatementUtils.joinWithGPC(localMap.getLocalPC()));
        logger.info("New LPC is " + localMap.getLocalPC().toString());
    }



    /**
     * Exit scope of an if-Statement. For each if-statement which ends at this
     * position one lpc is popped from LPCstack. If the lpc belongs to this
     * position is checked with the identity of the position.
     *
     * @param dominatorIdentity identity of the dominator.
     */
    public void exitInnerScope(String dominatorIdentity) {
        while (localMap.dominatorIdentityEquals(Integer.valueOf(dominatorIdentity))) {
            logger.info("Pop LPC for identity " + dominatorIdentity);
            localMap.popLocalPC(Integer.valueOf(dominatorIdentity));
            objectMap.popGlobalPC();    // pop needs to be removed
        }
    }

    /**
     * Join the level of the local to the assignment-level. This possibly
     * increases, never decreases, the assignment-level.
     * <p>
     * This method is called when an assign happens.
     *
     * @param local signature of the local.
     * @return security-level of the local.
     */
    public Object joinLevelOfLocalAndAssignmentLevel(String local) {
        Object localLevel = localMap.getLevel(local);
        objectMap.setAssignmentLevel(handleStatementUtils.joinLevels(objectMap.getAssignmentLevel(), localLevel));
        logger.log(Level.INFO, "Set assignment-level to level " + objectMap.getAssignmentLevel() + " because of " + local);
        return objectMap.getAssignmentLevel();
    }

    /**
     * This method is for instance fields and for static fields. Join the level
     * of the field to the assignment-level.
     *
     * @param object The object of the field.
     * @param field  Signature of the field.
     * @return SecurityLevel of the field.
     */
    public Object joinLevelOfFieldAndAssignmentLevel(Object object, String field) {
        Object fieldLevel = objectMap.getFieldLevel(object, field);
        logger.log(Level.INFO, "Set assignment-level to level {0} (which is the level of " + "local {1})", new Object[]{fieldLevel, field});
        objectMap.setAssignmentLevel(handleStatementUtils.joinLevels(objectMap.getAssignmentLevel(), fieldLevel));
        return objectMap.getAssignmentLevel();
    }

    /**
     * Join the level of the field to the assignment-level.
     *
     * @param object The object of the field.
     * @param field  Signature of the field.
     * @return SecurityLevel of the field.
     */
    public Object joinLevelOfArrayFieldAndAssignmentLevel(Object object, String field) {
        Object fieldLevel = objectMap.getFieldLevel(object, field);
        logger.log(Level.INFO,  "Set assignment-level to level {0} (which is the level of " + "local {1})", new Object[]{fieldLevel, field});
        objectMap.setAssignmentLevel(handleStatementUtils.joinLevels(objectMap.getAssignmentLevel(), fieldLevel));
        return objectMap.getAssignmentLevel();
    }

    /**
     * Set the level of the local to given security level.
     *
     * @param signature Signature of the local.
     * @param level     security-level
     * @return The new security-level
     */
    public Object setLocal(String signature, Object securitylevel) {
        logger.log(Level.INFO, "Set level of local {0} to {1}", new Object[]{signature, securitylevel});
        localMap.setLevel(signature, securitylevel);
        return localMap.getLevel(signature);
    }

    /**
     * Patch to set the security value of a left-hand side, where the
     * statement is a virtualInvoke.
     *
     * @param signature
     */
    /*public void setReturnLevelAfterInvokeStmt(String signature) {

        Object leftHandSideSecValue = localMap.getLevel(signature);
        leftHandSideSecValue = handleStatementUtils.joinLevels(objectMap.getActualReturnLevel(), leftHandSideSecValue);
        setLocal(signature, leftHandSideSecValue);
    }*/

    public void setReturnLevelAfterInvokeStmt(String signature) {
        setLocal(signature, objectMap.getActualReturnLevel());
    }

    /**
     * Set the level of a local to default security-level. Called on every
     * assignment, and on initialization; but not on declaration.
     *
     * @param signature signature of the local
     * @return new security-level
     */
    public Object setLocalToCurrentAssignmentLevel(String signature) {
        // For assignments like a = x + y, we need to calculate the
        // new security-level of a: this sec-level depends either on
        // the local PC (for example, if inside a high-security if), or on either
        // of the right-hand variables' sec-levels, which is accumulated
        // in the assignmentLevel
        Object newSecValue = handleStatementUtils.joinWithLPC(objectMap.getAssignmentLevel());
        logger.log(Level.INFO, "Set level of local {0} to {1}", new Object[]{signature, newSecValue});
        localMap.setLevel(signature, newSecValue);
        logger.log(Level.INFO, "New level of local {0} is {1} ", new Object[]{signature, localMap.getLevel(signature)});
        objectMap.clearAssignmentLevel();
        return localMap.getLevel(signature);
    }

    /**
     * This method is for instance-fields and for static-fields.
     *
     * @param object Object of the field.
     * @param field  signature of the field.
     * @return The security-level of the field.
     */
    public Object setLevelOfField(Object object, String field) {
        logger.log(Level.INFO, "Set level of field {0} to {1}", new Object[]{field, handleStatementUtils.joinWithGPC(objectMap.getAssignmentLevel())});
        objectMap.setField(object, field, handleStatementUtils.joinWithGPC(objectMap.getAssignmentLevel()));
        logger.log(Level.INFO, "New level of field {0} is {1}", new Object[]{field, objectMap.getFieldLevel(object, field)});
        objectMap.clearAssignmentLevel();
        return objectMap.getFieldLevel(object, field);
    }

    /**
     * Check the array-field and the local-level of the object against the gpc,
     * and read the level stored as assignment-level. This level - joined with
     * the gpc - is set as the new level for given array-field. This method is
     * needed if the index is a local and must be checked against the gpc.
     *
     * @param object         - Object - The array object
     * @param field          - String - the signature of the field (array
     *                       element)
     * @param localForObject - String - the signature of the local where the
     *                       object is stored
     * @param localForIndex  - String - the signature of the local where the
     *                       index is stored
     * @return Returns the new SecurityLevel of the array-element
     */
    public Object setLevelOfArrayField(Object object, String field,
                                       String localForObject, String
                                               localForIndex) {
        logger.log(
                Level.INFO,
                "Set level of array-field {0} to {1}",
                new Object[]{
                        field,
                        handleStatementUtils.joinWithGPC(objectMap
                                .getAssignmentLevel())});

        objectMap.setField(object, field, handleStatementUtils
                .joinWithGPC(objectMap.getAssignmentLevel()));
        logger.log(Level.INFO, "New level of array-field {0} is {1}",
                new Object[]{field, objectMap.getFieldLevel(object, field)
                });
        objectMap.clearAssignmentLevel();
        return objectMap.getFieldLevel(object, field);
    }

    /**
     * Check the array-field and the local-level of the object against the gpc,
     * and read the level stored as assignment-level. This level - joined with
     * the gpc - is set as the new level for given array-field. This method is
     * needed if the index is a constant and it is not needed to be checked
     * against the gpc.
     *
     * @param object         - Object - The array object
     * @param field          - String - the signature of the field (array
     *                       element)
     * @param localForObject - String - the signature of the local where the
     *                       object is stored
     * @return Returns the new SecurityLevel of the array-element
     */
    public Object setLevelOfArrayField(Object object, String field,
                                       String localForObject) {
        logger.log(
                Level.INFO,
                "Set level of array-field {0} to {1}",
                new Object[]{
                        field,
                        handleStatementUtils.joinWithGPC(objectMap
                                .getAssignmentLevel())});

        objectMap.setField(object, field, handleStatementUtils
                .joinWithGPC(objectMap.getAssignmentLevel()));
        logger.log(Level.INFO, "New level of array-field {0} is {1}",
                new Object[]{field, objectMap.getFieldLevel(object, field)
                });
        objectMap.clearAssignmentLevel();
        return objectMap.getFieldLevel(object, field);
    }

    /**
     * Reference to handleStatementUtils.checkArrayWithGlobalPC
     *
     * @param object
     * @param signature
     * @param localForObject
     */
    public void checkArrayWithGlobalPC(Object object, String signature,
                                       String localForObject) {
        handleStatementUtils.checkArrayWithGlobalPC(object, signature,
                localForObject);
    }

    /**
     * Reference to handeStatementUtils method
     *
     * @param object
     * @param signature
     * @param localForObject
     * @param localForIndex
     */
    public void checkArrayWithGlobalPC(Object object, String signature,
                                       String localForObject, String
                                               localForIndex) {
        handleStatementUtils.checkArrayWithGlobalPC(object, signature,
                localForObject,
                localForIndex);
    }

    /**
     * Check if level of field is greater then global PC
     *
     * @param object
     * @param field
     */
    public void checkGlobalPC(Object object, String field) {
        Object fieldLevel = objectMap.getFieldLevel(object, field);
        Object globalPC = objectMap.getGlobalPC();

        if (!CurrentSecurityDomain.le(globalPC, fieldLevel)) {
            handleStatementUtils.abort(new NSUError(NSU_ERROR_MESSAGE + field));
        }
    }

    /**
     * Check if local > localPC
     *
     * NSU policy: For initialized locals, check if level of given local is greater
     * than localPC. If it's not, throw IFCError
     *
     * @param signature the local to test against the localPC
     */
    // TODO: checking the local pc is only a "partial" enforcement primitive, that is, it is never useful by itself. E.g. it is used in assignments and method returns. So, it should be packed together with the other actions needed for the "complete" enforcement primitive.
    // TODO: before fixing the issue above, check why returning from functions  and assignments are different cases.
    public void checkLocalPC(String signature) {
        logger.log(Level.INFO, "NSU check for local {0}", signature);
        if (localMap == null) {
            throw new InternalAnalyzerException("LocalMap is null");
        }
        //check if local is initialized
        if (!localMap.isTracked(signature)) {
            logger.log(Level.INFO, "Local {0} is not tracked; skipping NSU check", signature);
            return;
        }

        // the following check must only be executed if local is initialised
        Object level = localMap.getLevel(signature);
        Object lpc = localMap.getLocalPC();
        logger.log(Level.INFO, "Check if level of local {0} ({1}) >= lpc ({2}) --- checkLocalPC",
                new Object[] {signature, level, lpc });


        if (!CurrentSecurityDomain.le(lpc, level)) {
            handleStatementUtils.abort(new NSUError(NSU_ERROR_MESSAGE + signature));
        }
    }

    /**
     * Check if the current PC is public (i.e. bottom). This methods
     * functions as an NSU check for (non-dynamic) public variable which
     * don't require a cast.
     */
    public void checkNonSensitiveLocalPC() {
        logger.info("NSU check for updating public a variable");
        if (!CurrentSecurityDomain.le(localMap.getLocalPC(),
                CurrentSecurityDomain.bottom())) {
            handleStatementUtils.abort(new NSUError("Sensitive update to public variable"));
        }
    }

    /**
     * Check level of signature is less/equal than @param level
     *
     * @param signature signature of the local to test
     * @param level     level which mustn't be exceeded
     */
    // TODO: This method is called whenever a local variable is compared to a security level. This comparison happens in several cases: casts, passing of arguments, maybe more. These cases should be distinguished.
    // against remove in favor of more specific checks (casts, etc)
    public void checkThatLe(String signature, String level) {
        checkThatLe(signature, level,
                "Passed argument " + signature + " with level "
                        + localMap.getLevel(signature) + " to some method" +
                        " which requires a"
                        + " security level of less/equal " + level);
    }

    public void checkCastToStatic(String signature, String level) {
        checkThatLe(signature, level, "Illegal cast to static type " + level +
                " of " + signature + "("
                + localMap.getLevel(signature) + ")");
    }

    public void checkThatLe(String signature, String level, String msg) {
        logger.info("Check if " + signature + " <= " + level);

        if (!CurrentSecurityDomain.le(localMap.getLevel(signature),
                CurrentSecurityDomain.readLevel(level))) {
            handleStatementUtils.abort(new IllegalFlowError(msg));
        }
    }

    /**
     * This method is used if a print statement is identified. Print statements
     * require the PC to be on a less/equal a certain level (lessThan LOW for
     * system.println, lessThan MEDIUM for SecurePrinter.printMedium(String s);
     *
     * @param level level the PC must be less/equal than.
     */
    @SuppressWarnings("unused")
    public void checkThatPCLe(String level) {

        logger.info(
                "About to print something somewhere. Requires to check that "
                        + "PC is less than "
                        + level);

        if (!CurrentSecurityDomain.le(localMap.getLocalPC(),
                CurrentSecurityDomain.readLevel(level))) {
            handleStatementUtils.abort(new IllegalFlowError("Invalid security "
                    + "context: PC "
                    + "must be "
                    + "less/equal " +
                    level + ", but PC "
                    + "was " +
                    localMap
                            .getLocalPC()));
        }
    }

    public void stopTrackingLocal(String signature) {
        logger.log(Level.INFO, "Stop tracking local {0}",signature);
        localMap.removeLocal(signature);
    }
}
