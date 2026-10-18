parameter('file', type: 'path', required: true, description: 'Datei')

task('messen', FileSize) {
    input = parameter('file')
}

action('report', dependsOn: 'messen') {
    return result('messen').bytes
}

defaults 'report'
