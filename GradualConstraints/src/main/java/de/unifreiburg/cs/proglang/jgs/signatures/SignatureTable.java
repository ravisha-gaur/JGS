package de.unifreiburg.cs.proglang.jgs.signatures;

import de.unifreiburg.cs.proglang.jgs.constraints.SecDomain;
import scala.Option;
import soot.SootMethod;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static de.unifreiburg.cs.proglang.jgs.signatures.MethodSignatures.makeSignature;

/**
 * A table mapping methods to their security signatures.
 */
public class SignatureTable<Level> {

    private static final Logger logger = Logger.getLogger(SignatureTable.class.getName());

    static List stringMethodList = new ArrayList();

    static {
        // store all string class methods
       String s = "testString";
       Class cls = s.getClass();
       Method[] method = cls.getMethods();
       stringMethodList = Arrays.asList(method);
    }

    private final SecDomain<Level> secDomain;
    public final Map<SootMethod, Signature<Level>> signatureMap;

    /**
     * Create a new table from a map.
     */
    public static <Level> SignatureTable<Level> of(SecDomain<Level> secDomain, Map<SootMethod, Signature<Level>> signatureMap) {
        return new SignatureTable<>(secDomain, new HashMap<>(signatureMap));
    }

    private SignatureTable(SecDomain<Level> secDomain, Map<SootMethod, Signature<Level>> signatureMap) {
        this.signatureMap = signatureMap;
        this.secDomain = secDomain;
    }

    public SignatureTable<Level> extendWith(SootMethod m, Collection<SigConstraint<Level>> constraints, Effects<Level> effects) {
        HashMap<SootMethod, Signature<Level>> freshTable = new HashMap<>(this.signatureMap);
        freshTable.put(m, makeSignature(m.getParameterCount(), constraints, effects));
        return of(secDomain, freshTable);
    }

    @Override
    public String toString() {
        return this.signatureMap.toString();
    }

    public Option<Signature<Level>> get(SootMethod m) {
        Option<Signature<Level>> result = Option.apply(signatureMap.get(m));
        if (result.isEmpty()) {
            if(Pattern.compile(m.getName()).matcher(stringMethodList.toString()).find()){
                // create signatures for different string(and other data types) class methods as and when needed
                switch (m.getName()){
                    case "substring":
                        Signature signature = Signature.exampleSignature(secDomain, m.getParameterCount());
                        result = Option.apply(signature);
                        break;
                    case "indexOf":
                        signature = Signature.exampleSignature(secDomain, m.getParameterCount());
                        result = Option.apply(signature);
                        break;
                    default:
                        List<SigConstraint<Level>> constraints = Collections.emptyList();
                        Effects<Level> effects = Effects.emptyEffect();
                        result = Option.apply(MethodSignatures.makeSignature(m.getParameterCount(), constraints, effects));
                        logger.severe("====== IGNORING UNKNOWN LIBRARY METHOD " + m.getName() + " =======");
                        break;
                }

            }
            else {
                List<SigConstraint<Level>> constraints = Collections.emptyList();
                Effects<Level> effects = Effects.emptyEffect();
                result = Option.apply(MethodSignatures.makeSignature(m.getParameterCount(), constraints, effects));
                logger.severe("====== IGNORING UNKNOWN LIBRARY METHOD " + m.getName() + " =======");
            }
        }
        return result;
    }

}
