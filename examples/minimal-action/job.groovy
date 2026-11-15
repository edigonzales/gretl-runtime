parameter('output', type: 'path', required: true, description: 'Output text file')
action('write') {
    def target = parameter('output')
    java.nio.file.Files.createDirectories(target.parent)
    java.nio.file.Files.copy(file('message.txt'), target)
}
defaults 'write'
