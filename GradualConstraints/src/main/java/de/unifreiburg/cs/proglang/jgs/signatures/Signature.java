package de.unifreiburg.cs.proglang.jgs.signatures;

import de.unifreiburg.cs.proglang.jgs.constraints.*;
import de.unifreiburg.cs.proglang.jgs.constraints.TypeViews.TypeView;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Signatures: constraints + effects
 */
public class Signature<Level> {
    public final MethodSignatures.SigConstraintSet<Level> constraints;
    public final Effects<Level> effects;

    Signature(MethodSignatures.SigConstraintSet<Level> constraints, Effects<Level> effects) {
        this.constraints = constraints;
        this.effects = effects;
    }

    @Override
    public String toString() {
        return String.format("C:%s, E:%s", constraints.toString(), effects.toString());
    }

    public Signature<Level> addConstraints(scala.collection.Iterator<SigConstraint<Level>> sigs) {
        MethodSignatures.SigConstraintSet<Level> newConstraints =
                this.constraints.addAll(sigs);
        return new Signature<>(newConstraints, this.effects);
    }

    // TODO: use a dedicated result type (to be able to name the second component sensibly)
    public Pair<ConstraintSet.RefinementCheckResult<Level>, Effects.EffectRefinementResult<Level>> refines(ConstraintSetFactory<Level> csets, TypeDomain<Level> types, Signature<Level> other) {
        return Pair.of(this.constraints.refines(csets, types, other.constraints), this.effects.refines(types, other.effects));
    }

    public static <Level> Signature<Level> exampleSignature(SecDomain<Level> secDomain, int paramCount) {
        // { @0 <= @ret, @1 <= @ret, ... }
        List<SigConstraint<Level>> constraints = new ArrayList<>();
        for(int i = 0; i < paramCount; i++) {
            SigConstraint<Level> c1 = MethodSignatures.le(new Param<>(0), new Return<>());
            constraints.add(c1);
        }

        Level staticTop = secDomain.top();
        TypeDomain<Level> typeDomain = new TypeDomain<>(secDomain);

        // effects: { ?, top }
        List<TypeView<Level>> effectTypes = Arrays.asList(typeDomain.dyn(), typeDomain.level(staticTop));
        Effects<Level> effs = Effects.makeEffects(effectTypes);

        Signature<Level> sig = MethodSignatures.makeSignature(paramCount, constraints, effs);
        return sig;
    }
}
