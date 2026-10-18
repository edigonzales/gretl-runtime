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
}

task('entpacken', Extract, dependsOn: 'download') {
    archive = result('download').file
    compression = 'ZIP'
    outputDirectory = parameter('ziel')
}

defaults 'entpacken'
