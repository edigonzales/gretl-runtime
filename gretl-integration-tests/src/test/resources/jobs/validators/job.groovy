parameter('variant', defaultValue: 'CsvValidator', description: 'Fixture variant')
parameter('repository', defaultValue: 'path', description: 'Path or repository string')
parameter('table', defaultValue: 'Attributes', description: 'GeoPackage table')
parameter('log', type: 'path', required: true, description: 'Validation log')
parameter('invalidRepository', type: 'boolean', defaultValue: false, description: 'Invalid repository test')

task('csv', CsvValidator) {
    dataFiles = [file(parameter('variant') + '/' + (parameter('variant') == 'CsvValidator' ? 'data1.csv' : 'dataFail.csv'))]
    modelNames = ['CsvModel']
    modelDirectories = parameter('invalidRepository') ? [42] :
        [parameter('repository') == 'path' ? file(parameter('variant')) : file(parameter('variant')).toString()]
    firstLineIsHeader = false
    logFile = parameter('log')
}
task('gpkg', GpkgValidator) {
    dataFiles = [file(parameter('variant') + '/attributes.gpkg')]
    tableName = parameter('table')
    modelNames = ['GpkgModel']
    modelDirectories = [parameter('repository') == 'path' ? file(parameter('variant')) : file(parameter('variant')).toString()]
    logFile = parameter('log')
}
task('json', JsonValidator) {
    dataFiles = [file(parameter('variant') + '/structAttrList.json')]
    modelNames = ['Test2']
    modelDirectories = [parameter('repository') == 'path' ? file(parameter('variant')) : file(parameter('variant')).toString()]
    logFile = parameter('log')
}
task('shp', ShpValidator) {
    dataFiles = [file(parameter('variant') + '/data.shp')]
    modelNames = ['ShpModel']
    modelDirectories = [parameter('repository') == 'path' ? file(parameter('variant')) : file(parameter('variant')).toString()]
    logFile = parameter('log')
}
defaults 'csv'
task('gpkg-missing-table', GpkgValidator) {
    dataFiles = [file('GpkgValidator/attributes.gpkg')]
    modelNames = ['GpkgModel']
    modelDirectories = [file('GpkgValidator')]
    logFile = parameter('log')
}
task('csv-unsupported-table', CsvValidator) {
    dataFiles = [file('CsvValidator/data1.csv')]
    tableName = 'Attributes'
}
