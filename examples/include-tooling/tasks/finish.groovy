task('finish', Gzip, dependsOn: 'prepare') {
    dataFile = result('prepare').file
    gzipFile = workPath('input.txt.gz.gz')
    overwrite = true
}
