package testclasses;

import de.unifreiburg.cs.proglang.jgs.support.*;
import testclasses.utils.SimpleObject;

/**
 * Simple test that should fail, since it leaks a high Field
 */
public class AccessFieldsOfObjectsFail {

		public static void main(String[] args) {
		SimpleObject oneObject = new SimpleObject();
		oneObject.field = DynamicLabel.makeHigh("New field value");
		System.out.println(oneObject.field);
	}

}
