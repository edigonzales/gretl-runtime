// Preserve CRLF, comments and emoji 😀.
parameter('sourceUrl', defaultValue:'https://example.invalid/a.zip')
task('download', HttpDownload) {
    url=parameter('sourceUrl')
    output=workPath('archive.zip')
    overwrite=false // preserve this comment
}
action('opaque', dependsOn:'download') {
    def greeting="hello ${'world'} 😀"
    new File('/Users/stefan/.cache/gretl-tooling-review/P8-candidate-1-gui/workspace ä space/project-created/MUST_NOT_EXECUTE').text=greeting
}
defaults 'opaque'
