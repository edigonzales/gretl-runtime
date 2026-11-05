import java.nio.file.Files
import java.nio.file.StandardOpenOption

action('prepareFinalizer') {
    assert Files.readString(parameter('output').resolve('owner-closed.txt')) == 'closed'
    Files.writeString(parameter('output').resolve('trace.txt'), 'prepareFinalizer\n', StandardOpenOption.APPEND)
}
task('release', Gzip, dependsOn: 'prepareFinalizer') {
    Files.writeString(parameter('output').resolve('trace.txt'), 'release\n', StandardOpenOption.APPEND)
    dataFile = file('input.txt')
    gzipFile = parameter('output').resolve('finalized.gz')
}
action('record', after: 'release') {
    Files.writeString(parameter('output').resolve('trace.txt'), 'record\n', StandardOpenOption.APPEND)
    Files.writeString(parameter('output').resolve('record.txt'), 'task-bound')
}
