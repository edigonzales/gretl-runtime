package ch.so.agi.gretl.tasks.io;

import ch.so.agi.gretl.runtime.api.*;
import java.util.*;
import ch.so.agi.gretl.tasks.io.http.HttpDownloadOperation;
import ch.so.agi.gretl.tasks.io.http.CurlOperation;
import ch.so.agi.gretl.tasks.io.archive.GzipOperation;
import ch.so.agi.gretl.tasks.io.archive.ExtractOperation;
import ch.so.agi.gretl.tasks.io.ftp.FtpListOperation;
import ch.so.agi.gretl.tasks.io.ftp.FtpDownloadOperation;
import ch.so.agi.gretl.tasks.io.ftp.FtpUploadOperation;
import ch.so.agi.gretl.tasks.io.ftp.FtpDeleteOperation;
import ch.so.agi.gretl.tasks.io.s3.S3DownloadOperation;
import ch.so.agi.gretl.tasks.io.s3.S3UploadOperation;
import ch.so.agi.gretl.tasks.io.s3.S3DeleteOperation;
import ch.so.agi.gretl.tasks.io.s3.S3Bucket2BucketOperation;
import ch.so.agi.gretl.tasks.io.xslt.XslTransformerOperation;

/** Complete io operation catalog. */
public final class IoTaskProvider implements OperationProvider {
    @Override public Collection<OperationType<?, ?>> operations() {
        return List.of(HttpDownloadOperation.type(),
                CurlOperation.type(),
                GzipOperation.type(),
                ExtractOperation.type(),
                FtpListOperation.type(),
                FtpDownloadOperation.type(),
                FtpUploadOperation.type(),
                FtpDeleteOperation.type(),
                S3DownloadOperation.type(),
                S3UploadOperation.type(),
                S3DeleteOperation.type(),
                S3Bucket2BucketOperation.type(),
                XslTransformerOperation.type());
    }
}
