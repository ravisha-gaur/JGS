package util;

import java.util.regex.Pattern;

public class CommonUtil {

    // check if the unit string is an arithmetic expression
    public static boolean isArithmeticExpression(String s){

        if(Pattern.compile("[-+*/]").matcher(s).find()){
            return true;
        }

        return false;
    }

}
