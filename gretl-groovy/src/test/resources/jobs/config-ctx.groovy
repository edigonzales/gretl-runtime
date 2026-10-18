parameter('text', required: true, description: 'Text')

task('echo', Echo) { ctx ->
    text = ctx.parameter('text')
}

defaults 'echo'
