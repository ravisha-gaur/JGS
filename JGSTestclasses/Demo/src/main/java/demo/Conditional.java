package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class Conditional {

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        int x = Casts.cast("LOW ~> ?", 7);
        //boolean a = Casts.cast("LOW ~> ?", true);
        int y = Casts.cast("LOW ~> ?", 3);

       //int x = 7;
       //int y = 7;


        //if(a) {
        if (x > 5 && y < 10) {
            x = 0;
            x = DynamicLabel.makeHigh(5);
            y = 100;
            /*if(y > 6) {  // if (x < 0) {
                y = 0;
            }*/
        }
        else if(x > 3) {
            x = 10;
            //y = 0;
        }
        else if(x > 3) {
            x = 100;
            y = 0;
        }
        else{				// Tricky: b1 is uninitialized, throws IlFlowEx
            x = 500;
        }
        //IOUtils.printSecret(x);
        IOUtils.printSecret(y);

     /*
     * if (x < 0) {			// if (x < 0) {
            x = 0;				//		b1 = 0;
        } else if (x < 2) {		// } else if (x < 2) {
            x = 2;				//		b1 = 2;
        } else if (x < 4) {		// } ..
            x = 4;				//
        } else {				// Tricky: b1 is uninitialized, throws IlFlowEx
            x = 6;
        }
     * */

    }
}
