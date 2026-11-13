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
    assert result('list').entries.any { it.relativePath() == 'nested/second.CSV' && it.size() > 0 }
    def files = result('download').files
    assert files.size() == 2
    def processed = Files.writeString(parameter('output').resolve('processed.csv'),
        files.collect { Files.readString(it) }.sort().join(''))
    [processed: processed]
}
task('upload', SftpUpload, dependsOn: 'process') {
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
