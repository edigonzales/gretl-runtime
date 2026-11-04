action('child') { ctx ->
    ctx.run(HttpDownload) { url='https://example.invalid/child'; output=workPath('child.txt') }
}
