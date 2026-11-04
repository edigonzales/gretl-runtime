task('producer', HttpDownload) {
    url = 'https://example.invalid/a.zip'
    output = workPath('archive.zip')
}
action('bridge', dependsOn: 'producer') { println 'not executed' }
task('consumer', Extract, dependsOn: ['producer', 'bridge']) {
    archive = result('producer').file
    compression = 'ZIP'
    outputDirectory = workPath('out')
}
defaults 'consumer'
