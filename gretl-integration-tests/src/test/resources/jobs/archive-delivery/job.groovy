import java.nio.file.Files

parameter('input', type: 'path', required: true, description: 'Directory containing the input ZIPs')
parameter('output', type: 'path', required: true, description: 'Delivery directory')

task('extract', Extract) {
    archive = path(parameter('input').resolve('survey.zip').toString())
    compression = 'ZIP'
    outputDirectory = workPath('selected')
    includes = ['**/*.xtf']
    excludes = ['backup/**']
}

action('prepare', dependsOn: 'extract') {
    def files = result('extract').files
    assert files.size() == 1
    def xtf = Files.move(files.first(), workPath('data.xtf'))
    def toml = Files.copy(file('delivery.toml'), workPath('delivery.toml'))
    [xtf: xtf, toml: toml]
}

task('bundle', Zip, dependsOn: 'prepare') {
    outputFile = parameter('output').resolve('delivery.zip')
    sources = [[file: result('prepare').xtf], [file: result('prepare').toml]]
}

action('moreArchives') {
    def input = parameter('input')
    def archives
    Files.list(input).withCloseable { paths ->
        archives = paths.filter { it.fileName.toString().startsWith('region-') &&
            it.fileName.toString().endsWith('.zip') }.sorted().toList()
    }
    def outputs = []
    for (def archivePath : archives) {
        def name = archivePath.fileName.toString().replaceFirst(/\.zip$/, '')
        def result = ctx.run(Extract) {
            archive = archivePath
            compression = 'ZIP'
            outputDirectory = workPath(name)
            includes = ['**/*.xtf']
        }
        assert result.files().size() == 1
        outputs.add(Files.copy(result.files().first(), parameter('output').resolve(name + '.xtf')))
    }
    outputs
}

task('regionalBundle', Zip, dependsOn: 'moreArchives') {
    outputFile = parameter('output').resolve('regional.zip')
    sources = result('moreArchives').collect { [file: it] }
}

action('finishDelivery', dependsOn: ['bundle', 'regionalBundle']) {
    Files.deleteIfExists(parameter('output').resolve('previous.xtf'))
}

defaults 'finishDelivery'
