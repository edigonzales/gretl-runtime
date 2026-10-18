task('producer', Echo) {
    text = 'producer-value'
}

task('consumer', Echo) {
    text = result('producer').result
}

defaults 'producer', 'consumer'
