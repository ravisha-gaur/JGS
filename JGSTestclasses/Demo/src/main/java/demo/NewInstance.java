package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class NewInstance {

    @Sec("HIGH")
    static int a;
    @Sec("?")
    double b;


    @Constraints({"@0 <= HIGH", "@1 <= ?"})
    @Effects({"HIGH"})
    NewInstance(int a, int b) {
        this.a = a;
        this.b = b;
    }

    NewInstance(){

    }

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        NewInstance n = new NewInstance();
        IOUtils.printSecret(n);

        int y = Casts.cast("? ~> HIGH", 4);
        int z = Casts.cast("HIGH ~> ?", 14);

        NewInstance newInstance1 = new NewInstance(y, z);
        //IOUtils.printSecret(newInstance1.b);
        newInstance1 = Casts.cast("LOW ~> ?", newInstance1);
        IOUtils.printSecret(newInstance1);

    }
}
