package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class MethodCallAllStatic_2 {

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@1 <= ?", "LOW <= @ret"})
    @Effects({"LOW"})
    public static int add(int x, int y){

        y = Casts.cast("? ~> LOW", y);

        int a = x + y;
        return a;
    }

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

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@2 <= @ret", "HIGH <= @ret", "@2 <= ?", "@1 <= LOW"})
    @Effects({"LOW"})
    public static int sub(int a, int b, int c){

        a  = add(b, c);
        //b = Casts.cast("? ~> LOW", b);
        int r = b - a;
        r = Casts.cast("LOW ~> ?", r);
        r = Casts.cast("? ~> HIGH", r);

        return r;
    }

}
