package com.ankit.joblens.jdbc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

public final class ClasspathSql {

    private ClasspathSql() {
    }

    public static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Could not load SQL resource " + path, exception);
        }
    }
}
