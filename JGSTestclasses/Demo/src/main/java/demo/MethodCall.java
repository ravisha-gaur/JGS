package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class MethodCall {

    @Sec("?")
    static String dynField;

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        dynField = Casts.cast("HIGH ~> ?", "hello");
        int z = add(5,6);
        IOUtils.printSecret(z);
    }

    @Constraints({"@0 <= LOW", "@1 <= LOW", "@ret <= ?", "@0 <= ret", "@1 <= ret"})
    @Effects({"LOW", "?"})
    public static int add(int x, int y){
        return x + y;
    }
}
