package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class StupidTestCase {

    @Sec("?")
    static int z;

    @Constraints({"@0 <= @ret", "@0 <= HIGH"})
    @Effects({"HIGH", "?"})
    public static int stupid1(int x, double y){
        y = y + 1;
        return x + 1;
    }

    @Constraints("LOW <= @0")
    @Effects({"HIGH", "?", "LOW"})
    public static void main(String[] args) {

        int r = Casts.cast("HIGH ~> ?", 70);
        int a = Casts.cast("? ~> HIGH", 999);
        double d = Casts.cast("HIGH ~> ?", 70.0);
        int s = stupid(a, r);
        int s1 = stupid1(a, d);
        IOUtils.printSecret(z);
        IOUtils.printSecret(s);
        IOUtils.printSecret(s1);
    }

    @Constraints({"@0 <= @ret", "@0 <= HIGH", "@1 <= ?"})
    @Effects({"HIGH", "?"})
    public static int stupid(int x, int y){
        z = y;
        return x + 1;
    }

}
