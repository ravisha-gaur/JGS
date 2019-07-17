package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class StupidTestCase {

    @Sec("?")
    static int z;

    @Constraints("LOW <= @0")
    @Effects({"HIGH", "?", "LOW"})
    public static void main(String[] args) {

        int r = Casts.cast("HIGH ~> ?", 70);
        int a = Casts.cast("? ~> HIGH", 999);
        int s = stupid(a, r);
        IOUtils.printSecret(z);
        IOUtils.printSecret(s);
    }

    @Constraints({"@0 <= @ret", "@0 <= HIGH", "@1 <= ?"})
    @Effects({"HIGH", "?"})
    public static int stupid(int x, int y){

        z = y;
        return x+1;

    }

}
