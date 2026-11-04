task('producer', HttpDownload) {
    url = 'https://example.invalid/a.zip'
    output = workPath('archive.zip')
}
action('bridge', dependsOn: 'producer') { println 'not executed' }
task('consumer', Extract, after: 'producer', dependsOn: [ 'bridge']) {
    archive = workPath('archive.zip')
    compression = 'ZIP'
    outputDirectory = workPath('out')
}
defaults 'consumer'
