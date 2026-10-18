println 'load-message'

task('echo', Echo) {
    text = 'value'
}

action('say', dependsOn: 'echo') {
    println 'action-message'
    return 'ok'
}

defaults 'say'
