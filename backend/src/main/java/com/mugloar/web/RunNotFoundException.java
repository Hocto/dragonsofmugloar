package com.mugloar.web;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String runId) {
        super("No run with id " + runId);
    }
}
