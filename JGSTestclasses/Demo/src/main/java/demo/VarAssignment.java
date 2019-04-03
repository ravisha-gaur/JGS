package demo;

import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.DynamicLabel;
import de.unifreiburg.cs.proglang.jgs.support.Effects;

public class VarAssignment {

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {
        String y = DynamicLabel.makeHigh("hi");
        String z = y ;
    }
}
