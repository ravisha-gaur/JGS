package analyzer.level2;

import static org.junit.Assert.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import utils.logging.L2Logger;

import org.junit.Before;
import org.junit.Test;

public class ArraysSuccess {
	
	Logger LOGGER = L2Logger.getLogger();
	
	@Before
	public void init() {
		HandleStmtForTests.init();
	}

	@Test
	public void createArrayTest() {

		LOGGER.log(Level.INFO, "CREATE ARRAY SUCCESS TEST STARTED");
		
		HandleStmtForTests hs = new HandleStmtForTests();
		
		hs.addLocal("String[]_a");
		String[] a = new String[] {"asd", "", ""};
		hs.addArrayToObjectMap(a);
		
		assertEquals(3, hs.getNumberOfFields(a));
		
		hs.close();

		LOGGER.log(Level.INFO, "CREATE ARRAY SUCCESS TEST FINISHED");
	}
	
	@Test
	public void readArray() {
		
		LOGGER.log(Level.INFO, "READ ARRAY SUCCESS TEST STARTED");
		
		HandleStmtForTests hs = new HandleStmtForTests();
		
		String[] a = new String[] {"asd", "", ""};
		hs.addArrayToObjectMap(a);
		
		assertTrue(hs.containsObjectInObjectMap(a));
		
		/*
		 * check ( x >= lpc)
		 * x = Join(i,a, lpc, a_i)
		 */
		hs.assignArrayFieldToLocal("String_x", a , Integer.toString(2));
		String x = a[2];
		
		int i = 1;
		hs.assignArrayFieldToLocal("String_x", a , Integer.toString(2));
		x = a[i];
		
		hs.close();
		
		LOGGER.log(Level.INFO, "READ ARRAY SUCCESS TEST FINISHED");
		
	}
	
	@Test
	public void writeArray() {

		LOGGER.log(Level.INFO, "WRITE ARRAY SUCCESS TEST STARTED");
		
		HandleStmtForTests hs = new HandleStmtForTests();
		
		String[] a = new String[] {"asd", "v", "v"};
		assertEquals(3, a.length);
		hs.addArrayToObjectMap(a);
		assertTrue(hs.containsObjectInObjectMap(a));
		assertEquals(3, hs.getNumberOfFields(a));
		
		/*
		 * check(a_t >= pgc)
		 * level(a) = join(gpc,local, ??i??)
		 * level(a_i) = join(gpc,local, ??i??)
		 * i = ??
		 */
		hs.assignLocalsToArrayField(a, Integer.toString(2));
		a[2] = "3";
		
		int i = 2;
		hs.assignLocalsToArrayField(a, Integer.toString(2));
		a[i] = "3";
		
		hs.close();
		
		LOGGER.log(Level.INFO, "WRITE ARRAY SUCCESS TEST FINISHED");
		
	}

}
