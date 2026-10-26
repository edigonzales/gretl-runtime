parameter('output', type: 'path', required: true, description: 'GeoPackage output')
parameter('srs', defaultValue: '2056', description: 'Default SRS code')
task('import', Ili2gpkgImport) {
    databaseFile = parameter('output')
    dataFiles = [file('Beispiel2a.xtf')]
    modelNames = ['Beispiel2']
    modelDirectories = [file('models')]
    defaultSrsCode = parameter('srs')
}
defaults 'import'
