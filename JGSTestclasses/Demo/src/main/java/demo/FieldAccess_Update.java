package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class FieldAccess_Update {

    @Sec("HIGH")
    static String a;

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {
        /*FieldAccess_Update fieldAccess_update = new FieldAccess_Update();

        String h = fieldAccess_update.a;
        System.out.print(h);*/


        a = Casts.cast("? ~> HIGH", "5");
    }
}
