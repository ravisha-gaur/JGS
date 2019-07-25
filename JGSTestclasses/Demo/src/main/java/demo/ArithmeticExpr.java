package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class ArithmeticExpr {

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {

        int a = DynamicLabel.makeLow(4);
        a = a - 3;
        int b = DynamicLabel.makeLow(5);
        a = a - 3 ;
        a = a + b + 5;
        IOUtils.printSecret(a);

    }
}
