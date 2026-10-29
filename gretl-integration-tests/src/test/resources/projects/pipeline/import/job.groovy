package pipeline.consumer
import helpers.Label
new File('toplevel-marker.txt').append('import;')
parameter('count', type: 'integer', defaultValue: '7', description: 'Count')
action('run', dependsOn: ':validation:nested:run', after: ':unrelated:run') { ctx ->
    if (parameter(':mode') == 'fail') throw new IllegalStateException('synthetic-project-failure', new IOException('original-cause'))
    if (parameter(':mode') == 'empty') ctx.finishJob('synthetic-empty')
    if (parameter(':mode') in ['nested-fail', 'caught']) {
        try {
            ctx.run(Gzip) { dataFile = file('missing.txt'); gzipFile = workPath('missing.gz') }
        } catch (ch.so.agi.gretl.tasks.io.archive.GzipTaskException failure) {
            ctx.log().error('observed-inner-failure', failure)
            if (parameter(':mode') != 'caught') throw failure
            java.nio.file.Files.writeString(parameter(':output').parent.resolve('caught.txt'), failure.class.name)
        }
        return
    }
    assert parameter('count') instanceof Long
    assert parameter(':enabled') instanceof Boolean
    def nested = ctx.run(Gzip) { dataFile = file('sql/update.sql'); gzipFile = workPath('nested.gz') }
    assert java.nio.file.Files.size(nested.file) > 0
    def text = java.nio.file.Files.readString(result(':validation:nested:run').files.first())
    java.nio.file.Files.writeString(parameter(':output'), text + '|' + java.nio.file.Files.readString(ctx.file('sql/update.sql')) + '|' + parameter('count') + '|' + parameter(':enabled') + '|' + Label.value())
}
cleanup('release') {
    java.nio.file.Files.writeString(parameter(':output').parent.resolve('cleanup.txt'), 'i', java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
}
