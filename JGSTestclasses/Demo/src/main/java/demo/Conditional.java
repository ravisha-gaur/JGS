package demo;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.IOUtils;

public class Conditional {

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {

        int x = Casts.cast("LOW ~> ?", 7);

        if (x < 0) {			// if (x < 0) {
            x = 0;				//		b1 = 0;
        } else if (x < 2) {		// } else if (x < 2) {
            x = 2;				//		b1 = 2;
        } else if (x < 4) {		// } ..
            x = 4;				//
        } else {				// Tricky: b1 is uninitialized, throws IlFlowEx
            x = 6;
        }

        IOUtils.printSecret(x);
    }
}
