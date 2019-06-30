package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class NewInstanceSuccess {

    @Sec("HIGH")
    static int a;
    @Sec("HIGH")
    double b;


    @Constraints({"@0 <= HIGH", "@1 <= HIGH"})
    @Effects({"HIGH", "?", "LOW"})
    NewInstanceSuccess(int a, int b){
        this.a = a;
        this.b = b;
    }

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        int y = Casts.cast("? ~> HIGH", 4);
        int z = Casts.cast("? ~> LOW", 14);

        NewInstanceSuccess newInstance1 = new NewInstanceSuccess(y, z);
        newInstance1 = Casts.cast("LOW ~> ?", newInstance1);
        IOUtils.printSecret(newInstance1);

    }
}
