package com.ankit.joblens.discovery;

public class MissingJobSourceCredentialsException extends JobSourceException {

    public MissingJobSourceCredentialsException() {
        super("Adzuna credentials are missing; set ADZUNA_APP_ID and ADZUNA_APP_KEY");
    }
}
