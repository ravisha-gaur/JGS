package junitAnalysis;

import static security.Definition.*;

public class Invalid03 {
	
	@ParameterSecurity({"low"})
	public static void main(String[] args) {}

	@ParameterSecurity({ "high", "low" })
	// too many parameter security levels
	public Invalid03(int arg) {
	}

}
// @error("For each constructor parameter should exist exactly one security level.")