package com.example;

/**
 * Simple greeter example used in smoke tests.
 */
public class Greeter {

    /**
     * Generates a greeting for the provided {@code name}.
     *
     * @param name person to greet
     * @return greeting message
     */
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
