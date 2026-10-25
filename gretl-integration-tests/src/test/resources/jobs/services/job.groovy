parameter('selection', defaultValue: 'combined', description: 'Original upload selection')
parameter('output', type: 'path', required: true, description: 'Service fixture parameter')
parameter('server', defaultValue: 'unused', description: 'Service fixture parameter')
parameter('user', defaultValue: 'unused', description: 'Service fixture parameter')
parameter('remoteDir', defaultValue: '/', description: 'Service fixture parameter')
parameter('sourceBucket', defaultValue: 'unused', description: 'Service fixture parameter')
parameter('targetBucket', defaultValue: 'unused', description: 'Service fixture parameter')
parameter('endpoint', defaultValue: 'http://unused.invalid', description: 'Service fixture parameter')
parameter('region', defaultValue: 'us-east-1', description: 'Service fixture parameter')
parameter('accessKey', defaultValue: 'unused', description: 'Service fixture parameter')
parameter('overwrite', type: 'boolean', defaultValue: true, description: 'Service fixture parameter')
parameter('key', defaultValue: 'one.txt', description: 'Service fixture parameter')
parameter('all', type: 'boolean', defaultValue: false, description: 'Service fixture parameter')

task('ftp-upload', FtpUpload) {
    server = parameter('server'); user = parameter('user'); password = secret('ftpPassword')
    remoteDir = parameter('remoteDir'); localFile = file('one.txt'); fileType = 'BINARY'
}
task('ftp-list', FtpList) {
    server = parameter('server'); user = parameter('user'); password = secret('ftpPassword'); remoteDir = parameter('remoteDir')
}
action('ftp-list-result', dependsOn: 'ftp-list') {
    java.nio.file.Files.writeString(parameter('output').resolve('listing.txt'), result('ftp-list').files.toString())
}
task('ftp-download', FtpDownload) {
    server = parameter('server'); user = parameter('user'); password = secret('ftpPassword'); remoteDir = parameter('remoteDir')
    localDir = parameter('output'); remoteFiles = ['one.txt']; fileType = 'BINARY'; overwrite = parameter('overwrite')
}
task('ftp-delete', FtpDelete) {
    server = parameter('server'); user = parameter('user'); password = secret('ftpPassword'); remoteDir = parameter('remoteDir')
    remoteFiles = parameter('all') ? [] : ['one.txt']
}
task('s3-upload', S3Upload) {
    accessKey = parameter('accessKey'); secretKey = secret('s3SecretKey'); endpoint = parameter('endpoint'); region = parameter('region')
    bucketName = parameter('sourceBucket')
    if (parameter('selection') == 'directory') sourceDir = file('original/S3UploadDirectory/docs')
    else if (parameter('selection') == 'tree') sourceFiles = [file('original/S3UploadFileTree/docs/foo.csv'), file('original/S3UploadFileTree/docs/bar.csv')]
    else if (parameter('selection') == 'file') sourceFile = file('original/S3UploadFile/docs/bar.txt')
    else { sourceFile = file('one.txt'); sourceDir = file('dir'); sourceFiles = [file('tree/three.csv')] }
    acl = 'private'; contentType = 'text/plain'; metadata = [source: 'test']
}
task('s3-copy', S3Bucket2Bucket) {
    accessKey = parameter('accessKey'); secretKey = secret('s3SecretKey'); endpoint = parameter('endpoint'); region = parameter('region')
    sourceBucket = parameter('sourceBucket'); targetBucket = parameter('targetBucket'); acl = 'private'; metadata = [copied: 'true']
}
task('s3-download', S3Download) {
    accessKey = parameter('accessKey'); secretKey = secret('s3SecretKey'); endpoint = parameter('endpoint'); region = parameter('region')
    bucketName = parameter('targetBucket'); downloadDir = parameter('output'); overwrite = parameter('overwrite')
    if (!parameter('all')) key = parameter('key')
}
task('s3-delete', S3Delete) {
    accessKey = parameter('accessKey'); secretKey = secret('s3SecretKey'); endpoint = parameter('endpoint'); region = parameter('region')
    bucketName = parameter('targetBucket'); if (!parameter('all')) key = parameter('key')
}
defaults 'ftp-list'
