package com.ankit.joblens.discovery;

public class JobSourceException extends RuntimeException {

    public JobSourceException(String message) {
        super(message);
    }

    public JobSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
