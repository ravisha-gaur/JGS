package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class ValueCast {

    @Sec("?")
    static int dynField;

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        dynField = Casts.cast("HIGH ~> ?", 7);
    }
}
