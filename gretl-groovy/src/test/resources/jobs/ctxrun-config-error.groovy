action('run') {
    ctx.run(Echo) {
        unknown = 'x'
    }
}

defaults 'run'
