task('run', Extract, dependsOn: ':prepare:run') {
    archive = result(':prepare:run').file
    compression = 'GZIP'
    outputFile = workPath('input.txt')
}
cleanup('release') {
    ctx.log().info('Checks abgeschlossen')
}
