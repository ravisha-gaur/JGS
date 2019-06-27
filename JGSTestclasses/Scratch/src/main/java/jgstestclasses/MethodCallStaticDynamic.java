package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class MethodCallStaticDynamic {
    @Constraints({"@0 <= @ret", "@1 <= @ret", "@2 <= @ret"})
    @Effects({"LOW"})
    public static int sub(int a, int b, int c){

        a  = add(b, c);
        int r = b - a;

        return r;
    }

    @Constraints({"@0 <= @ret", "@1 <= @ret"})
    @Effects({"LOW"})
    public static int add(int x, int y){

        int a = x + y;
        return a;
    }

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {
        int q = Casts.cast("LOW ~> ?", 7);
        int b = Casts.cast("? ~> LOW", 4);
        int s = sub(b, q, b);

        int x = Casts.cast("LOW ~> ?", 2);
        int z = Casts.cast("? ~> HIGH", 9);
        int r = add(x, z);

        IOUtils.printSecret(r);
        IOUtils.printSecret(s);
    }


}
