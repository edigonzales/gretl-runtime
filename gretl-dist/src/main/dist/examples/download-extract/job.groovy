parameter('quelle',
    required: true,
    description: 'Adresse der ZIP-Datei')

parameter('ziel',
    type: 'path',
    required: true,
    description: 'Zielverzeichnis für die entpackten Daten')

task('download', HttpDownload) {
    url = parameter('quelle')
    output = workPath('daten.zip')
    // Optional: overwrite = true, um eine vorhandene Zieldatei zu ersetzen.
}

task('entpacken', Extract, dependsOn: 'download') {
    archive = result('download').file
    compression = 'ZIP'
    outputDirectory = parameter('ziel')
}

defaults 'entpacken'
