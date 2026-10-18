task('write', FileSize) {
    def path = workPath('data.txt')
    path.toFile().setText('abc', 'UTF-8')
    input = path
}

defaults 'write'
