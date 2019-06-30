package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class MethodCall_addS_addD_Fail {

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= ?"})
    @Effects({"LOW"})
    public static int addD(int x, int y){

        int a = x + y;
        return a;
    }


    @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= HIGH"})
    @Effects({"LOW"})
    public static int addS(int x, int y){

        int a = x + y;
        return a;
    }


    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {

        int x = Casts.cast("? ~> HIGH", 7);
        int z = Casts.cast("? ~> HIGH", 7);
        int r = addS(x, z);

        IOUtils.printSecret(r);

        int xd = Casts.cast("HIGH ~> ?", x);
        int zd = Casts.cast("HIGH ~> ?", z);
        int rd = addD(xd, zd);

        IOUtils.printSecret(rd);

    }


}
