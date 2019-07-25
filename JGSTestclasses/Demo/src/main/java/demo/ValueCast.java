package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;
import fj.data.IO;

public class ValueCast {

    @Sec("HIGH")
    static String d = "hi";

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {

        String s = Casts.cast("HIGH ~> ?", d);
        IOUtils.printSecret(s); // breaks with IllegalFlow Error

        String s1 = Casts.cast("? ~> HIGH", "hello1");
        IOUtils.printSecret(s1);

    }
}
