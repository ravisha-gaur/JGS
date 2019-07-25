package demo;

import de.unifreiburg.cs.proglang.jgs.support.*;
import testclasses.utils.C;

public class MethodCall {

    @Constraints({"@0 <= LOW"})
    @Effects({"?"})
    public static void main(String[] args) {
        simpleWhile(2);
    }

    /**
     * Simple while-loop.
     * @param x input
     * @return output
     */
    @Constraints({"@0 <= ?", "HIGH <= @ret"})
    @Effects({"?"})
    public static int simpleWhile(int x) {
        int y = 0;
        x = Casts.cast("? ~> HIGH", x) ;
        //x = DynamicLabel.makeHigh(x);
        while (x < 10) {
            y = x++;
        }
        return y;
    }

}
