parameter('text', required: true, description: 'Text')

task('echo', Echo) {
    text = parameter('text')
    text = "prefix-${parameter('text')}"
}

defaults 'echo'
