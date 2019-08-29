package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class MethodAnalysis {

    @Sec("?")
    static int z;

    public static void main(String args[]){
        int x = Casts.cast("LOW ~> ?", 5);
        int y = Casts.cast("LOW ~> ?", 6);
        int i = add(x, y);
        IOUtils.printSecret(i);


        int x1 = Casts.cast("? ~> HIGH", 5);
        int y1 = Casts.cast("? ~> LOW", 6);
        int i1 = sub(x1, y1);
        IOUtils.printSecret(i1);


        int i2 = mul(x, y);
        int i3 = mul(x1, y1);
        IOUtils.printSecret(i2);
        IOUtils.printSecret(i3);

        /*int i4 = div(6, x);
        int i5 = div(y1, 3);
        IOUtils.printSecret(i4);
        IOUtils.printSecret(i5);*/

        //unusual(x1, y);

        //noArgs1();

    }

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= ?"})
    @Effects({"LOW"})
    public static int add(int x, int y){

        int a = x + y;
        return a;
    }

    @Constraints({"@0 <= @ret", "@1 <= @ret", "@ret <= LOW"})
    @Effects({"LOW"})
    public static int sub(int x, int y){

        int a = x - y;
        return a;
    }

    @Constraints({"@0 <= @ret", "@1 <= @ret"})
    @Effects({"LOW"})
    public static int mul(int x, int y){

        int a = x * y;
        return a;
    }

    /*@Constraints({"@0 <= @ret", "@1 <= @ret", "? <= @ret", "@1 <= LOW"})
    @Effects({"LOW"})
    public static int div(int x, int y){

        int a  = x / y;
        a = Casts.cast("LOW ~> ?", a);

        return a;
    }*/


    /*@Constraints({"@0 <= @ret", "@0 <= HIGH", "@1 <= ?"})
    @Effects({"HIGH", "?"})
    public static int unusual(int x, int y){
        z = y;
        return x + 1;
    }*/

    /*@Constraints({})
    @Effects({})
    private static void noArgs1() {

    }*/

}
