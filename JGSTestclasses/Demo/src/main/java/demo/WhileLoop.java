package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;


public class WhileLoop {

    @Constraints("LOW <= @0")
    @Effects({"LOW"})
    public static void main(String[] args) {

        int y = Casts.cast("LOW ~> ?", 7);
        while (y < 10) {
            y++;
        }

        IOUtils.printSecret(y);
    }

}
