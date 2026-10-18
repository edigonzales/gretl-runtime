action('run') {
    try {
        ctx.run(Fail) {
        }
        return 'not-reached'
    } catch (IllegalStateException expected) {
        return 'caught'
    }
}

defaults 'run'
