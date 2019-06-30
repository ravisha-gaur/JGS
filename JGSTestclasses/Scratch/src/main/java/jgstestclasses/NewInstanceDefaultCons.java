package jgstestclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;

public class NewInstanceDefaultCons {


    NewInstanceDefaultCons(){

    }

    @Constraints("LOW <= @0")
    @Effects({"LOW", "?"})
    public static void main(String[] args) {
        NewInstanceDefaultCons newInstance = new NewInstanceDefaultCons();
        newInstance = Casts.cast("LOW ~> ?", newInstance);
        IOUtils.printSecret(newInstance);
    }

}
