package testclasses;

import de.unifreiburg.cs.proglang.jgs.support.Casts;
import testclasses.utils.SimpleObject;

public class AccessFieldsOfObjectsSuccess {

	public static void main(String[] args) {
		/*SimpleObject oneObject = new SimpleObject();
		oneObject.field = "New field value";
		String local = oneObject.field;
		System.out.println(local);*/

        /*Casts.castCxLowToDyn();
        int x = 5;
        while (x < 10) {
            x++;
        }
        System.out.println(x);
        Casts.castCxEnd();*/

        int y = Casts.cast("HIGH ~> ?", 3);


    }

}
