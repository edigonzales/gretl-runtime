task('echo', Echo) {
    text = 'x'
}

action('read', dependsOn: 'echo') {
    return unknownActionField
}

defaults 'read'
