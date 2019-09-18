package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class ContextCast {

    @Sec("HIGH")
    static int highField = 45;

    @Sec("?")
    static int dynField = 9;

    @Sec("LOW")
    static int lowField = 8;

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {
        if (highField == 42) {
            Casts.castCx("HIGH ~> ?"); // NSU Failure
            dynField = 10;
            Casts.castCx("? ~> LOW");
            lowField = 5;
            Casts.castCxEnd();
            Casts.castCxEnd();
        }
    }
}
