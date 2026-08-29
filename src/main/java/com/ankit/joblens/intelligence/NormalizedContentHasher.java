package com.ankit.joblens.intelligence;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class NormalizedContentHasher {

    public String hash(NormalizedJob job) {
        String canonical = String.join("",
                field(job.title()),
                field(job.company()),
                field(job.location()),
                field(job.descriptionText()),
                field(job.employmentType()),
                field(decimal(job.salaryMin())),
                field(decimal(job.salaryMax())),
                field(job.salaryCurrency()),
                field(job.remoteType()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String field(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
