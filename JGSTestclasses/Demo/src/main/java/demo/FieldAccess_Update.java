package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class FieldAccess_Update {

    @Sec("?")
    String a = Casts.cast("HIGH ~> ?", "hi");             // = "v";

    @Sec("?")
    static String b = Casts.cast("LOW ~> ?", "hello");

    @Sec("?")
    static String c = Casts.cast("LOW ~> ?","jgvkjdnv");

    @Sec("?")
    String d = Casts.cast("LOW ~> ?", "hello");

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {
        IOUtils.printSecret(b);
        FieldAccess_Update fieldAccess_update = new FieldAccess_Update();

        String h = fieldAccess_update.a;
        String z = h;
        //IOUtils.printSecret(z);

        String g = Casts.cast("LOW ~> ?","hi");
        IOUtils.printSecret(g);
        //IOUtils.printSecret(c);
    }

}
