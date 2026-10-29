@groovy.transform.Field def captured = binding.getVariable('Gzip')
def ownSymbol = captured
new File('toplevel-marker.txt').append('download;')
task('run', Gzip) {
    assert ownSymbol.is(Gzip)
    dataFile = file('sql/update.sql')
    gzipFile = workPath('data.gz')
}
cleanup('release') {
    java.nio.file.Files.writeString(parameter(':output').parent.resolve('cleanup.txt'), 'd', java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
}
