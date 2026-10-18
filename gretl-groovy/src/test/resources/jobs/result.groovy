parameter('text', required: true, description: 'Text')

task('producer', Echo) {
    text = parameter('text')
}

task('consumer', Echo, dependsOn: 'producer') {
    text = result('producer').result
}

defaults 'consumer'
