action('run') {
    def values = []
    def outer = ctx
    2.times { i ->
        values << outer.run(Echo) {
            text = "v${i}"
        }.result
    }
    return values.join(',')
}

defaults 'run'
