package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class MethodCall_addS_addD_Success {

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= ?"})
    @Effects({"LOW"})
    public static int addD(int x, int y){

        int a = x + y;
        return a;
    }


    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {

        int x = Casts.cast("? ~> LOW", 7);
        int z = Casts.cast("? ~> LOW", 7);
        int r = addS(x, z);

        IOUtils.printSecret(r);

        int xd = Casts.cast("LOW ~> ?", x);
        int zd = Casts.cast("LOW ~> ?", z);
        int rd = addD(xd, zd);

        IOUtils.printSecret(rd);

    }

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= LOW" +
            ""})
    @Effects({"LOW"})
    public static int addS(int x, int y){

        int a = x + y;
        return a;
    }


}
