task('producer', Echo) {
    text = 'x'
}

action('raw', dependsOn: 'producer') {
    return 'raw-action-value'
}

action('read', dependsOn: 'raw') {
    return result('raw')
}

defaults 'read'
