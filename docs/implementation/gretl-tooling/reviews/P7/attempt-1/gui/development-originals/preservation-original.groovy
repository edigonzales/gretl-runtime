// Keep literal download in this comment. 😀
parameter('sourceUrl', description:'Archive URL', defaultValue:'https://example.invalid/a.zip')
task('download', HttpDownload) {
    url=parameter('sourceUrl')
    output=workPath('archive.zip')
    overwrite=false // keep this field comment
}
action('opaque', dependsOn:'download') {
    def untouched = "preserve ${parameter('sourceUrl')} 😀"
    new File('/Users/stefan/.cache/gretl-tooling-review/P7-editor-development/must-not-execute-opaque').text = untouched
    println "nested ${[1,2].collect { it * 2 }}"
}
defaults 'opaque'
