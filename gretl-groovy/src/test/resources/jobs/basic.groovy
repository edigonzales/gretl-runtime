parameter('eingabe', type: 'text', required: true,
          description: 'Eingabe fuer den Echo-Task')

task('echo', Echo) {
    text = parameter('eingabe')
}

action('melden', dependsOn: 'echo') {
    def value = result('echo').result
    println "echo=${value}"
    return value
}

cleanup('cleanup') {
    println 'cleanup ran'
}

defaults 'melden'
