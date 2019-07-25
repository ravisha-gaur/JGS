package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class VarAssignment {

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {

        double a = 5.0;
        float f = 3.5f;
        f = Casts.cast("HIGH ~> ?", f);
        int i = Casts.cast("? ~> LOW", 8);
        String s = Casts.cast("? ~> HIGH", "hi");
        String t = Casts.cast("HIGH ~> ?", "hello");
        IOUtils.printSecret(s);
        double x2 = Casts.cast("LOW ~> ?", a);
        IOUtils.printSecret(x2);

        String s1 = "dfjgsf";
        System.out.println(s1);

    }
}
