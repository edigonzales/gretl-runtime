new File('toplevel-marker.txt').append('validation;')
task('run', Extract, dependsOn: ':download:run', when: { java.nio.file.Files.readString(ctx.file('sql/update.sql')) == 'validation-B\n' }) {
    archive = result(':download:run').file
    compression = 'GZIP'
    outputFile = workPath('data.txt')
}
cleanup('release') {
    java.nio.file.Files.writeString(parameter(':output').parent.resolve('cleanup.txt'), 'v', java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
}
