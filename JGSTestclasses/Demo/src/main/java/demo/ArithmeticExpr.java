package demo;

import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.DynamicLabel;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class ArithmeticExpr {

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {
        int x = DynamicLabel.makeLow(5);
        int y = DynamicLabel.makeLow(7);
        int z = x + y;
        IOUtils.printSecret(z);
    }
}
