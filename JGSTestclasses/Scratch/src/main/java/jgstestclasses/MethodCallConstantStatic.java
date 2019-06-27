package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class MethodCallConstantStatic {

    @Constraints({"@0 <= @ret", "@1 <= @ret"})
    @Effects({"LOW"})
    public static int add(int x, int y){

        int a = x + y;
        return a;
    }



    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {
        int q = Casts.cast("? ~> HIGH", 5);
        int s = sub(10, q, q);

        int x = 7;
        int z = Casts.cast("? ~> LOW", 9);
        int r = add(z, x);
        IOUtils.printSecret(r);
        IOUtils.printSecret(s);
    }

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@2 <= @ret"})
    @Effects({"LOW"})
    public static int sub(int a, int b, int c){

        a  = add(b, c);
        int r = b - a;

        return r;
    }

}
