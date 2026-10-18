task('skip', Echo, when: { false }) {
    text = 'never'
}

action('read', dependsOn: 'skip') {
    return resultOptional('skip').isPresent() ? 'present' : 'absent'
}

defaults 'read'
