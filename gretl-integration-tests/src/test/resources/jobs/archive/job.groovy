parameter('output', type: 'path', required: true, description: 'Extracted XML file')
task('compress', Gzip) {
    dataFile = file('planregister.xml')
    gzipFile = workPath('data.gz')
}
task('extract', Extract, dependsOn: 'compress') {
    archive = result('compress').file
    compression = 'GZIP'
    outputFile = parameter('output')
}
defaults 'extract'
