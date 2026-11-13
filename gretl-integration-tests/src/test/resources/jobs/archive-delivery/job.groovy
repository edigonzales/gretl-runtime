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

action('moreArchives', dependsOn: 'bundle') {
    def input = parameter('input')
    for (String name : ['region-a', 'region-b']) {
        def result = ctx.run(Extract) {
            archive = input.resolve(name + '.zip')
            compression = 'ZIP'
            outputDirectory = workPath(name)
            includes = ['**/*.xtf']
        }
        assert result.files().size() == 1
        Files.copy(result.files().first(), parameter('output').resolve(name + '.xtf'))
    }
    Files.deleteIfExists(parameter('output').resolve('previous.xtf'))
}

defaults 'moreArchives'
