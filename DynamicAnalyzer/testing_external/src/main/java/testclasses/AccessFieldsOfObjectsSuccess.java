package testclasses;

import util.test.SimpleObject;

public class AccessFieldsOfObjectsSuccess {

	public static void main(String[] args) {
		/*SimpleObject oneObject = new SimpleObject();
		oneObject.field = "New field value";
		String local = oneObject.field;
		System.out.println(local);*/



		String y = Casts.cast("HIGH ~> ?", "Hello");
		String z = y ;

		int a = Casts.cast("? ~> LOW", 5);
		int b = a ;
	}

}
