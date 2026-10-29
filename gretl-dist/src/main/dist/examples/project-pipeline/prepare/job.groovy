task('run', Gzip) {
    dataFile = file('data/input.txt')
    gzipFile = workPath('input.txt.gz')
}
cleanup('release') {
    ctx.log().info('Prepare abgeschlossen')
}
