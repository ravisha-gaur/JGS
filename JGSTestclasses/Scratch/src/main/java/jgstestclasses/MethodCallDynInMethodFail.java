package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class MethodCallDynInMethodFail {

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {
        int q = 5;
        int s = sub(10, q, 7);

        int x = 7;
        int z = 9;
        int r = add(x, z);
        IOUtils.printSecret(r);
        IOUtils.printSecret(s);
    }

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@2 <= @ret", "? <= @ret", "@1 <= HIGH", "@2 <= HIGH"})
    @Effects({"LOW"})
    public static int sub(int a, int b, int c){

        a  = add(b, c);
        //b = Casts.cast("? ~> LOW", b);
        int r = b - a;
        r = Casts.cast("HIGH ~> ?", r);

        return r;
    }

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@1 <= ?", "HIGH <= @ret"})
    @Effects({"LOW"})
    public static int add(int x, int y){

        y = Casts.cast("? ~> HIGH", y);

        int a = x + y;
        return a;
    }






}
