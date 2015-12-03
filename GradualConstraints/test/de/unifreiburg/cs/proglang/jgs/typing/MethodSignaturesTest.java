package de.unifreiburg.cs.proglang.jgs.typing;

import de.unifreiburg.cs.proglang.jgs.constraints.ConstraintSet;
import de.unifreiburg.cs.proglang.jgs.constraints.SomeConstraintSets;
import de.unifreiburg.cs.proglang.jgs.constraints.TypeVars;
import de.unifreiburg.cs.proglang.jgs.signatures.Symbol;
import org.junit.Before;
import org.junit.Test;
import soot.IntType;
import soot.jimple.Jimple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static de.unifreiburg.cs.proglang.jgs.signatures.MethodSignatures.*;
import static de.unifreiburg.cs.proglang.jgs.TestDomain.*;
import static de.unifreiburg.cs.proglang.jgs.constraints.secdomains.LowHigh.*;
import static de.unifreiburg.cs.proglang.jgs.constraints.TypeVars.*;

/**
 * Created by fennell on 10/29/15.
 */
public class MethodSignaturesTest {

    private Jimple j;
    private SomeConstraintSets cs;
    private TypeVars tvars;

    @Before public void setUp() throws Exception {
        this.j = Jimple.v();
        tvars = new TypeVars();
        this.cs = new SomeConstraintSets(tvars);
    }

    @Test public void testConstraints() {
        Symbol<Level> s1 = Symbol.param(IntType.v(), 0);
        Symbol<Level> s2 = Symbol.param(IntType.v(), 1);

        Map<Symbol<Level>, TypeVar> mapping = new HashMap<>();
        mapping.put(s1, cs.v1);
        mapping.put(s2, cs.v2);
        mapping.put(Symbol.ret(), cs.v3);

        // a typical constraint for an "add" method: @return >= x, @return >= y
        List<SigConstraint<Level>> sig =
                asList(leS(s1, Symbol.ret()), leS(s2, Symbol.ret()));
        ConstraintSet<Level> sigAsCSet =
                makeNaive((signatureConstraints(sig).toTypingConstraints(mapping)).collect(Collectors.toSet()));
        assertThat(sigAsCSet, (equivalent(cs.x1_le_x3__x2_le_x3)));
    }
}