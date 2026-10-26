package ch.so.agi.gretl.tasks.io.s3;

import ch.so.agi.gretl.runtime.api.SecretValue;

/** Resolved S3 connection configuration shared by the S3 task family. */
public record S3Connection(
        String endpoint,
        String region,
        String accessKey,
        SecretValue secretKey
) {
}
