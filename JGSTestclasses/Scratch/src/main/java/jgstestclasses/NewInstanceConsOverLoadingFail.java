package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class NewInstanceConsOverLoadingFail {

    @Sec("?")
    static int a;
    @Sec("HIGH")
    double b;
    @Sec("?")
    int c;

    @Constraints({"@0 <= ?", "@1 <= HIGH"})
    @Effects({"HIGH", "?", "LOW"})
    NewInstanceConsOverLoadingFail(int a, double b){
        this.a = a;
        this.b = b;
    }


    @Constraints({"@0 <= ?", "@1 <= ?"})
    @Effects({"HIGH", "?", "LOW"})
    NewInstanceConsOverLoadingFail(int a, int c){
        this.a = a;
        this.c = c;
    }

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        int x = Casts.cast("HIGH ~> ?", 5);
        double y = Casts.cast("? ~> HIGH", 7.0);
        int z = Casts.cast("HIGH ~> ?", 3);

        NewInstanceConsOverLoadingFail newInstance1 = new NewInstanceConsOverLoadingFail(x, z);
        newInstance1 = Casts.cast("LOW ~> ?", newInstance1);
        IOUtils.printSecret(newInstance1);

        NewInstanceConsOverLoadingFail newInstance = new NewInstanceConsOverLoadingFail(x, y);
        newInstance = Casts.cast("LOW ~> ?", newInstance);
        IOUtils.printSecret(newInstance);
        IOUtils.printSecret(newInstance1.c);

    }


}
