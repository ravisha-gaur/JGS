package demo;

import de.unifreiburg.cs.proglang.jgs.support.Constraints;
import de.unifreiburg.cs.proglang.jgs.support.DynamicLabel;
import de.unifreiburg.cs.proglang.jgs.support.Effects;
import de.unifreiburg.cs.proglang.jgs.support.Sec;

public class FieldsSuccess {

	@Sec("?") //always retains security level as tracking of fields at compile time is difficult
	static String field = "testfield";

	@Constraints("LOW <= @0")
	@Effects({"LOW", "?", "pub"})
	public static void main(String[] args) {
		readField();
		writeField("Test");
	}

	@Constraints({"@0 <= LOW", "@0 <= ?"})
	@Effects("LOW")
	public static void writeField(String arg) {
		field = arg;
		System.out.println(field);
	}

	@Effects("pub")
	public static void readField() {
		field = DynamicLabel.makeHigh(field);
		// System.out.println("Value of Field:" + local);
	}

}
