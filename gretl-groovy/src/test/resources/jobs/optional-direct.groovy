task('skip', Echo, when: { false }) {
    text = 'never'
}

action('read', dependsOn: 'skip') {
    return result('skip').result
}

defaults 'read'
