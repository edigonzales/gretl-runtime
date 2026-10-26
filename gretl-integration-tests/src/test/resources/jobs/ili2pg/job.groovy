// tag::reference-parameters[]
parameter('datasetA', defaultValue: 'DatasetA', description: 'Explicit first dataset name')
parameter('schema', required: true, description: 'Isolated PostgreSQL schema')
parameter('output', type: 'path', required: true, description: 'Writable output')
parameter('model', defaultValue: 'Beispiel2', description: 'Original model')
parameter('fixture', defaultValue: 'Ili2pgImportFileSet', description: 'Original resource folder')
parameter('first', defaultValue: 'Beispiel2a.xtf', description: 'First transfer')
parameter('second', defaultValue: 'Beispiel2b.xtf', description: 'Second transfer or empty')
parameter('options', type: 'boolean', defaultValue: false, description: 'Existing SQL representation options')
parameter('disableValidation', type: 'boolean', defaultValue: false, description: 'Import intentionally invalid fixture for validation test')
parameter('validationSelection', defaultValue: 'multiple', description: 'single, multiple, or global validation')
parameter('deleteSelection', defaultValue: 'multiple', description: 'Single or multiple dataset deletion')
parameter('meta', defaultValue: '', description: 'Local meta configuration or repository ID')
parameter('repository', defaultValue: '', description: 'Repository root')
parameter('datasetSubstring', type: 'boolean', defaultValue: false, description: 'Original filename-derived dataset variant')

// end::reference-parameters[]
task('schema', Ili2pgImportSchema) {
    database = 'db'; schema = parameter('schema'); modelNames = parameter('meta') ? [] : [parameter('model')]
    modelDirectories = [file(parameter('fixture')), file('models')]
    if (parameter('repository')) modelDirectories = [file(parameter('fixture')), file('models'), parameter('repository')]
    defaultSrsCode = '2056'; createBasketCol = true; createTidCol = true
    coalesceMultiSurface = true; coalesceMultiLine = true; smart2Inheritance = true; strokeArcs = true
    if (parameter('options')) { sqlExtRefCols = true; sqlColsAsText = true }
    if (parameter('meta')) {
        metaConfig = parameter('meta').startsWith('ilidata:') ? parameter('meta') : file(parameter('fixture') + '/' + parameter('meta'))
    }
}
// tag::reference-import[]
task('import', Ili2pgImport) {
    database = 'db'; schema = parameter('schema'); modelNames = [parameter('model')]
    modelDirectories = [file(parameter('fixture')), file('models')]
    dataFiles = parameter('second') ? [file(parameter('fixture') + '/' + parameter('first')), file(parameter('fixture') + '/' + parameter('second'))] : [file(parameter('fixture') + '/' + parameter('first'))]
    datasetNames = parameter('second') ? [parameter('datasetA'), 'DatasetB'] : [parameter('datasetA')]; importTid = true; importBid = true
    disableValidation = parameter('disableValidation')
    if (parameter('datasetSubstring')) { datasetNames = []; datasetFiles = [file('A_Dataset.txt'), file('B_Dataset.txt')]; datasetSubstring = ['0', '4'] }
}
// end::reference-import[]
// tag::reference-replace[]
task('replace', Ili2pgReplace) {
    database = 'db'; schema = parameter('schema'); modelDirectories = [file(parameter('fixture')), file('models')]
    dataFiles = [file(parameter('fixture') + '/' + parameter('first'))]; datasetNames = [parameter('datasetA')]; importTid = true; importBid = true
}
// end::reference-replace[]
// tag::reference-update[]
task('update', Ili2pgUpdate) {
    database = 'db'; schema = parameter('schema'); modelDirectories = [file(parameter('fixture')), file('models')]
    dataFiles = [file(parameter('fixture') + '/' + parameter('first'))]; datasetNames = [parameter('datasetA')]; importTid = true; importBid = true
}
// end::reference-update[]
// tag::reference-export[]
task('export', Ili2pgExport) {
    modelNames = [parameter('model')]
    database = 'db'; schema = parameter('schema'); modelDirectories = [file(parameter('fixture')), file('models')]
    dataFiles = [parameter('output').resolve('DatasetA.xtf'), parameter('output').resolve('DatasetB.xtf')]
    datasetNames = [parameter('datasetA'), 'DatasetB']
}
// end::reference-export[]
// tag::reference-validate[]
task('validate', Ili2pgValidate) {
    database = 'db'; schema = parameter('schema'); modelNames = [parameter('model')]
    modelDirectories = [file(parameter('fixture')), file('models')]
    datasetNames = parameter('validationSelection') == 'global' ? [] : parameter('validationSelection') == 'single' ? [parameter('datasetA')] : [parameter('datasetA'), 'DatasetB']
    logFile = parameter('output').resolve('validation.log')
}
// end::reference-validate[]
// tag::reference-delete[]
task('delete', Ili2pgDelete) {
    database = 'db'; schema = parameter('schema')
    datasetNames = parameter('deleteSelection') == 'single' ? [parameter('datasetA')] : [parameter('datasetA'), 'DatasetB']
}
// end::reference-delete[]
defaults 'schema'
task('replace-repository', Ili2pgReplace) {
    database = 'db'; schema = parameter('schema'); modelDirectories = [file(parameter('fixture')), file('models')]
    repositoryDataIds = ['ilidata:2549.ch.so.agi.av.mopublic']; datasetNames = ['2549']
}
