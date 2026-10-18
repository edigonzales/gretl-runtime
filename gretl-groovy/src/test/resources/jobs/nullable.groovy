task('nullable', Nullable) {
}

action('read', dependsOn: 'nullable') {
    return result('nullable').maybe
}

defaults 'read'
