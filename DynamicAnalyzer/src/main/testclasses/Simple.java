package main.testclasses;

import utils.analyzer.HelperClass;


public class Simple {
/**
 * Simple test-method which tries to print a message with high security level.
 * Result should be an illegal flow exception.
 * @param args
 */
  public static void main(String[] args) {
    String message = "Hello World";
    HelperClass.makeHigh(message);
    System.out.println(message);
  }
}
