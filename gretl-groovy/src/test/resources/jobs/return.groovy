task('echo', Echo) {
    text = 'before'
    return
    text = 'after'
}

defaults 'echo'
