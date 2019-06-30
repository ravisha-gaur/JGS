package testclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;
import testclasses.utils.SimpleObject;

/**
 * Simple test that should fail, since it leaks a high Field
 */
public class AccessFieldsOfObjectsFail {

	@Sec("HIGH")
	static int a;
	@Sec("HIGH")
	static double b;

	@Constraints("LOW <= @0")
	@Effects({"LOW", "?"})
	public static void main(String[] args) {
		int x = Casts.cast("? ~> LOW", 5);
		double y = Casts.cast("? ~> LOW", 7.0);

		//AccessFieldsOfObjectsFail newInstance = new AccessFieldsOfObjectsFail(x, y);
		//IOUtils.printSecret(b);
		AccessFieldsOfObjectsFail newInstance = new AccessFieldsOfObjectsFail();
		//CxCast_Fail1 newInstance = new CxCast_Fail1(5, 7.0);
		newInstance = Casts.cast("? ~> HIGH", newInstance);
		IOUtils.printSecret(newInstance);
	}

	/*@Constraints({"@0 <= HIGH", "@1 <= HIGH"})
	@Effects({"LOW", "?"})
	AccessFieldsOfObjectsFail(int a, double b){
		this.a = a;
		this.b = b;
		IOUtils.printSecret(b);
	}*/


	AccessFieldsOfObjectsFail(){

	}


/*

	public static void main(String[] args) {
		SimpleObject oneObject = new SimpleObject();
		oneObject.field = DynamicLabel.makeHigh("New field value");
		System.out.println(oneObject.field);
	}
*/

}
