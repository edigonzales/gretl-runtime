task('echo', Echo) {
    def text = 'local'
    text = 'still-local'
}

defaults 'echo'
