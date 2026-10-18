parameter('quelle',
    required: true,
    defaultValue: 'input.txt',
    description: 'Quelldatei der M0-Probe')

task('vorbereiten', 'Prepare') {
    input = 'prepare.txt'
}

task('messen', 'FileSize', dependsOn: 'vorbereiten') {
    input = parameter('quelle')
    note = "Datei: ${input}"
}

task('melden', 'Report', dependsOn: 'messen') { ctx ->
    input = ctx.parameter('quelle')
    summary = ctx.file('summary.txt')
}

defaults 'melden'
