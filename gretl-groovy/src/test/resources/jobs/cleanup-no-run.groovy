task('echo', Echo) {
    text = 'x'
}

cleanup('forbidden') {
    ctx.run(Echo) {
        text = 'never'
    }
}

defaults 'echo'
