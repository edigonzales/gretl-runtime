import java.nio.file.Files

parameter('output', type: 'path', required: true, description: 'Output directory')
parameter('fail', type: 'text', required: true, description: 'Simulate a processing failure')

task('list', SftpList) {
    connection = 'exchange'
    remoteDirectory = './exchange'
    recursive = true
    includes = ['**/*.csv']
    caseSensitive = false
}
task('download', SftpDownload, dependsOn: 'list') {
    connection = 'exchange'
    remotePath = 'exchange'
    outputDirectory = workPath('download')
    recursive = true
    includes = ['**/*.csv']
    excludes = ['**/keep.csv', '**/upload.csv']
    caseSensitive = false
}
action('process', dependsOn: ['list', 'download']) {
    if (parameter('fail') == 'yes') {
        throw new IllegalStateException('Synthetic processing failure')
    }
    def listing = result('list').entries.findAll { it.type().name() == 'FILE' && it.name() != 'upload.csv' }
    assert listing.any { it.relativePath() == 'nested/second.CSV' && it.size() > 0 }
    def counts = listing.countBy {
        def relative = it.relativePath()
        def folder = relative.contains('/') ? relative.substring(0, relative.lastIndexOf('/')) : '.'
        folder + ':' + it.name().tokenize('.').last().toLowerCase(Locale.ROOT)
    }
    Files.writeString(parameter('output').resolve('formats.txt'),
        counts.keySet().sort().collect { key -> key + '=' + counts[key] }.join('\n') + '\n')
    def files = result('download').files
    assert files.size() == 2
    def processed = Files.writeString(parameter('output').resolve('processed.csv'),
        files.collect { Files.readString(it) }.sort().join(''))
    [processed: processed]
}
task('bundle', Zip, dependsOn: 'process') {
    outputFile = parameter('output').resolve('processed.zip')
    sources = [[file: result('process').processed]]
}
task('upload', SftpUpload, dependsOn: ['process', 'bundle']) {
    connection = 'exchange'
    sourceFile = result('process').processed
    remotePath = 'exchange/upload.csv'
    overwrite = true
}
task('delete', SftpDelete, dependsOn: ['upload', 'download']) {
    connection = 'exchange'
    remotePaths = result('download').remotePaths
}
defaults 'delete'
