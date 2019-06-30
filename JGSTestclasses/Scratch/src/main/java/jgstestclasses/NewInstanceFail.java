package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class NewInstanceFail {

    @Sec("?")
    static int a;
    @Sec("HIGH")
    double b;


    @Constraints({"@0 <= HIGH", "@1 <= HIGH"})
    @Effects({"HIGH", "?", "LOW"})
    NewInstanceFail(int a, int b){
        int r = Casts.cast("HIGH ~> ?", a);
        this.a = r;
        this.b = b;
    }

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        int y = Casts.cast("? ~> HIGH", 4);
        int z = Casts.cast("? ~> LOW", 14);

        NewInstanceFail newInstance1 = new NewInstanceFail(y, z);
        IOUtils.printSecret(newInstance1.a);
        newInstance1 = Casts.cast("LOW ~> ?", newInstance1);
        IOUtils.printSecret(newInstance1);

    }

}
