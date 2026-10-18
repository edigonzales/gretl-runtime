parameter('text', required: true, description: 'Text')

action('run') {
    def value = ctx.run(Echo) {
        text = parameter('text')
    }
    return value.result
}

defaults 'run'
