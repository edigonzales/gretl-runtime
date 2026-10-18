task('echo', Echo) {
    text = 'x'
}

action('read', dependsOn: 'echo') {
    return result('echo').missing
}

defaults 'read'
