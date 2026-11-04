// Created in the GRETL Modeler. 😀
parameter('sourceUrl', description: 'Archive URL', type: 'text', required: false, defaultValue: 'https://example.invalid/archive.zip')
task('download', HttpDownload) {
    url = parameter('sourceUrl')
    output = workPath('archive.zip')
}
task('extract', Extract, after: 'download', dependsOn: 'download') {
    archive = result('download').file
    compression = 'ZIP'
    outputDirectory = workPath('out')
}
defaults 'extract'
