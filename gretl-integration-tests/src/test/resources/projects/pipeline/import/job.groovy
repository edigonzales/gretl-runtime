package pipeline.consumer
import helpers.Label
new File('toplevel-marker.txt').append('import;')
parameter('count', type: 'integer', defaultValue: '7', description: 'Count')
action('run', dependsOn: ':validation:nested:run', after: ':unrelated:run') { ctx ->
    if (parameter(':mode') == 'fail') throw new IllegalStateException('synthetic-project-failure', new IOException('original-cause'))
    if (parameter(':mode') == 'empty') ctx.finishJob('synthetic-empty')
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
