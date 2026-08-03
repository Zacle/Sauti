package com.sauti.demo;

public class DemoRequestRateLimitExceededException extends RuntimeException {
    public DemoRequestRateLimitExceededException() {
        super("Too many demo requests. Please try again later.");
    }
}
