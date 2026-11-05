task('prepare', Gzip) {
    dataFile = file('data/input.txt')
    gzipFile = workPath('input.txt.gz')
    overwrite = true
}
