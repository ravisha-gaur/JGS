package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;
import fj.data.IO;

public class ValueCast {

    @Sec("HIGH")
    static String d = "hi";

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {

        int x = Casts.cast("LOW ~> ?", 7);
        IOUtils.printSecret(x); // breaks with IllegalFlow Error

        int y = Casts.cast("? ~> HIGH", 7);
        IOUtils.printSecret(y);

    }
}
