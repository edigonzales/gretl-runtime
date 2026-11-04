// Preserve this comment during rename and quick fixes. 😀
parameter('sourceUrl', description:'Archive URL', defaultValue:'https://example.invalid/a.zip')
task('download', HttpDownload) {
    url=parameter('sourceUrl')
    output=workPath('archive.zip')
}
task('extract', Extract, dependsOn:'download', after:'download') {
    archive=result('download').file
    compression='ZIP'
    outputDirectory=workPath('out')
}
action('done', dependsOn:['extract', ':sub:child']) {
    println 'done'
}
defaults 'done'

cleanup('close') { println 'cleanup' }
