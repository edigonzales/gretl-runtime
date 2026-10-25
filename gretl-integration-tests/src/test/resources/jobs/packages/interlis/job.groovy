parameter('output', type: 'path', required: true, description: 'Result directory')
parameter('url', required: true, description: 'Controlled function endpoint')
task('gpkg', Ili2gpkgImport) {
    databaseFile = parameter('output').resolve('result.gpkg'); defaultSrsCode = '2056'
    dataFiles = [file('original-gpkg/ch.so.agi.av_gb_admin_einteilung_edit_2020-08-20.xtf')]
    modelDirectories = [file('original-gpkg'), file('models')]
}
task('excel', Csv2Excel) {
    csvFile = file('original-csv/20230124_sap_Gebaeude.csv'); firstLineIsHeader = true
    valueSeparator = ';'; encoding = 'ISO-8859-1'; models = 'SO_HBA_Gebaeude_20230111'
    modeldir = file('original-csv').toString(); outputFile = parameter('output').resolve('converted.xlsx')
}
action('input') { java.nio.file.Files.writeString(parameter('output').resolve('resource.csv'), parameter('url') + '\n') }
task('validate', CsvValidator, dependsOn: 'input') {
    dataFiles = [parameter('output').resolve('resource.csv')]; modelNames = ['FunctionProbe']
    modelDirectories = [file('.')]; firstLineIsHeader = false; logFile = parameter('output').resolve('validation.log')
}
defaults 'gpkg', 'excel', 'validate'
