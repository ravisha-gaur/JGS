package classfiletests;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class ClassRunner {

	static String s = null;
	static boolean error_recognized = false;
	static String fileName = "";
	
	protected static void runClass(String fileName) {
		
		ClassRunner.fileName = fileName;
		
		try {
		
		String[] cmd = {"/bin/sh", "-c", "cd sootOutput; java " + fileName};
		Process p = Runtime.getRuntime().exec(cmd);
		
        BufferedReader stdInput = new BufferedReader(new
        InputStreamReader(p.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
        InputStreamReader(p.getErrorStream()));
        

        // read the output from the command
        System.out.println("Here is the standard output of the command:\n");
        while ((s = stdInput.readLine()) != null) {
           System.out.println(s);
        }
            
        // read any errors from the attempted command
        System.out.println("Here is the standard error of the command (if any):\n");
        while ((s = stdError.readLine()) != null) {
          System.out.println(s);
          error_recognized = true;
        }
        
        if (error_recognized) {
        	printByteCode();
        }
        
		} catch (IOException e) {
			System.out.println("Class couldn't be executed");
			e.printStackTrace();
		}
	}
	
	private static void printByteCode() {

			try {
				
				String[] cmd = {"/bin/sh", "-c", "cd sootOutput; javap -c " + fileName};
				Process p = Runtime.getRuntime().exec(cmd);
				
		        BufferedReader stdInput = new BufferedReader(new
		        InputStreamReader(p.getInputStream()));

		        BufferedReader stdError = new BufferedReader(new
		        InputStreamReader(p.getErrorStream()));
		        

		        // read the output from the command
		        System.out.println("Here is the standard output of the command:\n");
		        while ((s = stdInput.readLine()) != null) {
		           System.out.println(s);
		        }
		            
		        // read any errors from the attempted command
		        System.out.println("Here is the standard error of the command (if any):\n");
		        while ((s = stdError.readLine()) != null) {
		          System.out.println(s);
		        }
		        
				} catch (IOException e) {
					e.printStackTrace();
				}
	}
}
