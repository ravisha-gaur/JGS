package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

/**
 * Simple JGS demo.
 *
 * Security lattice: LOW < MIDDLE < HIGH
 */
public class Demo1 {

    static int z = 7;
    @Sec("?")
    static String dynField;

    @Sec("HIGH")
    static String staticField;

    @Constraints({"LOW <= @0", "@0 <= ret"})
    @Effects({"?"})
    public static void main(String[] args) {

        //String secret = IOUtils.readSecret(); // <- library method
        /* secret has level H as it is read using readSecret() */
        //IOUtils.printSecret(secret);          // <- no leak

        /* secret has level H, hence println() causes an error */


       /* String y = Casts.cast("HIGH ~> ?", "Hello");
        String z = y ;

        int a = Casts.cast("? ~> LOW", 5);
        int b = a ;*/

        Casts.castCxLowToDyn();
        int x = 5;
        while (x < 10) {
            x++;
        }
        //System.out.println(x);
        Casts.castCxEnd();



        //String secret = IOUtils.readSecret(); // <- library method

        //IOUtils.printSecret(secret);          // <- no leak
        // System.out.println(secret);        // <- static leak

        /* dynField has level ? and it cannot be cast to HIGH which is secret's level, hence causes an error */
        // dynField = Casts.cast("HIGH ~> ?", secret);             // <- journey through dynamic code

        /* ? cannot flow into HIGH type */
        // IOUtils.printSecret(Casts.cast("? ~> HIGH", dynField)); // <-

        /* ? can flow into LOW type */
        // IOUtils.printSecret(Casts.cast("? ~> LOW", dynField)); // <- dynamically detected leak


        /* staticField has level H like secret, so assignment and printSecret cause no error */
        /* staticField = secret;
        IOUtils.printSecret(staticField); */
    }

    public void test(){
        this.dynField = "fgdhc";
    }

}
