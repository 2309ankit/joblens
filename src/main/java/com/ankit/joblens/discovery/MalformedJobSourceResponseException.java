package com.ankit.joblens.discovery;

public class MalformedJobSourceResponseException extends JobSourceException {

    public MalformedJobSourceResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedJobSourceResponseException(String message) {
        super(message);
    }
}
