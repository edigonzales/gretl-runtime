package ch.so.agi.gretl.tasks.core;

import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.tasks.ftp.FtpDeleteOperation;
import ch.so.agi.gretl.tasks.ftp.FtpDownloadOperation;
import ch.so.agi.gretl.tasks.ftp.FtpListOperation;
import ch.so.agi.gretl.tasks.ftp.FtpUploadOperation;
import ch.so.agi.gretl.tasks.s3.S3Bucket2BucketOperation;
import ch.so.agi.gretl.tasks.s3.S3DeleteOperation;
import ch.so.agi.gretl.tasks.s3.S3DownloadOperation;
import ch.so.agi.gretl.tasks.s3.S3UploadOperation;
import ch.so.agi.gretl.tasks.xslt.XslTransformerOperation;
import java.util.Collection;
import java.util.List;

/** Core task catalog beyond the HTTP/archive/SQL basic module. */
public final class CoreTaskProvider implements OperationProvider {
    @Override
    public Collection<OperationType<?, ?>> operations() {
        return List.of(
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
