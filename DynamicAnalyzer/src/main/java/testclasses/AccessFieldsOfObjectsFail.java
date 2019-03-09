package testclasses;

import testclasses.utils.SimpleObject;
import de.unifreiburg.cs.proglang.jgs.support.DynamicLabel;

/**
 * Simple test that should fail, since it leaks a high Field
 */
public class AccessFieldsOfObjectsFail {

    static String abc;

	public static void main(String[] args) {
		/*SimpleObject oneObject = new SimpleObject();
		oneObject.field = DynamicLabel.makeHigh("New field value");
		System.out.println(oneObject.field);*/

		AccessFieldsOfObjectsFail accessFieldsOfObjectsFail = new AccessFieldsOfObjectsFail();
		String v1 = accessFieldsOfObjectsFail.abc;
		String v2 = accessFieldsOfObjectsFail.abc;
		String v = v1 + v2;
	}

}
