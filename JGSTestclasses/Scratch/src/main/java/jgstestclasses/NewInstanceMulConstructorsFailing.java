package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class NewInstanceMulConstructorsFailing {

    @Sec("?")
    static int a;
    @Sec("HIGH")
    static double b;


    @Constraints({"@0 <= ?", "@1 <= HIGH"})
    @Effects({"HIGH", "?", "LOW"})
    NewInstanceMulConstructorsFailing(int a, double b){
        this.a = a;
        this.b = b;
    }

    NewInstanceMulConstructorsFailing(){

    }

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {
        int x = Casts.cast("HIGH ~> ?", 5);
        double y = Casts.cast("? ~> HIGH", 7.0);

        NewInstanceMulConstructorsFailing newInstance = new NewInstanceMulConstructorsFailing(x, y);
        IOUtils.printSecret(a);
        newInstance = Casts.cast("LOW ~> ?", newInstance);
        IOUtils.printSecret(newInstance);

        NewInstanceMulConstructorsFailing newInstance1 = new NewInstanceMulConstructorsFailing();
        newInstance1 = Casts.cast("? ~> HIGH", newInstance1);
        IOUtils.printSecret(newInstance1);


        NewInstanceMulConstructorsFailing newInstance2 = new NewInstanceMulConstructorsFailing(10, 9.3);
        newInstance2 = Casts.cast("LOW ~> ?", newInstance2);
        IOUtils.printSecret(newInstance2);

    }

}
